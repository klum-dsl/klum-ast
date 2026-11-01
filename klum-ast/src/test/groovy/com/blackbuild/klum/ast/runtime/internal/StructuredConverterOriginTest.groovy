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
import java.nio.file.Path
import java.time.format.DateTimeParseException

@Issue('952')
class StructuredConverterOriginTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations) {
        createClass("""
            package converterorigin
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('converterorigin.Service', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Converters.md#structured-string-input')
    def 'a field converter accepts the same duration String through ordinary and structured construction'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters = [{ String value -> Duration.parse(value) }])
                Duration timeout
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [timeout: 'PT30S'], 'service.yaml')
        def direct = type.Create.With { timeout 'PT30S' }
        def entry = new InputContractResolver().read(type).entries().timeout

        then:
        result.timeout == Duration.ofSeconds(30)
        result.timeout == direct.timeout
        entry.source() == 'converterorigin.Service#timeout'
        entry.methods()[0].parameterTypes == [String] as Class[]
        !entry.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()
    }

    def 'a field String closure remains distinct from the implicit CharSequence factory'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters = [{ String value -> Duration.parse(value).plusSeconds(7) }])
                Duration timeout
            }
        ''')

        when:
        def direct = type.Create.With { timeout 'PT30S' }
        def structured = KlumStructuredInput.readRoot(type, [timeout: 'PT30S'], 'closure-route.yaml')
        def selected = new InputContractResolver().read(type).entries().timeout.methods()[0]
                .getAnnotation(GeneratedInput.Converter)

        then:
        Duration.parse('PT30S') == Duration.ofSeconds(30)
        direct.timeout == Duration.ofSeconds(37)
        structured.timeout == direct.timeout
        selected.owner().endsWith('Service$_timeout_converterClosures')
        selected.name() == 'from_String'
        !selected.parameterFacts()
    }

    def 'the same effective String projection admits same-compilation values and adapters: #origin'() {
        given:
        def type = schema("""
            @DSL class Service { $annotation Timeout timeout }
            class Timeout {
                final String value
                private Timeout(String value) { this.value = value }
                $factory
            }
            class Adapter {
                static Timeout fromString(String value) { new Timeout('converted:' + value) }
            }
        """)

        when:
        def contract = new InputContractResolver().read(type)
        def result = KlumStructuredInput.readRoot(type, [timeout: 'PT30S'], 'origin.yaml')
        def direct = type.Create.With { timeout 'PT30S' }

        then:
        result.timeout.value == 'converted:PT30S'
        result.timeout.value == direct.timeout.value
        contract.entries().timeout.methods()[0].parameterTypes == [String] as Class[]
        contract.constraintProfiles().values().collectMany { it.converters().values() }.every { it.rules().isEmpty() }

        where:
        origin | factory | annotation
        'domain factory' | "static Timeout fromString(String value) { new Timeout('converted:' + value) }" | ''
        'adapter' | '' | '@Converters([Adapter])'
    }

    def 'an interface-valued field closure uses the existing exact String projection'() {
        given:
        def type = schema('''
            import java.nio.file.Path
            @DSL class Service {
                @Field(converters = [{ String value -> Path.of(value) }]) Path location
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [location: 'config/service.yaml'], 'path.yaml')
        def direct = type.Create.With { location 'config/service.yaml' }

        then:
        result.location == Path.of('config/service.yaml')
        result.location == direct.location
    }

    def 'a subtype-returning adapter and a field closure expose the same exact Builder shape'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Converters([Adapter]) Timeout adapted
                @Field(converters = [{ String value -> Adapter.fromString(value) }]) Timeout wrapped
            }
            abstract class Timeout { String value }
            class ConcreteTimeout extends Timeout {}
            class Adapter {
                static ConcreteTimeout fromString(String value) { new ConcreteTimeout(value: value) }
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [adapted: 'PT30S', wrapped: 'PT30S'], 'subtype.yaml')
        def direct = type.Create.With { adapted 'PT30S'; wrapped 'PT30S' }
        def entries = new InputContractResolver().read(type).entries()

        then:
        result.adapted.value == 'PT30S'
        result.adapted.value == result.wrapped.value
        result.adapted.value == direct.adapted.value
        result.wrapped.value == direct.wrapped.value
        entries.adapted.methods()[0].returnType == entries.wrapped.methods()[0].returnType
        entries.adapted.methods()[0].parameterTypes == entries.wrapped.methods()[0].parameterTypes
    }

    def 'native scalar fields retain their existing payload grammar when they also have a String converter'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters = [{ String value -> Integer.parseInt(value) }]) int timeout
            }
        ''')

        when:
        def result = KlumStructuredInput.readRoot(type, [timeout: 30], 'native.yaml')

        then:
        result.timeout == 30

        when:
        KlumStructuredInput.readRoot(type, [timeout: '30'], 'native.yaml')

        then:
        thrown(KlumModelException)
    }

    def 'String projections do not admit raw Object or converter-backed container fields: #target'() {
        given:
        def type = schema("""
            @DSL class Service {
                @Field(converters = [{ String value -> throw new AssertionError('excluded converter') }]) $target timeout
            }
            class Timeout {}
        """)

        when:
        def contract = new InputContractResolver().read(type)
        def result = KlumStructuredInput.readRoot(type, [:], 'excluded.yaml')

        then:
        result != null
        !contract.entries().containsKey('timeout')
        contract.rejections().any { it.source().endsWith('#timeout') }

        when:
        KlumStructuredInput.readRoot(type, [timeout: 'value'], 'excluded.yaml')

        then:
        thrown(KlumModelException)

        where:
        target << ['Object', 'List<Timeout>', 'Map<String, Timeout>', 'Timeout[]']
    }

    def 'field converter rejects typed payloads without coercion: #value'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters = [{ String value -> throw new AssertionError('must not execute') }])
                Duration timeout
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [timeout: value], 'typed.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('typed.yaml')
        failure.message.contains('timeout')
        !failure.message.contains('must not execute')

        where:
        value << [null, 30, true, new StringBuilder('PT30S'), "${30}", [:], []]
    }

    def 'converter-thrown causes retain the input source and field path'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters = [{ String value -> Duration.parse(value) }]) Duration timeout
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [timeout: 'bad'], 'duration.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('duration.yaml')
        failure.message.contains('timeout')
        causes(failure).any { it instanceof DateTimeParseException }
    }

    def 'colliding generated String projections reject even with an authored marker on the factory'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters = [{ String value -> new Timeout() }]) Timeout timeout
            }
            class Timeout {
                @Struct.Operation static Timeout fromString(String value) { new Timeout() }
            }
        ''')

        when:
        KlumStructuredInput.readRoot(type, [timeout: 'PT30S'], 'collision.yaml')

        then:
        thrown(KlumModelException)
        !new InputContractResolver().read(type).entries().containsKey('timeout')
    }

    def 'long structural converter descriptors remain inert during inspection'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters = [{ String value -> throw new AssertionError('converter executed') }])
                Duration timeout
            }
        ''')
        damageContract('long valid descriptor')

        when:
        def contract = new InputContractResolver().read(type)

        then:
        contract.entries().timeout.payload().type() == String
        !contract.entries().timeout.methods()[0].getAnnotation(GeneratedInput.Converter).parameterFacts()
    }

    def 'structural converter association and exact projection fail closed when damaged: #damage'() {
        given:
        def type = schema('''
            @DSL class Service {
                @Field(converters = [{ String value -> Duration.parse(value) }]) Duration timeout
            }
        ''')
        damageContract(damage)

        when:
        KlumStructuredInput.readRoot(type, [:], 'damaged.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile')

        where:
        damage << ['missing projection', 'missing association', 'missing authority', 'invalid authority', 'wrong descriptor',
                   'long invalid descriptor']
    }

    private void damageContract(String damage) {
        File file = new File(compilerConfiguration.targetDirectory, 'converterorigin/Service_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (damage == 'missing projection' && name == 'timeout' && descriptor.startsWith('(Ljava/lang/String;)')) {
                    return null
                }
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (damage == 'missing association' && annotation.endsWith('GeneratedInput$Converter;')) {
                            return null
                        }
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
                if (damage == 'missing authority' && member == 'parameterFacts') {
                    return
                }
                if (damage == 'invalid authority' && member == 'parameterFacts') {
                    value = 'false'
                }
                if (damage == 'wrong descriptor' && member == 'descriptor') {
                    value = '(I)V'
                }
                if (member == 'descriptor' && damage.startsWith('long ')) {
                    value = '(Ljava/lang/String;)L' + 'segment/' * 4096 + (damage == 'long valid descriptor' ? 'Value;' : ';')
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
