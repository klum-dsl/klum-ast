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
package com.blackbuild.klum.ast.compile

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import javax.tools.ToolProvider
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Issue('895')
class JpmsDescriptorInputsTest extends Specification {
    @TempDir File fixture

    def setup() {
        write('settings.gradle', '''
            rootProject.name = 'descriptor-inputs'
            dependencyResolutionManagement {
                versionCatalogs {
                    libs {
                        library('groovy-v4', 'fixture', 'unused').version('1')
                        library('groovy-v4-bom', 'fixture', 'unused-bom').version('1')
                    }
                }
            }
        ''')
        write('build.gradle', '''
            plugins {
                id 'java-library'
                id 'klum-ast.jpms-descriptor-conventions' apply false
            }
            java { withSourcesJar() }
            apply plugin: 'klum-ast.jpms-descriptor-conventions'
            // Drive the real convention with hermetic Java modules rather than external Groovy coordinates.
            configurations.jpmsModulePath.dependencies.clear()
            dependencies {
                jpmsModulePath files(providers.gradleProperty('moduleJar').orElse('modules-a/dependency.jar'))
            }
            tasks.named('compileModuleInfo') {
                options.debug = !providers.gradleProperty('withoutDebug').isPresent()
            }
        ''')
        write('src/main/java/example/Sample.java', 'package example; public class Sample {}')
        write('src/main/module-info/example/ModuleMarker.java', 'package example; final class ModuleMarker {}')
        write('src/main/module-info/module-info.java', 'module fixture.owner { requires fixture.dependency; exports example; }')
        dependencyJar()
    }

    def 'reuses byte-identical relocated modules and repairs a missing descriptor without changing artifact bytes'() {
        when:
        BuildResult first = run()
        byte[] descriptor = file('build/classes/java/jpms/module-info.class').bytes
        File relocated = file('modules-b/dependency.jar')
        relocated.parentFile.mkdirs()
        Files.copy(file('modules-a/dependency.jar').toPath(), relocated.toPath())
        BuildResult repeat = run()
        BuildResult moved = run('-PmoduleJar=modules-b/dependency.jar')

        then:
        first.task(':compileModuleInfo').outcome == SUCCESS
        repeat.task(':compileModuleInfo').outcome == UP_TO_DATE
        moved.task(':compileModuleInfo').outcome == UP_TO_DATE
        file('build/classes/java/jpms/module-info.class').bytes == descriptor
        jarEntry('module-info.class') == descriptor

        when:
        assert file('build/classes/java/jpms/module-info.class').delete()
        BuildResult repaired = run('-PmoduleJar=modules-b/dependency.jar')

        then:
        repaired.task(':compileModuleInfo').outcome == SUCCESS
        file('build/classes/java/jpms/module-info.class').bytes == descriptor
        jarEntry('module-info.class') == descriptor
    }

    def 'detects same-path module descriptor changes even when dependency classes are byte-identical'() {
        given:
        run()
        byte[] originalClass = dependencyEntry('dep/Api.class')

        when:
        dependencyJar('fixture.other')
        BuildResult rejected = runner().buildAndFail()

        then:
        dependencyEntry('dep/Api.class') == originalClass
        rejected.output.contains('module not found: fixture.dependency')

        when:
        dependencyJar()
        BuildResult repaired = run()

        then:
        repaired.task(':compileModuleInfo').outcome == SUCCESS
    }

    def 'tracks dependency class and resource contents'() {
        given:
        run()

        when:
        dependencyJar('fixture.dependency', 'public int value() { return 1; }')
        BuildResult classesChanged = run()
        dependencyJar('fixture.dependency', 'public int value() { return 1; }', 'changed-resource')
        BuildResult resourceChanged = run()

        then:
        classesChanged.task(':compileModuleInfo').outcome == SUCCESS
        resourceChanged.task(':compileModuleInfo').outcome == SUCCESS
        jarEntry('module-info.class') == file('build/classes/java/jpms/module-info.class').bytes
    }

