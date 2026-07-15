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
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException
import spock.lang.PendingFeature

class ConfigurationReplaySpec extends AbstractDSLSpec {

    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()

    def "resolved JsonProperty name is shared by serialization and configuration replay"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL
            class RenamedValue {
                @JsonProperty("display_name")
                String name
            }
        ''')

        when:
        def deserialized = mapper.readValue('{"display_name":"Ada"}', clazz)

        then:
        deserialized.name == "Ada"
        mapper.writeValueAsString(deserialized) == '{"display_name":"Ada"}'
    }

    def "JsonAlias is accepted as configuration input without changing the serialization name"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonAlias

            @DSL
            class AliasedValue {
                @JsonAlias("legacy_name")
                String name
            }
        ''')

        when:
        def deserialized = mapper.readValue('{"legacy_name":"Ada"}', clazz)

        then:
        deserialized.name == "Ada"
        mapper.writeValueAsString(deserialized) == '{"name":"Ada"}'
    }

    def "configured naming strategy and mixin names are replayed from resolved metadata"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL
            class NamedValue {
                String givenName
                String familyName
            }

            abstract class NamedValueMixin {
                @JsonProperty("surname")
                abstract String getFamilyName()
            }
        ''')
        def configuredMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .addMixIn(clazz, getClass("pk.NamedValueMixin"))
                .findAndRegisterModules()

        when:
        def deserialized = configuredMapper.readValue('{"given_name":"Ada","surname":"Lovelace"}', clazz)

        then:
        deserialized.givenName == "Ada"
        deserialized.familyName == "Lovelace"
        configuredMapper.writeValueAsString(deserialized) == '{"given_name":"Ada","surname":"Lovelace"}'
    }

    def "missing configuration leaves the source initializer intact"() {
        given:
        createClass('''
            package pk

            @DSL
            class InitializedValue {
                String name = "initializer"
            }
        ''')

        expect:
        mapper.readValue('{}', clazz).name == "initializer"
    }

    def "present null clears an initialized nullable scalar"() {
        given:
        createClass('''
            package pk

            @DSL
            class NullableValue {
                String name = "initializer"
            }
        ''')

        expect:
        mapper.readValue('{"name":null}', clazz).name == null
    }

    def "present container authoritatively replaces initialized state for #json"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.klum.ast.util.copy.Overwrite
            import com.blackbuild.klum.ast.util.copy.OverwriteStrategy

            @DSL
            class ContainerValue {
                @Overwrite.Collection(OverwriteStrategy.Collection.ADD)
                List<String> tags = ["initializer"]
            }
        ''')

        expect:
        mapper.readValue(json, clazz).tags == expected

        where:
        json                | expected
        '{"tags":[]}'       | []
        '{"tags":["json"]}' | ["json"]
    }

    def "ignored and read-only properties are not configuration inputs"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonIgnore
            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL
            class InputBoundary {
                @JsonIgnore
                String ignored = "ignored-default"

                @JsonProperty(access = JsonProperty.Access.READ_ONLY)
                String derived = "derived-default"

                String accepted = "accepted-default"
            }
        ''')

        when:
        def deserialized = mapper.readValue(
                '{"ignored":"input","derived":"input","accepted":"json"}', clazz)

        then:
        deserialized.ignored == "ignored-default"
        deserialized.derived == "derived-default"
        deserialized.accepted == "json"

        and:
        mapper.readTree(mapper.writeValueAsString(deserialized)) == mapper.readTree(
                '{"derived":"derived-default","accepted":"json"}')
    }

    def "unknown configuration follows the ObjectMapper policy"() {
        given:
        createClass('''
            package pk

            @DSL
            class UnknownBoundary {
                String name = "initializer"
            }
        ''')

        when:
        mapper.readValue('{"unknown":"value"}', clazz)

        then:
        thrown(UnrecognizedPropertyException)

        when:
        def lenient = mapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        def deserialized = lenient.readValue('{"unknown":"value"}', clazz)

        then:
        deserialized.name == "initializer"
    }

    def "configuration replay recomputes a non-idempotent derived value in one lifecycle"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL
            class LifecycleValue {
                static List<String> events = []
                static int postApplyCount

                String input = "initializer"

                @JsonProperty(access = JsonProperty.Access.READ_ONLY)
                String derived = ""

                @PostCreate
                void afterCreate() {
                    events << "postCreate:$input".toString()
                }

                @PostApply
                void derive() {
                    postApplyCount++
                    events << "postApply:$input".toString()
                    derived += "[$input]"
                }

                @PostTree
                void afterTree() {
                    events << "postTree:$derived".toString()
                }

                @Validate
                void validateModel() {
                    events << "validate:$derived".toString()
                }
            }
        ''')
        def original = clazz.Create.With {
            input "json"
        }
        def json = mapper.writeValueAsString(original)
        clazz.events.clear()
        clazz.postApplyCount = 0

        when:
        def deserialized = mapper.readValue(json, clazz)

        then:
        deserialized.input == "json"
        deserialized.derived == "[json]"
        clazz.postApplyCount == 1
        clazz.events == [
                "postCreate:initializer",
                "postApply:json",
                "postTree:[json]",
                "validate:[json]"
        ]
    }

    def "owned child configuration is replayed as a Builder in the root Construction session"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.klum.ast.util.KlumBuilder
            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL
            class OwnedRoot {
                static List<String> events = []

                OwnedChild child

                @PostTree
                void afterTree() {
                    events << "root-postTree:${child instanceof KlumBuilder}".toString()
                }
            }

            @DSL
            class OwnedChild {
                @JsonProperty("child_name")
                String name = "child-initializer"

                @Owner
                OwnedRoot owner

                @PostCreate
                void afterCreate() {
                    OwnedRoot.events << "child-postCreate:$name".toString()
                }

                @PostApply
                void afterApply() {
                    OwnedRoot.events << "child-postApply:$name".toString()
                }
            }
        ''')

        when:
        def deserialized = mapper.readValue('{"child":{"child_name":"nested"}}', clazz)

        then:
        deserialized.child.name == "nested"
        deserialized.child.owner.is(deserialized)
        clazz.events == [
                "child-postCreate:child-initializer",
                "child-postApply:nested",
                "root-postTree:true"
        ]
    }

    def "explicit type-level custom deserializer opts out of Klum configuration replay"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.core.JsonParser
            import com.fasterxml.jackson.databind.DeserializationContext
            import com.fasterxml.jackson.databind.annotation.JsonDeserialize
            import com.fasterxml.jackson.databind.deser.std.StdDeserializer

            @JsonDeserialize(using = OptOutDeserializer)
            @DSL
            class OptOutValue {
                String value
            }

            class OptOutDeserializer extends StdDeserializer<OptOutValue> {
                OptOutDeserializer() {
                    super(OptOutValue)
                }

                @Override
                OptOutValue deserialize(JsonParser parser, DeserializationContext context) {
                    def tree = parser.codec.readTree(parser)
                    return OptOutValue.Create.With {
                        value "custom:${tree.get('value').asText()}"
                    }
                }
            }
        ''')

        expect:
        mapper.readValue('{"value":"json"}', clazz).value == "custom:json"
    }

    @PendingFeature(reason = "Blocked by #438: completed Templates have no stable identity before companion separation")
    def "marked Templates are rejected as Jackson values"() {
        given:
        createClass('''
            package pk

            @DSL
            class TemplateValue {
                String value
            }
        ''')
        def template = clazz.Template.Create {
            value "recipe"
        }

        when:
        mapper.writeValueAsString(template)

        then:
        thrown(com.fasterxml.jackson.databind.JsonMappingException)
    }

    def "Klum field types define the configurable persistence surface"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.groovy.configdsl.transform.FieldType

            @DSL
            class FieldSurface {
                String input = "input-default"

                @Field(FieldType.PROTECTED)
                String derived = "derived-default"

                @Field(FieldType.IGNORED)
                String hidden = "hidden-default"

                @Field(FieldType.BUILDER)
                String scratch = "scratch-default"
            }
        ''')

        when:
        def deserialized = mapper.readValue('{"input":"json","derived":"attempt"}', clazz)

        then:
        deserialized.input == "json"
        deserialized.derived == "derived-default"

        and:
        mapper.readTree(mapper.writeValueAsString(deserialized)) == mapper.readTree(
                '{"input":"json","derived":"derived-default"}')
    }

    def "ambient Templates do not participate in configuration replay"() {
        given:
        createClass('''
            package pk

            @DSL
            class AmbientTemplateValue {
                String value = "initializer"
            }
        ''')
        def template = clazz.Template.Create {
            value "template"
        }

        when:
        def deserialized = clazz.Template.With(template) {
            mapper.readValue('{}', clazz)
        }

        then:
        deserialized.value == "initializer"
    }
}
