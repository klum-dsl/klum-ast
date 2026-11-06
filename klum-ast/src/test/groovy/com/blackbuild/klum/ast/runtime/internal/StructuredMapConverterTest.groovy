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
package com.blackbuild.klum.ast.runtime.internal

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import java.time.Duration

@Issue('952')
class StructuredMapConverterTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations, String root = 'Schedule') {
        createSecondaryClass("""
            package mapconverter
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('mapconverter.' + root, false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#converter-backed-map-values')
    def 'converts duration Map values independently under literal String keys'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Converters(excludeMethods = ['parse'])
                @Field(converters = [{ String text -> Duration.parse(text) }])
                Map<String, Duration> intervals
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: [primary: 'PT5S', retry: 'PT2S']], 'schedule.yaml')
        def ordinary = type.Create.With { interval 'primary', 'PT5S'; interval 'retry', 'PT2S' }
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        result.intervals == [primary: Duration.ofSeconds(5), retry: Duration.ofSeconds(2)]
        result.intervals == ordinary.intervals
        result.intervals.keySet().toList() == ['primary', 'retry']
        result.intervals.values().every { it.class == Duration }
        entry.payload().kind() == CompiledBuilderSchema.Kind.MAP
        entry.payload().item().type() == String
        entry.operation() == 'interval'
        entry.methods().size() == 1
        entry.methods()[0].parameterTypes == [String, String] as Class[]
        !entry.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()
    }
    def 'first value failure retains initializers and prior work and never invokes later operations: #bad'() {
        given:
        def type = schema('''
            class Audit {
                static List<String> events = []
                static final IllegalArgumentException failure = new IllegalArgumentException('bad interval')
            }
            class Interval {
                final String value
                Interval(String value) { this.value = value }
                static Interval fromString(String raw) {
                    Audit.events.add(raw)
                    if (raw == 'bad') throw Audit.failure
                    new Interval('converted:' + raw)
                }
            }
            @DSL class Schedule {
                Map<String, Interval> intervals = [seed: new Interval('seed')]
                @Builder.Method void finish() { Audit.events.add('finish') }
                @PostApply void applied() { Audit.events.add('applied') }
            }
        ''')
        def audit = Class.forName('mapconverter.Audit', false, artifacts)
        Throwable observed
        List<String> partial

        when:
        def result = type.Create.With {
            try {
                applyStruct([intervals: [first: 'one', 'bad/~key': bad, later: 'later'], finish: [:]], 'ordered.yaml')
            } catch (KlumModelException failure) {
                observed = failure
                partial = intervals.values()*.value
            }
        }

        then:
        observed != null
        observed.message.contains('ordered.yaml')
        observed.message.contains('/intervals/bad~1~0key')
        partial == ['seed', 'converted:one']
        result.intervals.values()*.value == partial
        audit.events == (bad == 'bad' ? ['one', 'bad', 'applied'] : ['one', 'applied'])
        (bad == 'bad') == causes(observed).contains(audit.failure)

        where:
        bad << ['bad', null, 12, true, "${12}", new Object(), new StringBuilder('one'), [:], []]
    }

    def 'non-String data keys reject at the reached entry without coercion: #badKey'() {
        given:
        def type = schema('''
            class Interval {
                String value
                static Interval fromString(String raw) { new Interval(value: raw) }
            }
            @DSL class Schedule { Map<String, Interval> intervals }
        ''')
        def input = new LinkedHashMap()
        input.put('first', 'one')
        input.put(badKey, 'two')
        input.put('later', 'three')
        Throwable observed

        when:
        def result = type.Create.With {
            try { applyStruct([intervals: input], 'keys.yaml') }
            catch (KlumModelException failure) { observed = failure }
        }

        then:
        observed.message.contains('keys.yaml')
        observed.message.contains('/intervals')
        observed.message.contains('String data-map key')
        result.intervals.collectEntries { key, value -> [(key): value.value] } == [first: 'one']
        input.size() == 3

        where:
        badKey << [null, 3, true, "${3}", new Object(), new StringBuilder('key')]
    }

    def 'supplied Map iteration order and literal names survive value population'() {
        given:
        def type = schema('''
            class Interval {
                static List<String> calls = []
                String value
                static Interval fromString(String raw) { calls.add(raw); new Interval(value: raw) }
            }
            @DSL class Schedule { @Struct.Name('timings') Map<String, Interval> intervals }
        ''')
        def values = new TreeMap<String, String>(Comparator.reverseOrder())
        values.put('a/b~c', 'one')
        values.put('class', 'two')
        values.put('interval', 'three')
        values.put('', 'four')

        when:
        def result = type.Create.FromStruct([timings: values])
        def intervalType = Class.forName('mapconverter.Interval', false, artifacts)

        then:
        result.intervals.keySet().toList() == values.keySet().toList()
        result.intervals.collectEntries { key, value -> [(key): value.value] } == values
        intervalType.calls == values.values().toList()
    }

    def 'key mapping remains excluded instead of reinterpreting authoritative input keys'() {
        given:
        def type = schema('''
            class Interval {
                String value
                static Interval fromString(String raw) { throw new AssertionError('converter executed') }
            }
            @DSL class Schedule {
                @Field(keyMapping={ Interval value -> throw new AssertionError('key mapping executed') })
                Map<String, Interval> intervals
            }
        ''')

        when:
        def contract = new InputContractResolver().read(type)

        then:
        !contract.entries().containsKey('intervals')

        when:
        type.Create.FromStruct([intervals: [literal: 'one']])

        then:
        thrown(KlumModelException)
    }

    def 'empty Map preserves defaults and repeated keys use ordinary entry population'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Converters(excludeMethods=['parse'])
                @Field(converters=[{ String raw -> Duration.parse(raw) }])
                Map<String, Duration> intervals = [seed: Duration.ofSeconds(7), primary: Duration.ofSeconds(9)]
                @Builder.Method void setIntervals(Map<String, Duration> values) { throw new AssertionError('replacement setter') }
            }
        ''')

        when:
        def empty = type.Create.FromStruct([intervals: [:]])
        def populated = type.Create.FromStruct([intervals: [primary: 'PT2S', retry: 'PT3S']])

        then:
        empty.intervals == [seed: Duration.ofSeconds(7), primary: Duration.ofSeconds(9)]
        populated.intervals == [seed: Duration.ofSeconds(7), primary: Duration.ofSeconds(2), retry: Duration.ofSeconds(3)]
        populated.intervals.keySet().toList() == ['seed', 'primary', 'retry']

        when:
        type.Create.FromStruct([intervals: ['PT2S']])

        then:
        thrown(KlumModelException)
    }

    def 'exact generated String value route supports the ordinary converter origin: #origin'() {
        given:
        def type = schema("""
            class Interval {
                String value
                $factory
            }
            class Adapter { static Interval fromString(String raw) { new Interval(value: 'adapted:' + raw) } }
            @DSL class Schedule { $annotation Map<String, Interval> intervals }
        """)

        when:
        def result = type.Create.FromStruct([intervals: [first: 'one', second: 'two']])
        def ordinary = type.Create.With { interval 'first', 'one'; interval 'second', 'two' }

        then:
        result.intervals.values()*.value == expected
        result.intervals.values()*.value == ordinary.intervals.values()*.value

        where:
        origin | annotation | factory | expected
        'same-compilation factory' | '' | "static Interval fromString(String raw) { new Interval(value: 'factory:' + raw) }" | ['factory:one', 'factory:two']
        'external adapter' | '@Converters([Adapter])' | '' | ['adapted:one', 'adapted:two']
        'constructor' | '@Converters(includeConstructors=true)' | "Interval(String raw) { value = 'constructor:' + raw }" | ['constructor:one', 'constructor:two']
    }

    def 'unsupported Map key value and storage shapes remain excluded: #target'() {
        given:
        def type = schema("""
            class Interval {}
            @DSL class Schedule {
                @Field(converters=[{ String raw -> throw new AssertionError('excluded converter') }])
                $target intervals
            }
        """)

        when:
        def contract = new InputContractResolver().read(type)
        def empty = type.Create.FromStruct([:])

        then:
        empty != null
        !contract.entries().containsKey('intervals')

        when:
        type.Create.FromStruct([intervals: [first: 'one']])

        then:
        thrown(KlumModelException)

        where:
        target << ['Map<String, Object>', 'Map<Integer, Interval>', 'Map<Object, Interval>',
                   'Map<CharSequence, Interval>', 'Map<String, List<Interval>>', 'SortedMap<String, Interval>']
    }

    def 'ordinary compiler rejection of unsupported raw Map declarations is retained: #target'() {
        when:
        schema("@DSL class Schedule { $target intervals }")

        then:
        thrown(MultipleCompilationErrorsException)

        where:
        target << ['Map']
    }

    def 'ambiguous excluded and missing generated value routes reject: #declarations'() {
        given:
        def type = schema("""
            class Interval {
                String value
                static Interval fromString(String raw) { new Interval(value: 'first:' + raw) }
                $peer
            }
            @DSL class Schedule { $declarations }
        """)

        when:
        def contract = new InputContractResolver().read(type)

        then:
        !contract.entries().containsKey('intervals')

        when:
        type.Create.FromStruct([intervals: [first: 'one']])

        then:
        thrown(KlumModelException)

        where:
        declarations | peer
        'Map<String, Interval> intervals' | "static Interval parse(String raw) { new Interval(value: 'second:' + raw) }"
        'Map<String, Interval> intervals' | "static Interval parse(CharSequence raw) { new Interval(value: 'sequence:' + raw) }"
        'Map<String, Interval> intervals' | "static Interval fromString(CharSequence raw) { new Interval(value: 'overloaded:' + raw) }"
        '@Struct.Ignore Map<String, Interval> intervals' | ''
        "@Converters(excludeMethods=['from']) Map<String, Interval> intervals" | ''
        'Map<String, Interval> intervals; @Builder.Method Interval interval(String key, String raw) { new Interval(value: raw) }' | ''
        '@Field(converters=[{ String raw -> new Interval(value: raw) }]) Map<String, Interval> intervals' | ''
    }

    def 'native scalar Maps keep their existing value grammar despite converter overloads'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Field(converters=[{ String raw -> throw new AssertionError('converter must not run') }])
                Map<String, Integer> intervals
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: [first: 2, second: 1]])

        then:
        result.intervals == [first: 2, second: 1]

        when:
        type.Create.FromStruct([intervals: [first: '2']])

        then:
        thrown(KlumModelException)
    }

    def 'CharSequence-only Map converters use their projected payload signature'() {
        given:
        def type = schema('@DSL class Schedule { Map<String, Duration> intervals }')

        when:
        def contract = new InputContractResolver().read(type)
        def ordinary = type.Create.With { interval 'first', 'PT2S' }

        then:
        ordinary.intervals == [first: Duration.ofSeconds(2)]
        contract.entries().intervals.payload().item().type() == CharSequence
        type.Create.FromStruct([intervals: [first: 'PT2S']]).intervals == ordinary.intervals
    }

    def 'separately compiled inherited value routes retain ordinary dispatch and reject descendant shadowing'() {
        given:
        schema("""
            @DSL class Base {
                @Converters(excludeMethods=['parse'])
                @Field(converters=[{ $inputType raw -> Duration.parse(raw).plusSeconds(3) }])
                Map<String, Duration> intervals
            }
            @DSL class SameCompilation extends Base {}
        """, 'Base')
        def binaryCompiler = new GroovyClassLoader(artifacts, compilerConfiguration)
        binaryCompiler.parseClass("""
            package mapconverter
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            @DSL class Inherited extends Base {}
            @DSL class Overridden extends Base {
                @Struct.Name('replacement') @Struct.Ignore @Builder.Method
                Duration interval(String key, $inputType raw) { Duration.ofSeconds(99) }
            }
            @DSL class Leaf extends Overridden {}
        """)
        def inherited = Class.forName('mapconverter.Inherited', false, artifacts)
        def local = Class.forName('mapconverter.SameCompilation', false, artifacts)
        def leaf = Class.forName('mapconverter.Leaf', false, artifacts)
        Duration ordinaryOverride

        when:
        def result = inherited.Create.FromStruct([intervals: [first: 'PT5S', second: 'PT2S']])
        def ordinary = inherited.Create.With { interval 'first', 'PT5S'; interval 'second', 'PT2S' }
        def sameCompilation = local.Create.FromStruct([intervals: [first: 'PT5S', second: 'PT2S']])
        def overridden = leaf.Create.With { ordinaryOverride = interval('first', 'PT5S') }
        def contract = new InputContractResolver().read(inherited)
        def rejected = new InputContractResolver().read(leaf)

        then:
        result.intervals == [first: Duration.ofSeconds(8), second: Duration.ofSeconds(5)]
        result.intervals == ordinary.intervals
        result.intervals == sameCompilation.intervals
        contract.entries().intervals.source() == 'mapconverter.Base#intervals'
        !rejected.entries().containsKey('intervals')
        rejected.rejections().any { it.reason().contains('overridden') }
        ordinaryOverride == Duration.ofSeconds(99)
        overridden.intervals.isEmpty()

        when:
        leaf.Create.FromStruct([intervals: [first: 'PT5S']])

        then:
        thrown(KlumModelException)

        cleanup:
        binaryCompiler?.close()

        where:
        inputType << ['String', 'CharSequence']
    }

    def 'source-free Map association is inspected without initializing Schema or converter code'() {
        given:
        def type = inertSchema()

        when:
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        entry.payload().kind() == CompiledBuilderSchema.Kind.MAP
        entry.payload().item().type() == String
        entry.methods()[0].returnType == Duration

        when: 'an explicit positive control initializes the same Schema after inspection'
        Class.forName(type.name, true, artifacts)

        then:
        def failure = thrown(AssertionError)
        failure.message == 'Schema initialized'
    }

    def 'String grammar accepts arbitrary String contents and preserves exact converter results: #values'() {
        given:
        def type = schema('''
            @DSL class Schedule { Map<String, Interval> intervals }
            class Interval {
                String value
                static Interval fromString(String raw) { new Interval(value: raw) }
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: values])

        then:
        result.intervals.collectEntries { key, value -> [(key): value.value] } == values
        result.intervals.values().every { it.class.name == 'mapconverter.Interval' }

        where:
        values << [[:], ['': ''], [first: 'one', second: 'two'], ['a/b~c': 'unicode-✓', next: '\n']]
    }

    def 'separately compiled adapter value origins retain selected-route parity without C1 leakage'() {
        given:
        createSecondaryClass('''
            package mapconverter
            class Interval {
                String value
                static Interval fromString(int raw) { throw new AssertionError('wrong overload') }
            }
            class Adapter {
                static Interval fromString(String raw) { new Interval(value: 'adapter:' + raw) }
            }
        ''')
        def type = schema('@DSL class Schedule { @Converters([Adapter]) Map<String, Interval> intervals }')

        when:
        def result = type.Create.FromStruct([intervals: [first: 'one', second: 'two']])
        def ordinary = type.Create.With { interval 'first', 'one'; interval 'second', 'two' }
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        result.intervals.values()*.value == ['adapter:one', 'adapter:two']
        result.intervals.values()*.value == ordinary.intervals.values()*.value
        entry.methods()[0].getAnnotation(GeneratedInput.Converter).owner() == 'mapconverter.Adapter'
        !entry.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()
    }

    def 'bounded generic domain values are scalar Map values rather than Builder self types'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Converters(includeConstructors=true) Map<String, Interval<String>> intervals
            }
            class Interval<T extends CharSequence> {
                final String value
                Interval(String raw) { value = 'generic:' + raw }
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: [first: 'one', second: 'two']])
        def ordinary = type.Create.With { interval 'first', 'one'; interval 'second', 'two' }
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        result.intervals.values()*.value == ['generic:one', 'generic:two']
        result.intervals.values()*.value == ordinary.intervals.values()*.value
        result.intervals.values().every { it.class == entry.methods()[0].returnType }
        !entry.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()
    }

    def 'incomplete or corrupt Map value metadata fails before Schema initialization: #damage'() {
        given:
        def type = inertSchema(inputType)
        damageContract(damage, inputType)

        when:
        new InputContractResolver().read(type)

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile')
        !causes(failure).any { it instanceof AssertionError }

        where:
        [inputType, damage] << [['String', 'CharSequence'], ['missing projection', 'missing association', 'missing selection', 'missing authority', 'wrong authority',
                   'wrong descriptor', 'wrong parameter', 'wrong count', 'wrong value type', 'wrong key type', 'raw Map']].combinations()
    }

    private Class inertSchema(String inputType = 'String') {
        schema("""
            @DSL class Schedule {
                static { throw new AssertionError('Schema initialized') }
                @Converters(excludeMethods=['parse'])
                @Field(converters=[{ $inputType raw -> throw new AssertionError('converter executed') }])
                Map<String, Duration> intervals
            }
        """)
    }

    private void damageContract(String damage, String inputType = 'String') {
        File file = new File(compilerConfiguration.targetDirectory, 'mapconverter/Schedule_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (damage == 'missing projection' && name == 'interval' && descriptor.startsWith("(Ljava/lang/String;Ljava/lang/$inputType;)")) {
                    return null
                }
                if (name == 'getIntervals') {
                    if (damage == 'wrong value type') {
                        signature = '()Ljava/util/Map<Ljava/lang/String;Ljava/lang/Object;>;'
                    }
                    if (damage == 'wrong key type') {
                        signature = '()Ljava/util/Map<Ljava/lang/Integer;Ljava/time/Duration;>;'
                    }
                    if (damage == 'raw Map') {
                        signature = null
                    }
                }
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (damage == 'missing association' && annotation.endsWith('GeneratedInput$Converter;')) {
                            return null
                        }
                        if (damage == 'missing selection' && annotation.endsWith('GeneratedInput$Selection;')) {
                            return null
                        }
                        def downstream = super.visitAnnotation(annotation, visible)
                        if (annotation.endsWith('GeneratedInput$Converter;') || annotation.endsWith('GeneratedInput$Field;')) {
                            return damageAssociation(downstream, damage)
                        }
                        downstream
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }

    private static AnnotationVisitor damageAssociation(AnnotationVisitor downstream, String damage) {
        new AnnotationVisitor(Opcodes.ASM9, downstream) {
            @Override void visit(String member, Object value) {
                if (damage == 'missing authority' && member == 'parameterFacts') {
                    return
                }
                if (damage == 'wrong authority' && member == 'parameterFacts') {
                    value = true
                }
                if (damage == 'wrong descriptor' && member == 'descriptor') {
                    value = '(I)V'
                }
                if (damage == 'wrong parameter' && member == 'parameter') {
                    value = 1
                }
                if (damage == 'wrong count' && member == 'converterCount') {
                    value = 0
                }
                super.visit(member, value)
            }
        }
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable cause = failure; cause != null; cause = cause.cause) {
            result.add(cause)
        }
        result
    }
}
