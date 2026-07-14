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

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Shared
import spock.lang.Specification

import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile

class KlumDslSourceMirrorsIntegrationTest extends Specification {

    @Shared File fixture = new File('src/test/fixtures/dsl-g').absoluteFile
    @Shared File testProject = new File('build/test-dsl-g').absoluteFile

    def setup() {
        testProject.deleteDir()
        testProject.mkdirs()
        fixture.eachFileRecurse { source ->
            if (source.file) {
                File target = new File(testProject, fixture.relativePath(source))
                target.parentFile.mkdirs()
                target.bytes = source.bytes
            }
        }
    }

    def "manual mirror refresh is isolated and deterministic"() {
        when: 'a clean IntelliJ model is inspected before any build task runs'
        BuildResult model = run('clean', ':schema:assertKlumDslIdeModel')

        then:
        model.output.contains('idea.source=true')
        model.output.contains('idea.generated=true')
        model.output.contains('idea.mirrorExists=false')
        model.task(':schema:compileGroovy') == null
        model.task(':schema:createKlumDslSourceMirrors') == null

        when: 'the documented refresh task is executed on that clean checkout'
        BuildResult generated = run(':schema:createKlumDslSourceMirrors', '--build-cache')
        File mirror = new File(testProject, 'schema/build/generated/sources/klum-dsl-ide/main/example/Foo_DSL.java')
        String firstHash = sha256(mirror)

        then:
        generated.task(':schema:compileGroovy').outcome == TaskOutcome.SUCCESS
        generated.task(':schema:createKlumDslSourceMirrors').outcome == TaskOutcome.SUCCESS
        generated.tasks.count { it.path == ':schema:compileGroovy' } == 1
        generated.tasks.findIndexOf { it.path == ':schema:compileGroovy' } <
                generated.tasks.findIndexOf { it.path == ':schema:createKlumDslSourceMirrors' }
        realDslClassFiles().size() == 1
        mirror.text.contains('Documentation for Foo_DSL')
        mirror.text.contains('Documentation for Builder')

        when: 'the mirror output is deleted and restored from the build cache'
        mirror.parentFile.parentFile.parentFile.deleteDir()
        BuildResult restored = run(':schema:createKlumDslSourceMirrors', '--build-cache')

        then:
        restored.task(':schema:createKlumDslSourceMirrors').outcome == TaskOutcome.FROM_CACHE
        sha256(mirror) == firstHash

        when: 'configuration cache is probed against the real AnnoDocimal compiler hook'
        BuildResult configurationCache = run(
                ':schema:createKlumDslSourceMirrors', '--configuration-cache', '--configuration-cache-problems=warn')

        then: 'the build remains usable but Gradle reports AnnoDocimal 0.7.1 as the unsupported upstream seam'
        configurationCache.task(':schema:createKlumDslSourceMirrors').outcome == TaskOutcome.UP_TO_DATE
        configurationCache.output.contains('problems were found storing the configuration cache')
        configurationCache.output.contains("Task `:schema:compileGroovy`")
        configurationCache.output.contains("cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject'")

        when: 'an undeclared stale output is introduced and the task is rerun'
        File stale = new File(mirror.parentFile, 'Stale_DSL.java')
        stale.text = 'stale'
        BuildResult cleaned = run(':schema:createKlumDslSourceMirrors', '--rerun-tasks')

        then:
        cleaned.task(':schema:createKlumDslSourceMirrors').outcome == TaskOutcome.SUCCESS
        !stale.exists()
        sha256(mirror) == firstHash

        when: 'all ordinary production, documentation, publication, test, and downstream surfaces run'
        BuildResult production = run(
                ':schema:classes', ':schema:test', ':schema:jar', ':schema:sourcesJar', ':schema:javadocJar',
                ':schema:publishMavenJavaPublicationToFixtureRepository', ':schema:assertKlumDslIsolation',
                ':consumer:assertKlumDslDownstreamIsolation')

        then:
        production.task(':schema:createKlumDslSourceMirrors') == null
        def isolationChecks = production.output.readLines().findAll { it.startsWith('isolation.') }
        isolationChecks.size() == 15
        isolationChecks.every { it.endsWith('=false') }
        def downstreamChecks = production.output.readLines().findAll { it.startsWith('downstream.') }
        downstreamChecks.size() == 4
        downstreamChecks.every { it.endsWith('=false') }
        !new File(testProject, 'schema/build/docs/javadoc/example/Foo_DSL.html').exists()
        publishedArchivesContainNoMirror()
    }

    def "AnnoDoc-only schema changes invalidate the mirror task"() {
        given:
        File mirror = new File(testProject, 'schema/build/generated/sources/klum-dsl-ide/main/example/Foo_DSL.java')
        File schema = new File(testProject, 'schema/compiler-input/example/Foo_DSL.groovy')
        run(':schema:createKlumDslSourceMirrors', '--build-cache')
        String firstHash = sha256(mirror)

        when:
        schema.text = schema.text.replace('Documentation for Foo_DSL', 'Updated documentation for Foo_DSL')
        BuildResult updated = run(':schema:createKlumDslSourceMirrors', '--build-cache')

        then:
        updated.task(':schema:compileGroovy').outcome == TaskOutcome.SUCCESS
        updated.task(':schema:createKlumDslSourceMirrors').outcome == TaskOutcome.SUCCESS
        sha256(mirror) != firstHash
        mirror.text.contains('Updated documentation for Foo_DSL')
    }

    private BuildResult run(String... arguments) {
        GradleRunner.create()
                .withProjectDir(testProject)
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain'])
                .withPluginClasspath()
                .forwardOutput()
                .build()
    }

    private boolean publishedArchivesContainNoMirror() {
        List<File> archives = []
        new File(testProject, 'schema/build/repository').eachFileRecurse { file ->
            if (file.file && file.name.endsWith('.jar')) archives << file
        }
        assert !archives.empty
        archives.each { archive ->
            new ZipFile(archive).withCloseable { zip ->
                assert !zip.entries().toList()*.name.any { it.endsWith('Foo_DSL.java') || it.endsWith('Foo_DSL.html') }
            }
        }
        true
    }

    private List<File> realDslClassFiles() {
        List<File> result = []
        new File(testProject, 'schema/build/classes').eachFileRecurse { file ->
            if (file.file && file.name == 'Foo_DSL.class') result << file
        }
        result
    }

    private static String sha256(File file) {
        assert file.file
        MessageDigest.getInstance('SHA-256').digest(Files.readAllBytes(file.toPath())).encodeHex().toString()
    }
}