    def 'invalidates automatic module identity when only the JAR filename changes'() {
        given:
        File automatic = file('modules-a/fixture.dependency.jar')
        assert file('dependency-classes/module-info.class').delete()
        writeJar(file('dependency-classes'), automatic)
        run('-PmoduleJar=modules-a/fixture.dependency.jar')

        when:
        Files.copy(automatic.toPath(), file('modules-a/renamed.jar').toPath())
        BuildResult rejected = runner('-PmoduleJar=modules-a/renamed.jar').buildAndFail()

        then:
        file('modules-a/renamed.jar').bytes == automatic.bytes
        rejected.output.contains('module not found: fixture.dependency')
    }

    def 'retains compiler source and owning artifact resource invalidation'() {
        given:
        run()
        byte[] original = jarEntry('module-info.class')

        when:
        BuildResult optionsChanged = run('-PwithoutDebug')
        write('src/main/module-info/module-info.java', 'module fixture.owner { requires fixture.dependency; requires static java.logging; exports example; }')
        BuildResult descriptorChanged = run('-PwithoutDebug')
        byte[] changed = jarEntry('module-info.class')
        write('src/main/java/example/Sample.java', 'package example; public class Sample { public int value; }')
        BuildResult sourceChanged = run('-PwithoutDebug')
        write('src/main/resources/proof.txt', 'resource-change')
        BuildResult resourceChanged = run('-PwithoutDebug')

        then:
        optionsChanged.task(':compileModuleInfo').outcome == SUCCESS
        descriptorChanged.task(':compileModuleInfo').outcome == SUCCESS
        changed != original
        sourceChanged.task(':compileJava').outcome == SUCCESS
        sourceChanged.task(':compileModuleInfo').outcome == SUCCESS
        resourceChanged.task(':processResources').outcome == SUCCESS
        resourceChanged.task(':jar').outcome == SUCCESS
        // Own resources do not affect descriptor compilation; they remain owning JAR inputs.
        resourceChanged.task(':compileModuleInfo').outcome == UP_TO_DATE
        new String(jarEntry('proof.txt'), 'UTF-8') == 'resource-change'
    }

    private BuildResult run(String... arguments) {
        runner(arguments).build()
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create()
                .withProjectDir(fixture)
                .withPluginClasspath()
                .withArguments(['jar', '--stacktrace', '--no-build-cache', '--no-configuration-cache', '--max-workers=2'] + arguments.toList())
    }

    private void dependencyJar(String module = 'fixture.dependency', String members = '', String resource = 'original') {
        File source = write('dependency-source/module-info.java', "module $module { exports dep; }")
        File api = write('dependency-source/dep/Api.java', "package dep; public class Api { $members }")
        File classes = file('dependency-classes')
        classes.mkdirs()
        assert ToolProvider.systemJavaCompiler.run(null, null, null, '-d', classes.absolutePath,
                source.absolutePath, api.absolutePath) == 0
        write('dependency-classes/proof.txt', resource)
        File jar = file('modules-a/dependency.jar')
        writeJar(classes, jar)
    }

    private static void writeJar(File classes, File jar) {
        jar.parentFile.mkdirs()
        new JarOutputStream(new FileOutputStream(jar)).withCloseable { output ->
            classes.eachFileRecurse { entry ->
                if (entry.file) {
                    JarEntry zipEntry = new JarEntry(classes.toPath().relativize(entry.toPath()).toString())
                    zipEntry.time = 0
                    output.putNextEntry(zipEntry)
                    output.write(entry.bytes)
                    output.closeEntry()
                }
            }
        }
    }

    private byte[] dependencyEntry(String name) {
        entryBytes(file('modules-a/dependency.jar'), name)
    }

    private byte[] jarEntry(String name) {
        entryBytes(file('build/libs/descriptor-inputs.jar'), name)
    }

    private static byte[] entryBytes(File jar, String name) {
        new JarFile(jar).withCloseable { archive ->
            archive.getInputStream(archive.getJarEntry(name)).withCloseable { it.bytes }
        }
    }

    private File write(String path, String content) {
        File target = file(path)
        target.parentFile.mkdirs()
        target.text = content.stripIndent()
        target
    }

    private File file(String path) {
        new File(fixture, path)
    }
}
