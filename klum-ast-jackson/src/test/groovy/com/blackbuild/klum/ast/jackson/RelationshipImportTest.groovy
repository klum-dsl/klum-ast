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
import com.blackbuild.klum.ast.runtime.KlumException
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.internal.DslHelper
import com.blackbuild.klum.ast.runtime.internal.TemplateManager
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.Issue

@Issue('856')
class RelationshipImportTest extends AbstractDSLSpec {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()

    def 'managed root import records inherited single list set and map declarations without wire metadata'() {
        given:
        schema()
        def importer = KlumJacksonImporter.using(mapper)

        when:
        def result = importer.readRoot(getClass('Graph'), KlumJacksonInput.map(input()))

        then:
        descriptor(result.primary).declaringClass.is(getClass('GraphBase'))
        descriptor(result.primary).name == 'primary'
        descriptor(result.primary).getAnnotation(getClass('Slot')).orElseThrow().value() == 'schema-primary'
        descriptor(result.nodes[0]).name == 'nodes'
        descriptor(result.unique.first()).name == 'unique'
        descriptor(result.indexed['a.b']).name == 'indexed'
        getClass('Node').observed.toSet() == ['primary', 'nodes', 'unique', 'indexed'].toSet()
        KlumObjectSupport.of(result).structure.owningRelationship.empty
        mapper.readTree(mapper.writeValueAsString(result)) == mapper.valueToTree(input())
    }

    def 'readBuilder and applyToBuilder keep recipient placement and lifecycle metadata in the outer session'() {
        given:
        schema()
        def importer = KlumJacksonImporter.using(mapper)
        def graphType = getClass('Graph')
        def data = input()

        when:
        def result = create('Workspace') {
            def imported = importer.readBuilder(graphType.Create.AsBuilder(), KlumJacksonInput.map(data))
            graph = imported
            importer.applyToBuilder(imported, KlumJacksonInput.map([wirePrimary: [id: 'replacement', value: 'applied']]))
        }

        then:
        result.graph.primary.id == 'replacement'
        result.graph.primary.value == 'applied'
        descriptor(result.graph).name == 'graph'
        descriptor(result.graph.primary).name == 'primary'
        descriptor(result.graph.nodes[0]).name == 'nodes'
        descriptor(result.graph.indexed['a.b']).name == 'indexed'
        getClass('Node').observed.count { it == 'primary' } == 1
    }

    def 'value-only Template import retains definition declarations without a lifecycle or public inspection'() {
        given:
        schema()
        def importer = KlumJacksonImporter.using(mapper)

        when:
        def recipe = importer.readTemplate(getClass('Graph'), KlumJacksonInput.map(input()))
        def recipientType = getClass('Recipient')

        then:
        TemplateManager.isTemplate(recipe)
        retained(recipe) == null
        retained(recipe.primary).declaringClass.is(getClass('GraphBase'))
        retained(recipe.primary).name == 'primary'
        retained(recipe.nodes[0]).name == 'nodes'
        retained(recipe.unique.first()).name == 'unique'
        retained(recipe.indexed['a.b']).name == 'indexed'
        getClass('Node').observed.empty

        when:
        KlumObjectSupport.of(recipe.primary)

        then:
        thrown(KlumException)

        when:
        mapper.writeValueAsString(recipe)

        then:
        thrown(JsonMappingException)

        when:
        def result = recipientType.Create.With { actual('recipient') { copyFrom recipe.primary } }

        then:
        result.actual.id == 'recipient'
        descriptor(result.actual).name == 'actual'
        descriptor(result.actual).declaringClass.is(recipientType)
        getClass('Node').observed == ['actual']
    }

