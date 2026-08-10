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
package com.blackbuild.klum.ast.gradle

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.Specification

import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

@Issue('703')
class KlumDslGdslMaterializationPluginTest extends Specification {

    def "registers one root-owned GDSL materialization task"() {
        given:
        Project root = ProjectBuilder.builder().build()

        when:
        root.pluginManager.apply(KlumDslGdslMaterializationPlugin)
        KlumDslGdslMaterializationTask materialization = root.tasks.named(
                KlumDslGdslMaterializationPlugin.TASK_NAME, KlumDslGdslMaterializationTask).get()

        then:
        materialization.group == 'klum'
        materialization.outputDirectory.get().asFile ==
                new File(root.buildDir, KlumDslGdslMaterializationPlugin.OUTPUT_DIRECTORY)
    }

    def "rejects application to a child project"() {
        given:
        Project root = ProjectBuilder.builder().build()
        Project child = ProjectBuilder.builder().withParent(root).build()

        when:
        new KlumDslGdslMaterializationPlugin().apply(child)

        then:
        IllegalStateException exception = thrown()
        exception.message == 'The Klum DSL GDSL materialization belongs to the root project.'
    }

    def "materializes packaged GDSL resources from runtime archives and directories only"() {
        given: 'a root task with one packaged runtime archive and one unpacked runtime directory'
        File projectDirectory = File.createTempDir('klum-gdsl-materialization-', '')
        Project root = ProjectBuilder.builder().withProjectDir(projectDirectory).build()
        root.pluginManager.apply(KlumDslGdslMaterializationPlugin)
        KlumDslGdslMaterializationTask materialization = root.tasks.named(
                KlumDslGdslMaterializationPlugin.TASK_NAME, KlumDslGdslMaterializationTask).get()
        File archive = gdslArchive(projectDirectory)
        File unpacked = new File(projectDirectory, 'unpacked')
        source(unpacked, 'com/blackbuild/klum/ast/gdsl/Directory.gdsl', 'directory contributor')
        source(unpacked, 'com/blackbuild/klum/ast/gdsl/ignored.txt', 'not a contributor')
        materialization.runtimeClasspath.from(archive, unpacked)
        materialization.outputDirectory.fileValue(new File(projectDirectory, 'materialized'))

        when:
        materialization.materialize()

        then: 'both runtime shapes contribute only GDSL resources at their packaged path'
        File output = materialization.outputDirectory.get().asFile
        new File(output, 'com/blackbuild/klum/ast/gdsl/Archive.gdsl').text == 'archive contributor'
        new File(output, 'com/blackbuild/klum/ast/gdsl/Directory.gdsl').text == 'directory contributor'
        !new File(output, 'com/blackbuild/klum/ast/gdsl/ignored.txt').exists()

        cleanup:
        projectDirectory.deleteDir()
    }

    private File gdslArchive(File projectDirectory) {
        File archive = new File(projectDirectory, 'runtime.jar')
        new JarOutputStream(archive.newOutputStream()).withCloseable { output ->
            output.putNextEntry(new JarEntry('com/blackbuild/klum/ast/gdsl/Archive.gdsl'))
            output << 'archive contributor'
            output.closeEntry()
            output.putNextEntry(new JarEntry('com/blackbuild/klum/ast/gdsl/ignored.txt'))
            output << 'not a contributor'
            output.closeEntry()
        }
        archive
    }

    private void source(File root, String path, String contents) {
        File source = new File(root, path)
        source.parentFile.mkdirs()
        source.text = contents
    }
}
