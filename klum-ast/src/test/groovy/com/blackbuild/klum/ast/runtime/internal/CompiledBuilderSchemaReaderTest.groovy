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
import groovy.transform.CompileStatic
import com.blackbuild.klum.ast.runtime.KlumModelException
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.ClassWriter
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationUnit
import spock.lang.Issue

@Issue(['529', '932'])
class CompiledBuilderSchemaReaderTest extends AbstractDSLSpec {

    def 'reads compiled input roles and exact public contracts without Schema or enum initialization'() {
        given:
        createClass '''
            package runtimeplan
            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.layer3.Cluster
            @DSL class Document {
                static { throw new AssertionError('Schema initialized during extraction') }
                String title
                Stage stage
                Branch branch
                @SlotMarker Slot slot
                @Cluster(value = SlotMarker, fixedKeys = true) Map<String, Slot> getSlots() {}
                Map<String, Entry> entries
                @Builder.Method void words(List<String> value) { }
            }
            enum Stage {
                READY
                static { throw new AssertionError('Enum initialized during extraction') }
            }
            @interface SlotMarker {}
            @DSL class Slot {
                static { throw new AssertionError('Fixed-key child initialized during extraction') }
                @Key String name
            }
            @DSL class Branch { String text }
            @DSL class Entry { @Key String name; String text }
        '''
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def document = Class.forName('runtimeplan.Document', false, binaryLoader)

        when:
        def plan = new InputContractResolver().read(document)

        then:
        plan.entries().keySet() == ['branch', 'entries', 'slot', 'stage', 'title', 'words'] as Set
        plan.entries().get('slot').fixedChildKey() == 'slot'
        plan.entries().get('branch').role().name() == 'OWNED_OBJECT'
        plan.entries().get('entries').role().name() == 'KEYED_OWNED_MAP'
        nameOf(plan.entries().get('stage').payload().type()) == 'runtimeplan.Stage'
        plan.entries().get('words').methods()*.parameterTypes*.toList() == [[List]]
        nameOf(plan.contract()) == 'runtimeplan.Document_DSL$Builder'

        cleanup:
        binaryLoader.close()
    }

    def 'follows only explicit separately compiled Builder parents and requires their enrichment: missing=#missingParent'() {
        given:
        def parentDirectory = tempFolder.newFolder('parent')
        def parentConfiguration = new CompilerConfiguration(compilerConfiguration)
        parentConfiguration.targetDirectory = parentDirectory
        def parentLoader = new GroovyClassLoader(getClass().classLoader, parentConfiguration)
        def parentUnit = new CompilationUnit(parentConfiguration, null, parentLoader)
        parentUnit.addSource('Parent.groovy', '''
            package inheritedplan
            import com.blackbuild.klum.ast.*
            @DSL abstract class Parent {
                static { throw new AssertionError('Parent initialized during extraction') }
                String parentValue
                @Builder.Method void parentOperation(String value) { }
            }
        ''')
        parentUnit.compile()
        loader.addClasspath(parentDirectory.absolutePath)
        def childUnit = new CompilationUnit(compilerConfiguration, null, loader)
        childUnit.addSource('Child.groovy', '''
            package inheritedplan
            import com.blackbuild.klum.ast.DSL
            @DSL class Child extends Parent {
                static { throw new AssertionError('Child initialized during extraction') }
                String childValue
            }
        ''')
        childUnit.compile()
        if (missingParent) {
            removeEnrichment(new File(parentDirectory, 'inheritedplan/Parent_DSL$Builder.class'))
        }
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL(), parentDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def child = Class.forName('inheritedplan.Child', false, binaryLoader)
        def plan
        def failure

        when:
        try { plan = new InputContractResolver().read(child) }
        catch (KlumModelException caught) { failure = caught }

        then:
        if (missingParent) {
            assert failure != null
            assert failure.message.contains('recompile and package the Schema')
            assert failure.message.contains('Missing generated input enrichment')
        } else {
            assert failure == null
            assert plan.entries().keySet() == ['childValue', 'parentOperation', 'parentValue'] as Set
            assert plan.entries().get('parentOperation').source() == 'inheritedplan.Parent#parentOperation(java.lang.String):void'
        }

        cleanup:
        binaryLoader?.close()
        parentLoader?.close()

        where:
        missingParent << [false, true]
    }

    def 'fresh JVM reads accepted or rejected public enrichment without Schema enum factory or callback execution: accepted=#accepted'() {
        given:
        createClass '''
            package freshplan
            import com.blackbuild.klum.ast.*
            @DSL class Configuration {
                static { throw new AssertionError('Schema initialization') }
                String title = fail()
                Mode mode
                static String fail() { throw new AssertionError('Source initializer') }
                @PostCreate void created() { throw new AssertionError('Lifecycle execution') }
            }
            enum Mode {
                READY
                static { throw new AssertionError('Enum initialization') }
            }
        '''
        if (!accepted) {
            removeEnrichment(new File(compilerConfiguration.targetDirectory, 'freshplan/Configuration_DSL$Builder.class'))
        }
        def classpath = System.getProperty('java.class.path') + File.pathSeparator + compilerConfiguration.targetDirectory.absolutePath

        when:
        def process = new ProcessBuilder(new File(System.getProperty('java.home'), 'bin/java').absolutePath,
                '-cp', classpath, StructuredInputReaderProbe.name, 'freshplan.Configuration', accepted ? 'accepted' : 'rejected')
                .redirectErrorStream(true).start()
        def output = process.inputStream.text
        def status = process.waitFor()

        then:
        status == 0
        output.contains(accepted ? 'ACCEPTED:[mode, title]' : 'REJECTED:')

        where:
        accepted << [true, false]
    }

