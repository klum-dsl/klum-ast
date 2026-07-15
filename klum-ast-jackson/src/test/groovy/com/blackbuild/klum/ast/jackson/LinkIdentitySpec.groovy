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
//file:noinspection GrPackage
package com.blackbuild.klum.ast.jackson

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper

class LinkIdentitySpec extends AbstractDSLSpec {

    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()

    def "#direction LINK reference resolves to the same owned model without creating ownership"() {
        given:
        createIdentityGraph()

        when:
        def result = mapper.readValue(json, clazz)

        then:
        result.nodes*.id == ["first", "second"]
        result.nodes[linkSource].link.is(result.nodes[linkTarget])
        result.nodes.every { it.owner.is(result) }

        where:
        direction  | json                                                                                         | linkSource | linkTarget
        "backward" | '{"nodes":[{"id":"first"},{"id":"second","link":"first"}]}'                       | 1          | 0
        "forward"  | '{"nodes":[{"id":"first","link":"second"},{"id":"second"}]}'                     | 0          | 1
    }

    def "LINK serialization is always reference-only when an identity strategy is configured"() {
        given:
        createIdentityGraph()
        def result = mapper.readValue(
                '{"nodes":[{"id":"first"},{"id":"second","link":"first"}]}', clazz)

        expect:
        mapper.readTree(mapper.writeValueAsString(result)) == mapper.readTree(
                '{"nodes":[{"id":"first","link":null},{"id":"second","link":"first"}]}')
    }

    def "inline LINK configuration is rejected with a focused mapping error"() {
        given:
        createIdentityGraph()

        when:
        mapper.readValue('{"nodes":[{"id":"first","link":{"id":"second"}}]}', clazz)

        then:
        JsonMappingException failure = thrown()
        failure.message.contains("LINK property pk.IdentityNode.link must contain a reference id, not an inline object")
    }

    def "non-null LINK output without a reference strategy is rejected"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonIdentityInfo
            import com.fasterxml.jackson.annotation.ObjectIdGenerators

            @DSL
            class UnconfiguredRoot {
                List<UnconfiguredNode> nodes
            }

            @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
            @DSL
            class UnconfiguredNode {
                @Key
                String id

                @Field(FieldType.LINK)
                UnconfiguredNode link
            }
        ''')
        def Node = getClass("pk.UnconfiguredNode")
        def external = Node.Create.With("external") {}
        def result = clazz.Create.With {
            nodes {
                node("second") {
                    link external
                }
            }
        }

        when:
        mapper.writeValueAsString(result)

        then:
        JsonMappingException failure = thrown()
        failure.message.contains("Non-null LINK property pk.UnconfiguredNode.link requires")
        failure.message.contains("JsonIdentityReference(alwaysAsId = true)")
    }

    private void createIdentityGraph() {
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonIdentityInfo
            import com.fasterxml.jackson.annotation.JsonIdentityReference
            import com.fasterxml.jackson.annotation.ObjectIdGenerators

            @DSL
            class IdentityRoot {
                List<IdentityNode> nodes
            }

            @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
            @DSL
            class IdentityNode {
                @Key
                String id

                @Owner
                IdentityRoot owner

                @JsonIdentityReference(alwaysAsId = true)
                @Field(FieldType.LINK)
                IdentityNode link
            }
        ''')
    }
}
