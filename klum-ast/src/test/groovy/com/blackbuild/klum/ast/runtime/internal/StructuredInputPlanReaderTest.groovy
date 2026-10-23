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
import groovyjarjarasm.asm.FieldVisitor
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationUnit
import spock.lang.Issue

@Issue('932')
class StructuredInputPlanReaderTest extends AbstractDSLSpec {

    def 'untouched 938 artifact requires recompilation after the approved unreleased format refinement'() {
        given:
        def resource = getClass().getResource('/structured-input-938/wire-plan-b-938.jar')
        assert resource != null
        def binaryLoader = new URLClassLoader([resource] as URL[], getClass().classLoader)
        def original = Class.forName('original938.Original', false, binaryLoader)

        when:
        LegacyStructuredInputPlan.read(original)

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile and package the Schema')
        failure.cause.message.contains('Malformed carrier record')

        cleanup:
        binaryLoader?.close()
    }

    def 'reads packaged carrier roles and exact public contracts without Schema or enum initialization'() {
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
        def plan = LegacyStructuredInputPlan.read(document)

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

    def 'rejects #damage packaged carrier without initializing Schema or enum code'() {
        given:
        createClass '''
            package damagedplan
            import com.blackbuild.klum.ast.DSL
            @DSL class Configuration {
                static { throw new AssertionError('Schema initialized during rejected extraction') }
                String title
                List<String> words
                Mode mode
            }
            enum Mode {
                READY
                static { throw new AssertionError('Enum initialized during rejected extraction') }
            }
        '''
        def file = new File(compilerConfiguration.targetDirectory, 'damagedplan/Configuration_DSL$WirePlan.class')
        String data = dataOf(file)
        switch (damage) {
            case 'missing': assert file.delete(); break
            case 'truncated class': file.bytes = [0, 1, 2] as byte[]; break
            case 'header': replaceData(file, data.replace('WIRE-PLAN/B', 'UNKNOWN')); break
            case 'identity': replaceData(file, data.replace(token('damagedplan.Configuration'), token('damagedplan.Other'))); break
            case 'signature': replaceData(file, data.replace(token('title(java.lang.String):java.lang.String'), token('title(java.lang.Integer):java.lang.String'))); break
            case 'source owner': replaceData(file, data.replace(token('damagedplan.Configuration#title'), token('damagedplan.Other#title'))); break
            case 'scalar type': replaceData(file, data.replace('[SCALAR,' + token('java.lang.String'), '[SCALAR,' + token('java.lang.Object'))); break
            case 'container type': replaceData(file, data.replace('[LIST,' + token('java.util.List'), '[LIST,' + token('java.util.ArrayList'))); break
            case 'fixed key on property':
                replaceData(file, data.readLines().collect { line ->
                    line.startsWith('entry|' + token('title') + '|') ? line.substring(0, line.lastIndexOf('|') + 1) + token('forged') : line
                }.join('\n') + '\n')
                break
            case 'enum type': replaceData(file, data.replace('[ENUM,' + token('damagedplan.Mode'), '[ENUM,' + token('java.lang.String'))); break
        }
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def configuration = Class.forName('damagedplan.Configuration', false, binaryLoader)

        when:
        LegacyStructuredInputPlan.read(configuration)

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('recompile and package the Schema')
        failure.cause != null
        if (damage == 'fixed key on property') assert failure.cause.message.contains('Fixed direct-child key')

        cleanup:
        binaryLoader?.close()

        where:
        damage << ['missing', 'truncated class', 'header', 'identity', 'signature', 'source owner', 'enum type', 'scalar type', 'container type', 'fixed key on property']
    }

    def 'follows only explicit separately compiled Builder parents and requires their carrier: missing=#missingParent'() {
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
        if (missingParent) assert new File(parentDirectory, 'inheritedplan/Parent_DSL$WirePlan.class').delete()
        def binaryLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL(), parentDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def child = Class.forName('inheritedplan.Child', false, binaryLoader)
        def plan
        def failure

        when:
        try { plan = LegacyStructuredInputPlan.read(child) }
        catch (KlumModelException caught) { failure = caught }

        then:
        if (missingParent) {
            assert failure != null
            assert failure.message.contains('recompile and package the Schema')
            assert failure.cause.message.contains('Parent_DSL$WirePlan.class')
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

    def 'fresh JVM reads accepted or rejected carrier without Schema enum factory or callback execution: accepted=#accepted'() {
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
        def carrier = new File(compilerConfiguration.targetDirectory, 'freshplan/Configuration_DSL$WirePlan.class')
        if (!accepted) replaceData(carrier, dataOf(carrier).replace('WIRE-PLAN/B', 'UNKNOWN'))
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
        def plan = LegacyStructuredInputPlan.read(child)

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

    private static String token(String value) {
        Base64.urlEncoder.withoutPadding().encodeToString(value.getBytes('UTF-8'))
    }

    private static String dataOf(File file) {
        String[] data = new String[1]
        new ClassReader(file.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (name == 'DATA') data[0] = (String) value
                null
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        data[0]
    }

    private static void replaceData(File file, String data) {
        def writer = new ClassWriter(0)
        writer.visit(Opcodes.V1_8, Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | Opcodes.ACC_SYNTHETIC,
                new ClassReader(file.bytes).className, null, 'java/lang/Object', null)
        writer.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                'DATA', 'Ljava/lang/String;', null, data).visitEnd()
        writer.visitEnd()
        file.bytes = writer.toByteArray()
    }

    @CompileStatic
    private static String nameOf(Class<?> type) { type.getName() }
}