    def 'composes separately compiled external-name collisions without inventing a winner'() {
        given:
        def parentDirectory = tempFolder.newFolder('parent')
        def parentConfiguration = new CompilerConfiguration(compilerConfiguration)
        parentConfiguration.targetDirectory = parentDirectory
        def parentLoader = new GroovyClassLoader(getClass().classLoader, parentConfiguration)
        def parentUnit = new CompilationUnit(parentConfiguration, null, parentLoader)
        parentUnit.addSource('JsonProperty.groovy', '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
        ''')
        parentUnit.addSource('Parent.groovy', '''
            package collisionplan
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL abstract class Parent {
                static { throw new AssertionError('Parent initialization') }
                @JsonProperty('clash') String first
            }
        ''')
        parentUnit.compile()
        loader.addClasspath(parentDirectory.absolutePath)
        def childUnit = new CompilationUnit(compilerConfiguration, null, loader)
        childUnit.addSource('Child.groovy', '''
            package collisionplan
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonProperty
            @DSL class Child extends Parent {
                static { throw new AssertionError('Child initialization') }
                @JsonProperty('clash') String other
                String available
            }
        ''')
        childUnit.compile()
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL(), parentDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def child = Class.forName('collisionplan.Child', false, binaryLoader)

        when:
        def plan = new InputContractResolver().read(child)

        then:
        plan.entries().keySet() == ['available'] as Set
        plan.rejections()*.source().containsAll(['collisionplan.Child#other', 'collisionplan.Parent#first'])
        plan.rejections()*.reason().every {
            it == "external-name collision for 'clash': collisionplan.Child#other, collisionplan.Parent#first"
        }

        cleanup:
        binaryLoader?.close()
        parentLoader?.close()
    }

    @Issue('959')
    def 'validated declaration snapshots defensively freeze nested facts'() {
        given:
        createClass '''
            package frozenfacts
            import com.blackbuild.klum.ast.*
            @DSL class Document {
                @InputExamples(['first', 'second']) String title
            }
        '''
        def binaries = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def builder = Class.forName('frozenfacts.Document_DSL$Builder', false, binaries)
        def facts = new BuilderContractReader().metadata(builder)
        def field = facts.methods().values().find { it.field() == 'title' }

        when:
        facts.methods().clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        facts.invisible().clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        field.examples().add('mutated')

        then:
        thrown(UnsupportedOperationException)
        field.examples() == ['first', 'second']

        when:
        field.constraints().clear()

        then:
        thrown(UnsupportedOperationException)

        cleanup:
        binaries?.close()
    }

    @Issue('959')
    def 'one resolution session shares exact declaration reads and exposes immutable effective contracts'() {
        given:
        createClass '''
            package sessionfacts
            import com.blackbuild.klum.ast.*
            @DSL abstract class Base {
                static { System.setProperty('input.session.initialized', 'yes'); throw new AssertionError('initialization control') }
                @Key @InputExamples(['catalog']) String documentId
                @InputExamples(['first', 'second']) String displayName
            }
            @InputNaming(InputNaming.Profile.KEBAB_CASE)
            @DSL class Document extends Base { String title }
        '''
        def binaries = new CountingContractLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def document = Class.forName('sessionfacts.Document', false, binaries)
        def builder = Class.forName('sessionfacts.Base_DSL$Builder', false, binaries)
        def resolver = new InputContractResolver()

        when:
        def plan = resolver.read(document)
        def repeated = resolver.read(document)

        then:
        binaries.reads['sessionfacts/Document_DSL$Builder.class'] == 1
        binaries.reads['sessionfacts/Base_DSL$Builder.class'] == 1
        plan.key().externalName() == 'document-id'
        plan.entries().keySet() == ['display-name', 'title'] as Set
        plan.entries() == repeated.entries()
        plan.constraintProfiles().keySet().contains(builder)
        plan.constraintProfiles().get(builder).fields().keySet() == ['displayName'] as Set
        System.getProperty('input.session.initialized') == null

        when:
        plan.constraintProfiles().clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        plan.constraintProfiles().get(builder).fields().clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        plan.entries().get('display-name').examples().clear()

        then:
        thrown(UnsupportedOperationException)

        when:
        plan.key().examples().clear()

        then:
        thrown(UnsupportedOperationException)

        when: 'another session reads declarations again rather than retaining a loader cache'
        new InputContractResolver().read(document)

        then:
        binaries.reads['sessionfacts/Document_DSL$Builder.class'] == 2
        binaries.reads['sessionfacts/Base_DSL$Builder.class'] == 2
        System.getProperty('input.session.initialized') == null

        when: 'a positive control proves the binary initialization trap is live'
        Class.forName('sessionfacts.Document', true, binaries)

        then:
        def failure = thrown(AssertionError)
        failure.message == 'initialization control'
        System.getProperty('input.session.initialized') == 'yes'

        cleanup:
        binaries?.close()
        System.clearProperty('input.session.initialized')
    }

    @CompileStatic
    private static class CountingContractLoader extends URLClassLoader {
        final Map<String, Integer> reads = new LinkedHashMap<>()

        CountingContractLoader(URL[] urls, ClassLoader parent) { super(urls, parent) }

        @Override
        URL getResource(String name) {
            if (name.startsWith('sessionfacts/')) {
                reads.put(name, reads.getOrDefault(name, 0) + 1)
            }
            return super.getResource(name)
        }
    }

    private static void removeEnrichment(File contract) {
        def writer = new ClassWriter(0)
        new ClassReader(contract.bytes).accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                descriptor.endsWith('GeneratedInput$Schema;') ? null : super.visitAnnotation(descriptor, visible)
            }
        }, 0)
        contract.bytes = writer.toByteArray()
    }

    @CompileStatic
    private static String nameOf(Class<?> type) { type.getName() }
}
