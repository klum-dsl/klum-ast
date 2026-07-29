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

import spock.lang.Issue
import spock.lang.Specification

import java.io.DataInputStream
import java.lang.module.ModuleFinder
import java.lang.module.ModuleDescriptor
import java.lang.reflect.Modifier
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

import groovyjarjarasm.asm.ClassReader
import groovyjarjarasm.asm.ClassVisitor
import groovyjarjarasm.asm.Handle
import groovyjarjarasm.asm.MethodVisitor
import groovyjarjarasm.asm.Opcodes

@Issue("391")
class JpmsPackageBoundaryTest extends Specification {

    private static final Set<String> LEGACY_PREFIXES = [
            'com.blackbuild.groovy.configdsl',
            'com.blackbuild.klum.ast.ast',
            'com.blackbuild.klum.ast.doc',
            'com.blackbuild.klum.ast.util',
            'com.blackbuild.klum.ast.process',
            'com.blackbuild.klum.ast.validation',
            'com.blackbuild.klum.ast.runtime.internal.reflect',
            'com.blackbuild.klum.common'
    ] as Set

    def "migrated artifacts own distinct final package families"() {
        given:
        Map<String, Set<String>> packagesByArtifact = artifacts().collectEntries { name, jar ->
            [(name): packages(jar)]
        }

        expect:
        packagesByArtifact.annotations.contains('com.blackbuild.klum.ast')
        !packagesByArtifact.runtime.contains('com.blackbuild.klum.ast')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.generated')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.validation')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.internal')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.internal.layer3')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.internal.process')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.internal.validation')
        packagesByArtifact.compiler.containsAll([
                'com.blackbuild.klum.ast.compiler.internal.ast',
                'com.blackbuild.klum.ast.compiler.internal.ast.converters',
                'com.blackbuild.klum.ast.compiler.internal.ast.mutators',
                'com.blackbuild.klum.ast.compiler.internal.common',
                'com.blackbuild.klum.ast.compiler.internal.doc',
                'com.blackbuild.klum.ast.compiler.internal.layer3',
                'com.blackbuild.klum.ast.compiler.internal.reflect',
                'com.blackbuild.klum.ast.compiler.internal.validation'
        ])
        packagesByArtifact.compiler.every { packageName ->
            packageName.startsWith('com.blackbuild.klum.ast.compiler.internal')
        }
        packagesByArtifact.every { artifact, packageNames ->
            packageNames.every { packageName ->
                LEGACY_PREFIXES.every { prefix ->
                    !packageName.startsWith(prefix) ||
                            artifact == 'beanValidation' && packageName.startsWith('com.blackbuild.klum.ast.validation.bean')
                }
            }
        }
        packageOwners(packagesByArtifact).every { packageName, owners -> owners.size() == 1 }
    }

    def "runtime service resources use final public service names"() {
        expect:
        resourceNames(runtimeJar()) == [
                'META-INF/services/com.blackbuild.klum.ast.runtime.PhaseAction',
                'META-INF/services/com.blackbuild.klum.ast.runtime.validation.InstanceValidator'
        ] as Set
        resourceNames(beanValidationJar()) == [
                'META-INF/services/com.blackbuild.klum.ast.runtime.validation.InstanceValidator'
        ] as Set
    }

    def "artifacts publish the approved explicit module contracts"() {
        given:
        def descriptors = artifacts().collectEntries { name, jar ->
            [(name): ModuleFinder.of(jar).findAll().first().descriptor()]
        }

        expect:
        descriptors.collectEntries { name, descriptor -> [(name): descriptor.name()] } == [
                annotations   : 'com.blackbuild.klum.ast.annotations',
                runtime       : 'com.blackbuild.klum.ast.runtime',
                compiler      : 'com.blackbuild.klum.ast.compiler',
                jackson       : 'com.blackbuild.klum.ast.jackson',
                beanValidation: 'com.blackbuild.klum.ast.validation.bean'
        ]
        exportedPackages(descriptors.annotations) == [
                'com.blackbuild.klum.ast',
                'com.blackbuild.klum.ast.copy',
                'com.blackbuild.klum.ast.layer3'
        ] as Set
        exportedPackages(descriptors.runtime) == [
                'com.blackbuild.klum.ast.runtime',
                'com.blackbuild.klum.ast.runtime.generated',
                'com.blackbuild.klum.ast.runtime.validation'
        ] as Set
        exportedPackages(descriptors.compiler).empty
        exportedPackages(descriptors.jackson) == ['com.blackbuild.klum.ast.jackson'] as Set
        exportedPackages(descriptors.beanValidation) == ['com.blackbuild.klum.ast.validation.bean'] as Set
        descriptors.beanValidation.requires()*.name().contains('com.fasterxml.classmate')
        !descriptors.beanValidation.requires().find { it.name() == 'com.fasterxml.classmate' }
                .modifiers().contains(ModuleDescriptor.Requires.Modifier.TRANSITIVE)
        qualifiedOpenTargets(descriptors.compiler) == [
                'com.blackbuild.klum.ast.compiler.internal.ast'           : [
                        'org.apache.groovy',
                        'com.blackbuild.klum.cast.compiler'
                ] as Set,
                'com.blackbuild.klum.ast.compiler.internal.ast.converters': ['org.apache.groovy'] as Set,
                'com.blackbuild.klum.ast.compiler.internal.ast.mutators'  : [
                        'org.apache.groovy',
                        'com.blackbuild.klum.cast.compiler'
                ] as Set,
                'com.blackbuild.klum.ast.compiler.internal.layer3'        : [
                        'org.apache.groovy',
                        'com.blackbuild.klum.cast.compiler'
                ] as Set,
                'com.blackbuild.klum.ast.compiler.internal.validation'    : ['com.blackbuild.klum.cast.compiler'] as Set
        ]
        !descriptors.compiler.provides().any { it.service() == 'org.codehaus.groovy.transform.ASTTransformation' }
        descriptors.runtime.uses().containsAll([
                'com.blackbuild.klum.ast.runtime.PhaseAction',
                'com.blackbuild.klum.ast.runtime.validation.InstanceValidator'
        ])
        descriptors.beanValidation.provides().any {
            it.service() == 'com.blackbuild.klum.ast.runtime.validation.InstanceValidator' &&
                    it.providers() == ['com.blackbuild.klum.ast.validation.bean.internal.JSR380Validator']
        }
        descriptors.jackson.provides().any {
            it.service() == 'com.fasterxml.jackson.databind.Module' &&
                    it.providers() == ['com.blackbuild.klum.ast.jackson.KlumAstModule']
        }
    }

    def "Groovy 4 and 5 activate local transformations from the named compiler module"() {
        given:
        boolean namedGroovy = GroovySystem.version.startsWith('4.') || GroovySystem.version.startsWith('5.')

        when:
        ProcessResult classpathResult = compileClasspathSchema()
        ProcessResult namedModuleResult = namedGroovy ? compileNamedSchema() : null

        then:
        assert classpathResult.exitCode == 0 : classpathResult.output
        Files.exists(classpathResult.outputDirectory.resolve('NamedRoot.class'))

        and: 'Groovy 3 remains classpath-only while Groovy 4 and 5 also prove the named-module path.'
        !namedGroovy || namedModuleResult.exitCode == 0
        !namedGroovy || Files.exists(namedModuleResult.outputDirectory.resolve('NamedRoot.class'))

        and: 'the class-file owner and descriptor scanner finds no runtime-internal reference in any generated named-schema class'
        if (namedGroovy) {
            assertNoRuntimeInternalReferences(namedModuleResult.outputDirectory)
            assertGeneratedHelperContract(namedModuleResult.outputDirectory, 'NamedRoot', '$_Template', true)
            assertGeneratedHelperContract(namedModuleResult.outputDirectory, 'NamedRoot', '$_Factory', false)
        }
    }

    private static void assertNoRuntimeInternalReferences(Path classes) {
        assert !Files.walk(classes).withCloseable { paths ->
            paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith('.class') }
                    .any(this::hasRuntimeInternalReference)
        }
    }

    private static void assertGeneratedHelperContract(Path classes, String modelName, String suffix, boolean synthetic) {
        new URLClassLoader([classes.toUri().toURL()] as URL[], JpmsPackageBoundaryTest.classLoader).withCloseable { loader ->
            Class<?> helper = Class.forName(modelName + suffix, false, loader)
            assert Modifier.isPublic(helper.modifiers)
            assert Modifier.isStatic(helper.modifiers)
            assert Modifier.isFinal(helper.modifiers)
            assert helper.synthetic == synthetic
            assert Modifier.isPublic(helper.getDeclaredConstructor().modifiers)
        }
    }

    private static void assertProtectedLifecycleContract(Path classes) {
        new URLClassLoader([classes.toUri().toURL()] as URL[], JpmsPackageBoundaryTest.classLoader).withCloseable { loader ->
            Class<?> builder = Class.forName('fixture.schema.Station$Builder', false, loader)
            ['recordCreate', 'recordTree'].each { methodName ->
                assert Modifier.isProtected(builder.getDeclaredMethod(methodName).modifiers)
            }
        }
    }

    private static void assertGeneratedSetterDirectlyInvokesRuntimeHook(Path classes) {
        List<String> unresolvedSetters = []
        List<String> directlyLinkedSetters = []
        Path builderClass = classes.resolve('fixture/schema/Station$Builder.class')

        Files.newInputStream(builderClass).withCloseable { input ->
            new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM7) {
                @Override
                MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    new MethodVisitor(Opcodes.ASM7) {
                        @Override
                        void visitInvokeDynamicInsn(String methodName, String methodDescriptor, Handle bootstrapMethodHandle,
                                                    Object... bootstrapMethodArguments) {
                            if (bootstrapMethodArguments.contains('$setSingleField'))
                                unresolvedSetters << "$name$descriptor"
                        }

                        @Override
                        void visitMethodInsn(int opcode, String owner, String methodName, String methodDescriptor,
                                             boolean isInterface) {
                            if (owner == 'com/blackbuild/klum/ast/runtime/generated/GeneratedKlumBuilder' &&
                                    methodName == '$setSingleField')
                                directlyLinkedSetters << "$name$descriptor"
                        }
                    }
                }
            }, 0)
        }

        assert unresolvedSetters.empty: "Generated setters must not use invokedynamic for \$setSingleField: $unresolvedSetters"
        assert directlyLinkedSetters.any { it.toString() == 'name(Ljava/lang/String;)Ljava/lang/String;' }:
                "Generated setter must directly link \$setSingleField: $directlyLinkedSetters"
    }

    private static boolean hasRuntimeInternalReference(Path classFile) {
        classFileConstants(classFile).any { constant ->
            constant.contains('com/blackbuild/klum/ast/runtime/internal')
        }
    }

    private static Set<String> classFileConstants(Path classFile) {
        Files.newInputStream(classFile).withCloseable { input ->
            DataInputStream data = new DataInputStream(input)
            assert data.readInt() == (int) 0xCAFEBABE
            data.readUnsignedShort()
            data.readUnsignedShort()
            int constantPoolCount = data.readUnsignedShort()
            Set<String> constants = new LinkedHashSet<>()
            for (int index = 1; index < constantPoolCount; index++) {
                switch (data.readUnsignedByte()) {
                    case 1:
                        constants << data.readUTF()
                        break
                    case 3:
                    case 4:
                        data.readInt()
                        break
                    case 5:
                    case 6:
                        data.readLong()
                        index++
                        break
                    case 7:
                    case 8:
                    case 16:
                    case 19:
                    case 20:
                        data.readUnsignedShort()
                        break
                    case 9:
                    case 10:
                    case 11:
                    case 12:
                    case 17:
                    case 18:
                        data.readUnsignedShort()
                        data.readUnsignedShort()
                        break
                    case 15:
                        data.readUnsignedByte()
                        data.readUnsignedShort()
                        break
                    default:
                        assert false: "Unexpected class-file constant tag"
                }
            }
            constants
        }
    }

    @Issue("620")
    def "a real schema and consumer prove the classpath and named-module contracts"() {
        given:
        boolean namedGroovy = GroovySystem.version.startsWith('4.') || GroovySystem.version.startsWith('5.')

        when:
        ProcessResult classpathResult = executeRealSchemaFixture(false)
        ProcessResult namedModuleResult = namedGroovy ? executeRealSchemaFixture(true) : null

        then: 'all supported Groovy generations retain the ordinary classpath consumer contract'
        assert classpathResult.exitCode == 0 : classpathResult.output
        assertFixtureOutput(classpathResult)

        and: 'only Groovy 4 and 5 prove the user-owned schema on the module path'
        !namedGroovy || namedModuleResult.exitCode == 0
        if (namedGroovy)
            assertFixtureOutput(namedModuleResult)
    }

    private static Map<String, Set<String>> packageOwners(Map<String, Set<String>> packagesByArtifact) {
        Map<String, Set<String>> owners = new LinkedHashMap<>()
        packagesByArtifact.each { artifact, packageNames ->
            packageNames.each { packageName ->
                owners.computeIfAbsent(packageName) { new LinkedHashSet<String>() }.add(artifact)
            }
        }
        owners
    }

    private static Set<String> packages(Path jar) {
        ModuleFinder.of(jar).findAll().first().descriptor().packages()
    }

    private static Set<String> exportedPackages(def descriptor) {
        descriptor.exports().findAll { !it.isQualified() }.collect { it.source() } as Set
    }

    private static Map<String, Set<String>> qualifiedOpenTargets(def descriptor) {
        descriptor.opens().collectEntries { opened ->
            [(opened.source()): opened.targets()]
        }
    }

    private static Set<String> resourceNames(Path jar) {
        new JarFile(jar.toFile()).withCloseable { archive ->
            archive.entries().findAll { entry ->
                entry.name.startsWith('META-INF/services/') && !entry.directory
            }.collect { it.name } as Set
        }
    }

    private static void assertFixtureOutput(ProcessResult result) {
        assert result.output.readLines().contains(
                'java=true;station=Java;events=create,tree,validate;phase=true;validator=true;json=true') : result.output
        assert result.output.readLines().last() == 'static=true' : result.output
    }

    private static Map<String, Path> artifacts() {
        [
                annotations   : jarPath('klumAnnotationsJar'),
                runtime       : runtimeJar(),
                compiler      : jarPath('klumCompilerJar'),
                jackson       : jarPath('klumJacksonJar'),
                beanValidation: beanValidationJar()
        ]
    }

    private static Path runtimeJar() {
        jarPath('klumRuntimeJar')
    }

    private static Path beanValidationJar() {
        jarPath('klumBeanValidationJar')
    }

    private static Path jarPath(String property) {
        Path.of(System.getProperty(property))
    }

    private static ProcessResult compileNamedSchema() {
        List<String> command = [
                '--module-path', modulePathEntries().join(File.pathSeparator),
                '--add-modules', 'ALL-MODULE-PATH',
                '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler',
                '--classpath', modulePathEntries().join(File.pathSeparator)
        ]
        assertNamedModuleCommand(command)
        compileSchema(command)
    }

    private static ProcessResult compileClasspathSchema() {
        compileSchema([
                '--class-path', modulePathEntries().join(File.pathSeparator),
                'org.codehaus.groovy.tools.FileSystemCompiler'
        ])
    }

    private static ProcessResult executeRealSchemaFixture(boolean named) {
        Path fixture = Files.createTempDirectory(named ? 'klum-jpms-named-schema' : 'klum-classpath-schema')
        Path schemaSources = fixture.resolve('schema-sources')
        Path schemaClasses = fixture.resolve('schema-classes')
        Path consumerSources = fixture.resolve('consumer-sources')
        Path consumerClasses = fixture.resolve('consumer-classes')
        Files.createDirectories(schemaSources.resolve('fixture/schema'))
        Files.createDirectories(schemaClasses)
        Files.createDirectories(consumerSources.resolve('fixture/consumer'))
        Files.createDirectories(consumerClasses)
        Files.writeString(schemaSources.resolve('fixture/schema/Station.groovy'), realSchemaSource())
        Files.writeString(consumerSources.resolve('fixture/consumer/StaticConsumer.groovy'), staticGroovyConsumerSource())

        ProcessResult schemaCompilation = compileGroovySchema(schemaSources, schemaClasses, named)
        if (schemaCompilation.exitCode != 0)
            return schemaCompilation

        if (named)
            assertProtectedLifecycleContract(schemaClasses)
        if (named)
            assertGeneratedSetterDirectlyInvokesRuntimeHook(schemaClasses)

        Path schemaArtifact = named ? compileAndPackageNamedSchema(schemaSources, schemaClasses) : schemaClasses

        ProcessResult staticConsumerCompilation = compileStaticGroovyConsumer(
                schemaArtifact, consumerSources, consumerClasses, named)
        if (staticConsumerCompilation.exitCode != 0)
            return staticConsumerCompilation

        Files.writeString(consumerSources.resolve('fixture/consumer/Main.java'), realConsumerSource())
        if (named)
            Files.writeString(consumerSources.resolve('module-info.java'), namedConsumerDescriptor())

        ProcessResult consumerCompilation = compileConsumer(schemaArtifact, consumerSources, consumerClasses, named)
        if (consumerCompilation.exitCode != 0)
            return consumerCompilation
        runConsumer(schemaArtifact, consumerClasses, named)
    }

    private static ProcessResult compileGroovySchema(Path sources, Path output, boolean named) {
        List<String> compilerCommand = named ? [
                '--module-path', modulePathEntries().join(File.pathSeparator),
                '--add-modules', 'ALL-MODULE-PATH',
                '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler',
                '--classpath', modulePathEntries().join(File.pathSeparator)
        ] : [
                '--class-path', modulePathEntries().join(File.pathSeparator),
                'org.codehaus.groovy.tools.FileSystemCompiler'
        ]
        if (named)
            assertNamedModuleCommand(compilerCommand)
        List<String> command = [javaExecutable()]
        command.addAll(compilerCommand)
        command.addAll([
                '-d', output.toString(),
                sources.resolve('fixture/schema/Station.groovy').toString()
        ])
        execute(command, output)
    }

    private static Path compileAndPackageNamedSchema(Path sources, Path classes) {
        Files.writeString(sources.resolve('module-info.java'), namedSchemaDescriptor())
        List<String> descriptorArguments = [
                '--module-path', modulePathEntries().join(File.pathSeparator),
                '-d', classes.toString(),
                sources.resolve('module-info.java').toString()
        ]
        assertNoPortabilityWorkarounds(descriptorArguments)
        ProcessResult descriptorCompilation = compileJava(descriptorArguments, classes)
        assert descriptorCompilation.exitCode == 0 : descriptorCompilation.output

        Path archive = classes.parent.resolve('fixture.schema.jar')
        ProcessResult packaging = execute([
                jarExecutable(), '--create', '--file', archive.toString(), '-C', classes.toString(), '.'
        ], classes)
        assert packaging.exitCode == 0 : packaging.output
        assertNamedSchemaDescriptor(archive)
        assertNoRuntimeInternalReferences(classes)
        archive
    }

    private static ProcessResult compileStaticGroovyConsumer(Path schemaArtifact, Path sources, Path output, boolean named) {
        List<String> compilerCommand = named ? [
                '--module-path', modulePath(schemaArtifact),
                '--add-modules', 'ALL-MODULE-PATH',
                '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler',
                '--classpath', modulePath(schemaArtifact)
        ] : [
                '--class-path', classpath(schemaArtifact),
                'org.codehaus.groovy.tools.FileSystemCompiler'
        ]
        if (named)
            assertNamedModuleCommand(compilerCommand)
        List<String> command = [javaExecutable()]
        command.addAll(compilerCommand)
        command.addAll([
                '-d', output.toString(),
                sources.resolve('fixture/consumer/StaticConsumer.groovy').toString()
        ])
        execute(command, output)
    }

    private static ProcessResult compileConsumer(Path schemaArtifact, Path sources, Path output, boolean named) {
        List<String> arguments = named ? [
                '--module-path', modulePath(schemaArtifact),
                '-d', output.toString(),
                sources.resolve('module-info.java').toString(),
                sources.resolve('fixture/consumer/Main.java').toString()
        ] : [
                '--class-path', classpath(schemaArtifact),
                '-d', output.toString(),
                sources.resolve('fixture/consumer/Main.java').toString()
        ]
        if (named)
            assertNoPortabilityWorkarounds(arguments)
        compileJava(arguments, output)
    }

    private static ProcessResult runConsumer(Path schemaArtifact, Path consumerClasses, boolean named) {
        List<String> javaCommand = named ? [
                javaExecutable(),
                '--module-path', modulePath(schemaArtifact, consumerClasses),
                '-m', 'fixture.consumer/fixture.consumer.Main'
        ] : [
                javaExecutable(),
                '--class-path', [consumerClasses, schemaArtifact, *modulePathEntries()].join(File.pathSeparator),
                'fixture.consumer.Main'
        ]
        if (named)
            assertNoPortabilityWorkarounds(javaCommand)
        ProcessResult javaConsumer = execute(javaCommand, consumerClasses)
        if (javaConsumer.exitCode != 0)
            return javaConsumer

        List<String> staticGroovyCommand = named ? [
                javaExecutable(),
                '--module-path', modulePath(schemaArtifact, consumerClasses),
                '-m', 'fixture.consumer/fixture.consumer.StaticConsumer'
        ] : [
                javaExecutable(),
                '--class-path', [consumerClasses, schemaArtifact, *modulePathEntries()].join(File.pathSeparator),
                'fixture.consumer.StaticConsumer'
        ]
        if (named)
            assertNoPortabilityWorkarounds(staticGroovyCommand)
        ProcessResult staticConsumer = execute(staticGroovyCommand, consumerClasses)
        new ProcessResult(staticConsumer.exitCode, javaConsumer.output + staticConsumer.output, consumerClasses)
    }

    private static ProcessResult compileJava(List<String> arguments, Path output) {
        List<String> command = [javacExecutable()]
        command.addAll(arguments)
        execute(command, output)
    }

    private static ProcessResult execute(List<String> command, Path output) {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().remove('CLASSPATH')
        Process process = builder.start()
        new ProcessResult(process.waitFor(), process.inputStream.text, output)
    }

    private static String modulePath(Path... additionalEntries) {
        [*additionalEntries, *modulePathEntries()].join(File.pathSeparator)
    }

    private static String classpath(Path schemaArtifact) {
        [schemaArtifact, *modulePathEntries()].join(File.pathSeparator)
    }

    private static String realSchemaSource() {
        '''
            package fixture.schema

            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.PostCreate
            import com.blackbuild.klum.ast.PostTree
            import com.blackbuild.klum.ast.Validate
            import com.blackbuild.klum.ast.runtime.KlumBuilder
            import jakarta.validation.constraints.Min

            import java.util.List

            @DSL
            class Station {
                static final List<String> EVENTS = []

                String name = 'North'

                @Min(1L)
                int capacity = 4

                @PostCreate
                void recordCreate() {
                    assert this instanceof KlumBuilder
                    Station.EVENTS << 'create'
                }

                @PostTree
                void recordTree() {
                    assert this instanceof KlumBuilder
                    Station.EVENTS << 'tree'
                }

                @Validate
                void recordValidation() {
                    assert !(this instanceof KlumBuilder)
                    Station.EVENTS << 'validate'
                }

                static List<String> eventLog() {
                    EVENTS
                }
            }

            @DSL
            class Deployment {
                Endpoint endpoint
            }

            @DSL
            abstract class Endpoint {
            }

            @DSL
            class HttpEndpoint extends Endpoint {
                String url
            }
        '''.stripIndent()
    }

    private static String namedSchemaDescriptor() {
        '''
            module fixture.schema {
                requires com.blackbuild.klum.ast.annotations;
                requires com.blackbuild.klum.ast.runtime;
                requires static com.blackbuild.klum.ast.compiler;
                requires com.blackbuild.klum.ast.jackson;
                requires com.blackbuild.klum.ast.validation.bean;
                requires org.apache.groovy;

                exports fixture.schema;
                opens fixture.schema to com.blackbuild.klum.ast.runtime, com.fasterxml.jackson.databind, org.hibernate.validator;
            }
        '''.stripIndent()
    }

    private static String namedConsumerDescriptor() {
        '''
            module fixture.consumer {
                requires fixture.schema;
                requires com.blackbuild.klum.ast.runtime;
                requires com.blackbuild.klum.ast.jackson;
                requires com.blackbuild.klum.ast.validation.bean;
                requires com.fasterxml.jackson.databind;
                requires org.apache.groovy;

                uses com.blackbuild.klum.ast.runtime.PhaseAction;
                uses com.blackbuild.klum.ast.runtime.validation.InstanceValidator;
            }
        '''.stripIndent()
    }

    private static String realConsumerSource() {
        '''
            package fixture.consumer;

            import com.blackbuild.klum.ast.runtime.PhaseAction;
            import com.blackbuild.klum.ast.runtime.KlumFactory.BuilderFactoryProvider;
            import com.blackbuild.klum.ast.runtime.validation.InstanceValidator;
            import com.fasterxml.jackson.databind.ObjectMapper;
            import fixture.schema.HttpEndpoint;
            import fixture.schema.HttpEndpoint_DSL;
            import fixture.schema.Station;
            import fixture.schema.Station_DSL;

            import java.util.List;
            import java.util.Map;
            import java.util.ServiceLoader;

            public class Main {
                public static void main(String[] arguments) throws Exception {
                    Station_DSL.Factory factory = Station.Create;
                    BuilderFactoryProvider<HttpEndpoint, HttpEndpoint_DSL.Builder<HttpEndpoint>> endpointFactory =
                            HttpEndpoint.Create;
                    if (endpointFactory.getAsBuilder().getModelType() != HttpEndpoint.class)
                        throw new AssertionError("Generated typed relationship provider did not link through JPMS");
                    Station station = factory.With(Map.of("name", "Java"));
                    if (!"Java".equals(station.getName()))
                        throw new AssertionError("Generated factory did not apply the Java map value");
                    if (station.getCapacity() != 4)
                        throw new AssertionError("Bean Validation schema value was not materialized");
                    if (!Station.eventLog().equals(List.of("create", "tree", "validate")))
                        throw new AssertionError("Builder lifecycle and model validation did not run in order");
                    boolean phaseActionLoaded = ServiceLoader.load(PhaseAction.class).stream()
                            .anyMatch(provider -> provider.type().getName()
                                    .equals("com.blackbuild.klum.ast.runtime.internal.validation.ValidationPhase"));
                    if (!phaseActionLoaded)
                        throw new AssertionError("Runtime phase action was not discovered");
                    boolean beanValidatorLoaded = ServiceLoader.load(InstanceValidator.class).stream()
                            .anyMatch(provider -> provider.type().getName()
                                    .equals("com.blackbuild.klum.ast.validation.bean.internal.JSR380Validator"));
                    if (!beanValidatorLoaded)
                        throw new AssertionError("Bean Validation provider was not discovered");
                    String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(station);
                    if (!json.contains("\\\"name\\\":\\\"Java\\\"") || !json.contains("\\\"capacity\\\":4"))
                        throw new AssertionError("Jackson did not export the opened schema package: " + json);
                    System.out.println("java=true;station=Java;events=create,tree,validate;phase=true;validator=true;json=true");
                }
            }
        '''.stripIndent()
    }

    private static String staticGroovyConsumerSource() {
        '''
            package fixture.consumer

            import fixture.schema.Deployment
            import fixture.schema.HttpEndpoint
            import fixture.schema.HttpEndpoint_DSL
            import fixture.schema.Station
            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticConsumer {
                static Deployment createTypedEndpoint() {
                    Deployment.Create.With {
                        HttpEndpoint_DSL.Builder<HttpEndpoint> selected = endpoint(HttpEndpoint.Create) {
                            url 'https://example.test/health'
                        }
                        assert selected != null
                    }
                }

                static void main(String[] arguments) {
                    Station station = Station.Create.One()
                    assert station.name == 'North'
                    assert station.capacity == 4
                    println 'static=true'
                }
            }
        '''.stripIndent()
    }

    private static void assertNamedSchemaDescriptor(Path archive) {
        def descriptor = ModuleFinder.of(archive).findAll().first().descriptor()
        assert descriptor.name() == 'fixture.schema'
        assert descriptor.requires()*.name().containsAll([
                'com.blackbuild.klum.ast.annotations',
                'com.blackbuild.klum.ast.runtime',
                'com.blackbuild.klum.ast.compiler',
                'com.blackbuild.klum.ast.jackson',
                'com.blackbuild.klum.ast.validation.bean',
                'org.apache.groovy'
        ])
        assert exportedPackages(descriptor) == ['fixture.schema'] as Set
        assert qualifiedOpenTargets(descriptor) == [
                'fixture.schema': [
                        'com.blackbuild.klum.ast.runtime',
                        'com.fasterxml.jackson.databind',
                        'org.hibernate.validator'
                ] as Set
        ]
    }

    private static ProcessResult compileSchema(List<String> compilerCommand) {
        Path fixture = Files.createTempDirectory('klum-jpms-schema')
        Path source = fixture.resolve('NamedSchema.groovy')
        Path output = fixture.resolve('classes')
        Files.createDirectories(output)
        Files.writeString(source, '''
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Required
            import com.blackbuild.klum.ast.layer3.Cluster

            @DSL class NamedRoot {
                @Required String name
                @Cluster Map<String, NamedChild> children
                NamedChild child
            }

            @DSL class NamedChild {
                static NamedChild fromString(String value) {
                    return materialize(value)
                }

                private static NamedChild materialize(String value) {
                    return NamedChild.Create.With()
                }
            }
        '''.stripIndent())

        List<String> command = [Path.of(System.getProperty('java.home'), 'bin', 'java').toString()]
        command.addAll(compilerCommand)
        command.addAll([
                '-d', output.toString(),
                source.toString()
        ])
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true)
        builder.environment().remove('CLASSPATH')
        Process process = builder.start()
        new ProcessResult(process.waitFor(), process.inputStream.text, output)
    }

    private static void assertNamedModuleCommand(List<String> command) {
        assert command.containsAll([
                '--module-path',
                '--add-modules', 'ALL-MODULE-PATH',
                '-m', 'org.apache.groovy/org.codehaus.groovy.tools.FileSystemCompiler'
        ])
        assertNoPortabilityWorkarounds(command)
    }

    private static void assertNoPortabilityWorkarounds(Collection<String> command) {
        assert !command.any { option ->
            ['--add-reads', '--add-exports', '--add-opens', '--patch-module'].any { forbidden ->
                option == forbidden || option.startsWith("${forbidden}=")
            }
        }
    }

    private static String javaExecutable() {
        Path.of(System.getProperty('java.home'), 'bin', 'java').toString()
    }

    private static String javacExecutable() {
        Path.of(System.getProperty('java.home'), 'bin', 'javac').toString()
    }

    private static String jarExecutable() {
        Path.of(System.getProperty('java.home'), 'bin', 'jar').toString()
    }

    private static List<String> modulePathEntries() {
        List<String> paths = artifacts().values()*.toString()
        paths.addAll((System.getProperty('java.class.path') + File.pathSeparator +
                System.getProperty('jpmsAdapterModulePath')).split(File.pathSeparator).findAll { entry ->
            String name = Path.of(entry).fileName.toString()
            [
                    'groovy-3.', 'groovy-4.', 'groovy-5.',
                    'anno-docimal-annotations-', 'anno-docimal-ast-', 'anno-docimal-global-ast-',
                    'klum-cast-annotations-', 'klum-cast-compile-', 'klum-cast-spi-', 'jspecify-',
                    'jackson-', 'jakarta.validation-api-', 'hibernate-validator-', 'jboss-logging-', 'classmate-'
            ].any { marker -> name.startsWith(marker) }
        })
        paths.unique()
    }

    private static class ProcessResult {
        int exitCode
        String output
        Path outputDirectory

        ProcessResult(int exitCode, String output, Path outputDirectory) {
            this.exitCode = exitCode
            this.output = output
            this.outputDirectory = outputDirectory
        }
    }
}
