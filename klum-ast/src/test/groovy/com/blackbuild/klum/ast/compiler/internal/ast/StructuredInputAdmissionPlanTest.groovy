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
package com.blackbuild.klum.ast.compiler.internal.ast

import com.blackbuild.klum.ast.AbstractDSLSpec
import groovy.lang.GroovyClassLoader
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import groovyjarjarasm.asm.AnnotationVisitor
import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.FieldVisitor
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes

import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.jar.JarFile
import java.util.regex.Pattern

@Issue('932')
class StructuredInputAdmissionPlanTest extends AbstractDSLSpec {

    def "classifies direct and inherited WIRE-PLAN entries from compiler provenance"() {
        given:
        def unit = compilationUnit('''
            package admission

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            import com.fasterxml.jackson.annotation.JsonIgnore
            import com.fasterxml.jackson.annotation.JsonProperty

            enum Region { EU, US }

            @DSL class Base {
                String inheritedLabel
                @JsonProperty('renamed') Region region
                @JsonIgnore(true) String secret
                @JsonIgnore(true) @JsonProperty('still-hidden') String ignoredRename
                @JsonIgnore String implicitSecret
                @JsonIgnore(false) @JsonProperty('') String fallback

                @Builder.Method
                void inheritedOperation(String level) { }
            }

            @DSL class Child {
                @Key String name
                String value
            }

            @DSL class Parent extends Base {
                List<String> labels
                Map<String, String> annotations
                Map<String, List<String>> nestedAnnotations
                Child child
                Map<String, Child> children

                @Builder.Method
                void promote(String level) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'admission.Parent' })

        then:
        plan.model() == 'admission.Parent'
        plan.builder() == 'admission.Parent_DSL$Builder'
        plan.entries().collect { [it.externalName(), it.role().name(), it.operation().name(), it.payload().kind().name()] } == [
                ['annotations', 'PROPERTY', 'annotations', 'MAP'],
                ['child', 'OWNED_OBJECT', 'child', 'NESTED_OBJECT'],
                ['children', 'KEYED_OWNED_MAP', 'children', 'NESTED_OBJECT'],
                ['fallback', 'PROPERTY', 'fallback', 'SCALAR'],
                ['inheritedLabel', 'PROPERTY', 'inheritedLabel', 'SCALAR'],
                ['inheritedOperation', 'OPERATION', 'inheritedOperation', 'SCALAR'],
                ['labels', 'PROPERTY', 'labels', 'LIST'],
                ['nestedAnnotations', 'PROPERTY', 'nestedAnnotations', 'MAP'],
                ['promote', 'OPERATION', 'promote', 'SCALAR'],
                ['renamed', 'PROPERTY', 'region', 'ENUM']
        ]
        plan.entries().find { it.externalName() == 'renamed' }.nameOrigin().name() == 'JSON_PROPERTY'
        plan.entries().find { it.externalName() == 'nestedAnnotations' }.payload().with {
            kind().name() == 'MAP' && keyType() == 'java.lang.String' && item().kind().name() == 'LIST' && item().item().kind().name() == 'SCALAR'
        }
        plan.entries().find { it.externalName() == 'children' }.with {
            mapKeyType() == 'java.lang.String' && excludedChildKeyMember() == 'name' && payload().builder() == 'admission.Child_DSL$Builder'
        }
        plan.entries()*.operation().every { it.declaringBuilder().endsWith('_DSL$Builder') && it.descriptorGroup() }
        plan.rejections()*.reason() == ['excluded by @JsonIgnore(true)', 'excluded by @JsonIgnore(true)', 'excluded by @JsonIgnore(true)']
    }

    def "rejects unsupported authored operations relationships and colliding external names deterministically"() {
        given:
        def unit = compilationUnit('''
            package rejection

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL class Child {
                @Key String name
            }

            @DSL abstract class AbstractChild {
            }

            @DSL class Parent {
                List<Child> unkeyedChildren
                AbstractChild abstractChild
                Map<Integer, Child> numberedChildren
                char marker

                @JsonProperty('duplicate') String first
                @JsonProperty('duplicate') String second

                @Builder.Method
                void reset() { }

                @Builder.Method
                void replace(String first, String second) { }

                @Builder.Method
                void raw(Map values) { }

                @JsonProperty('operation') @Builder.Method
                void firstOperation(String value) { }

                @JsonProperty('operation') @Builder.Method
                void secondOperation(String value) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'rejection.Parent' })

        then:
        plan.entries().empty
        plan.rejections()*.source() == plan.rejections()*.source().sort()
        plan.rejections().collectEntries { [it.source(), it.reason()] }.with {
            get('rejection.Parent#abstractChild') == 'requires one concrete owned relationship'
            get('rejection.Parent#numberedChildren') == 'requires Map<String, concrete keyed DSL Object> ownership'
            get('rejection.Parent#unkeyedChildren') == 'has no admitted scalar, enum, List, or Map payload'
            get('rejection.Parent#marker') == 'has no admitted scalar, enum, List, or Map payload'
            get('rejection.Parent#firstOperation(java.lang.String):void').startsWith("external-name collision for 'operation':")
            get('rejection.Parent#secondOperation(java.lang.String):void').startsWith("external-name collision for 'operation':")
            get('rejection.Parent#replace(java.lang.String, java.lang.String):void') == '@Builder.Method must declare exactly one payload'
            get('rejection.Parent#raw(java.util.Map):void') == '@Builder.Method payload is outside the admitted grammar'
            get('rejection.Parent#reset():void') == '@Builder.Method must declare exactly one payload'
        }
    }

