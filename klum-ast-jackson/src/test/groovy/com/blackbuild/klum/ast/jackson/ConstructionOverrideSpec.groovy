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

class ConstructionOverrideSpec extends AbstractDSLSpec {

    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()

    def "JsonCreator and direct model mutators cannot bypass Builder replay"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonCreator
            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL
            class CreatorBoundary {
                static int creatorCalls
                static int mutatorCalls

                String value = "initializer"

                @JsonCreator
                static CreatorBoundary jacksonFactory(@JsonProperty("value") String value) {
                    creatorCalls++
                    throw new AssertionError("Jackson creator must not run")
                }

                @JsonProperty("value")
                void replaceCompletedValue(String value) {
                    mutatorCalls++
                    throw new AssertionError("completed model mutator must not run")
                }
            }
        ''')

        when:
        def result = mapper.readValue('{"value":"json"}', clazz)

        then:
        result.value == "json"
        clazz.creatorCalls == 0
        clazz.mutatorCalls == 0
    }

    def "Jackson Builder metadata cannot replace the generated Klum Builder"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.databind.annotation.JsonDeserialize
            import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder

            @JsonDeserialize(builder = ForeignJacksonBuilder)
            @DSL
            class BuilderBoundary {
                String value = "initializer"
            }

            @JsonPOJOBuilder(withPrefix = "with")
            class ForeignJacksonBuilder {
                static int calls

                ForeignJacksonBuilder withValue(String value) {
                    calls++
                    throw new AssertionError("foreign Jackson Builder must not run")
                }

                BuilderBoundary build() {
                    calls++
                    throw new AssertionError("foreign Jackson Builder must not run")
                }
            }
        ''')

        when:
        def result = mapper.readValue('{"value":"json"}', clazz)

        then:
        result.value == "json"
        getClass("pk.ForeignJacksonBuilder").calls == 0
    }

    def "owned relationship deserializers cannot inject completed models"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.core.JsonParser
            import com.fasterxml.jackson.databind.DeserializationContext
            import com.fasterxml.jackson.databind.JsonDeserializer
            import com.fasterxml.jackson.databind.annotation.JsonDeserialize

            @DSL
            class OwnedDeserializerRoot {
                @JsonDeserialize(using = CompletedChildDeserializer)
                OwnedDeserializerChild child
            }

            @DSL
            class OwnedDeserializerChild {
                String value

                @Owner
                OwnedDeserializerRoot owner
            }

            class CompletedChildDeserializer extends JsonDeserializer<OwnedDeserializerChild> {
                static int calls

                @Override
                OwnedDeserializerChild deserialize(JsonParser parser, DeserializationContext context) {
                    calls++
                    throw new AssertionError("owned deserializer must not run")
                }
            }
        ''')

        when:
        def result = mapper.readValue('{"child":{"value":"json"}}', clazz)

        then:
        result.child.value == "json"
        result.child.owner.is(result)
        getClass("pk.CompletedChildDeserializer").calls == 0
    }

    def "managed and back references do not replace Klum ownership"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonBackReference
            import com.fasterxml.jackson.annotation.JsonManagedReference

            @DSL
            class ManagedReferenceRoot {
                @JsonManagedReference
                ManagedReferenceChild child
            }

            @DSL
            class ManagedReferenceChild {
                String value

                @JsonBackReference
                @Owner
                ManagedReferenceRoot owner
            }
        ''')

        when:
        def result = mapper.readValue('{"child":{"value":"json","owner":{"child":null}}}', clazz)

        then:
        result.child.value == "json"
        result.child.owner.is(result)
        mapper.writeValueAsString(result) == '{"child":{"value":"json"}}'
    }
}
