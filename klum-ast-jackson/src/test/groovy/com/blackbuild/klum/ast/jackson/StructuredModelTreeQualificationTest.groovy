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
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.annotation.JsonSerialize
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

    @JsonPropertyOrder(['type', 'jar'])
    private abstract static class TypeOrder {}

    private static class KindTokenSerializer extends JsonSerializer<Object> {
        @Override
        void serialize(Object value, JsonGenerator generator, SerializerProvider provider) {
            generator.writeString(value.name().toLowerCase(Locale.ROOT))
        }
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Jackson-Integration.md#in-memory-effective-value-projection')
    def 'a named descriptor subset reconstructs effective values through caller projection: #route #kind #note'() {
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
        if (note != null) expected.note = note
        def original = descriptor.Create.FromStruct(expected)
        def enumModule = new SimpleModule().addSerializer(
                artifacts.loadClass('descriptorqualification.PackageKind'), new KindTokenSerializer())
        def mapper = noTextMapper().registerModule(enumModule)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .addMixIn(descriptor, DescriptorOrder)
                .addMixIn(artifacts.loadClass('descriptorqualification.TypeDeclaration'), TypeOrder)

        when:
        Map tree = project(mapper, original, route)
        def rebuilt = descriptor.Create.FromStruct(tree)

        then:
        tree == expected
        tree instanceof LinkedHashMap
        tree.keySet().toList() == (note == null
                ? ['kind', 'package-id', 'version', 'jars', 'root']
                : ['kind', 'package-id', 'version', 'jars', 'root', 'note'])
        tree.root instanceof LinkedHashMap
        tree.root.keySet().toList() == ['type', 'jar']
        rebuilt.root.owner.is(rebuilt)
        !rebuilt.is(original)
        rebuilt.id == 'catalog'
        rebuilt.version == '1'
        rebuilt.note == note
        rebuilt.kind.name() == kind.toUpperCase(Locale.ROOT)
        rebuilt.jars == ['lib/z.jar', 'lib/a.jar']
        rebuilt.root.type == 'example.Catalog'
        rebuilt.root.jar == 'lib/a.jar'
        project(mapper, rebuilt, route) == expected
        project(mapper, descriptor.Create.FromStruct(project(mapper, rebuilt, route)), route) == expected

        where:
        [route, kind, note] << [['direct', 'via-node'], ['application', 'operator'], [null, 'release note']].combinations()
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

    def 'root and direct keys survive but keyed Map bodies need caller positional adaptation'() {
        given:
        def type = schema('''
            package keyqualification
            @DSL class Catalog {
                @Key String name
                Service direct
                Map<String, Service> services
            }
            @DSL class Service {
                @Key String name
                int port
                @Owner Catalog owner
            }
        ''', 'keyqualification.Catalog')
        def original = type.Create.FromStruct([name: 'catalog', direct: [name: 'main', port: 9090],
                                              services: [api: [port: 8080]]])
        Map tree = project(noTextMapper(), original, 'direct')

        when:
        type.Create.FromStruct(tree)

        then:
        tree.name == 'catalog'
        tree.direct == [name: 'main', port: 9090]
        tree.services.api == [name: 'api', port: 8080]
        def failure = thrown(KlumModelException)
        failure.message.contains('/services/api/name')

        when:
        // Deliberate adaptation of this one known relationship, not a generic tree repair.
        tree.services.api.remove('name')
        def rebuilt = type.Create.FromStruct(tree)

        then:
        rebuilt.services.api.name == 'api'
        rebuilt.services.api.port == 8080
        rebuilt.services.api.owner.is(rebuilt)
        rebuilt.direct.name == 'main'
        rebuilt.direct.owner.is(rebuilt)

        when:
        type.Create.FromStruct('catalog', tree)

        then:
        thrown(KlumModelException)

        when:
        Map body = new LinkedHashMap(tree)
        body.remove('name')
        def explicitRoot = type.Create.FromStruct('catalog', body)

        then:
        explicitRoot.name == 'catalog'
        explicitRoot.direct.name == 'main'
    }

    def 'effective defaults and seeded collection population do not recover authoring input'() {
        given:
        def type = schema('''
            package defaultqualification
            @DSL class Service {
                @Default(code = { 'local' }) String host
                List<String> names = ['base']
            }
        ''', 'defaultqualification.Service')
        def original = type.Create.FromStruct([:])
        Map tree = project(noTextMapper(), original, 'direct')

        when:
        def rebuilt = type.Create.FromStruct(tree)
        def emptyPopulation = type.Create.FromStruct([names: []])

        then:
        tree == [host: 'local', names: ['base']]
        original.host == 'local'
        rebuilt.host == 'local'
        rebuilt.names == ['base', 'base']
        emptyPopulation.names == ['base']
        original.names == ['base']
    }

    def 'derived output is effective state and dropping it does not undo non-idempotent lifecycle'() {
        given:
        def type = schema('''
            package lifecyclequalification
            @DSL class Service {
                String label
                @Field(FieldType.PROTECTED) String summary
                @PostApply void completeLabel() {
                    label += '!'
                    summary = label.toUpperCase()
                }
            }
        ''', 'lifecyclequalification.Service')
        def original = type.Create.FromStruct([label: 'catalog'])
        Map tree = project(noTextMapper(), original, 'direct')

        when:
        type.Create.FromStruct(tree)

        then:
        tree == [label: 'catalog!', summary: 'CATALOG!']
        thrown(KlumModelException)

        when:
        Map writable = new LinkedHashMap(tree)
        writable.remove('summary')
        def rebuilt = type.Create.FromStruct(writable)

        then:
        rebuilt.label == 'catalog!!'
        rebuilt.summary == 'CATALOG!!'
        original.label == 'catalog!'
    }

    def 'a converter value projection is not automatically its String operation argument'() {
        given:
        def type = schema('''
            package converterqualification
            @DSL class Service {
                @Field(converters = [{ String raw -> new Slug(raw.toLowerCase()) }]) Slug slug
            }
            class Slug {
                final String value
                Slug(String value) { this.value = value }
            }
        ''', 'converterqualification.Service')
        def original = type.Create.FromStruct([slug: 'CATALOG'])
        Map tree = project(noTextMapper(), original, 'direct')

        when:
        type.Create.FromStruct(tree)

        then:
        tree == [slug: [value: 'catalog']]
        def failure = thrown(KlumModelException)
        failure.message.contains('/slug')
        failure.message.contains('String')
    }

    def 'effective scalar equality does not replay an authored command'() {
        given:
        def type = schema('''
            package commandqualification
            @DSL class Service {
                static List<String> events = []
                String name
                @Builder.Method void activate() { events << 'activated' }
            }
        ''', 'commandqualification.Service')
        def original = type.Create.FromStruct([name: 'catalog', activate: [:]])
        Map tree = project(noTextMapper(), original, 'direct')

        when:
        def rebuilt = type.Create.FromStruct(tree)

        then:
        tree == [name: 'catalog']
        rebuilt.name == original.name
        type.events == ['activated']
    }
    private static class NodeIdSerializer extends JsonSerializer<Object> {
        @Override
        void serialize(Object value, JsonGenerator generator, SerializerProvider provider) {
            generator.writeString(value.id)
        }
    }

    private abstract static class LinkProjection {
        @JsonSerialize(using = NodeIdSerializer) abstract Object getSelected()
    }

    def 'LINK output requires a caller choice and its id is not a Struct reference instruction'() {
        given:
        def type = schema('''
            package linkqualification
            @DSL class Selection { @Field(FieldType.LINK) Node selected }
            @DSL class Node { @Key String id; String value }
        ''', 'linkqualification.Selection')
        def node = artifacts.loadClass('linkqualification.Node').Create.With('external') { value 'existing' }
        def original = type.Create.With { selected node }

        when:
        project(noTextMapper(), original, 'direct')

        then:
        def failure = thrown(IllegalArgumentException)
        failure.message.contains('selected')

        when:
        def mapper = noTextMapper().addMixIn(type, LinkProjection)
        Map tree = project(mapper, original, 'direct')
        type.Create.FromStruct(tree)

        then:
        tree == [selected: 'external']
        thrown(KlumModelException)
        original.selected.is(node)
    }

    def 'Jackson polymorphic output remains distinct from Struct concrete-owned admission'() {
        given:
        def type = schema('''
            package polymorphicqualification
            import com.fasterxml.jackson.annotation.JsonSubTypes
            import com.fasterxml.jackson.annotation.JsonTypeInfo
            @DSL class Zoo { Animal animal }
            @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = 'kind')
            @JsonSubTypes([@JsonSubTypes.Type(value = Dog, name = 'dog')])
            @DSL abstract class Animal { String name }
            @DSL class Dog extends Animal { int volume }
        ''', 'polymorphicqualification.Zoo')
        def dog = artifacts.loadClass('polymorphicqualification.Dog')
        def original = type.Create.With { animal(dog) { name 'Ada'; volume 3 } }
        def mapper = noTextMapper()
        Map tree = project(mapper, original, 'direct')

        when:
        type.Create.FromStruct(tree)

        then:
        tree == [animal: [kind: 'dog', name: 'Ada', volume: 3]]
        thrown(KlumModelException)

        when:
        def imported = KlumJacksonImporter.using(mapper).readRoot(type, KlumJacksonInput.tree(mapper.valueToTree(original)))

        then:
        imported.animal.class == dog
        imported.animal.name == 'Ada'
        imported.animal.volume == 3
    }

    def 'in-memory output retains the ordinary marked Template rejection'() {
        given:
        def type = schema('''
            package templatequalification
            @DSL class Service { String name }
        ''', 'templatequalification.Service')
        def template = type.Create.Template.FromStruct([name: 'recipe'])

        when:
        project(noTextMapper(), template, route)

        then:
        def failure = thrown(IllegalArgumentException)
        failure.message.contains('marked Template')

        where:
        route << ['direct', 'via-node']
    }

    @JsonPropertyOrder(['labels', 'names'])
    private abstract static class OrderedProjection {}

    def 'fixed caller ordering retains nested Map and List traversal independently: #route'() {
        given:
        def type = schema('''
            package orderqualification
            @DSL class Service { List<String> names; Map<String, String> labels }
        ''', 'orderqualification.Service')
        def original = type.Create.FromStruct([names: ['z', 'a'], labels: [z: 'last', a: 'first']])
        def mapper = noTextMapper().addMixIn(type, OrderedProjection)

        when:
        List<Map> trees = (1..3).collect { project(mapper, original, route) }
        def sorted = noTextMapper().addMixIn(type, OrderedProjection)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
        Map sortedTree = project(sorted, original, route)

        then:
        trees.every { it instanceof LinkedHashMap && it.keySet().toList() == ['labels', 'names'] }
        trees.every { it.labels instanceof LinkedHashMap && it.labels.keySet().toList() == ['z', 'a'] }
        trees.every { it.names == ['z', 'a'] }
        sortedTree.labels.keySet().toList() == ['a', 'z']
        sortedTree.names == ['z', 'a']
        original.labels.keySet().toList() == ['z', 'a']

        where:
        route << ['direct', 'via-node']
    }
    def 'a native scalar and container subset preserves effective values on re-entry: #route'() {
        given:
        def type = schema('''
            package scalarqualification
            @DSL class Values {
                boolean enabled
                int count
                String text
                BigInteger total
                BigDecimal amount
                List<BigDecimal> samples
                Map<String, BigInteger> bins
            }
        ''', 'scalarqualification.Values')
        Map expected = [enabled: false, count: 0, text: 'a\u0085b-世界-😀',
                        total: new BigInteger('9007199254740993123456789'),
                        amount: new BigDecimal('12345678901234567890.1234567890123456789'),
                        samples: [new BigDecimal('0.00000000000000000000001'), new BigDecimal('2.50')],
                        bins: [z: new BigInteger('9007199254740993123456789'), a: BigInteger.ZERO]]
        def original = type.Create.FromStruct(expected)
        def mapper = noTextMapper()

        when:
        Map tree = project(mapper, original, route)
        def rebuilt = type.Create.FromStruct(tree)

        then:
        tree == expected
        tree.bins.keySet().toList() == ['z', 'a']
        tree.samples.every { it instanceof BigDecimal }
        rebuilt.enabled == false
        rebuilt.count == 0
        rebuilt.text == 'a\u0085b-世界-😀'
        rebuilt.total == new BigInteger('9007199254740993123456789')
        rebuilt.amount == new BigDecimal('12345678901234567890.1234567890123456789')
        rebuilt.samples == [new BigDecimal('0.00000000000000000000001'), new BigDecimal('2.50')]
        rebuilt.bins == [z: new BigInteger('9007199254740993123456789'), a: BigInteger.ZERO]
        project(mapper, rebuilt, route) == expected

        where:
        route << ['direct', 'via-node']
    }

    def 'preexisting finite binary floats retain selected numeric values without decimal recovery: #route #number'() {
        given:
        createClass('''
            package binaryqualification
            @DSL class Reading { Object value }
        ''')
        def original = clazz.Create.With { value(number) }

        when:
        Map tree = project(noTextMapper(), original, route)

        then:
        tree.value instanceof Number
        tree.value.doubleValue() == expectedDouble

        where:
        [route, number, expectedDouble] << [
                ['direct', Float.valueOf('0.1'), 0.10000000149011612d],
                ['via-node', Float.valueOf('0.1'), 0.10000000149011612d],
                ['direct', Double.valueOf('0.1'), 0.1d],
                ['via-node', Double.valueOf('0.1'), 0.1d]]
    }

    def 'successful in-memory conversion does not reject nonfinite numbers: #route #number'() {
        given:
        def type = schema('''
            package nonfinitequalification
            @DSL class Reading { double value }
        ''', 'nonfinitequalification.Reading')
        def original = type.Create.With { value(number) }
        Map tree = project(noTextMapper(), original, route)

        when:
        type.Create.FromStruct(tree)

        then:
        tree.value instanceof Double
        !Double.isFinite(tree.value)
        thrown(KlumModelException)

        where:
        [route, number] << [['direct', 'via-node'],
                           [Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY]].combinations()
    }

    private static class RoundedDecimalSerializer extends JsonSerializer<BigDecimal> {
        @Override
        void serialize(BigDecimal value, JsonGenerator generator, SerializerProvider provider) {
            generator.writeNumber(value.doubleValue())
        }
    }

    def 'caller codecs can silently lose precision despite successful conversion: #route'() {
        given:
        createClass('''
            package codecqualification
            @DSL class Quantity { BigDecimal amount }
        ''')
        def original = clazz.Create.With {
            amount new BigDecimal('12345678901234567890.1234567890123456789')
        }
        def codec = new SimpleModule().addSerializer(BigDecimal, new RoundedDecimalSerializer())
        def mapper = noTextMapper().registerModule(codec)

        when:
        Map tree = project(mapper, original, route)

        then:
        tree.amount instanceof Double
        new BigDecimal(tree.amount.toString()) != new BigDecimal('12345678901234567890.1234567890123456789')
        original.amount == new BigDecimal('12345678901234567890.1234567890123456789')

        where:
        route << ['direct', 'via-node']
    }

    def 'repeated acyclic values are structural output without shared identity: #route'() {
        given:
        createClass('''
            package aliasqualification
            @DSL class Values { Object left; Object getRight() { left } }
        ''')
        List shared = [[name: 'catalog', note: null, enabled: false, count: 0, text: '']]
        def original = clazz.Create.With { left(shared) }

        when:
        Map tree = project(noTextMapper(), original, route)

        then:
        original.left.is(original.right)
        tree == [left: [[name: 'catalog', note: null, enabled: false, count: 0, text: '']],
                 right: [[name: 'catalog', note: null, enabled: false, count: 0, text: '']]]
        !tree.left.is(tree.right)

        where:
        route << ['direct', 'via-node']
    }
}
