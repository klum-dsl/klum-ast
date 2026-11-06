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
import groovy.yaml.YamlSlurper
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
class StructuredSetConverterTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations, String root = 'Schedule') {
        createSecondaryClass("""
            package setconverter
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('setconverter.' + root, false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    def 'converts duration Set elements independently in supplied order'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Converters(excludeMethods = ['parse'])
                @Field(converters = [{ String text -> Duration.parse(text) }])
                Set<Duration> intervals
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: ['PT5S', 'PT2S']], 'schedule.yaml')
        def ordinary = type.Create.With { interval 'PT5S'; interval 'PT2S' }
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        result.intervals == [Duration.ofSeconds(5), Duration.ofSeconds(2)] as Set
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
                Set<Interval> intervals = [new Interval('seed')]
                @Builder.Method void finish() { Audit.events.add('finish') }
                @PostApply void applied() { Audit.events.add('applied') }
            }
        ''')
        def audit = Class.forName('setconverter.Audit', false, artifacts)
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

    def 'empty Set preserves population defaults and rejects whole-container replacement'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Converters(excludeMethods=['parse'])
                @Field(converters=[{ String raw -> Duration.parse(raw) }])
                Set<Duration> intervals = [Duration.ofSeconds(7)]
                @Builder.Method void setIntervals(Set<Duration> values) { throw new AssertionError('replacement setter') }
            }
        ''')

        when:
        def empty = type.Create.FromStruct([intervals: []])
        def populated = type.Create.FromStruct([intervals: ['PT2S']])

        then:
        empty.intervals == [Duration.ofSeconds(7)] as Set
        populated.intervals == [Duration.ofSeconds(7), Duration.ofSeconds(2)] as Set

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
            @DSL class Schedule { $annotation Set<Interval> intervals }
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
            @DSL class Child { @Key String name }
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
        target << ['Set<Object>', 'List<Set<Interval>>', 'SortedSet<Interval>', 'NavigableSet<Interval>',
                   'Interval[]']
    }

    def 'ordinary compiler rejection of unsupported collection declarations is retained: #target'() {
        when:
        schema("@DSL class Schedule { $target intervals }")

        then:
        thrown(MultipleCompilationErrorsException)

        where:
        target << ['Set', 'Collection<Duration>']
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
        'Set<Interval> intervals' | "static Interval parse(String raw) { new Interval(value: 'second:' + raw) }"
        'Set<Interval> intervals' | "static Interval parse(CharSequence raw) { new Interval(value: 'sequence:' + raw) }"
        '@Struct.Ignore Set<Interval> intervals' | ''
        "@Converters(excludeMethods=['from']) Set<Interval> intervals" | ''
        'Set<Interval> intervals; @Builder.Method Interval interval(String raw) { new Interval(value: raw) }' | ''
        '@Field(converters=[{ String raw -> new Interval(value: raw) }]) Set<Interval> intervals' | ''
    }

    def 'native scalar Sets keep their existing element grammar despite converter overloads'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Field(converters=[{ String raw -> throw new AssertionError('converter must not run') }])
                Set<Integer> intervals
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: [2, 1]])

        then:
        result.intervals == [2, 1] as Set

        when:
        type.Create.FromStruct([intervals: ['2']])

        then:
        thrown(KlumModelException)
    }

    def 'CharSequence-only Set element factories remain outside this exact String slice'() {
        given:
        def type = schema('@DSL class Schedule { Set<Duration> intervals }')

        when:
        def contract = new InputContractResolver().read(type)
        def ordinary = type.Create.With { interval 'PT2S' }

        then:
        ordinary.intervals == [Duration.ofSeconds(2)] as Set
        !contract.entries().containsKey('intervals')
    }

    def 'separately compiled inherited element routes retain ordinary dispatch and reject descendant shadowing'() {
        given:
        schema('''
            @DSL class Base {
                @Converters(excludeMethods=['parse'])
                @Field(converters=[{ String raw -> Duration.parse(raw).plusSeconds(3) }])
                Set<Duration> intervals
            }
            @DSL class SameCompilation extends Base {}
        ''', 'Base')
        def binaryCompiler = new GroovyClassLoader(artifacts, compilerConfiguration)
        binaryCompiler.parseClass('''
            package setconverter
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            @DSL class Inherited extends Base {}
            @DSL class Overridden extends Base {
                @Struct.Name('replacement') @Struct.Ignore @Builder.Method
                Duration interval(String raw) { Duration.ofSeconds(99) }
            }
            @DSL class Leaf extends Overridden {}
        ''')
        def inherited = Class.forName('setconverter.Inherited', false, artifacts)
        def local = Class.forName('setconverter.SameCompilation', false, artifacts)
        def leaf = Class.forName('setconverter.Leaf', false, artifacts)
        Duration ordinaryOverride

        when:
        def result = inherited.Create.FromStruct([intervals: ['PT5S', 'PT2S']])
        def ordinary = inherited.Create.With { interval 'PT5S'; interval 'PT2S' }
        def sameCompilation = local.Create.FromStruct([intervals: ['PT5S', 'PT2S']])
        def overridden = leaf.Create.With { ordinaryOverride = interval('PT5S') }
        def contract = new InputContractResolver().read(inherited)
        def rejected = new InputContractResolver().read(leaf)

        then:
        result.intervals == [Duration.ofSeconds(8), Duration.ofSeconds(5)] as Set
        result.intervals == ordinary.intervals
        result.intervals == sameCompilation.intervals
        contract.entries().intervals.source() == 'setconverter.Base#intervals'
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

    def 'source-free Set association is inspected without initializing Schema or converter code'() {
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
            @DSL class Schedule { Set<Interval> intervals }
            class Interval {
                String value
                static Interval fromString(String raw) { new Interval(value: raw) }
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: values])

        then:
        result.intervals*.value == values
        result.intervals.every { it.class.name == 'setconverter.Interval' }

        where:
        values << [[], [''], ['one', 'two'], ['bad', 'unicode-✓', '\n']]
    }

    def 'separately compiled adapter element origins retain selected-route parity without C1 leakage'() {
        given:
        createSecondaryClass('''
            package setconverter
            class Interval {
                String value
                static Interval fromString(int raw) { throw new AssertionError('wrong overload') }
            }
            class Adapter {
                static Interval fromString(String raw) { new Interval(value: 'adapter:' + raw) }
            }
        ''')
        def type = schema('@DSL class Schedule { @Converters([Adapter]) Set<Interval> intervals }')

        when:
        def result = type.Create.FromStruct([intervals: ['one', 'two']])
        def ordinary = type.Create.With { interval 'one'; interval 'two' }
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        result.intervals*.value == ['adapter:one', 'adapter:two']
        result.intervals*.value == ordinary.intervals*.value
        entry.methods()[0].getAnnotation(GeneratedInput.Converter).owner() == 'setconverter.Adapter'
        !entry.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()
    }

    def 'bounded generic domain values are scalar Set elements rather than Builder self types'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                @Converters(includeConstructors=true) Set<Interval<String>> intervals
            }
            class Interval<T extends CharSequence> {
                final String value
                Interval(String raw) { value = 'generic:' + raw }
            }
        ''')

        when:
        def result = type.Create.FromStruct([intervals: ['one', 'two']])
        def ordinary = type.Create.With { interval 'one'; interval 'two' }
        def entry = new InputContractResolver().read(type).entries().intervals

        then:
        result.intervals*.value == ['generic:one', 'generic:two']
        result.intervals*.value == ordinary.intervals*.value
        result.intervals.every { it.class == entry.methods()[0].returnType }
        !entry.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()
    }

    def 'incomplete or corrupt Set element metadata fails before Schema initialization: #damage'() {
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
                   'wrong descriptor', 'wrong parameter', 'wrong count', 'wrong element type', 'raw Set']
    }


    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#converter-backed-set-elements')
    def 'converts every supplied tag and retains ordinary Set equality semantics'() {
        given:
        def type = schema(setSchema('Set<Tag> tags = [new Tag(name: "seed")] as LinkedHashSet'))
        def audit = artifacts.loadClass('setconverter.Audit')
        def document = new YamlSlurper().parseText('tags: [blue, green, BLUE, seed]')

        when:
        def result = type.Create.FromStruct(document, 'tags.yaml')

        then:
        audit.events == ['blue', 'green', 'BLUE', 'seed', 'applied']
        result.tags*.name == ['seed', 'blue', 'green']
        audit.converted.size() == 4
        !audit.converted[0].is(audit.converted[2])
        result.tags.find { it.name == 'blue' }.is(audit.converted[0])
        !result.tags.any { it.is(audit.converted[2]) || it.is(audit.converted[3]) }

        when:
        audit.events.clear()
        def ordinary = type.Create.With { tag 'blue'; tag 'green'; tag 'BLUE'; tag 'seed' }

        then:
        ordinary.tags == result.tags
        audit.events == ['blue', 'green', 'BLUE', 'seed', 'applied']
    }

    def 'backing comparator order stays authoritative while conversion follows supplied order'() {
        given:
        def type = schema(setSchema('Set<Tag> tags = new TreeSet<Tag>(new TagOrder())'))
        def audit = artifacts.loadClass('setconverter.Audit')

        when:
        def result = type.Create.FromStruct([tags: ['blue', 'green', 'blue']])
        def ordinary = type.Create.With { tag 'blue'; tag 'green'; tag 'blue' }

        then:
        result.tags*.name == ['green', 'blue']
        result.tags == ordinary.tags
        audit.events == ['blue', 'green', 'blue', 'applied', 'blue', 'green', 'blue', 'applied']
    }

    def 'failed conversion or insertion preserves earlier effects and indexed cause: #bad'() {
        given:
        def type = schema(setSchema('Set<Tag> tags = new TreeSet<Tag>(new TagOrder())'))
        def audit = artifacts.loadClass('setconverter.Audit')
        Throwable observed

        when:
        def result = type.Create.With {
            try { applyStruct([tags: ['blue', bad, 'later']], 'tags.yaml') }
            catch (KlumModelException failure) { observed = failure }
        }

        then:
        result.tags*.name == ['blue']
        audit.events == ['blue', bad, 'applied']
        observed.message.contains('tags.yaml')
        observed.message.contains('/tags/1')
        causes(observed).contains(audit.failure)
        audit.converted*.name == (bad == 'bad' ? ['blue'] : ['blue', 'reject'])

        where:
        bad << ['bad', 'reject']
    }

    def 'empty input preserves defaults without conversion or replacement'() {
        given:
        def type = schema(setSchema('''Set<Tag> tags = [new Tag(name: 'seed')] as LinkedHashSet
            @Builder.Method void setTags(Set<Tag> values) { throw new AssertionError('replacement setter') }'''))
        def audit = artifacts.loadClass('setconverter.Audit')

        when:
        def result = type.Create.FromStruct([tags: []])

        then:
        result.tags*.name == ['seed']
        audit.events == ['applied']
        audit.converted.empty

        when:
        type.Create.FromStruct([tags: ['blue'] as Set])

        then:
        thrown(KlumModelException)
        audit.converted.empty
    }

    def 'Template definition evaluates all inputs while recipients replay retained values'() {
        given:
        def type = schema(setSchema('Set<Tag> tags'))
        def audit = artifacts.loadClass('setconverter.Audit')

        when:
        def template = type.Create.Template.FromStruct([tags: ['blue', 'BLUE', 'green']], 'recipe.yaml')
        def definitionEvents = audit.events.toList()
        def first
        def second
        type.Template.With(template) {
            first = type.Create.One()
            second = type.Create.One()
        }

        then:
        definitionEvents == ['blue', 'BLUE', 'green']
        audit.events == ['blue', 'BLUE', 'green', 'applied', 'applied']
        first.tags*.name == ['blue', 'green']
        second.tags*.name == ['blue', 'green']
        !first.tags.is(second.tags)
        first.tags.first().is(audit.converted[0])
        second.tags.first().is(audit.converted[0])
    }

    private static String setSchema(String field) {
        """
            class Audit {
                static List<String> events = []
                static List<Tag> converted = []
                static final IllegalArgumentException failure = new IllegalArgumentException('bad tag')
            }
            class TagOrder implements Comparator<Tag> {
                int compare(Tag a, Tag b) {
                    if (a.name == 'reject') throw Audit.failure
                    b.name <=> a.name
                }
            }
            class Tag {
                String name
                static Tag fromString(String raw) {
                    Audit.events.add(raw)
                    if (raw == 'bad') throw Audit.failure
                    def result = new Tag(name: raw.toLowerCase())
                    Audit.converted.add(result)
                    result
                }
                boolean equals(Object other) { other instanceof Tag && other.name == name }
                int hashCode() { name.hashCode() }
            }
            @DSL class Schedule {
                $field
                @PostApply void applied() { Audit.events.add('applied') }
            }
        """
    }

    private Class inertSchema() {
        schema('''
            @DSL class Schedule {
                static { throw new AssertionError('Schema initialized') }
                @Converters(excludeMethods=['parse'])
                @Field(converters=[{ String raw -> throw new AssertionError('converter executed') }])
                Set<Duration> intervals
            }
        ''')
    }

    private void damageContract(String damage) {
        File file = new File(compilerConfiguration.targetDirectory, 'setconverter/Schedule_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (damage == 'missing projection' && name == 'interval' && descriptor.startsWith('(Ljava/lang/String;)')) return null
                if (name == 'getIntervals') {
                    if (damage == 'wrong element type') {
                        signature = '()Ljava/util/Set<Ljava/lang/Object;>;'
                    }
                    if (damage == 'raw Set') {
                        signature = null
                    }
                }
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (damage == 'missing association' && annotation.endsWith('GeneratedInput$Converter;')) return null
                        if (damage == 'missing selection' && annotation.endsWith('GeneratedInput$Selection;')) return null
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
