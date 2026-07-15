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
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper

class JacksonCustomizationSpec extends AbstractDSLSpec {

    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()

    def "polymorphic owned DSL subtypes allocate Builders in the root Construction session"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.klum.ast.util.KlumBuilder
            import com.fasterxml.jackson.annotation.JsonSubTypes
            import com.fasterxml.jackson.annotation.JsonTypeInfo

            @DSL
            class Zoo {
                List<Animal> animals
            }

            @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
            @JsonSubTypes([
                @JsonSubTypes.Type(value = Dog, name = "dog"),
                @JsonSubTypes.Type(value = Cat, name = "cat")
            ])
            @DSL
            abstract class Animal {
                static boolean postApplyUsedBuilders = true

                String name

                @Owner
                Zoo owner

                @PostApply
                void recordConstructionState() {
                    postApplyUsedBuilders &= this instanceof KlumBuilder
                }
            }

            @DSL
            class Dog extends Animal {
                int barkVolume
            }

            @DSL
            class Cat extends Animal {
                int lives
            }
        ''')

        when:
        def result = mapper.readValue('''{
            "animals":[
                {"kind":"dog","name":"Ada","barkVolume":3},
                {"kind":"cat","name":"Grace","lives":9}
            ]
        }''', clazz)

        then:
        result.animals*.class*.simpleName == ["Dog", "Cat"]
        result.animals*.name == ["Ada", "Grace"]
        result.animals[0].barkVolume == 3
        result.animals[1].lives == 9
        result.animals.every { it.owner.is(result) }
        getClass("pk.Animal").postApplyUsedBuilders
    }

    def "active views control configuration input and ordinary serialization"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonView

            @DSL
            class ViewedValue {
                @JsonView(PublicView)
                String visible = "visible-default"

                @JsonView(InternalView)
                String internal = "internal-default"

                String unscoped = "unscoped-default"
            }

            class PublicView {}
            class InternalView extends PublicView {}
        ''')
        def PublicView = getClass("pk.PublicView")

        when:
        def result = mapper.readerWithView(PublicView)
                .forType(clazz)
                .readValue('{"visible":"json","internal":"attempt","unscoped":"json-default"}')

        then:
        result.visible == "json"
        result.internal == "internal-default"
        result.unscoped == "json-default"

        and:
        mapper.writerWithView(PublicView).writeValueAsString(result) ==
                '{"visible":"json","unscoped":"json-default"}'

        when:
        def strictViews = mapper.copy().disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
        def strictResult = strictViews.readerWithView(PublicView)
                .forType(clazz)
                .readValue('{"visible":"json","unscoped":"attempt"}')

        then:
        strictResult.visible == "json"
        strictResult.unscoped == "unscoped-default"
    }

    def "formats, inclusion, and Simple Value property codecs remain Jackson customization seams"() {
        given:
        createClass('''
            package pk

            import com.fasterxml.jackson.annotation.JsonFormat
            import com.fasterxml.jackson.annotation.JsonInclude
            import com.fasterxml.jackson.core.JsonGenerator
            import com.fasterxml.jackson.core.JsonParser
            import com.fasterxml.jackson.databind.DeserializationContext
            import com.fasterxml.jackson.databind.JsonDeserializer
            import com.fasterxml.jackson.databind.JsonSerializer
            import com.fasterxml.jackson.databind.SerializerProvider
            import com.fasterxml.jackson.databind.annotation.JsonDeserialize
            import com.fasterxml.jackson.databind.annotation.JsonSerialize

            @DSL
            class CustomizedValue {
                @JsonFormat(pattern = "yyyy/MM/dd", timezone = "UTC")
                Date scheduled

                @JsonInclude(JsonInclude.Include.NON_EMPTY)
                String note = ""

                @JsonSerialize(using = CodeSerializer)
                @JsonDeserialize(using = CodeDeserializer)
                Code code
            }

            class Code {
                String value
            }

            class CodeSerializer extends JsonSerializer<Code> {
                @Override
                void serialize(Code value, JsonGenerator generator, SerializerProvider serializers) {
                    generator.writeString("code:${value.value}")
                }
            }

            class CodeDeserializer extends JsonDeserializer<Code> {
                @Override
                Code deserialize(JsonParser parser, DeserializationContext context) {
                    new Code(value: parser.valueAsString - "code:")
                }
            }
        ''')

        when:
        def result = mapper.readValue('{"scheduled":"2026/07/15","note":"","code":"code:ABC"}', clazz)
        def json = mapper.readTree(mapper.writeValueAsString(result))

        then:
        result.code.value == "ABC"
        json.get("scheduled").asText() == "2026/07/15"
        json.get("code").asText() == "code:ABC"
        !json.has("note")
    }

    def "mixins can provide property value codecs without replacing Klum construction"() {
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
            class MixinValue {
                String text
            }

            abstract class MixinValueMixin {
                @JsonSerialize(using = MixinStringSerializer)
                @JsonDeserialize(using = MixinStringDeserializer)
                abstract String getText()
            }

            class MixinStringSerializer extends JsonSerializer<String> {
                @Override
                void serialize(String value, JsonGenerator generator, SerializerProvider serializers) {
                    generator.writeString("mix:${value.toLowerCase()}")
                }
            }

            class MixinStringDeserializer extends JsonDeserializer<String> {
                @Override
                String deserialize(JsonParser parser, DeserializationContext context) {
                    (parser.valueAsString - "mix:").toUpperCase()
                }
            }
        ''')
        def configuredMapper = new ObjectMapper()
                .addMixIn(clazz, getClass("pk.MixinValueMixin"))
                .findAndRegisterModules()

        when:
        def result = configuredMapper.readValue('{"text":"mix:hello"}', clazz)

        then:
        result.text == "HELLO"
        configuredMapper.writeValueAsString(result) == '{"text":"mix:hello"}'
    }

}
