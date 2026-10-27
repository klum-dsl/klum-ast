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
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import javax.tools.ToolProvider

@Issue('954')
class StructuredInputKeyBoundaryTest extends AbstractDSLSpec {
    def 'binary inherited key retains original declaring getter and external admission: #annotation'() {
        given:
        def parentDirectory = tempFolder.newFolder('parent')
        def parentConfiguration = new CompilerConfiguration(compilerConfiguration)
        parentConfiguration.targetDirectory = parentDirectory
        def parentLoader = new GroovyClassLoader(getClass().classLoader, parentConfiguration)
        def parentUnit = new CompilationUnit(parentConfiguration, null, parentLoader)
        parentUnit.addSource('JsonProperty.groovy', '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
            @interface JsonIgnore { boolean value() default true }
        ''')
        parentUnit.addSource('Base.groovy', """
            package binarykey
            import com.blackbuild.klum.ast.*
            import com.fasterxml.jackson.annotation.*
            @DSL abstract class Base { @Key $annotation String $nativeName }
        """)
        parentUnit.compile()
        loader.addClasspath(parentDirectory.absolutePath)
        createClass '''
            package binarykey
            import com.blackbuild.klum.ast.*
            @DSL class Child extends Base { String value }
            @DSL class Root {
                Child direct
                @Field(key = Field.FieldName) Child fixed
                Map<String, Child> children
            }
        '''
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL(),
                                               parentDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def child = Class.forName('binarykey.Child', false, binaryLoader)
        def root = Class.forName('binarykey.Root', false, binaryLoader)
        def failure
        def model

        when:
        try { model = KlumStructuredInput.readRoot(child, [(spelling): 'root', value: 'payload'], 'binary.yaml') }
        catch (KlumModelException caught) { failure = caught }
        def external = KlumStructuredInput.readRoot(root,
                [fixed: [:], children: [outer: [:]]], 'external.yaml')
        def plan = CompiledBuilderSchema.read(child)

        then:
        plan.key().source() == 'binarykey.Base#' + nativeName
        plan.key().contract().name == 'binarykey.Base_DSL$Builder'
        external.fixed[nativeName] == 'fixed'
        external.children.outer[nativeName] == 'outer'
        if (ignored) {
            assert failure.message.contains('@JsonIgnore(true)')
            assert failure.message.contains('binarykey.Base#' + nativeName)
        } else {
            assert failure == null
            assert model[nativeName] == 'root'
            assert model.value == 'payload'
            assert KlumStructuredInput.readRoot(root, [direct: [(spelling): 'direct']], 'direct.yaml').direct[nativeName] == 'direct'
        }

        cleanup:
        binaryLoader?.close()
        parentLoader?.close()

        where:
        annotation                                      | spelling  | ignored | nativeName
        ''                                              | 'name'    | false   | 'name'
        "@JsonProperty('identity')"                     | 'identity'| false   | 'name'
        "@JsonIgnore(false) @JsonProperty('identity')"   | 'identity'| false   | 'name'
        '@JsonIgnore'                                   | 'name'    | true    | 'name'
        "@JsonProperty('identity')"                     | 'identity'| false   | 'key'
        '@JsonIgnore'                                   | 'key'     | true    | 'key'
    }

    @Issue('959')
    def 'missing key facts reject member and external-root input'() {
        given:
        createClass '''
            package oldkey
            import com.blackbuild.klum.ast.*
            @DSL class Root { @Key String name; String value }
        '''
        rewriteContract('oldkey/Root_DSL$Builder.class', 'incomplete')
        def binaryLoader = binaryLoader()
        def root = Class.forName('oldkey.Root', false, binaryLoader)

        when:
        KlumStructuredInput.readRoot(root, [name: 'input'], 'old.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('Construction key enrichment disagrees')
        failure.message.contains('recompile and package the Schema')

        when:
        KlumStructuredInput.readRoot(root, 'external', [value: 'ok'], 'explicit.yaml')

        then:
        def externalFailure = thrown(KlumModelException)
        externalFailure.message.contains('Construction key enrichment disagrees')

        cleanup:
        binaryLoader?.close()
    }

    @Issue('959')
    def 'missing direct authority rejects direct and map payloads'() {
        given:
        createClass '''
            package oldkey
            import com.blackbuild.klum.ast.*
            @DSL class Root { Child child; Map<String, Child> children }
            @DSL class Child { @Key String name }
        '''
        rewriteContract('oldkey/Root_DSL$Builder.class', 'incomplete')
        def binaryLoader = binaryLoader()
        def root = Class.forName('oldkey.Root', false, binaryLoader)

        when:
        KlumStructuredInput.readRoot(root, [child: [name: 'input']], 'old-child.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('direct relationship key-authority metadata')
        failure.message.contains('recompile and package the Schema')

        when:
        KlumStructuredInput.readRoot(root, [children: [outer: [:]]], 'map.yaml')

        then:
        def mapFailure = thrown(KlumModelException)
        mapFailure.message.contains('direct relationship key-authority metadata')

        cleanup:
        binaryLoader?.close()
    }

    @Issue('959')
    def 'malformed and future key metadata reject with contract and recompile guidance: #mutation'() {
        given:
        createClass '''
            package oldkey
            import com.blackbuild.klum.ast.*
            @DSL class Root { @Key String name }
        '''
        rewriteContract('oldkey/Root_DSL$Builder.class', mutation)
        def binaryLoader = binaryLoader()
        def root = Class.forName('oldkey.Root', false, binaryLoader)

        when:
        KlumStructuredInput.readRoot(root, [name: 'input'], 'metadata.yaml')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('metadata.yaml')
        failure.message.contains('Root_DSL$Builder')
        failure.message.contains('recompile and package the Schema')
        failure.message.contains(detail)
        failure.cause != null

        cleanup:
        binaryLoader?.close()

        where:
        mutation        | detail
        'missingGetter' | 'Construction key enrichment disagrees'
        'wrongName'     | 'Invalid construction key getter'
        'future'        | 'Unknown schema member futureKeyFact'
        'wrongType'     | 'Invalid construction key getter'
    }

    def 'Java and statically compiled Groovy call the exact three-argument public overload'() {
        given:
        createClass '''
            package keyclients
            import com.blackbuild.klum.ast.*
            @DSL class Deployment { @Key String name }
        '''
        def source = new File(compilerConfiguration.targetDirectory, 'JavaKeyClient.java')
        source.text = '''
            import keyclients.Deployment;
            import com.blackbuild.klum.ast.runtime.KlumStructuredInput;
            import java.util.Map;
            public class JavaKeyClient {
                public static Deployment read() {
                    return KlumStructuredInput.readRoot(Deployment.class, Map.of("name", "java"), "java.yaml");
                }
            }
        '''
        def classpath = System.getProperty('java.class.path') + File.pathSeparator + compilerConfiguration.targetDirectory.absolutePath

        when:
        def status = ToolProvider.systemJavaCompiler.run(null, null, null,
                '--release', '17', '-cp', classpath, '-d', compilerConfiguration.targetDirectory.absolutePath, source.absolutePath)
        loader.parseClass '''
            package keyclients
            import com.blackbuild.klum.ast.runtime.KlumStructuredInput
            import groovy.transform.CompileStatic
            @CompileStatic class StaticKeyClient {
                static Deployment read() { KlumStructuredInput.readRoot(Deployment, [name: 'groovy'], 'groovy.yaml') }
            }
        '''
        def binaryLoader = binaryLoader()
        def javaModel = Class.forName('JavaKeyClient', true, binaryLoader).read()
        def groovyModel = Class.forName('keyclients.StaticKeyClient', true, binaryLoader).read()

        then:
        status == 0
        javaModel.name == 'java'
        groovyModel.name == 'groovy'

        cleanup:
        binaryLoader?.close()
    }

    def 'multiple actual keys remain a compiler error'() {
        when:
        createClass '''
            package duplicatekey
            import com.blackbuild.klum.ast.*
            @DSL abstract class Base { @Key String name }
            @DSL class Child extends Base { @Key String other }
        '''

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('only one is allowed in hierarchy')
    }

    private URLClassLoader binaryLoader() {
        new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
    }

    private void rewriteContract(String path, String mutation) {
        def file = new File(compilerConfiguration.targetDirectory, path)
        def writer = new ClassWriter(0)
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                def target = super.visitAnnotation(descriptor, visible)
                if (!descriptor.endsWith('GeneratedInput$Schema;')) { return target }
                new AnnotationVisitor(Opcodes.ASM9, target) {
                    @Override void visit(String member, Object value) {
                        if (mutation != 'incomplete' || member != 'keyInput') { super.visit(member, value) }
                    }
                    @Override void visitEnd() {
                        if (mutation == 'future') { super.visit('futureKeyFact', true) }
                        super.visitEnd()
                    }
                }
            }
            @Override MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                String resultDescriptor = mutation == 'wrongType' && name == 'getName' ? '()Ljava/lang/Object;' : descriptor
                new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, resultDescriptor, signature, exceptions)) {
                    @Override AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        if (annotation.endsWith('GeneratedInput$Key;') && mutation in ['incomplete', 'missingGetter']) { return null }
                        def target = super.visitAnnotation(annotation, visible)
                        if (annotation.endsWith('GeneratedInput$Key;') && mutation == 'wrongName') {
                            return new AnnotationVisitor(Opcodes.ASM9, target) {
                                @Override void visit(String member, Object value) {
                                    super.visit(member, member == 'value' ? 'other' : value)
                                }
                            }
                        }
                        if (annotation.endsWith('GeneratedInput$Field;') && mutation == 'incomplete') {
                            return new AnnotationVisitor(Opcodes.ASM9, target) {
                                @Override void visit(String member, Object value) {
                                    if (member != 'schemaKey') { super.visit(member, value) }
                                }
                            }
                        }
                        target
                    }
                }
            }
        }, 0)
        file.bytes = writer.toByteArray()
    }
}
