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
import spock.lang.Issue
import spock.lang.Unroll

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

    @Issue('461')
    def "manual mirror refresh is isolated deterministic and configuration-cache safe"() {
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

        when: 'configuration cache is stored and reused through the supported AnnoDocimal task'
        BuildResult configurationCache = run(':schema:createKlumDslSourceMirrors', '--configuration-cache')
        BuildResult configurationCacheReused = run(':schema:createKlumDslSourceMirrors', '--configuration-cache')

        then:
        configurationCache.task(':schema:createKlumDslSourceMirrors').outcome == TaskOutcome.UP_TO_DATE
        !configurationCache.output.contains('problems were found storing the configuration cache')
        configurationCacheReused.output.contains('Configuration cache entry reused.')

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

    @Issue('700')
    @Unroll
    def "source mirrors resolve polymorphic factory providers from the Schema compile classpath with Groovy #groovyVersion"() {
        when: 'the external Schema project projects a generated signature that names a nested runtime type'
        BuildResult generated = run(
                ':schema:createKlumDslSourceMirrors', ':schema:assertKlumDslReferencedClassesClasspath',
                "-PfixtureGroovyVersion=$groovyVersion")

        then: 'the dependency is supplied to projection but remains absent from the plugin loader'
        generated.task(':schema:createKlumDslSourceMirrors').outcome == TaskOutcome.SUCCESS
        generated.output.contains('projection.pluginLoaderContainsFactoryProvider=false')

        and: 'the IDE-only mirror retains the nested public factory-provider reference'
        File mirror = new File(testProject, 'schema/build/generated/sources/klum-dsl-ide/main/example/Foo_DSL.java')
        mirror.text.contains('KlumFactory.BuilderFactoryProvider')
        mirror.text.contains('recipient(')

        and: 'the mirror does not become a compilation input'
        BuildResult isolation = run(':schema:assertKlumDslIsolation', "-PfixtureGroovyVersion=$groovyVersion")
        isolation.output.readLines().findAll { it.startsWith('isolation.') }.every { it.endsWith('=false') }

        where:
        groovyVersion << [3, 4, 5]
    }

    @Issue('559')
    def "root aggregate refreshes one Schema mirror without producing a payload"() {
        when: 'the root entry point refreshes the Schema-owned mirror'
        BuildResult generated = run('generateAllKlumDslSourceMirrors', '--configuration-cache')

        then:
        generated.task(':generateAllKlumDslSourceMirrors').outcome == TaskOutcome.SUCCESS
        generated.task(':schema:createKlumDslSourceMirrors').outcome == TaskOutcome.SUCCESS
        !generated.output.contains('problems were found storing the configuration cache')
        new File(testProject, 'schema/build/generated/sources/klum-dsl-ide/main/example/Foo_DSL.java').file

        when: 'the aggregate has no payload and the producers retain their mirror isolation'
        BuildResult inspected = run(':assertKlumDslSourceMirrorAggregate', ':schema:assertKlumDslIsolation')

        then:
        inspected.output.contains('aggregate.actions=true')
        inspected.output.contains('aggregate.outputs=true')
        inspected.output.contains('aggregate.name=generateAllKlumDslSourceMirrors')
        inspected.output.contains('aggregate.dependencies=:schema:createKlumDslSourceMirrors')
        inspected.output.readLines().findAll { it.startsWith('isolation.') }.every { it.endsWith('=false') }

        when: 'the aggregate configuration cache is reused'
        BuildResult reused = run('generateAllKlumDslSourceMirrors', '--configuration-cache')

        then:
        reused.output.contains('Configuration cache entry reused.')

        when: 'ordinary production work runs after the aggregate refresh'
        BuildResult production = run(':schema:classes', ':schema:jar', ':schema:sourcesJar', ':schema:javadocJar')

        then:
        production.task(':generateAllKlumDslSourceMirrors') == null
        production.task(':schema:createKlumDslSourceMirrors') == null
    }

    @Issue('559')
    def "root aggregate refreshes every Schema project in a Layer 3 layout"() {
        given: 'a Layer 3 API, Schema, and API-only consumer relationship'
        addLayer3ApiProject()

        when: 'the root entry point runs once for the Layer 3 build'
        BuildResult generated = run('generateAllKlumDslSourceMirrors', '--configuration-cache')

        then:
        generated.task(':generateAllKlumDslSourceMirrors').outcome == TaskOutcome.SUCCESS
        generated.task(':api:createKlumDslSourceMirrors').outcome == TaskOutcome.SUCCESS
        generated.task(':schema:createKlumDslSourceMirrors').outcome == TaskOutcome.SUCCESS
        !generated.output.contains('problems were found storing the configuration cache')
        new File(testProject, 'api/build/generated/sources/klum-dsl-ide/main/example/Foo_DSL.java').file
        new File(testProject, 'schema/build/generated/sources/klum-dsl-ide/main/example/Foo_DSL.java').file

        when: 'the aggregate contains both Schema-owned producer tasks and no payload'
        BuildResult inspected = run(
                ':assertKlumDslSourceMirrorAggregate', ':consumer:assertKlumDslDownstreamIsolation',
                '-PexpectedKlumDslSourceMirrorProjects=:api,:schema')

        then:
        inspected.output.contains('aggregate.actions=true')
        inspected.output.contains('aggregate.outputs=true')
        inspected.output.contains('aggregate.name=generateAllKlumDslSourceMirrors')
        inspected.output.contains('aggregate.dependencies=:api:createKlumDslSourceMirrors,:schema:createKlumDslSourceMirrors')
        inspected.output.readLines().findAll { it.startsWith('downstream.') }.every { it.endsWith('=false') }

        when: 'the Layer 3 aggregate is run again with the configuration cache'
        BuildResult reused = run('generateAllKlumDslSourceMirrors', '--configuration-cache')

        then:
        reused.output.contains('Configuration cache entry reused.')
    }

    private BuildResult run(String... arguments) {
        GradleRunner.create()
                .withProjectDir(testProject)
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain'])
                .withPluginClasspath()
                .forwardOutput()
                .build()
    }

    private void addLayer3ApiProject() {
        File schema = new File(testProject, 'schema')
        File api = new File(testProject, 'api')
        schema.eachFileRecurse { source ->
            if (source.file) {
                File target = new File(api, schema.relativePath(source))
                target.parentFile.mkdirs()
                target.bytes = source.bytes
            }
        }
        File settings = new File(testProject, 'settings.gradle')
        settings.text += "\ninclude 'api'\n"
        File schemaBuild = new File(schema, 'build.gradle')
        schemaBuild.text = schemaBuild.text.replace(
                "publishing {",
                "dependencies {\n    api project(':api')\n}\n\npublishing {")
        File consumerBuild = new File(testProject, 'consumer/build.gradle')
        consumerBuild.text = consumerBuild.text.replaceAll("':schema'", "':api'")
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