    def '#direction reference imports retain owned declarations and exact cyclic target identity'() {
        given:
        createClass '''
            package metadata
            import com.fasterxml.jackson.annotation.JsonIdentityInfo
            import com.fasterxml.jackson.annotation.JsonIdentityReference
            import com.fasterxml.jackson.annotation.ObjectIdGenerators
            @DSL class Graph { List<Node> nodes }
            @DSL @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = 'id')
            class Node {
                @Key String id
                @JsonIdentityReference(alwaysAsId = true) @Field(FieldType.LINK) Node peer
            }
        '''

        when:
        def result = KlumJacksonImporter.using(mapper).readRoot(clazz, KlumJacksonInput.map(data))

        then:
        result.nodes[0].peer.is(result.nodes[1])
        result.nodes[1].peer.is(result.nodes[0])
        result.nodes.every { descriptor(it).name == 'nodes' }
        mapper.readTree(mapper.writeValueAsString(result)) == mapper.valueToTree(data)

        where:
        direction | data
        'forward' | [nodes: [[id: 'a', peer: 'b'], [id: 'b', peer: 'a']]]
        'reverse' | [nodes: [[id: 'b', peer: 'a'], [id: 'a', peer: 'b']]]
    }

    def 'a reference codec preserves an externally completed target original declaration through import and export'() {
        given:
        createClass '''
            package metadata
            import com.fasterxml.jackson.core.JsonParser
            import com.fasterxml.jackson.databind.DeserializationContext
            import com.fasterxml.jackson.databind.JsonDeserializer
            import com.fasterxml.jackson.databind.annotation.JsonDeserialize
            import com.fasterxml.jackson.annotation.JsonIdentityInfo
            import com.fasterxml.jackson.annotation.JsonIdentityReference
            import com.fasterxml.jackson.annotation.ObjectIdGenerators
            @DSL class Provider { Node original }
            @DSL @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = 'id')
            class Node { @Key String id }
            @DSL class Consumer {
                @JsonDeserialize(using = ExistingNodeCodec)
                @JsonIdentityReference(alwaysAsId = true)
                @Field(FieldType.LINK) Node reference
            }
            class ExistingNodeCodec extends JsonDeserializer<Node> {
                static Node target
                @Override Node deserialize(JsonParser parser, DeserializationContext context) { target }
            }
        '''
        def provider = create('Provider') { original('external') {} }
        getClass('ExistingNodeCodec').target = provider.original

        when:
        def result = KlumJacksonImporter.using(mapper).readRoot(getClass('Consumer'), KlumJacksonInput.map([reference: 'external']))

        then:
        result.reference.is(provider.original)
        descriptor(result.reference).name == 'original'
        descriptor(result.reference).declaringClass.is(getClass('Provider'))
        mapper.readTree(mapper.writeValueAsString(result)) == mapper.readTree('{"reference":"external"}')
    }

    private static Object descriptor(Object model) {
        KlumObjectSupport.of(model).structure.owningRelationship.orElseThrow()
    }

    // Test-only inspection of Template state; the public facade must continue rejecting it.
    private static Object retained(Object model) {
        def field = DslHelper.getField(model.class, '$state').orElseThrow()
        field.accessible = true
        def state = field.get(model)
        def declaration = state.class.getDeclaredField('owningRelationship')
        declaration.accessible = true
        declaration.get(state)
    }

    private static Map input() {
        [wirePrimary: [id: 'p', value: 'primary'], nodes: [[id: 'n', value: 'list']],
         unique: [[id: 's', value: 'set']], indexed: [('a.b'): [id: 'a.b', value: 'map']]]
    }

    private void schema() {
        createNonDslClass '''
            package metadata
            import com.fasterxml.jackson.annotation.JsonProperty
            import com.blackbuild.klum.ast.layer3.AutoLink
            import com.blackbuild.klum.ast.runtime.KlumBuilderSupport
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface Slot { String value() }
            @DSL class Node {
                static List<String> observed = []
                @Key String id
                String value
                @AutoLink void inspect() {
                    observed << KlumBuilderSupport.of(this).structure.owningRelationship.orElseThrow().name
                }
            }
            @DSL class GraphBase {
                @Slot('schema-primary') @JsonProperty('wirePrimary') Node primary
                List<Node> nodes
                Set<Node> unique
                Map<String, Node> indexed
            }
            @DSL class Graph extends GraphBase {}
            @DSL class Workspace { Graph graph }
            @DSL class Recipient { Node actual }
        '''
    }
}