    def "retains unrelated entries while rejecting an ambiguous external name deterministically"() {
        given:
        def unit = compilationUnit('''
            package collision

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL class Configuration {
                @JsonProperty('endpoint') String primaryEndpoint
                @JsonProperty('endpoint') String secondaryEndpoint
                String region

                @JsonProperty('endpoint') @Builder.Method
                void configure(String value) { }

                @Builder.Method
                void refresh(String value) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'collision.Configuration' })

        then:
        plan.entries().collect { [it.externalName(), it.role().name()] } == [
                ['refresh', 'OPERATION'],
                ['region', 'PROPERTY']
        ]
        plan.entries().find { it.externalName() == 'refresh' }.operation().descriptorGroup().any {
            it.startsWith('refresh(java.lang.String):')
        }
        plan.rejections()*.source() == plan.rejections()*.source().sort()
        plan.rejections()*.source() == [
                'collision.Configuration#configure(java.lang.String):void',
                'collision.Configuration#primaryEndpoint',
                'collision.Configuration#secondaryEndpoint'
        ]
        plan.rejections()*.reason().every {
            it == "external-name collision for 'endpoint': collision.Configuration#configure(java.lang.String):void, collision.Configuration#primaryEndpoint, collision.Configuration#secondaryEndpoint"
        }
    }

    def "classifies an owned relationship before the target namespace is generated"() {
        given:
        def unit = compilationUnit('''
            package consumer

            import com.blackbuild.klum.ast.DSL

            @DSL class Parent {
                Child child
            }

            @DSL class Child {
                String value
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'consumer.Parent' })

        then:
        plan.entries()[0].payload().with {
            type() == 'consumer.Child' && builder() == 'consumer.Child_DSL$Builder'
        }
    }

