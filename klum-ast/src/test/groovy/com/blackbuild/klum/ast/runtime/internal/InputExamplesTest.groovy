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
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.KlumModelException
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

@Issue('959')
class InputExamplesTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations) {
        createClass("""
            package examples
            import com.blackbuild.klum.ast.*
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('examples.Service', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#advisory-input-examples')
    def 'document example values without supplying defaults or changing input admission'() {
        given:
        def type = schema('''
            @DSL class Service {
                @InputExamples(['"Catalog"', 'null']) String displayName
                @InputExamples(['not JSON']) String syntaxCheckedAtGeneration
            }
        ''')

        when:
        def configured = KlumStructuredInput.readRoot(type, [displayName: 'Configured'], 'service.yaml')
        def omitted = KlumStructuredInput.readRoot(type, [:], 'service.yaml')

        then:
        configured.displayName == 'Configured'
        omitted.displayName == null
        omitted.syntaxCheckedAtGeneration == null
        CompiledBuilderSchema.read(type).entries().get('displayName').examples() == ['"Catalog"', 'null']
    }

    def 'retain distinct field operation and suppressed-key facts and fold String constants'() {
        given:
        def type = schema('''
            @DSL class Service {
                static final String SAMPLE = '"' + 'sample' + '"'
                @Key @InputExamples(['"id"']) String key
                @InputExamples([SAMPLE]) String value
                @Builder.Method @InputName('operation') @InputExamples(['"operation"'])
                void setValue(String input) { value = input }
            }
        ''')

        when:
        def contract = Class.forName('examples.Service_DSL$Builder', false, artifacts)
        def schema = CompiledBuilderSchema.read(type)

        then:
        contract.getAnnotation(GeneratedInput.Schema).version() == 1
        schema.key().examples() == ['"id"']
        schema.entries().get('value').examples() == ['"sample"']
        schema.entries().get('operation').examples() == ['"operation"']
    }

    def 'missing owned examples facts reject input before mutating an active Builder'() {
        given:
        def type = schema('''
            @DSL class Service {
                String value
                Child child
                @Builder.Method void configure(String input) { value = input }
            }
            @DSL class Child { String value }
        ''')
        File file = new File(compilerConfiguration.targetDirectory, 'examples/Child_DSL$Builder.class')
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        def visitor = super.visitAnnotation(annotation, visible)
                        if (!annotation.endsWith('GeneratedInput$Field;')) return visitor
                        new AnnotationVisitor(Opcodes.ASM9, visitor) {
                            @Override AnnotationVisitor visitArray(String member) {
                                member == 'examples' ? null : super.visitArray(member)
                            }
                        }
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
        Throwable rejected

        when:
        def result = type.Create.With {
            value 'before'
            try { KlumStructuredInput.applyToBuilder(delegate, [configure: 'after'], 'active.yaml') }
            catch (KlumModelException failure) { rejected = failure }
        }

        then:
        rejected.message.contains('examples metadata')
        rejected.message.contains('Child')
        result.value == 'before'
        result.child == null
    }

    def 'nonconstant source references never execute static initialization to obtain examples'() {
        when:
        schema('''
            class Values {
                static final String SAMPLE = { throw new AssertionError('constant execution trap') }()
            }
            @DSL class Service { @InputExamples([Values.SAMPLE]) String value }
        ''')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('compile-time constant String')
        !failure.message.contains('AssertionError: constant execution trap')
    }

    def 'repeated authored examples annotations are rejected'() {
        when:
        schema('''@DSL class Service { @InputExamples(['null']) @InputExamples(['true']) String value }''')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('duplicate annotation') || failure.message.contains('repeated annotation')
    }

    def 'reject unsupported annotation values and transport bounds: #kind'() {
        when:
        schema("@DSL class Service { @InputExamples($annotation) String value }")

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('InputExamples')

        where:
        kind          | annotation
        'empty'       | '[]'
        'count'       | '[' + (1..17).collect { "'null'" }.join(',') + ']'
        'three bytes' | "'" + ('中' * 2731) + "'"
        'NUL bytes'   | "'" + ('\u0000' * 4097) + "'"
        'one bytes'   | "'" + ('x' * 8193) + "'"
        'total bytes' | '[' + (1..5).collect { "'" + ('x' * 7000) + "'" }.join(',') + ']'
        'null'        | '[null]'
        'number'      | '[42]'
        'call'        | '[System.getProperty("SECRET_PAYLOAD")]'
        'gstring'     | '["${System.nanoTime()}"]'
    }

    def 'accept exact declaration byte and count boundaries'() {
        given:
        String values = '[' + (1..4).collect { "'" + ('x' * 8192) + "'" }.join(',') + ']'

        when:
        def type = schema("@DSL class Service { @InputExamples($values) String value; @InputExamples(" +
                '[' + (1..16).collect { "'null'" }.join(',') + ']) String many }')

        then:
        CompiledBuilderSchema.read(type).entries().get('value').examples().size() == 4
        CompiledBuilderSchema.read(type).entries().get('many').examples().size() == 16
    }

    def 'accept modified UTF8 boundaries without parsing JSON at compilation'() {
        given:
        String sample = '\u0000' * 4096
        String quoted = "'" + sample + "'"

        when:
        def type = schema("@DSL class Service { @InputExamples([$quoted]) String value }")

        then:
        CompiledBuilderSchema.read(type).entries().get('value').examples() == [sample]
    }
}
