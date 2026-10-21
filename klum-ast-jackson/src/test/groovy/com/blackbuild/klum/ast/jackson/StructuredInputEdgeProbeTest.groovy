/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.klum.ast.jackson

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import org.codehaus.groovy.runtime.InvokerHelper
import spock.lang.Issue

/** Disposable evidence for the ADR 0031 feasibility comparison; no new input grammar. */
@Issue(['932', '529'])
class StructuredInputEdgeProbeTest extends AbstractDSLSpec {

    def 'an authored collection factory accepts external context and rejects absent context'() {
        given:
        createClass '''
            package edgecontext
            import com.blackbuild.klum.ast.runtime.KlumBuilder
            import com.blackbuild.klum.ast.runtime.KlumFactory

            @DSL class ContextRoot { @Field(members = 'child') List<ContextChild> children }
            class SuppliedContext { String value }
            @DSL class ContextChild {
                String value
                @Owner ContextRoot owner
                static class Factory extends KlumFactory.Unkeyed<ContextChild> {
                    protected Factory() { super(ContextChild) }
                    KlumBuilder<ContextChild> supplied(SuppliedContext context) {
                        if (context == null) throw new IllegalArgumentException('supplied context is required')
                        return (KlumBuilder<ContextChild>) (Object) AsBuilder().With(value: context.value)
                    }
                }
            }
        '''
        def root = getClass('edgecontext.ContextRoot')
        def context = getClass('edgecontext.SuppliedContext').newInstance(value: 'external')

        when:
        def model = root.Create.With { children { supplied(context) } }

        then:
        model.children*.value == ['external']
        model.children[0].owner.is(model)
        getClass('edgecontext.ContextRoot_DSL$Builder$CollectionFactory_children')
                .getMethod('supplied', context.class).returnType.name == 'edgecontext.ContextChild_DSL$Builder'

        when:
        root.Create.With { children { supplied(null) } }

        then:
        def missing = thrown(IllegalArgumentException)
        missing.message == 'supplied context is required'
    }

    def 'binary public contracts retain multi argument names while default overloads remain lane evidence'() {
        given:
        createClass '''
            package edgearguments
            @DSL class Endpoint {
                String host
                int port
                @Builder.Method void endpoint(String host, int port = 8080) {
                    this.host = host
                    this.port = port
                }
                @Builder.Method void choose(String value) { host = value }
                @Builder.Method void choose(Integer value) { port = value }
                @Builder.Method void ambiguous(String first, Integer second) { host = first }
                @Builder.Method void ambiguous(Integer first, String second) { host = second }
            }
        '''
        def endpointType = getClass('edgearguments.Endpoint')
        def publicBuilder = getClass('edgearguments.Endpoint_DSL$Builder')
        def operation = publicBuilder.getMethod('endpoint', String, Integer.TYPE)
        def optional = publicBuilder.methods.find { it.name == 'endpoint' && it.parameterCount == 1 }

        when:
        def configured = endpointType.Create.With { endpoint('api', 8443) }
        def defaulted = endpointType.Create.With { endpoint('api') }

        then:
        operation.parameters*.name == ['host', 'port']
        operation.parameters.every { it.namePresent }
        configured.host == 'api'
        configured.port == 8443
        defaulted.port == 8080
        publicBuilder.methods.findAll { it.name == 'choose' }*.parameterTypes*.toList().flatten().toSet() == [String, Integer] as Set
        optional == null || optional.parameterTypes.toList() == [String]

        when:
        endpointType.Create.With { InvokerHelper.invokeMethod(delegate, 'ambiguous', [null, null] as Object[]) }

        then:
        def ambiguous = thrown(RuntimeException)
        ambiguous.message.contains('Ambiguous method overloading')
    }

