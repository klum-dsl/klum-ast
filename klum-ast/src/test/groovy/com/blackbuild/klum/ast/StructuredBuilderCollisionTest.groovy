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
package com.blackbuild.klum.ast

import com.blackbuild.klum.ast.runtime.KlumModelException
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@Issue('953')
class StructuredBuilderCollisionTest extends AbstractDSLSpec {
    def 'reserve exact structured application signatures including default overloads: #declaration'() {
        when:
        createClass """
            package reservedinput
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                String value
                $declaration
            }
        """

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('reserved for structured Builder application')
        failure.message.contains('applyStruct')

        where:
        declaration << [
            '@Builder.Method void applyStruct(Map body) { value = "hijacked" }',
            '@Builder.Method void applyStruct(Map body, String source) { value = "hijacked" }',
            '@Builder.Method void applyStruct(Map<String, Object> body) {}',
            '@Builder.Method void applyStruct(Map body, String source = "request") {}',
            '@Builder.Method void applyStruct(Map body, int ignored = 0) {}',
            '@Builder.Method void applyStruct(Map body, String source, int ignored = 0) {}',
            'Map<String, String> applyStruct',
            '@Builder.Method <T extends Map> void applyStruct(T body) {}'
        ]
    }
    def 'inherited collisions reject regardless of source declaration order: childFirst=#childFirst'() {
        given:
        String base = '@DSL class Base { @Builder.Method void applyStruct(Map body) {} }'
        String child = '@DSL class Service extends Base {}'

        when:
        createClass 'package inheritedinput\nimport com.blackbuild.klum.ast.*\n' +
                (childFirst ? child + '\n' + base : base + '\n' + child)

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('reserved for structured Builder application')

        where:
        childFirst << [false, true]
    }

    def 'distinct Schema overloads remain usable alongside the guarded common capability'() {
        when:
        createClass '''
            package distinctinput
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                String value
                @Builder.Method void applyStruct(String text) { value = text }
            }
        '''
        def artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def service = artifacts.loadClass('distinctinput.Service')
        def captured
        def result = service.Create.With {
            captured = delegate
            applyStruct('authored')
            assert value == 'authored'
            applyStruct([value: 'interpreted'])
        }

        then:
        result.value == 'interpreted'

        when:
        captured.applyStruct([value: 'late'])

        then:
        thrown(KlumModelException)

        cleanup:
        artifacts?.close()
    }

    def 'legacy binary overrides cannot bypass the runtime guard: #descriptor'() {
        given:
        createClass '''
            package legacyinput
            import com.blackbuild.klum.ast.*
            @DSL class Service {
                @Builder.Method void configure(Map body) {}
                @Builder.Method void configure(Map body, String source) {}
            }
        '''
        def file = new File(compilerConfiguration.targetDirectory, 'legacyinput/Service$Builder.class')
        String collisionDescriptor = descriptor
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
                super.visitMethod(access, name == 'configure' && desc == collisionDescriptor ? 'applyStruct' : name,
                        desc, signature, exceptions)
            }
        }, 0)
        file.bytes = writer.toByteArray()
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)

        when:
        binaryLoader.loadClass('legacyinput.Service$Builder')

        then:
        def failure = thrown(IncompatibleClassChangeError)
        failure.message.contains('overrides final method')
        failure.message.contains('applyStruct')

        cleanup:
        binaryLoader?.close()

        where:
        descriptor << ['(Ljava/util/Map;)V', '(Ljava/util/Map;Ljava/lang/String;)V']
    }

}
