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
class StructuredListConverterTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations, String root = 'Schedule') {
        createSecondaryClass("""
            package listconverter
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('listconverter.' + root, false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#converter-backed-list-elements')
    def 'converts duration List elements independently in supplied order'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Converters(excludeMethods = ['parse'])
                @Field(converters = [{ String text -> Duration.parse(text) }])
                List<Duration> intervals
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: ['PT5S', 'PT2S']], 'schedule.yaml')
        def ordinary = type.Create.With { interval 'PT5S'; interval 'PT2S' }
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        result.intervals == [Duration.ofSeconds(5), Duration.ofSeconds(2)]
        result.intervals == ordinary.intervals
        result.intervals.every { it.class == Duration }
        entry.payload().kind() == CompiledBuilderSchema.Kind.LIST
        entry.payload().item().type() == String
        entry.operation() == 'interval'
        entry.methods().size() == 1
        entry.methods()[0].parameterTypes == [String] as Class[]
        !entry.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()
    }

    def 'first element failure retains initializers and prior work and never invokes later operations: #bad'() {
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
                List<Interval> intervals = [new Interval('seed')]
                @Builder.Method void finish() { Audit.events.add('finish') }
                @PostApply void applied() { Audit.events.add('applied') }
            }
        ''')
        def audit = Class.forName('listconverter.Audit', false, artifacts)
        Throwable observed
        List<String> partial

        when:
        def result = type.Create.With {
            try {
                applyStruct([intervals: ['one', bad, 'later'], finish: [:]], 'ordered.yaml')
            } catch (KlumModelException failure) {
                observed = failure
                partial = intervals*.value
            }
        }

        then:
        observed != null
        observed.message.contains('ordered.yaml')
        observed.message.contains('/intervals/1')
        partial == ['seed', 'converted:one']
        result.intervals*.value == partial
        audit.events == (bad == 'bad' ? ['one', 'bad', 'applied'] : ['one', 'applied'])
        (bad == 'bad') == causes(observed).contains(audit.failure)

        where:
        bad << ['bad', null, 12, true, "${12}", new Object(), new StringBuilder('one'), [:], []]
    }

    def 'empty List preserves population defaults and rejects whole-container replacement'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Converters(excludeMethods=['parse'])
                @Field(converters=[{ String raw -> Duration.parse(raw) }])
                List<Duration> intervals = [Duration.ofSeconds(7)]
                @Builder.Method void setIntervals(List<Duration> values) { throw new AssertionError('replacement setter') }
            }
        ''')

        when:
        def empty = type.Create.FromStruct([intervals: []])
        def populated = type.Create.FromStruct([intervals: ['PT2S']])

        then:
        empty.intervals == [Duration.ofSeconds(7)]
        populated.intervals == [Duration.ofSeconds(7), Duration.ofSeconds(2)]

        when:
        type.Create.FromStruct([intervals: 'PT2S'])

        then:
        thrown(KlumModelException)
    }

    def 'exact generated String element route supports the ordinary converter origin: #origin'() {
        given:
        def type = schema("""
            class Interval {
                String value
                $factory
            }
            class Adapter { static Interval fromString(String raw) { new Interval(value: 'adapted:' + raw) } }
            @DSL class Schedule { $annotation List<Interval> intervals }
        """)

        when:
        def result = type.Create.FromStruct([intervals: ['one', 'two']])
        def ordinary = type.Create.With { interval 'one'; interval 'two' }

        then:
        result.intervals*.value == expected
        result.intervals*.value == ordinary.intervals*.value

        where:
        origin | annotation | factory | expected
        'same-compilation factory' | '' | "static Interval fromString(String raw) { new Interval(value: 'factory:' + raw) }" | ['factory:one', 'factory:two']
        'external adapter' | '@Converters([Adapter])' | '' | ['adapted:one', 'adapted:two']
        'constructor' | '@Converters(includeConstructors=true)' | "Interval(String raw) { value = 'constructor:' + raw }" | ['constructor:one', 'constructor:two']
    }

    def 'unsupported container or element shapes remain excluded: #target'() {
        given:
        def type = schema("""
            class Interval {}
            @DSL class Child {}
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
        type.Create.FromStruct([intervals: ['one']])

        then:
        thrown(KlumModelException)

        where:
        target << ['List<Object>', 'List<Child>', 'List<List<Interval>>', 'Set<Interval>',
                   'Map<String, Interval>', 'Interval[]']
    }

