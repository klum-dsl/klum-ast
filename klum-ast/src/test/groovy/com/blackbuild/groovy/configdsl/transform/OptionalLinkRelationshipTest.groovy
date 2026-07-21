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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.util.KlumModelException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue("474")
class OptionalLinkRelationshipTest extends AbstractDSLSpec {

    @Tag("documentary")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Layer3.md#automatic-creation-and-linking")
    def "optional relationships retain local composition and aggregation identity for single List and Map entries"() {
        given:
        createClass '''
            package pk

            import com.blackbuild.klum.ast.util.layer3.annotations.LinkTo

            @DSL class Graph {
                Node owned
                @LinkTo Node optional
                @Field(FieldType.OPTIONAL_LINK) List<Node> optionalNodes
                @Field(value = FieldType.OPTIONAL_LINK, keyMapping = { it.name }) Map<String, Node> optionalNodeMap
                @Field(FieldType.LINK) Node linked
            }

            @DSL class Node { String name }
        '''
        def nodeType = getClass('Node')
        def external = nodeType.Create.With { name 'external' }

        when:
        def ownedBuilder
        instance = clazz.Create.With {
            ownedBuilder = owned { name 'owned' }
            optional = ownedBuilder
            optionalNodes = [ownedBuilder, external]
            optionalNodeMap = [owned: ownedBuilder, external: external]
            linked = ownedBuilder
        }

        then:
        instance.optional.is(instance.owned)
        instance.optionalNodes[0].is(instance.owned)
        instance.optionalNodes[1].is(external)
        instance.optionalNodeMap.owned.is(instance.owned)
        instance.optionalNodeMap.external.is(external)
        instance.linked.is(instance.owned)
    }

    def "LINK rejects an unclaimed Builder and Auto-Link fallback never overwrites a configured relationship"() {
        given:
        createClass '''
            package pk

            @DSL class Graph {
                @Field(FieldType.LINK) Node linked
                @Field(FieldType.OPTIONAL_LINK) Node optional
            }

            @DSL class Node { String name }
        '''
        def nodeType = getClass('Node')
        def fallback = nodeType.Create.With { name 'fallback' }

        when:
        clazz.Create.With {
            linked = nodeType.Create.AsBuilder.With { name 'fresh' }
        }

        then:
        KlumModelException linkError = thrown()
        linkError.message.contains('Fresh Builder inputs are not supported for LINK relationship')

        when:
        clazz.Create.With {
            optional { name 'configured' }
            delegate.link('optional', fallback)
        }

        then:
        KlumModelException fallbackError = thrown()
        fallbackError.message.contains('Cannot add Auto-Link fallback for occupied relationship')
    }
}
