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
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.annotation.JsonInclude
import spock.lang.Issue

@Issue('1113')
class StructuredModelTreeQualificationTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    def cleanup() { artifacts?.close() }

    private Class<?> schema(String source, String name) {
        createClass(source)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        return artifacts.loadClass(name)
    }

    @JsonPropertyOrder(['kind', 'package-id', 'version', 'jars', 'root', 'note'])
    private abstract static class DescriptorOrder {}

    private static class KindTokenSerializer extends JsonSerializer<Object> {
        @Override
        void serialize(Object value, JsonGenerator generator, SerializerProvider provider) {
            generator.writeString(value.name().toLowerCase(Locale.ROOT))
        }
    }

    def 'a named descriptor subset reconstructs effective values through caller projection'() {
        given:
        def descriptor = schema('''
            package descriptorqualification
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class ReleaseDescriptor {
                @JsonProperty('package-id') String id
                String version
                PackageKind kind
                List<String> jars
                TypeDeclaration root
                String note
            }
            enum PackageKind {
                @Struct.Name('application') APPLICATION,
                @Struct.Name('operator') OPERATOR
            }
            @DSL class TypeDeclaration {
                String type
                String jar
                @Owner ReleaseDescriptor owner
            }
        ''', 'descriptorqualification.ReleaseDescriptor')
        Map expected = [kind: 'application', 'package-id': 'catalog', version: '1',
                        jars: ['lib/z.jar', 'lib/a.jar'], root: [type: 'example.Catalog', jar: 'lib/a.jar']]
        def original = descriptor.Create.FromStruct(expected)
        def enumModule = new SimpleModule().addSerializer(
                artifacts.loadClass('descriptorqualification.PackageKind'), new KindTokenSerializer())
        def mapper = new ObjectMapper().registerModule(new KlumAstModule()).registerModule(enumModule)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .addMixIn(descriptor, DescriptorOrder)

        when:
        Map tree = mapper.convertValue(original, Map)
        def rebuilt = descriptor.Create.FromStruct(tree)

        then:
        tree == expected
        tree.keySet().toList() == ['kind', 'package-id', 'version', 'jars', 'root']
        rebuilt.root.owner.is(rebuilt)
        !rebuilt.is(original)
        rebuilt.id == 'catalog'
        rebuilt.version == '1'
        rebuilt.kind.name() == 'APPLICATION'
        rebuilt.jars == ['lib/z.jar', 'lib/a.jar']
        rebuilt.root.type == 'example.Catalog'
        rebuilt.root.jar == 'lib/a.jar'
        mapper.convertValue(rebuilt, Map) == expected
    }

    def 'caller numeric policy preserves a large integer and decimal in an ordinary tree'() {
        given:
        createClass('''
            package treequalification
            @DSL class Quantities {
                BigInteger count
                BigDecimal amount
            }
        ''')
        def original = clazz.Create.With {
            count new BigInteger('9007199254740993123456789')
            amount new BigDecimal('12345678901234567890.1234567890123456789')
        }
        def mapper = new ObjectMapper().registerModule(new KlumAstModule())

        when:
        Map tree = mapper.convertValue(mapper.valueToTree(original), Map)

        then:
        tree.count == new BigInteger('9007199254740993123456789')
        tree.amount == new BigDecimal('12345678901234567890.1234567890123456789')
    }
}
