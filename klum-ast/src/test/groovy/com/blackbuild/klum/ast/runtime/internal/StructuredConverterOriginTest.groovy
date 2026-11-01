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
        damage << ['missing projection', 'missing association', 'missing authority', 'invalid authority', 'wrong descriptor']
    }

    private void damageContract(String damage) {
        File file = new File(compilerConfiguration.targetDirectory, 'converterorigin/Service_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (damage == 'missing projection' && name == 'timeout' && descriptor.startsWith('(Ljava/lang/String;)')) return null
                def next = super.visitMethod(access, name, descriptor, signature, exceptions)
                new MethodVisitor(Opcodes.ASM9, next) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (damage == 'missing association' && annotation.endsWith('GeneratedInput$Converter;')) return null
                        def downstream = super.visitAnnotation(annotation, visible)
                        if (!annotation.endsWith('GeneratedInput$Converter;')) return downstream
                        new AnnotationVisitor(Opcodes.ASM9, downstream) {
                            @Override void visit(String member, Object value) {
                                if (damage == 'missing authority' && member == 'parameterFacts') return
                                if (damage == 'invalid authority' && member == 'parameterFacts') value = 'false'
                                if (damage == 'wrong descriptor' && member == 'descriptor') value = '(I)V'
                                super.visit(member, value)
                            }
                        }
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable cause = failure; cause != null; cause = cause.cause) result.add(cause)
        result
    }
}
