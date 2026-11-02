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
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import java.time.Duration
import java.time.format.DateTimeParseException

@Issue('952')
class StructuredCharSequenceTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations, String root = 'Service') {
        createClass("""
            package sequenceinput
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('sequenceinput.' + root, false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Converters.md#structured-string-input')
    def 'a projected Duration parser accepts a String through its exact CharSequence signature'() {
        given:
        def type = schema('@DSL class Service { Duration timeout }')

        when:
        def result = KlumStructuredInput.readRoot(type, [timeout: 'PT30S'], 'service.yaml')
        def direct = type.Create.With { timeout 'PT30S' }
        def entry = new InputContractResolver().read(type).entries().timeout
        def converter = entry.methods()[0].getAnnotation(GeneratedInput.Converter)

        then:
        result.timeout == Duration.ofSeconds(30)
        result.timeout == direct.timeout
        entry.source() == 'sequenceinput.Service#timeout'
        entry.methods()[0].parameterTypes == [CharSequence] as Class[]
        converter.owner() == Duration.name
        converter.name() == 'parse'
        converter.descriptor() == '(Ljava/lang/CharSequence;)Ljava/time/Duration;'
        !converter.parameterFacts()
    }

    def 'CharSequence conversion composes with merged commands and structured Templates'() {
        given:
        def type = schema('''
            @DSL class Service {
                Duration timeout
                @Builder.Method void extendTimeout() { timeout(timeout.plusSeconds(7)) }
            }
        ''')

        when:
        def template = type.Create.Template.FromStruct([timeout: 'PT30S', extendTimeout: [:]], 'integration.yaml')
        def recipient
        type.Template.With(template) { recipient = type.Create.One() }
        def direct = type.Create.With { timeout 'PT30S'; extendTimeout() }

        then:
        template.timeout == Duration.ofSeconds(37)
        recipient.timeout == direct.timeout
        recipient.timeout == template.timeout
    }

    def 'the exact marked CharSequence overload receives the original String: marked=#marked'() {
        given:
        def type = schema("""
            @DSL class Service {
                @Struct.Ignore Object received
                $marked @Builder.Method void endpoint(CharSequence value) { received = value }
                $peer
            }
        """)
        String input = new String('api.internal')

        when:
        def result = KlumStructuredInput.readRoot(type, [endpoint: input], 'endpoint.yaml')
        def method = new InputContractResolver().read(type).entries().endpoint.methods()[0]

        then:
        result.received.is(input)
        result.received.class == String
        method.parameterTypes == [CharSequence] as Class[]

        where:
        marked | peer
        '' | ''
        '@Struct.Operation' | "@Builder.Method void endpoint(String value) { throw new AssertionError('String overload selected') }"
    }

    def 'CharSequence scalar input rejects non-String values before invoking user code: #value'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Builder.Method void endpoint(CharSequence value) { throw new AssertionError('must not execute') }
                @Field(converters=[{ CharSequence value -> throw new AssertionError('must not execute') }])
                @Converters(excludeMethods=['parse']) Duration timeout
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [(member): value], 'typed.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('typed.yaml')
        failure.message.contains(member)
        failure.message.contains(value == null ? 'actual null' : 'String')

        where:
        [member, value] << [['endpoint', 'timeout'], [null, 30, true, new StringBuilder('PT30S'), new StringBuffer('PT30S'), new MutableSequence(), "${30}", new Object(), [:], []]].combinations()
    }

    def 'String and CharSequence converter competition rejects without authority'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters=[{ String value -> throw new AssertionError('unselected closure') }]) Duration timeout
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [timeout: 'PT30S'], 'competition.yaml')

        then:
        thrown(KlumModelException)
        !new InputContractResolver().read(type).entries().containsKey('timeout')
    }

    def 'an authored CharSequence wrapper selects among competing converter routes'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters=[{ String value -> throw new AssertionError('unselected closure') }]) Duration timeout
                @Struct.Operation @Struct.Name('timeout') @Builder.Method
                void selectedTimeout(CharSequence value) { timeout(Duration.parse(value).plusSeconds(7)) }
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [timeout: 'PT30S'], 'competition.yaml')

        then:
        result.timeout == Duration.ofSeconds(37)
    }

    def 'multiple marked String and CharSequence methods fail without implicit specificity'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Struct.Operation @Builder.Method void endpoint(String value) {}
                @Struct.Operation @Builder.Method void endpoint(CharSequence value) {}
            }
        ''')

        when:
        new InputContractResolver().read(type)

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Multiple @Struct.Operation')
    }

    def 'generated CharSequence converter uses the recorded overload even with an excluded String peer: #constructor'() {
        given:
        def type = schema("""
            @DSL class Service { @Converters(includeConstructors=$constructor) Value value }
            class Value {
                final Object received
                $declarations
            }
        """)
        String input = new String('original')

        when:
        def direct = type.Create.With { value((CharSequence) input) }
        def result = KlumStructuredInput.readRoot(type, [value: input], 'exact.yaml')
        def entry = new InputContractResolver().read(type).entries().value

        then:
        result.value.received.is(input)
        direct.value.received.is(input)
        entry.methods()[0].parameterTypes == [CharSequence] as Class[]
        !entry.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()

        where:
        constructor | declarations
        false | "private Value(Object value) { received = value }; static Value parse(CharSequence value) { new Value((Object)value) }; protected static Value parse(String value) { throw new AssertionError('String factory') }"
        true | "Value(CharSequence value) { received = value }"
    }

    def 'separately compiled inheritance retains exact CharSequence selection and rejects stale converter overrides'() {
        given:
        schema('''
            @DSL class Base {
                Duration timeout
                @Struct.Ignore Object received
                @Struct.Operation @Builder.Method void endpoint(CharSequence value) { received = value }
                @Builder.Method void endpoint(String value) { throw new AssertionError('String peer') }
            }
        ''', 'Base')
        def binaryCompiler = new GroovyClassLoader(artifacts, compilerConfiguration)
        binaryCompiler.parseClass('''
            package sequenceinput
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            @DSL class Inherited extends Base {}
            @DSL class Overridden extends Base {
                @Struct.Name('replacement') @Builder.Method
                Duration timeout(CharSequence value) { Duration.ofSeconds(7) }
                @Struct.Ignore @Builder.Method void endpoint(CharSequence value) {}
            }
            @DSL class Leaf extends Overridden {}
        ''')
        def inherited = Class.forName('sequenceinput.Inherited', false, artifacts)
        def leaf = Class.forName('sequenceinput.Leaf', false, artifacts)

        when:
        def result = KlumStructuredInput.readRoot(inherited, [timeout: 'PT30S', endpoint: 'api'], 'binary.yaml')
        def inheritedEntry = new InputContractResolver().read(inherited).entries().timeout
        def leafEntries = new InputContractResolver().read(leaf).entries()

        then:
        result.timeout == Duration.ofSeconds(30)
        result.received == 'api'
        inheritedEntry.source() == 'sequenceinput.Base#timeout'
        inheritedEntry.methods()[0].parameterTypes == [CharSequence] as Class[]
        !leafEntries.containsKey('timeout')
        leafEntries.replacement.methods()[0].parameterTypes == [CharSequence] as Class[]
        leafEntries.endpoint.methods()[0].parameterTypes == [String] as Class[]

        cleanup:
        binaryCompiler?.close()
    }

    def 'CharSequence admission does not extend to containers or arbitrary scalar targets: #target'() {
        given:
        def type = schema("@DSL class Service { @Builder.Method void endpoint($target value) {} }")

        when:
        KlumStructuredInput.readRoot(type, [endpoint: value], 'excluded.yaml')

        then:
        thrown(KlumModelException)
        !new InputContractResolver().read(type).entries().containsKey('endpoint')

        where:
        target | value
        'List<CharSequence>' | ['api']
        'Map<String, CharSequence>' | [name: 'api']
        'StringBuilder' | 'api'
        'Serializable' | 'api'
    }

    def 'an erased generic CharSequence signature cannot admit a String: marked=#marked'() {
        given:
        def type = schema("""
            @DSL class Service {
                $marked @Builder.Method
                <T extends CharSequence & Cloneable> void endpoint(T value) { throw new AssertionError('generic operation') }
            }
        """)

        when:
        def contract = new InputContractResolver().read(type)

        then:
        !contract.entries().containsKey('endpoint')

        when:
        KlumStructuredInput.readRoot(type, [endpoint: 'api'], 'generic.yaml')

        then:
        thrown(KlumModelException)

        where:
        marked << ['', '@Struct.Operation']
    }

    private static class MutableSequence implements CharSequence {
        final StringBuilder value = new StringBuilder('PT30S')
        int length() { value.length() }
        char charAt(int index) { value.charAt(index) }
        CharSequence subSequence(int start, int end) { value.subSequence(start, end) }
        String toString() { value.toString() }
    }

    def 'inherited converter overrides cannot retain the parent CharSequence route: #ignored'() {
        given:
        def type = schema("""
            @DSL class Base { Duration timeout }
            @DSL class Middle extends Base {
                @Builder.Method @Struct.Name('replacement') $ignored
                Duration timeout(CharSequence value) { Duration.ofSeconds(7) }
            }
            @DSL class Service extends Middle {}
        """)

        when:
        def contract = new InputContractResolver().read(type)

        then:
        !contract.entries().containsKey('timeout')
        contract.entries().containsKey('replacement') == !ignored

        where:
        ignored << ['', '@Struct.Ignore']
    }

    def 'converter failures retain source path and original cause'() {
        given:
        def type = schema('@DSL class Service { Duration timeout }')

        when:
        KlumStructuredInput.readRoot(type, [timeout: 'bad'], 'duration.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('duration.yaml')
        failure.message.contains('timeout')
        causes(failure).any { it instanceof DateTimeParseException }
    }

    def 'CharSequence converter metadata fails closed when damaged: #damage'() {
        given:
        schema('@DSL class Service { Duration timeout }')
        damageContract(damage)
        def type = Class.forName('sequenceinput.Service', false, artifacts)

        when:
        KlumStructuredInput.readRoot(type, [:], 'damaged.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile')

        where:
        damage << ['missing projection', 'missing association', 'missing authority', 'invalid authority', 'wrong descriptor']
    }

    private void damageContract(String damage) {
        File file = new File(compilerConfiguration.targetDirectory, 'sequenceinput/Service_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (damage == 'missing projection' && name == 'timeout' && descriptor.startsWith('(Ljava/lang/CharSequence;)')) return null
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (damage == 'missing association' && annotation.endsWith('GeneratedInput$Converter;')) return null
                        def downstream = super.visitAnnotation(annotation, visible)
                        annotation.endsWith('GeneratedInput$Converter;') ? damageAssociation(downstream, damage) : downstream
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
                if (damage == 'invalid authority' && member == 'parameterFacts') value = 'false'
                if (damage == 'wrong descriptor' && member == 'descriptor') value = '(I)V'
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
