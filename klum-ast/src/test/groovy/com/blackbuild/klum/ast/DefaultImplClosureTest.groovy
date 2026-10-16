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
package com.blackbuild.klum.ast

import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import spock.lang.Issue

@Issue('913')
class DefaultImplClosureTest extends AbstractDSLSpec {

    def "OPTIONAL_LINK explicit subtype closure configures the selected Builder"() {
        given:
        createClass '''
            @DSL class Kafka {
                @Field(FieldType.OPTIONAL_LINK) NamingStrategy topicNaming
            }

            @DSL(defaultImpl = TemplateNamingStrategy)
            abstract class NamingStrategy { }

            @DSL class TemplateNamingStrategy extends NamingStrategy {
                String template
            }

            @DSL class ExplicitNamingStrategy extends NamingStrategy {
                Map<String, String> names
            }
        '''
        def selectedType = getClass('ExplicitNamingStrategy')
        def configuredBuilder
        def returnedBuilder

        when:
        instance = clazz.Create.With {
            returnedBuilder = topicNaming(selectedType) {
                configuredBuilder = delegate
                names jobs: 'existing.jobs'
            }
        }

        then:
        getClass('ExplicitNamingStrategy_DSL$Builder').isInstance(configuredBuilder)
        returnedBuilder.is(configuredBuilder)
        KlumObjectSupport.of(instance.topicNaming).structure.owningRelationship.orElseThrow().name == 'topicNaming'
        selectedType.isInstance(instance.topicNaming)
        instance.topicNaming.names == [jobs: 'existing.jobs']
    }

    def "fresh #path explicit subtype creator configures the selected Builder"() {
        given:
        createClass """
            @DSL class Graph {
                $relationship
            }

            @DSL(defaultImpl = DefaultNode)
            abstract class Node {
                $keyDeclaration
                String value
            }
            @DSL class DefaultNode extends Node { }
            @DSL class SelectedNode extends Node { }
        """
        def client = createSecondaryClass """
            class Client {
                static Graph create() {
                    Graph.Create.With {
                        $creation { value 'selected' }
                    }
                }
            }
        """

        when:
        instance = client.create()
        def child = instance.nodes instanceof Map ? instance.nodes.a :
                instance.nodes instanceof Collection ? instance.nodes.first() : instance.nodes

        then:
        getClass('SelectedNode').isInstance(child)
        child.value == 'selected'

        and: 'Class selectors return the declared Builder and retain their closure target metadata'
        [getClass('Graph_DSL$Builder'), builderClass].every { contract ->
            def creators = contract.declaredMethods.findAll {
                it.name == methodName && it.parameterTypes.toList().contains(Class)
            }
            !creators.empty && creators.every { method ->
                method.genericReturnType.typeName == 'Node_DSL$Builder<Node>' &&
                        method.genericParameterTypes.find { it.typeName.startsWith('java.lang.Class') }.typeName ==
                                'java.lang.Class<? extends Node>'
            } && creators.findAll { it.parameterTypes.last() == Closure }.every { method ->
                def delegation = method.parameters.last().getAnnotation(DelegatesTo)
                delegation.genericTypeIndex() == 0 && delegation.strategy() == Closure.DELEGATE_ONLY &&
                        method.parameters.find { it.type == Class }.getAnnotation(DelegatesTo.Target) != null
            }
        }

        where:
        path            | relationship              | keyDeclaration   | methodName | creation
        'owned'         | 'Node nodes'             | ''               | 'nodes'    | 'nodes(SelectedNode)'
        'keyed owned'   | 'Node nodes'             | '@Key String id' | 'nodes'    | "nodes(SelectedNode, 'a')"
        'List'          | 'List<Node> nodes'       | ''               | 'node'     | 'node(SelectedNode)'
        'keyed List'    | 'List<Node> nodes'       | '@Key String id' | 'node'     | "node(SelectedNode, 'a')"
        'Map'           | 'Map<String, Node> nodes' | '@Key String id' | 'node'     | "node(SelectedNode, 'a')"
    }

    def "implicit default creation and exact Factory selection keep their Builder types"() {
        given:
        namingSchema()
        def defaultBuilder
        def selectedBuilder
        def selectedType = getClass('ExplicitNamingStrategy')
        def selectedFactory = selectedType.Create

        when:
        instance = clazz.Create.With {
            defaultBuilder = topicNaming { template 'default' }
            selectedBuilder = ownedNaming(selectedFactory) { names jobs: 'selected' }
        }

        then:
        getClass('TemplateNamingStrategy_DSL$Builder').isInstance(defaultBuilder)
        getClass('ExplicitNamingStrategy_DSL$Builder').isInstance(selectedBuilder)
        instance.topicNaming.template == 'default'
        instance.ownedNaming.names == [jobs: 'selected']
        def publicBuilder = getClass('Kafka_DSL$Builder')
        def implicitCreator = publicBuilder.getMethod('topicNaming', Closure)
        implicitCreator.genericReturnType.typeName == 'TemplateNamingStrategy_DSL$Builder<TemplateNamingStrategy>'
        implicitCreator.parameters.last().getAnnotation(DelegatesTo).value() == getClass('TemplateNamingStrategy_DSL$Builder')
        publicBuilder.declaredMethods.find { it.name == 'ownedNaming' && it.genericReturnType.typeName == 'B' }
    }