    def "emits one package-private constant-only carrier containing the canonical normalized plan"() {
        given:
        def unit = compilationUnit('''
            package carrier

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL

            @DSL class Configuration {
                String title
                @Builder.Method void promote(String level) { }
            }
        ''')

        when:
        unit.compile()
        def carrier = new File(compilerConfiguration.targetDirectory, 'carrier/Configuration_DSL$WirePlan.class')
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'carrier.Configuration' })
        def access = new int[1]
        def members = [:]
        def methods = []
        new ClassReader(carrier.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            void visit(int version, int flags, String name, String signature, String superName, String[] interfaces) {
                access[0] = flags
            }

            @Override
            FieldVisitor visitField(int flags, String name, String descriptor, String signature, Object value) {
                members[name] = [flags, descriptor, value]
                null
            }

            @Override
            MethodVisitor visitMethod(int flags, String name, String descriptor, String signature, String[] exceptions) {
                methods << [flags, name, descriptor]
                null
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)

        then:
        carrier.file
        (access[0] & Opcodes.ACC_PUBLIC) == 0
        members == [DATA: [Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL, 'Ljava/lang/String;', plan.canonicalForm()]]
        methods.empty
        plan.canonicalForm().startsWith("WIRE-PLAN/B\nmodel|")
        plan.canonicalForm().contains('entry|')
    }

    def "retained public Builder annotations cannot preserve the normalized relationship roles or source identities"() {
        given:
        def unit = compilationUnit('''
            package annotationcandidate

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key

            @DSL class Child { @Key String name }

            @DSL class Parent {
                String title
                Child child
                Map<String, Child> children
                @Builder.Method void rename(String value) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'annotationcandidate.Parent' })
        def annotations = []
        def builder = new File(compilerConfiguration.targetDirectory, 'annotationcandidate/Parent_DSL$Builder.class')
        new ClassReader(builder.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                        annotations << [method: name, annotation: annotation]
                        null
                    }
                }
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)

        then:
        plan.entries()*.role()*.name().containsAll(['PROPERTY', 'OWNED_OBJECT', 'KEYED_OWNED_MAP', 'OPERATION'])
        annotations.findAll { it.annotation == 'Lcom/blackbuild/klum/ast/Builder$Method;' }*.method == ['rename']
        readCarrierData(new File(compilerConfiguration.targetDirectory, 'annotationcandidate/Parent_DSL$WirePlan.class')).contains('KEYED_OWNED_MAP')
        plan.canonicalForm().contains(Base64.urlEncoder.withoutPadding().encodeToString('annotationcandidate.Parent#children'.bytes))
    }

    def "runtime and schema bytecode projections agree on direct inherited and rejected WIRE-PLAN facts"() {
        given:
        def unit = compilationUnit('''
            package binarycarrier

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key

            @DSL class Nested { @Key String name }

            @DSL class Parent {
                static { throw new AssertionError('Schema initialization is forbidden during extraction') }
                String inheritedValue
                @Builder.Method void inheritedOperation(String value) { }
            }

            @DSL class Child extends Parent {
                String directValue
                Nested child
                Map<String, Nested> children
                @Builder.Method void reset() { }
            }
        ''')

        when:
        unit.compile()
        def carrier = new File(compilerConfiguration.targetDirectory, 'binarycarrier/Child_DSL$WirePlan.class')
        def runtimeFacts = runtimeShapedFacts(carrier)
        def schemaFacts = schemaShapedFacts(carrier)

        then:
        carrier.file
        runtimeFacts == schemaFacts
        runtimeFacts.entries*.name.containsAll(['inheritedValue', 'directValue', 'child', 'children'])
        runtimeFacts.entries*.role.containsAll(['PROPERTY', 'OWNED_OBJECT', 'KEYED_OWNED_MAP', 'OPERATION'])
        runtimeFacts.entries*.payload.any { it.contains('NESTED_OBJECT') }
        runtimeFacts.entries*.source.contains('binarycarrier.Parent#inheritedValue')
        runtimeFacts.rejections*.reason.contains('@Builder.Method must declare exactly one payload')
    }

    def "separately compiled carrier readers compose inherited public Builder contracts"() {
        given:
        createClass '''
            package separatecarrier
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            @DSL class Parent {
                String inheritedValue
                @Builder.Method void inheritedOperation(String value) { }
            }
        '''
        when:
        createSecondaryClass '''
            package separatecarrier
            import com.blackbuild.klum.ast.DSL
            @DSL class Child extends Parent { String directValue }
        '''
        def carrier = new File(compilerConfiguration.targetDirectory, 'separatecarrier/Child_DSL$WirePlan.class')
        def runtimeFacts = runtimeShapedFacts(carrier)
        def schemaFacts = schemaShapedFacts(carrier)
        then:
        runtimeFacts == schemaFacts
        runtimeFacts.entries*.name.containsAll(['inheritedValue', 'inheritedOperation', 'directValue'])
    }

    def "a fresh JVM extracts accepted and rejected facts without initializing a schema class"() {
        given:
        def unit = compilationUnit('''
            package freshcarrier

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.PostApply
            import com.blackbuild.klum.ast.PostCreate
            import com.blackbuild.klum.ast.Validate

            enum ExplosiveMode {
                SAFE
                static { throw new AssertionError('Enum initialization is forbidden during extraction') }
            }

            @DSL class FactoryTrap {
                static { throw new AssertionError('Factory target initialization is forbidden during extraction') }
            }

            @DSL class Configuration {
                static { throw new AssertionError('Schema initialization is forbidden during extraction') }

                String title
                ExplosiveMode mode
                FactoryTrap factoryTarget
                @Builder.Method void promote(String level) { }
                @Builder.Method void reset() { }
                @PostCreate void afterCreate() { throw new AssertionError('Callback execution is forbidden during extraction') }
                @PostApply void afterApply() { throw new AssertionError('Callback execution is forbidden during extraction') }
                @Validate void validateConfiguration() { throw new AssertionError('Callback execution is forbidden during extraction') }
            }
        ''')

        when:
        unit.compile()
        def carrier = new File(compilerConfiguration.targetDirectory, 'freshcarrier/Configuration_DSL$WirePlan.class')
        def process = new ProcessBuilder(
                new File(System.getProperty('java.home'), 'bin/java').absolutePath,
                '-cp', System.getProperty('java.class.path'),
                WirePlanCarrierReader.name,
                carrier.absolutePath
        ).redirectErrorStream(true).start()
        def output = process.inputStream.text

        then:
        process.waitFor() == 0
        output.contains('entry|')
        output.contains('rejection|')
        output.contains('dGl0bGU')
        output.contains('cHJvbW90ZQ')
        output.contains('cmVzZXQ')
    }

    def "compiled carrier has no public or runtime-internal linkage"() {
        given:
        def unit = compilationUnit('''
            package carrierabi

            import com.blackbuild.klum.ast.DSL

            @DSL class Configuration { String title }
        ''')

        when:
        unit.compile()
        def carrier = new File(compilerConfiguration.targetDirectory, 'carrierabi/Configuration_DSL$WirePlan.class')
        def access = new int[1]
        def references = new LinkedHashSet<String>()
        new ClassReader(carrier.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            void visit(int version, int flags, String name, String signature, String superName, String[] interfaces) {
                access[0] = flags
                references.addAll([name, signature, superName])
                references.addAll(interfaces ?: [])
            }

            @Override
            FieldVisitor visitField(int flags, String name, String descriptor, String signature, Object value) {
                references.addAll([descriptor, signature])
                null
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)

        then:
        carrier.file
        (access[0] & Opcodes.ACC_PUBLIC) == 0
        !references.findAll().any { it.contains('com/blackbuild/klum/ast/runtime/internal') }
    }

    def "a failed compilation does not replace the last successful carrier"() {
        given:
        def successful = compilationUnit('''
            package outputlifecycle

            import com.blackbuild.klum.ast.DSL

            @DSL class Configuration { String stable }
        ''')
        successful.compile()
        File carrier = new File(compilerConfiguration.targetDirectory, 'outputlifecycle/Configuration_DSL$WirePlan.class')
        byte[] stableCarrier = carrier.bytes
        def failed = compilationUnit('''
            package outputlifecycle

            import com.blackbuild.klum.ast.DSL
            import groovy.transform.CompileStatic

            @CompileStatic
            @DSL class Configuration {
                Integer changed = 'not an Integer'
            }
        ''')

        when:
        failed.compile()

        then:
        thrown(MultipleCompilationErrorsException)
        carrier.bytes == stableCarrier
    }

    def "clean compiler output is deterministic and updates with its Schema"() {
        given:
        File output = tempFolder.newFolder('carrier-output')
        File initial = compileCarrier(output, 'title')
        byte[] initialBytes = initial.bytes

        when: 'the same Schema is compiled cleanly into the same binary-output directory'
        File repeated = compileCarrier(output, 'title')
        byte[] repeatedBytes = repeated.bytes

        and: 'a changed Schema is compiled into that output directory'
        File changed = compileCarrier(output, 'revision')

        then:
        repeatedBytes == initialBytes
        changed.bytes != initialBytes
        readCarrierData(changed).contains('cmV2aXNpb24')
    }

    def "in-memory compilation does not require a carrier output directory"() {
        given:
        File defaultOutput = new File('memoryonly')
        assert !defaultOutput.exists()
        CompilerConfiguration configuration = new CompilerConfiguration()
        def unit = compilationUnit(configuration, '''
            package memoryonly

            import com.blackbuild.klum.ast.DSL

            @DSL class Configuration { String title }
        ''')

        when:
        unit.compile()

        then:
        notThrown(MultipleCompilationErrorsException)
        StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'memoryonly.Configuration' }) != null
        !new File(defaultOutput, 'Configuration_DSL$WirePlan.class').exists()

        cleanup:
        defaultOutput.deleteDir()
    }

    def "Gradle removes a renamed Schema carrier with the obsolete source output"() {
        given:
        Path project = tempFolder.newFolder('gradle-schema').toPath()
        Files.writeString(project.resolve('settings.gradle'), "rootProject.name = 'carrier-lifecycle'\n")
        Files.writeString(project.resolve('build.gradle'), '''
            plugins { id 'groovy' }

            repositories { mavenCentral() }

            dependencies {
                implementation localGroovy()
                implementation files(System.getenv('WIRE_PLAN_CLASSPATH').split(File.pathSeparator))
            }
        '''.stripIndent())
        Path sources = Files.createDirectories(project.resolve('src/main/groovy/outputlifecycle'))
        Path oldSchema = sources.resolve('OldConfiguration.groovy')
        Files.writeString(oldSchema, schemaSource('OldConfiguration', 'title'))

        when:
        def initial = gradle(project, 'compileGroovy')
        Path classes = project.resolve('build/classes/groovy/main/outputlifecycle')
        Path oldCarrier = classes.resolve('OldConfiguration_DSL$WirePlan.class')
        boolean initialCarrier = Files.isRegularFile(oldCarrier)
        Path renamedSchema = sources.resolve('RenamedConfiguration.groovy')
        Files.move(oldSchema, renamedSchema)
        Files.writeString(renamedSchema, schemaSource('RenamedConfiguration', 'revision'))
        def renamed = gradle(project, 'compileGroovy')
        def packaged = gradle(project, 'jar')

        then:
        assert initial.exitCode == 0 : initial.output
        initialCarrier
        assert renamed.exitCode == 0 : renamed.output
        !Files.exists(oldCarrier)
        Files.isRegularFile(classes.resolve('RenamedConfiguration_DSL$WirePlan.class'))
        assert packaged.exitCode == 0 : packaged.output
        new JarFile(project.resolve('build/libs/carrier-lifecycle.jar').toFile()).withCloseable {
            it.getJarEntry('outputlifecycle/RenamedConfiguration_DSL$WirePlan.class') != null
        }
    }

    private static Map runtimeShapedFacts(File carrier) {
        def result = [entries: new LinkedHashSet<>(), rejections: new LinkedHashSet<>()]
        collectRuntimeFacts(carrier, result)
        result
    }

    private static void collectRuntimeFacts(File carrier, Map result) {
        readCarrierData(carrier).readLines().each { line ->
            def fields = line.split('\\|', -1)
            if (fields[0] == 'entry')
                result.entries << [name: decode(fields[1]), role: fields[2], source: decode(fields[3]), payload: fields[10]]
            if (fields[0] == 'rejection')
                result.rejections << [source: decode(fields[1]), reason: decode(fields[2])]
        }
        def parent = parentCarrier(carrier)
        if (parent?.file) collectRuntimeFacts(parent, result)
    }

    private static Map schemaShapedFacts(File carrier) {
        def result = [entries: new LinkedHashSet<>(), rejections: new LinkedHashSet<>()]
        collectSchemaFacts(carrier, result)
        result
    }

    private static void collectSchemaFacts(File carrier, Map result) {
        readCarrierData(carrier).readLines().each { line ->
            if (line.startsWith('entry|')) {
                def fields = line.tokenize('|')
                result.entries << [name: decode(fields[1]), role: fields[2], source: decode(fields[3]), payload: fields[10]]
            }
            if (line.startsWith('rejection|')) {
                def fields = line.tokenize('|')
                result.rejections << [source: decode(fields[1]), reason: decode(fields[2])]
            }
        }
        def parent = parentCarrier(carrier)
        if (parent?.file) collectSchemaFacts(parent, result)
    }

    private static String readCarrierData(File carrier) {
        String[] data = new String[1]
        new ClassReader(carrier.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (name == 'DATA') data[0] = value
                null
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        assert data[0] != null
        data[0]
    }

    private static File parentCarrier(File carrier) {
        String[] parents = new String[1]
        def builder = new File(carrier.parentFile, carrier.name.replace('_DSL$WirePlan.class', '_DSL$Builder.class'))
        new ClassReader(builder.bytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                parents[0] = interfaces.find { it.endsWith('_DSL$Builder') }
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES)
        if (parents[0] != null) {
            return new File(carrier.parentFile.parentFile, parents[0].replace('_DSL$Builder', '_DSL$WirePlan') + '.class')
        }
        null
    }

    private static String decode(String token) {
        token == '-' ? null : new String(Base64.urlDecoder.decode(token), 'UTF-8')
    }

    private static File compileCarrier(File output, String field) {
        def unit = compilationUnit(output, schemaSource('Configuration', field))
        unit.compile()
        new File(output, 'outputlifecycle/Configuration_DSL$WirePlan.class')
    }

    private static CompilationUnit compilationUnit(File output, String schema) {
        CompilerConfiguration configuration = new CompilerConfiguration()
        configuration.targetDirectory = output
        compilationUnit(configuration, schema)
    }

    private static CompilationUnit compilationUnit(CompilerConfiguration configuration, String schema) {
        def compilerLoader = new GroovyClassLoader(StructuredInputAdmissionPlanTest.classLoader, configuration)
        def unit = new CompilationUnit(configuration, null, compilerLoader)
        unit.addSource('Schema.groovy', schema)
        unit
    }

    private static String schemaSource(String name, String field) {
        """
            package outputlifecycle

            import com.blackbuild.klum.ast.DSL

            @DSL class $name { String $field }
        """.stripIndent()
    }

    private static Map gradle(Path project, String task) {
        ProcessBuilder builder = new ProcessBuilder(
                Path.of(System.getProperty('user.dir')).parent.resolve('gradlew').toString(), '--no-daemon', '-p', project.toString(), task)
                .redirectErrorStream(true)
        builder.environment().put('WIRE_PLAN_CLASSPATH', compilerClasspath())
        Process process = builder.start()
        String output = process.inputStream.text
        [exitCode: process.waitFor(), output: output]
    }

    private static String compilerClasspath() {
        System.getProperty('java.class.path').split(Pattern.quote(File.pathSeparator))
                .findAll { entry ->
                    String name = new File(entry).name
                    !name.startsWith('groovy-') && !name.startsWith('spock-')
                }
                .join(File.pathSeparator)
    }

    private CompilationUnit compilationUnit(String schema) {
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('JsonAnnotations.groovy', '''
            package com.fasterxml.jackson.annotation

            @interface JsonIgnore { boolean value() default true }
            @interface JsonProperty { String value() default '' }
        ''')
        unit.addSource('Schema.groovy', schema)
        unit
    }
}
