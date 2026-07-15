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
        result.nodes.every { it.role != "input" }
        getClass("pk.IdentityNode").postApplySawOnlyBuilders

        where:
        direction  | json                                                                                         | linkSource | linkTarget
        "backward" | '{"nodes":[{"id":"first","owner":"input","role":"input"},{"id":"second","link":"first"}]}' | 1 | 0
        "forward"  | '{"nodes":[{"id":"first","link":"second"},{"id":"second","role":"input"}]}'                 | 0 | 1
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

    def "generator identity supports forward references without exposing the synthetic id as configuration"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonIdentityInfo
            import com.fasterxml.jackson.annotation.JsonIdentityReference
            import com.fasterxml.jackson.annotation.ObjectIdGenerators

            @DSL
            class GeneratedIdentityRoot {
                List<GeneratedIdentityNode> nodes
            }

            @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator)
            @DSL
            class GeneratedIdentityNode {
                String name

                @JsonIdentityReference(alwaysAsId = true)
                @Field(FieldType.LINK)
                GeneratedIdentityNode link
            }
        ''')

        when:
        def result = mapper.readValue(
                '{"nodes":[{"@id":1,"name":"first","link":2},{"@id":2,"name":"second"}]}', clazz)

        then:
        result.nodes[0].link.is(result.nodes[1])
        mapper.readTree(mapper.writeValueAsString(result)).get("nodes").get(0).get("link").isIntegralNumber()
    }

    def "a custom ObjectIdResolver can supply an existing completed LINK target"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonIdentityInfo
            import com.fasterxml.jackson.annotation.JsonIdentityReference
            import com.fasterxml.jackson.annotation.ObjectIdGenerator
            import com.fasterxml.jackson.annotation.ObjectIdGenerators
            import com.fasterxml.jackson.annotation.ObjectIdResolver

            @DSL
            class ExternalRoot {
                @JsonIdentityReference(alwaysAsId = true)
                @Field(FieldType.LINK)
                ExternalNode node
            }

            @JsonIdentityInfo(
                generator = ObjectIdGenerators.PropertyGenerator,
                property = "id",
                resolver = ExistingNodeResolver
            )
            @DSL
            class ExternalNode {
                @Key
                String id
            }

            class ExistingNodeResolver implements ObjectIdResolver {
                static Map<Object, Object> values = [:]

                @Override
                void bindItem(ObjectIdGenerator.IdKey id, Object value) {
                    values[id.key] = value
                }

                @Override
                Object resolveId(ObjectIdGenerator.IdKey id) {
                    values[id.key]
                }

                @Override
                ObjectIdResolver newForDeserialization(Object context) {
                    this
                }

                @Override
                boolean canUseFor(ObjectIdResolver resolver) {
                    resolver.class == getClass()
                }
            }
        ''')
        def Node = getClass("pk.ExternalNode")
        def Resolver = getClass("pk.ExistingNodeResolver")
        def external = Node.Create.With("external") {}
        Resolver.values.external = external

        when:
        def result = mapper.readValue('{"node":"external"}', clazz)

        then:
        result.node.is(external)
        mapper.writeValueAsString(result) == '{"node":"external"}'
    }

    def "LINK collections and maps preserve reference identity and container shape"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonIdentityInfo
            import com.fasterxml.jackson.annotation.JsonIdentityReference
            import com.fasterxml.jackson.annotation.ObjectIdGenerators

            @DSL
            class LinkContainerRoot {
                List<LinkContainerNode> nodes

                @JsonIdentityReference(alwaysAsId = true)
                @Field(FieldType.LINK)
                List<LinkContainerNode> related

                @JsonIdentityReference(alwaysAsId = true)
                @Field(FieldType.LINK)
                Map<String, LinkContainerNode> indexed
            }

            @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
            @DSL
            class LinkContainerNode {
                @Key
                String id

                @Owner
                LinkContainerRoot owner
            }
        ''')

        when:
        def result = mapper.readValue('''{
            "nodes":[{"id":"first"},{"id":"second"}],
            "related":["second","first"],
            "indexed":{"primary":"first","secondary":"second"}
        }''', clazz)

        then:
        result.related[0].is(result.nodes[1])
        result.related[1].is(result.nodes[0])
        result.indexed.primary.is(result.nodes[0])
        result.indexed.secondary.is(result.nodes[1])
        result.related.every { it.owner.is(result) }

        when:
        def json = mapper.readTree(mapper.writeValueAsString(result))

        then:
        json.get("related") == mapper.readTree('["second","first"]')
        json.get("indexed") == mapper.readTree('{"primary":"first","secondary":"second"}')
    }

    def "custom LINK property codecs can resolve and write an explicit reference format"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.core.JsonGenerator
            import com.fasterxml.jackson.core.JsonParser
            import com.fasterxml.jackson.databind.DeserializationContext
            import com.fasterxml.jackson.databind.JsonDeserializer
            import com.fasterxml.jackson.databind.JsonSerializer
            import com.fasterxml.jackson.databind.SerializerProvider
            import com.fasterxml.jackson.databind.annotation.JsonDeserialize
            import com.fasterxml.jackson.databind.annotation.JsonSerialize

            @DSL
            class CustomReferenceRoot {
                @JsonSerialize(using = NodeReferenceSerializer)
                @JsonDeserialize(using = NodeReferenceDeserializer)
                @Field(FieldType.LINK)
                CustomReferenceNode node
            }

            @DSL
            class CustomReferenceNode {
                @Key
                String id
            }

            class NodeReferenceSerializer extends JsonSerializer<CustomReferenceNode> {
                @Override
                void serialize(CustomReferenceNode value, JsonGenerator generator, SerializerProvider serializers) {
                    generator.writeString("node:${value.id}")
                }
            }

            class NodeReferenceDeserializer extends JsonDeserializer<CustomReferenceNode> {
                static Map<String, CustomReferenceNode> nodes = [:]

                @Override
                CustomReferenceNode deserialize(JsonParser parser, DeserializationContext context) {
                    nodes[parser.valueAsString - "node:"]
                }
            }
        ''')
        def Node = getClass("pk.CustomReferenceNode")
        def Codec = getClass("pk.NodeReferenceDeserializer")
        def external = Node.Create.With("external") {}
        Codec.nodes.external = external

        when:
        def result = mapper.readValue('{"node":"node:external"}', clazz)

        then:
        result.node.is(external)
        mapper.writeValueAsString(result) == '{"node":"node:external"}'
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

    def "non-null LINK input without a reference strategy is rejected"() {
        given:
        createClass('''
            package pk

            @DSL
            class MissingInputStrategy {
                @Field(FieldType.LINK)
                MissingInputTarget target
            }

            @DSL
            class MissingInputTarget {
                @Key
                String id
            }
        ''')

        when:
        mapper.readValue('{"target":"external"}', clazz)

        then:
        JsonMappingException failure = thrown()
        failure.message.contains("Non-null LINK property pk.MissingInputStrategy.target requires")
        failure.message.contains("JsonIdentityReference(alwaysAsId = true)")
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

    def "array-shaped output cannot bypass the LINK reference strategy check"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonFormat

            @JsonFormat(shape = JsonFormat.Shape.ARRAY)
            @DSL
            class ArrayShapedLink {
                String name

                @Field(FieldType.LINK)
                ArrayShapedTarget target
            }

            @DSL
            class ArrayShapedTarget {
                String name
            }
        ''')
        def Target = getClass("pk.ArrayShapedTarget")
        def external = Target.Create.With { name "external" }
        def result = clazz.Create.With {
            name "root"
            target external
        }

        when:
        mapper.writeValueAsString(result)

        then:
        JsonMappingException failure = thrown()
        failure.message.contains("Non-null LINK property pk.ArrayShapedLink.target requires")
    }

    def "always-as-id output without target identity is rejected"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonIdentityReference

            @DSL
            class IncompleteOutputStrategy {
                @JsonIdentityReference(alwaysAsId = true)
                @Field(FieldType.LINK)
                IncompleteOutputTarget target
            }

            @DSL
            class IncompleteOutputTarget {
                String name
            }
        ''')
        def Target = getClass("pk.IncompleteOutputTarget")
        def external = Target.Create.With { name "external" }
        def result = clazz.Create.With { target external }

        when:
        mapper.writeValueAsString(result)

        then:
        JsonMappingException failure = thrown()
        failure.message.contains("Non-null LINK property pk.IncompleteOutputStrategy.target requires")
    }

    private void createIdentityGraph() {
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonIdentityInfo
            import com.fasterxml.jackson.annotation.JsonIdentityReference
            import com.fasterxml.jackson.annotation.ObjectIdGenerators
            import com.blackbuild.klum.ast.util.KlumBuilder

            @DSL
            class IdentityRoot {
                List<IdentityNode> nodes
            }

            @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
            @DSL
            class IdentityNode {
                static boolean postApplySawOnlyBuilders = true

                @Key
                String id

                @Owner
                IdentityRoot owner

                @Role
                String role

                @JsonIdentityReference(alwaysAsId = true)
                @Field(FieldType.LINK)
                IdentityNode link

                @PostApply
                void recordLinkState() {
                    postApplySawOnlyBuilders &= link == null || link instanceof KlumBuilder
                }
            }
        ''')
    }
}
