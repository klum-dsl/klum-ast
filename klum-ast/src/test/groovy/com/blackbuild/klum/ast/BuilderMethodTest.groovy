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

import com.blackbuild.annodocimal.generator.ProjectionPolicy
import com.blackbuild.annodocimal.generator.SourceProjector
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessHelper
import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.transform.GroovyASTTransformationClass
import spock.lang.Issue

import javax.tools.ToolProvider
import java.lang.annotation.Annotation
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target
import java.lang.reflect.Modifier

import static java.lang.annotation.ElementType.METHOD
import static org.codehaus.groovy.ast.ClassHelper.VOID_TYPE
import static org.codehaus.groovy.ast.tools.GeneralUtils.block

@Issue('689')
class BuilderMethodTest extends AbstractDSLSpec {

    def "Builder is a non-instantiable namespace containing only the runtime Method marker"() {
        expect:
        Modifier.isPublic(Builder.modifiers)
        Modifier.isFinal(Builder.modifiers)
        !Builder.annotation
        Builder.declaredConstructors.size() == 1
        Modifier.isPrivate(Builder.declaredConstructors.first().modifiers)
        Builder.declaredClasses.toList() == [Builder.Method]

        and:
        Builder.Method.name == 'com.blackbuild.klum.ast.Builder$Method'
        Builder.Method.annotation
        Builder.Method.getAnnotation(Target).value().toList() == [METHOD]
        Builder.Method.getAnnotation(Retention).value() == RetentionPolicy.RUNTIME
        Builder.Method.getAnnotation(WriteAccess).value() == WriteAccess.Type.MANUAL
    }

    def "Builder Method is the only compiler-visible manual method category"() {
        given:
        MethodNode canonical = methodAnnotatedWith(Builder.Method)

        expect:
        WriteAccessHelper.getWriteAccessTypeForMethodOrField(canonical).get() == WriteAccess.Type.MANUAL
        WriteAccessHelper.isManualWriteAccess(canonical)
        WriteAccessHelper.isBuilderMethod(canonical)

        and: 'the compatibility alias carries its deprecation promotion hook'
        Mutator.getAnnotation(Deprecated).since() == '4.1'
        !Mutator.getAnnotation(Deprecated).forRemoval()
        Mutator.getAnnotation(GroovyASTTransformationClass).value().toList() == [
                'com.blackbuild.klum.ast.compiler.internal.ast.converters.MutatorToBuilderMethodTransformation'
        ]
    }

    def "Builder Method follows the legacy movement retargeting and generated contract path"() {
        when:
        createClass('''
            package vocabulary

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Mutator

            @DSL
            class Registry {
                String host

                @Builder.Method
                void normalizeHost() {
                    host = host.toLowerCase()
                }

                @Mutator
                void legacyNormalizeHost() {
                    host = host.toLowerCase()
                }

                String completedUrl() {
                    "https://$host"
                }
            }
        ''')
        Class<?> publicBuilder = getClass('vocabulary.Registry_DSL$Builder')
        instance = clazz.Create.With {
            host 'EXAMPLE.TEST'
            normalizeHost()
        }

        then: 'both Builder-only spellings have the same runtime and public API shape'
        instance.host == 'example.test'
        builderClass.getDeclaredMethod('normalizeHost').returnType == Void.TYPE
        builderClass.getDeclaredMethod('legacyNormalizeHost').returnType == Void.TYPE
        publicBuilder.getMethod('normalizeHost').returnType == Void.TYPE
        publicBuilder.getMethod('legacyNormalizeHost').returnType == Void.TYPE
        builderClass.getDeclaredMethod('normalizeHost').getAnnotation(Builder.Method)
        builderClass.getDeclaredMethod('legacyNormalizeHost').getAnnotation(Builder.Method)
        !builderClass.getDeclaredMethod('legacyNormalizeHost').getAnnotation(Mutator)
        publicBuilder.getMethod('normalizeHost').getAnnotation(Builder.Method)
        publicBuilder.getMethod('legacyNormalizeHost').getAnnotation(Builder.Method)
        !publicBuilder.getMethod('legacyNormalizeHost').getAnnotation(Mutator)
        hasNoMethod(clazz, 'normalizeHost')
        hasNoMethod(clazz, 'legacyNormalizeHost')

        and: 'an ordinary Model method remains absent from both Builder surfaces'
        hasMethod(clazz, 'completedUrl')
        hasNoMethod(builderClass, 'completedUrl')
        hasNoMethod(publicBuilder, 'completedUrl')

        when: 'statically compiled Groovy names the public Builder contract'
        Class<?> staticConsumer = createSecondaryClass('''
            package vocabulary

            import groovy.transform.CompileStatic

            @CompileStatic
            final class StaticRegistryConsumer {
                static void configure(Registry_DSL.Builder<Registry> registry) {
                    registry.normalizeHost()
                }
            }
        ''')

        and: 'Java names the same generated method'
        compileJavaConsumer('''
            package vocabulary;

            public final class JavaRegistryConsumer {
                public static void configure(Registry_DSL.Builder<Registry> registry) {
                    registry.normalizeHost();
                }
            }
        ''')

        then:
        staticConsumer.getDeclaredMethod('configure', publicBuilder)
        noExceptionThrown()
    }