    def 'Jackson identity camel and kebab naming preserve explicit names and inherited fields'() {
        given:
        createClass '''
            package edgenames
            import com.fasterxml.jackson.annotation.JsonIgnore
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class NamingBase { String inheritedName }
            @DSL class NamingChild extends NamingBase {
                String serviceName
                @JsonProperty('class') String implementationName
                @JsonIgnore String internalName = 'hidden'
            }
        '''
        def type = getClass('edgenames.NamingChild')
        def mapper = new ObjectMapper().setPropertyNamingStrategy(strategy).findAndRegisterModules()
        def input = [(service): 'api', (inherited): 'base', 'class': 'explicit', (ignored): 'attempt']

        when:
        def model = KlumJacksonImporter.using(mapper).readRoot(type, KlumJacksonInput.map(input))

        then:
        model.serviceName == 'api'
        model.inheritedName == 'base'
        model.implementationName == 'explicit'
        model.internalName == 'hidden'
        type.getDeclaredField('implementationName').getAnnotation(JsonProperty).value() == 'class'
        type.getDeclaredField('internalName').isAnnotationPresent(JsonIgnore)
        getClass('edgenames.NamingChild_DSL$Builder').methods*.name.contains('inheritedName')

        where:
        strategy                                   | service        | inherited       | ignored
        null                                       | 'serviceName'  | 'inheritedName' | 'internalName'
        PropertyNamingStrategies.LOWER_CAMEL_CASE   | 'serviceName'  | 'inheritedName' | 'internalName'
        PropertyNamingStrategies.KEBAB_CASE         | 'service-name' | 'inherited-name'| 'internal-name'
    }

    def 'retained operations expose a transformed name collision without choosing a winner'() {
        given:
        createClass '''
            package edgecollision
            @DSL class CollisionBase { String URLValue }
            @DSL class CollisionChild extends CollisionBase { String urlValue }
        '''
        def publicBuilder = getClass('edgecollision.CollisionChild_DSL$Builder')
        def candidates = publicBuilder.methods.findAll {
            it.name in ['URLValue', 'urlValue'] && it.parameterTypes.toList() == [String]
        }

        when:
        def externalNames = candidates.collect {
            PropertyNamingStrategies.KEBAB_CASE.nameForField(null, null, it.name)
        }

        then:
        candidates*.name.toSet() == ['URLValue', 'urlValue'] as Set
        externalNames == ['url-value', 'url-value']
    }

    def 'Jackson keeps outer map keys and explicit child keys as distinct data'() {
        given:
        createClass '''
            package edgekeys
            @DSL class KeyRoot { Map<String, KeyChild> children }
            @DSL class KeyChild { @Key String id; String value }
        '''
        def type = getClass('edgekeys.KeyRoot')
        def importer = KlumJacksonImporter.using(new ObjectMapper().findAndRegisterModules())

        when:
        def model = importer.readRoot(type, KlumJacksonInput.map([children: [outer: [id: 'inner', value: 'data']]]))

        then:
        model.children.keySet() == ['outer'] as Set
        model.children.outer.id == 'inner'
        model.children.outer.value == 'data'
    }

    def 'Jackson discovers children before binding while named calls configure children immediately'() {
        given:
        createClass '''
            package edgeorder
            @DSL class OrderRoot {
                static List<String> events = []
                String value = 'initial'
                OrderChild child
                @PostCreate void created() { events << "root-create:$value".toString() }
                @PostApply void applied() { events << "root-apply:$value".toString() }
            }
            @DSL class OrderChild {
                String value = 'initial'
                @PostCreate void created() { OrderRoot.events << "child-create:$value".toString() }
                @PostApply void applied() { OrderRoot.events << "child-apply:$value".toString() }
            }
        '''
        def type = getClass('edgeorder.OrderRoot')
        def importer = KlumJacksonImporter.using(new ObjectMapper().findAndRegisterModules())

        when:
        importer.readRoot(type, KlumJacksonInput.map([value: 'root', child: [value: 'child']]))
        def firstOrder = new ArrayList(type.events)
        type.events.clear()
        importer.readRoot(type, KlumJacksonInput.map([child: [value: 'child'], value: 'root']))
        def secondOrder = new ArrayList(type.events)
        type.events.clear()
        type.Create.With(value: 'root') { child(value: 'child') }
        def namedOrder = new ArrayList(type.events)

        then:
        firstOrder == ['root-create:initial', 'child-create:initial', 'root-apply:root', 'child-apply:child']
        secondOrder == firstOrder
        namedOrder == ['root-create:initial', 'child-create:initial', 'child-apply:child', 'root-apply:root']
    }
}