    def "completed subtype OPTIONAL_LINK targets retain identity and Template overrides retain their subtype"() {
        given:
        namingSchema()
        def selectedType = getClass('ExplicitNamingStrategy')
        def completed = selectedType.Create.With { names jobs: 'completed' }
        def recipe = clazz.Create.Template.With {
            topicNaming(selectedType.Create) { names jobs: 'recipe' }
        }
        def kafkaType = clazz

        when:
        def linked = kafkaType.Create.With { topicNaming completed }
        def copied = kafkaType.Create.With { copyFrom recipe }

        then:
        linked.topicNaming.is(completed)
        KlumObjectSupport.of(completed).structure.owningRelationship.empty
        selectedType.isInstance(copied.topicNaming)
        copied.topicNaming.names == [jobs: 'recipe']
        !copied.topicNaming.is(recipe.topicNaming)
    }

    def "ordinary existing-child mismatch guards run before the incompatible closure"() {
        given:
        namingSchema()
        def selectedType = getClass('ExplicitNamingStrategy')
        boolean closureApplied = false

        when:
        clazz.Create.With {
            ownedNaming { template 'already configured' }
            ownedNaming(selectedType) { closureApplied = true }
        }

        then:
        KlumModelException error = thrown()
        error.message.contains('Type mismatch: TemplateNamingStrategy is not compatible with ExplicitNamingStrategy')
        !closureApplied
    }

    def "ScHelm-shaped LinkTo relationship accepts an explicit subtype and preserves automatic fallback"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.layer3.LinkTo

            @DSL class Application {
                Facts facts
                Kafka messaging
            }
            @DSL class Facts { NamingStrategy topicNaming }
            @DSL class Kafka {
                @Owner Application application
                @Field(FieldType.OPTIONAL_LINK)
                @LinkTo(provider = { application.facts }, field = 'topicNaming')
                NamingStrategy topicNaming
            }
            @DSL(defaultImpl = TemplateNamingStrategy)
            abstract class NamingStrategy { }
            @DSL class TemplateNamingStrategy extends NamingStrategy { String template }
            @DSL class ExplicitNamingStrategy extends NamingStrategy { Map<String, String> names }
        '''
        def selectedType = getClass('ExplicitNamingStrategy')
        def applicationType = clazz

        when:
        def explicit = applicationType.Create.With {
            facts { topicNaming { template 'fallback' } }
            messaging {
                topicNaming(selectedType) { names jobs: 'existing.jobs' }
            }
        }
        def automatic = applicationType.Create.With {
            facts { topicNaming { template 'fallback' } }
            messaging { }
        }

        then:
        selectedType.isInstance(explicit.messaging.topicNaming)
        explicit.messaging.topicNaming.names == [jobs: 'existing.jobs']
        explicit.facts.topicNaming.template == 'fallback'
        automatic.messaging.topicNaming.is(automatic.facts.topicNaming)
    }

    def "static Factory selection configures and returns the exact selected public Builder"() {
        given:
        namingSchema()
        def client = createSecondaryClass '''
            import groovy.transform.CompileStatic

            @CompileStatic class Client {
                static Kafka create() {
                    Kafka.Create.With {
                        ExplicitNamingStrategy_DSL.Builder<ExplicitNamingStrategy> selected =
                                topicNaming(ExplicitNamingStrategy.Create) { names jobs: 'selected' }
                        selected.names other: 'another'
                    }
                }
            }
        '''

        when:
        instance = client.create()

        then:
        instance.topicNaming.names == [jobs: 'selected', other: 'another']
    }

    def "ordinary keyed existing-child mismatch guard runs before configuration"() {
        given:
        createClass '''
            @DSL class Graph { Node node }
            @DSL(defaultImpl = DefaultNode) abstract class Node { @Key String id }
            @DSL class DefaultNode extends Node { }
            @DSL class SelectedNode extends Node { }
        '''
        def selectedType = getClass('SelectedNode')
        boolean closureApplied = false

        when:
        clazz.Create.With {
            node(selectedType.Create, 'first') { }
            node(selectedType, 'second') { closureApplied = true }
        }

        then:
        KlumModelException error = thrown()
        error.message.contains('Key mismatch: second != first')
        !closureApplied
    }

    private void namingSchema() {
        createClass '''
            @DSL class Kafka {
                @Field(FieldType.OPTIONAL_LINK) NamingStrategy topicNaming
                NamingStrategy ownedNaming
            }
            @DSL(defaultImpl = TemplateNamingStrategy)
            abstract class NamingStrategy { }
            @DSL class TemplateNamingStrategy extends NamingStrategy { String template }
            @DSL class ExplicitNamingStrategy extends NamingStrategy { Map<String, String> names }
        '''
    }

}
