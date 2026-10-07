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
import org.codehaus.groovy.runtime.metaclass.MethodSelectionException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue("846")
class NamedMapNullRelationshipTest extends AbstractDSLSpec {
    def setup() {
        createClass '''
            package pk
            import org.codehaus.groovy.runtime.InvokerHelper
            @DSL class Graph {
                @Field(FieldType.LINK) Node linked
                @Field(FieldType.OPTIONAL_LINK) Node optional
                Node owned
                List<String> names
                String observed
                @Field(FieldType.IGNORED) Node helper
                @Field(FieldType.IGNORED) Node ambiguousHelper
                @Mutator void helper(String value) { observed = value == null ? 'custom-null' : value }
                @Mutator void ambiguousHelper(String value) {}
                @Mutator void ambiguousHelper(Integer value) {}
                @Mutator void accept(String value) { observed = value == null ? 'accepted' : value }
                @Mutator void nested(String value) { InvokerHelper.invokeMethod(new Ambiguous(), 'choose', (Object) null) }
                @Mutator void virtual(String value) { observed = value }
            }
            @DSL class Node { @Key String name }
            @DSL class Container { Graph graph }
            class Ambiguous {
                void choose(String value) {}
                void choose(Integer value) {}
            }
        '''
    }

    def "explicit null relationship selection reports operation Model and path"() {
        when:
        clazz.Create.With([(operation): null])

        then:
        KlumModelException error = thrown()
        error.cause instanceof MethodSelectionException
        error.message.contains(operation)
        error.message.contains('pk.Graph')
        error.message.contains('Construction path: $')
        error.message.contains('Omit the key to preserve existing configuration')
        error.message.contains('explicitly typed setter')
        error.message.contains('Builder closure')

        where:
        operation << ['linked', 'optional', 'owned', 'setLinked', 'setOptional', 'setOwned']
    }

    def "selected custom null mutators and nested selection failures keep normal behavior"() {
        when:
        def accepted = clazz.Create.With([accept: null])
        def helperAccepted = clazz.Create.With([helper: null])

        then:
        accepted.observed == 'accepted'
        helperAccepted.observed == 'custom-null'

        when:
        clazz.Create.With([nested: null])

        then:
        thrown(MethodSelectionException)
    }

    def "selected relationship mutators retain success and nested failures"() {
        given:
        createClass '''
            package pk
            import org.codehaus.groovy.runtime.InvokerHelper
            @DSL class CustomGraph {
                @Field(FieldType.LINK) Node linked
                String observed
                @Mutator void linked() { observed = 'selected' }
                @Mutator void setLinked() {
                    InvokerHelper.invokeMethod(new Ambiguous(), 'choose', (Object) null)
                }
            }
        '''

        expect:
        clazz.Create.With([linked: null]).observed == 'selected'

        when:
        clazz.Create.With([setLinked: null])

        then:
        thrown(MethodSelectionException)
    }

    def "nested relationship selection reports the child construction path"() {
        given:
        def containerType = getClass('Container')
        String childPath

        when:
        containerType.Create.With {
            graph {
                childPath = delegate.breadcrumbPath
                apply([linked: null])
            }
        }

        then:
        KlumModelException error = thrown()
        error.cause instanceof MethodSelectionException
        childPath.contains('graph')
        error.message.contains("Construction path: " + childPath)
    }

    def "ignored DSL helper fields are not reclassified as direct relationships"() {
        when:
        clazz.Create.With([ambiguousHelper: null])

        then:
        thrown(MethodSelectionException)
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/release/4.0.x/docs/user/Basics.md#explicit-null-relationship-values')
    def "omission preserves configuration and typed setters deliberately clear it"() {
        given:
        def node = getClass('Node').Create.With('existing')

        when:
        def preserved = clazz.Create.With {
            linked node
            apply([:])
        }
        def cleared = new GroovyShell(loader).evaluate('''
            import pk.Node
            import pk.Graph
            def existing = Node.Create.With('existing')
            Graph.Create.With {
                linked existing
                apply([:]) { setLinked((Node) null) }
            }
        ''')

        then:
        preserved.linked.is(node)
        cleared.linked == null
    }

    def "non-null Model and Builder inputs retain relationship dispatch"() {
        given:
        def nodeType = getClass('Node')
        def node = nodeType.Create.With('external')

        expect:
        clazz.Create.With([linked: node, optional: node]).linked.is(node)

        when:
        def graph = clazz.Create.With {
            def child = nodeType.Create.AsBuilder().With('owned') {}
            apply([owned: child])
            apply([linked: child, optional: child])
        }

        then:
        graph.linked.is(graph.owned)
        graph.optional.is(graph.owned)
    }

    def "existing unkeyed zero-argument creator selection remains unchanged"() {
        given:
        createClass '''
            package other
            @DSL class Root { Child child }
            @DSL class Child { String name }
        '''

        when:
        def graph = clazz.Create.With([child: null])

        then:
        graph.child != null
    }

    def "collection virtual and unknown keys retain ordinary dispatch"() {
        when:
        def graph = clazz.Create.With([names: ['one'], virtual: 'value'])

        then:
        graph.names == ['one']
        graph.observed == 'value'

        when:
        clazz.Create.With([unknown: null])

        then:
        thrown(MissingMethodException)
    }
}
