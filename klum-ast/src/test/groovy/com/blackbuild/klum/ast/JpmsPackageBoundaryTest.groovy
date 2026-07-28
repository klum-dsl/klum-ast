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

import java.lang.module.ModuleFinder
import java.nio.file.Path
import java.nio.file.Files
import java.util.jar.JarFile

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
                'com.blackbuild.klum.ast.runtime.validation'
        ] as Set
        exportedPackages(descriptors.compiler).empty
        exportedPackages(descriptors.jackson) == ['com.blackbuild.klum.ast.jackson'] as Set
        exportedPackages(descriptors.beanValidation) == ['com.blackbuild.klum.ast.validation.bean'] as Set
        qualifiedOpenTargets(descriptors.compiler) == [
                'com.blackbuild.klum.ast.compiler.internal.ast'           : ['org.apache.groovy'] as Set,
                'com.blackbuild.klum.ast.compiler.internal.ast.converters': ['org.apache.groovy'] as Set,
                'com.blackbuild.klum.ast.compiler.internal.ast.mutators'  : ['org.apache.groovy'] as Set,
                'com.blackbuild.klum.ast.compiler.internal.layer3'        : ['org.apache.groovy'] as Set,
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
            }

            @DSL class NamedChild {}
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
        assert !command.any { option ->
            ['--add-reads', '--add-exports', '--patch-module'].any { forbidden ->
                option == forbidden || option.startsWith("${forbidden}=")
            }
        }
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