    def 'ordinary compiler rejection of unsupported collection declarations is retained: #target'() {
        when:
        schema("@DSL class Schedule { $target intervals }")

        then:
        thrown(MultipleCompilationErrorsException)

        where:
        target << ['List', 'Collection<Duration>']
    }

    def 'ambiguous excluded and missing generated element routes reject: #declarations'() {
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
        type.Create.FromStruct([intervals: ['one']])

        then:
        thrown(KlumModelException)

        where:
        declarations | peer
        'List<Interval> intervals' | "static Interval parse(String raw) { new Interval(value: 'second:' + raw) }"
        'List<Interval> intervals' | "static Interval parse(CharSequence raw) { new Interval(value: 'sequence:' + raw) }"
        '@Struct.Ignore List<Interval> intervals' | ''
        "@Converters(excludeMethods=['from']) List<Interval> intervals" | ''
        'List<Interval> intervals; @Builder.Method Interval interval(String raw) { new Interval(value: raw) }' | ''
        '@Field(converters=[{ String raw -> new Interval(value: raw) }]) List<Interval> intervals' | ''
    }

    def 'native scalar Lists keep their existing element grammar despite converter overloads'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Field(converters=[{ String raw -> throw new AssertionError('converter must not run') }])
                List<Integer> intervals
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: [2, 1]])

        then:
        result.intervals == [2, 1]

        when:
        type.Create.FromStruct([intervals: ['2']])

        then:
        thrown(KlumModelException)
    }

    def 'CharSequence-only List element factories remain outside this exact String slice'() {
        given:
        def type = schema('@DSL class Schedule { List<Duration> intervals }')

        when:
        def contract = new InputContractResolver().read(type)
        def ordinary = type.Create.With { interval 'PT2S' }

        then:
        ordinary.intervals == [Duration.ofSeconds(2)]
        !contract.entries().containsKey('intervals')
    }

    def 'separately compiled inherited element routes retain ordinary dispatch and reject descendant shadowing'() {
        given:
        schema('''
            @DSL class Base {
                @Converters(excludeMethods=['parse'])
                @Field(converters=[{ String raw -> Duration.parse(raw).plusSeconds(3) }])
                List<Duration> intervals
            }
            @DSL class SameCompilation extends Base {}
        ''', 'Base')
        def binaryCompiler = new GroovyClassLoader(artifacts, compilerConfiguration)
        binaryCompiler.parseClass('''
            package listconverter
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            @DSL class Inherited extends Base {}
            @DSL class Overridden extends Base {
                @Struct.Name('replacement') @Struct.Ignore @Builder.Method
                Duration interval(String raw) { Duration.ofSeconds(99) }
            }
            @DSL class Leaf extends Overridden {}
        ''')
        def inherited = Class.forName('listconverter.Inherited', false, artifacts)
        def local = Class.forName('listconverter.SameCompilation', false, artifacts)
        def leaf = Class.forName('listconverter.Leaf', false, artifacts)
        Duration ordinaryOverride

        when:
        def result = inherited.Create.FromStruct([intervals: ['PT5S', 'PT2S']])
        def ordinary = inherited.Create.With { interval 'PT5S'; interval 'PT2S' }
        def sameCompilation = local.Create.FromStruct([intervals: ['PT5S', 'PT2S']])
        def overridden = leaf.Create.With { ordinaryOverride = interval('PT5S') }
        def contract = new InputContractResolver().read(inherited)
        def rejected = new InputContractResolver().read(leaf)

        then:
        result.intervals == [Duration.ofSeconds(8), Duration.ofSeconds(5)]
        result.intervals == ordinary.intervals
        result.intervals == sameCompilation.intervals
        contract.entries().intervals.source() == 'listconverter.Base#intervals'
        !rejected.entries().containsKey('intervals')
        rejected.rejections().any { it.reason().contains('overridden') }
        ordinaryOverride == Duration.ofSeconds(99)
        overridden.intervals.empty

        when:
        leaf.Create.FromStruct([intervals: ['PT5S']])

        then:
        thrown(KlumModelException)

        cleanup:
        binaryCompiler?.close()
    }

    def 'source-free List association is inspected without initializing Schema or converter code'() {
        given:
        def type = inertSchema()

        when:
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        entry.payload().kind() == CompiledBuilderSchema.Kind.LIST
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
            @DSL class Schedule { List<Interval> intervals }
            class Interval {
                String value
                static Interval fromString(String raw) { new Interval(value: raw) }
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: values])

        then:
        result.intervals*.value == values
        result.intervals.every { it.class.name == 'listconverter.Interval' }

        where:
        values << [[], [''], ['one', 'two'], ['bad', 'unicode-✓', '\n']]
    }

    def 'separately compiled adapter element origins retain selected-route parity without C1 leakage'() {
        given:
        createSecondaryClass('''
            package listconverter
            class Interval {
                String value
                static Interval fromString(int raw) { throw new AssertionError('wrong overload') }
            }
            class Adapter {
                static Interval fromString(String raw) { new Interval(value: 'adapter:' + raw) }
            }
        ''')
        def type = schema('@DSL class Schedule { @Converters([Adapter]) List<Interval> intervals }')

        when:
        def result = type.Create.FromStruct([intervals: ['one', 'two']])
        def ordinary = type.Create.With { interval 'one'; interval 'two' }
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        result.intervals*.value == ['adapter:one', 'adapter:two']
        result.intervals*.value == ordinary.intervals*.value
        entry.methods()[0].getAnnotation(GeneratedInput.Converter).owner() == 'listconverter.Adapter'
        !entry.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()
    }

    def 'incomplete or corrupt List element metadata fails before Schema initialization: #damage'() {
        given:
        def type = inertSchema()
        damageContract(damage)

        when:
        new InputContractResolver().read(type)

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile')
        !causes(failure).any { it instanceof AssertionError }

        where:
        damage << ['missing projection', 'missing association', 'missing selection', 'missing authority', 'wrong authority',
                   'wrong descriptor', 'wrong parameter', 'wrong count', 'wrong element type', 'raw List']
    }

    private Class inertSchema() {
        schema('''
            @DSL class Schedule {
                static { throw new AssertionError('Schema initialized') }
                @Converters(excludeMethods=['parse'])
                @Field(converters=[{ String raw -> throw new AssertionError('converter executed') }])
                List<Duration> intervals
            }
        ''')
    }

    private void damageContract(String damage) {
        File file = new File(compilerConfiguration.targetDirectory, 'listconverter/Schedule_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (damage == 'missing projection' && name == 'interval' && descriptor.startsWith('(Ljava/lang/String;)')) return null
                if (name == 'getIntervals') {
                    if (damage == 'wrong element type') signature = '()Ljava/util/List<Ljava/lang/Object;>;'
                    if (damage == 'raw List') signature = null
                }
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (damage == 'missing association' && annotation.endsWith('GeneratedInput$Converter;')) return null
                        if (damage == 'missing selection' && annotation.endsWith('GeneratedInput$Selection;')) return null
                        def downstream = super.visitAnnotation(annotation, visible)
                        if (annotation.endsWith('GeneratedInput$Converter;') || annotation.endsWith('GeneratedInput$Field;'))
                            return damageAssociation(downstream, damage)
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
                if (damage == 'missing authority' && member == 'parameterFacts') return
                if (damage == 'wrong authority' && member == 'parameterFacts') value = true
                if (damage == 'wrong descriptor' && member == 'descriptor') value = '(I)V'
                if (damage == 'wrong parameter' && member == 'parameter') value = 1
                if (damage == 'wrong count' && member == 'converterCount') value = 0
                super.visit(member, value)
            }
        }
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable cause = failure; cause != null; cause = cause.cause) result.add(cause)
        result
    }
}
