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
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonEncoding
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.annotation.JsonInclude
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('1113')
class StructuredModelTreeQualificationTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    def cleanup() { artifacts?.close() }

    private static Map project(ObjectMapper mapper, Object model, String route) {
        return mapper.convertValue(route == 'direct' ? model : mapper.valueToTree(model), Map)
    }

    private static ObjectMapper noTextMapper() {
        return new ObjectMapper(new NoTextFactory()).registerModule(new KlumAstModule())
    }

    private static class NoTextFactory extends JsonFactory {
        @Override
        JsonGenerator createGenerator(OutputStream output, JsonEncoding encoding) {
            throw new UnsupportedOperationException('text encoding is outside this qualifier')
        }

        @Override
        JsonGenerator createGenerator(Writer output) {
            throw new UnsupportedOperationException('text encoding is outside this qualifier')
        }
    }

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

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Jackson-Integration.md#in-memory-effective-value-projection')
    def 'a named descriptor subset reconstructs effective values through caller projection: #route #kind'() {
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
        Map expected = [kind: kind, 'package-id': 'catalog', version: '1',
                        jars: ['lib/z.jar', 'lib/a.jar'], root: [type: 'example.Catalog', jar: 'lib/a.jar']]
        def original = descriptor.Create.FromStruct(expected)
        def enumModule = new SimpleModule().addSerializer(
                artifacts.loadClass('descriptorqualification.PackageKind'), new KindTokenSerializer())
        def mapper = noTextMapper().registerModule(enumModule)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .addMixIn(descriptor, DescriptorOrder)

        when:
        Map tree = project(mapper, original, route)
        def rebuilt = descriptor.Create.FromStruct(tree)

        then:
        tree == expected
        tree.keySet().toList() == ['kind', 'package-id', 'version', 'jars', 'root']
        rebuilt.root.owner.is(rebuilt)
        !rebuilt.is(original)
        rebuilt.id == 'catalog'
        rebuilt.version == '1'
        rebuilt.kind.name() == kind.toUpperCase(Locale.ROOT)
        rebuilt.jars == ['lib/z.jar', 'lib/a.jar']
        rebuilt.root.type == 'example.Catalog'
        rebuilt.root.jar == 'lib/a.jar'
        project(mapper, rebuilt, route) == expected
        project(mapper, descriptor.Create.FromStruct(project(mapper, rebuilt, route)), route) == expected

        where:
        [route, kind] << [['direct', 'via-node'], ['application', 'operator']].combinations()
    }

    def 'in-memory routes preserve selected exact integer and decimal values: #route'() {
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
        def mapper = noTextMapper()

        when:
        Map tree = project(mapper, original, route)

        then:
        tree.count == new BigInteger('9007199254740993123456789')
        tree.amount == new BigDecimal('12345678901234567890.1234567890123456789')
        tree.count instanceof BigInteger
        tree.amount instanceof BigDecimal

        where:
        route << ['direct', 'via-node']
    }

    def 'the qualifier rejects accidental text encoding at its public factory boundary'() {
        given:
        def mapper = noTextMapper()

        when:
        mapper.writeValueAsString([id: 'catalog'])

        then:
        thrown(UnsupportedOperationException)

        when:
        mapper.writeValueAsBytes([id: 'catalog'])

        then:
        thrown(UnsupportedOperationException)
    }

    def 'caller inclusion preserves supplied null or omits it without inventing authored absence: #route'() {
        given:
        def type = schema('''
            package nullqualification
            @DSL class Service { String host }
        ''', 'nullqualification.Service')
        def omitted = type.Create.FromStruct([:])
        def explicitNull = type.Create.With { host((String) null) }
        def mapper = noTextMapper()

        when:
        Map present = project(mapper, omitted, route)
        Map suppliedNull = project(mapper, explicitNull, route)

        then:
        present.containsKey('host')
        present.host == null
        suppliedNull == present

        when:
        type.Create.FromStruct(present)

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/host')

        when:
        // A fresh caller mapper avoids changing inclusion after serializers have been cached.
        def excluding = noTextMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL)
        Map absent = project(excluding, explicitNull, route)
        def rebuilt = type.Create.FromStruct(absent)

        then:
        !absent.containsKey('host')
        rebuilt.host == null

        where:
        route << ['direct', 'via-node']
    }

    @JsonPropertyOrder(['service-name'])
    private abstract static class ServiceNameProjection {
        @JsonProperty('service-name') abstract String getServiceName()
    }

    def 'Struct naming is independent and caller mixins can align one chosen projection'() {
        given:
        def type = schema('''
            package namequalification
            @DSL class Service { @Struct.Name('service-name') String serviceName }
        ''', 'namequalification.Service')
        def original = type.Create.FromStruct(['service-name': 'catalog'])
        def plain = noTextMapper()
        Map nativeNames = project(plain, original, 'direct')

        when:
        type.Create.FromStruct(nativeNames)

        then:
        nativeNames == [serviceName: 'catalog']
        thrown(KlumModelException)

        when:
        def aligned = noTextMapper().addMixIn(type, ServiceNameProjection)
        Map externalNames = project(aligned, original, 'direct')
        def rebuilt = type.Create.FromStruct(externalNames)

        then:
        externalNames == ['service-name': 'catalog']
        rebuilt.serviceName == 'catalog'
        project(plain, original, 'direct') == [serviceName: 'catalog']
    }

}