    def "canonical Builder Method drives manual configurator diagnostics"() {
        when:
        createClass('''
            @DSL
            class Mailbox {
                String outboxUrl

                @Builder.Method
                Integer outboxUrl(String value) {
                    0
                }
            }
        ''')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("Manual configurator 'outboxUrl' shadows map configuration")
    }

    def "canonical and legacy Builder-only markers cannot be combined"() {
        when:
        createClass('''
            @DSL
            class Registry {
                @Builder.Method
                @Mutator
                void normalizeHost() { }
            }
        ''')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('cannot declare both @Builder.Method and deprecated @Mutator')
        error.message.count('cannot declare both @Builder.Method and deprecated @Mutator') == 1
    }

    def "Builder Method is rejected outside a DSL Object"() {
        when:
        createNonDslClass('''
            class ExternalHelper {
                @Builder.Method
                void normalizeHost() { }
            }
        ''')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Builder-only methods can only be declared by a @DSL class')
    }

    def "legacy Mutator is promoted before Builder Method validation outside a DSL Object"() {
        when:
        createNonDslClass('''
            class ExternalHelper {
                @Mutator
                void normalizeHost() { }
            }
        ''')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains('Builder-only methods can only be declared by a @DSL class')
    }

    def "the IDE mirror contains only explicitly selected Builder methods"() {
        given:
        createClass('''
            package mirror

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL

            @DSL
            class Registry {
                @Builder.Method
                void normalizeHost() { }

                String completedUrl() { 'https://example.test' }
            }
        ''')
        File mirrorRoot = new File(tempFolder.root, 'builder-method-mirror')
        File namespaceClass = new File(compilerConfiguration.targetDirectory, 'mirror/Registry_DSL.class')

        when:
        new SourceProjector(ProjectionPolicy.documentation()).projectToDirectory(namespaceClass.toPath(), mirrorRoot.toPath())
        String mirror = new File(mirrorRoot, 'mirror/Registry_DSL.java').text

        then:
        mirror.contains('void normalizeHost()')
        !mirror.contains('completedUrl')
    }

    private static MethodNode methodAnnotatedWith(Class<? extends Annotation> annotationType) {
        MethodNode method = new MethodNode('configure', Modifier.PUBLIC, VOID_TYPE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, block())
        method.addAnnotation(new AnnotationNode(ClassHelper.make(annotationType)))
        method
    }

    private void compileJavaConsumer(String source) {
        File sourceFile = new File(tempFolder.root, 'vocabulary/JavaRegistryConsumer.java')
        sourceFile.parentFile.mkdirs()
        sourceFile.text = source.stripIndent()
        String classpath = [System.getProperty('java.class.path'), compilerConfiguration.targetDirectory.absolutePath]
                .join(File.pathSeparator)
        ByteArrayOutputStream errors = new ByteArrayOutputStream()
        int result = ToolProvider.systemJavaCompiler.run(
                null,
                null,
                errors,
                '-classpath', classpath,
                '-d', compilerConfiguration.targetDirectory.absolutePath,
                sourceFile.absolutePath
        )
        assert result == 0: errors.toString()
    }
}
