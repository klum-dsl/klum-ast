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
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@Issue('1011')
class CopyFromCollisionTest extends AbstractDSLSpec {
    private static final String RESERVED_SIGNATURE = 'copyFrom(java.lang.Object)'
    private static final String SHADOWED_ROUTE_DIAGNOSTIC = 'shadows a framework Builder copy route'

    def 'reject effective Object copy signatures: #declaration'() {
        when:
        createClass """
            @DSL class Service {
                String value
                $declaration
            }
        """

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains(RESERVED_SIGNATURE)
        failure.message.contains(SHADOWED_ROUTE_DIAGNOSTIC)
        failure.message.contains('rename the conflicting Schema operation')

        where:
        declaration << [
            '@Builder.Method void copyFrom(Object body) {}',
            '@Builder.Method void copyFrom(body) {}',
            '@Mutator void copyFrom(Object body) {}',
            '@Builder.Method <T> void copyFrom(T body) {}',
            '@Builder.Method <T extends Object> void copyFrom(T body) {}',
            '@Builder.Method void copyFrom(Object body, int ignored = 0) {}',
            '@Builder.Method void copyFrom(int ignored = 0, Object body) {}',
            '@Builder.Method <T> void copyFrom(T body, String ignored = "default") {}',
            '@Builder.Method void copyFrom(Object body = null) {}'
        ]
    }

    def 'inherited source collisions reject in either declaration order: #childFirst'() {
        given:
        String base = '@DSL class Base { @Builder.Method void copyFrom(Object body, int ignored = 0) {} }'
        String child = '@DSL class Service extends Base {}'

        when:
        createClass(childFirst ? child + '\n' + base : base + '\n' + child)

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains(RESERVED_SIGNATURE)
        failure.message.contains(SHADOWED_ROUTE_DIAGNOSTIC)

        where:
        childFirst << [false, true]
    }

    def 'a separately compiled conflicting parent rejects when compiling a descendant'() {
        given:
        createClass '''
            package inheritedcopy
            @DSL class Base {
                @Builder.Method void configure(Object body) {}
            }
        '''
        // Reproduce an older compiler's conflicting binary without relying on an external release.
        File file = new File(compilerConfiguration.targetDirectory, 'inheritedcopy/Base$Builder.class')
        ClassWriter writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                super.visitMethod(access, name == 'configure' ? 'copyFrom' : name, descriptor, signature, exceptions)
            }
        }, 0)
        file.bytes = writer.toByteArray()
        def artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def childLoader = new GroovyClassLoader(artifacts, new CompilerConfiguration(compilerConfiguration))

        when:
        childLoader.parseClass('''
            package inheritedcopy
            import com.blackbuild.klum.ast.DSL
            @DSL class Service extends Base {}
        ''')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('inheritedcopy.Base$Builder')
        failure.message.contains(RESERVED_SIGNATURE)
        failure.message.contains(SHADOWED_ROUTE_DIAGNOSTIC)

        cleanup:
        childLoader?.close()
        artifacts?.close()
    }

    def 'distinct String overload preserves Map and Model copy routes and completed-session guard'() {
        given:
        createClass '''
            @DSL class Service {
                String value
                @Builder.Method void copyFrom(String text) { value = text }
            }
        '''
        def donor = clazz.Create.With { value 'donor' }
        def retained

        when:
        def result = clazz.Create.With {
            retained = delegate
            copyFrom('custom')
            assert value == 'custom'
            copyFrom([value: 'mapped'])
            assert value == 'mapped'
            copyFrom(donor)
        }

        then:
        result.value == 'donor'
        !result.is(donor)

        when:
        retained.copyFrom([value: 'late'])

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Construction session has completed')
        result.value == 'donor'
    }

    def 'distinct overload preserves sealed LINK recipient and sealed Builder source rejection'() {
        given:
        createClass '''
            @DSL class Service {
                String value
                @Builder.Method void copyFrom(String text) { value = text }
            }
            @DSL class Registry {
                @Field(FieldType.LINK) Service linked
            }
        '''
        def retained
        def service = getClass('Service').Create.With { retained = delegate; value 'original' }

        when:
        getClass('Registry').Create.With {
            linked service
            delegate.linked.copyFrom([value: 'late'])
        }

        then:
        def recipientFailure = thrown(KlumModelException)
        recipientFailure.message.contains('sealed Builder cannot be configured')
        service.value == 'original'

        when:
        getClass('Service').Create.With { copyFrom(retained) }

        then:
        def sourceFailure = thrown(KlumModelException)
        sourceFailure.message.contains('sealed Builder')
    }

    def 'nonconflicting effective signatures and Model-only helpers still compile: #declaration'() {
        when:
        createClass """
            @DSL class Service {
                $declaration
            }
        """

        then:
        notThrown(MultipleCompilationErrorsException)

        where:
        declaration << [
            '@Builder.Method void copyFrom(Object body, int required) {}',
            '@Builder.Method <T extends CharSequence> void copyFrom(T body) {}',
            '@Builder.Method void copyFrom(String body, int ignored = 0) {}',
            'void copyFrom(Object body) {}'
        ]
    }
}
