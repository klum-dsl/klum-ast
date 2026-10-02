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

import groovy.util.XmlSlurper
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.See
import spock.lang.TempDir

import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry
import java.util.jar.JarFile

@Issue('805')
class KlumGdslProjectWideValidationTest extends Specification {
    @TempDir File directory
    @Shared @TempDir File sharedDirectory
    @Shared File repository

    def setupSpec() {
        repository = new File(sharedDirectory, 'repository')
        ['1.0', '2.0'].each { version ->
            File producer = new File(sharedDirectory, "binary-$version")
            write(producer, 'settings.gradle', "rootProject.name = 'binary-schema'")
            write(producer, 'build.gradle', schema('org.binary', version, '.binary.groovy') + """
apply plugin: 'maven-publish'
publishing.repositories { maven { name = 'fixture'; url = '${repository.toURI()}' } }
""")
            run(producer, 'publishMavenJavaPublicationToFixtureRepository')
        }
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/implementation/adr-0025-portable-schema-gdsl-metadata.md#gdsl-3-engineering-contract-and-evidence')
    def "source authoring and two explicit project consumers share one root and one archive with #order evaluation"() {
        given:
        fixture(order)
        write(directory, 'build.gradle', "evaluationDependsOn(':${order.split(',')[0]}')")
        write(directory, 'model/build.gradle', model("project(':schema')", "klumGdsl project(':schema')"))
        write(directory, 'other/build.gradle', model("project(':schema')", "klumGdsl project(':schema')"))
        new File(directory, 'schema/build.gradle') << '\ndependencies { api files(' +
                System.getProperty('gdslRuntimeClasspath').split(File.pathSeparator).collect { "'$it'" }.join(',') + ') }\n'

        when:
        def first = run(directory, 'materializeKlumDslGdsl', '--configuration-cache', '--build-cache')
        def reused = run(directory, 'materializeKlumDslGdsl', '--configuration-cache', '--build-cache')

        then:
        first.task(':materializeKlumDslGdsl').outcome == TaskOutcome.SUCCESS
        first.task(':schema:klumGdslJar').outcome in [TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE]
        first.task(':schema:jar') == null
        first.task(':schema:compileGroovy') == null
        first.task(':schema:compileJava') == null
        first.tasks.count { it.path.endsWith(':materializeKlumDslGdsl') } == 1
        payloads()*.name == ['environment.gdsl']
        frameworkPayloadCount(directory) > 0
        reused.output.contains('Configuration cache entry reused.')
        reused.task(':materializeKlumDslGdsl').outcome == TaskOutcome.UP_TO_DATE

        when:
        new File(directory, 'build/generated/klum-dsl-ide/gdsl').deleteDir()
        def restored = run(directory, 'materializeKlumDslGdsl', '--configuration-cache', '--build-cache')

        then:
        restored.task(':materializeKlumDslGdsl').outcome == TaskOutcome.FROM_CACHE
        payloads().size() == 1

        when: 'a fresh project directory restores the same source union without compilation'
        File relocated = new File(directory, 'relocated')
        ['settings.gradle', 'build.gradle', 'schema/build.gradle', 'model/build.gradle', 'other/build.gradle'].each { path ->
            write(relocated, path, new File(directory, path).text)
        }
        def relocatedResult = run(relocated, 'materializeKlumDslGdsl', '--configuration-cache', '--build-cache')

        then:
        relocatedResult.task(':materializeKlumDslGdsl').outcome == TaskOutcome.FROM_CACHE
        frameworkPayloadCount(relocated) == frameworkPayloadCount(directory)
        relocatedResult.task(':schema:compileJava') == null

        where:
        order << ['schema,model,other', 'model,other,schema']
    }

    def "source-only mappings refresh edit rename removal empty metadata and final opt-out"() {
        given:
        fixture('schema')
        run(directory, 'materializeKlumDslGdsl')
        File initial = payloads().first()

        when:
        write(directory, 'schema/build.gradle', schema('org.source', '1.0', '.renamed.groovy', 'renamed'))
        run(directory, 'materializeKlumDslGdsl')

        then:
        !initial.exists()
        payloads()*.name == ['renamed.gdsl']
        payloads().first().text.contains('.renamed.groovy')

        when:
        write(directory, 'schema/build.gradle', schema('org.source', '1.0', '.renamed.groovy', 'renamed', true, false))
        run(directory, 'materializeKlumDslGdsl')

        then:
        payloads().empty

        when:
        write(directory, 'schema/build.gradle', schema('org.source', '1.0', '.source.groovy'))
        run(directory, 'materializeKlumDslGdsl')
        write(directory, 'schema/build.gradle', schema('org.source', '1.0', '.source.groovy', 'environment', false))
        run(directory, 'materializeKlumDslGdsl')

        then:
        payloads().empty

        when:
        run(directory, 'clean')

        then:
        !new File(directory, 'build/generated/klum-dsl-ide/gdsl').exists()
    }

    def "source plus binary union permits equal payload basenames and rejects suffix overlap before sync"() {
        given:
        fixture()
        write(directory, 'model/build.gradle', model("'org.binary:binary-schema:1.0'", "klumGdsl 'org.binary:binary-schema'"))
        run(directory, 'materializeKlumDslGdsl')
        def previous = payloads().collectEntries { [(it.path): it.text] }

        when:
        write(directory, 'schema/build.gradle', schema('org.source', '1.0', '.groovy'))
        def failed = runner(directory, 'materializeKlumDslGdsl').buildAndFail()

        then:
        previous.size() == 2
        failed.output.contains('Overlapping project-wide GDSL mappings')
        failed.output.contains('org.source:schema:1.0 / environment (.groovy)')
        failed.output.contains('org.binary:binary-schema:1.0 / environment (.binary.groovy)')
        failed.output.contains('witness filename: recipe.binary.groovy')
        failed.output.contains('previous IDE output is stale')
        payloads().collectEntries { [(it.path): it.text] } == previous
    }

    def "a non-opted-in Model normal version conflicts with project-wide binary metadata"() {
        given:
        fixture('schema,model,other')
        write(directory, 'model/build.gradle', model("'org.binary:binary-schema:1.0'", "klumGdsl 'org.binary:binary-schema'"))
        write(directory, 'other/build.gradle', model("'org.binary:binary-schema:2.0'", '', false))

        when:
        def failed = runner(directory, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains('Conflicting project-wide Schema versions')
        failed.output.contains('org.binary:binary-schema:1.0')
        failed.output.contains('org.binary:binary-schema:2.0')
        payloads().empty
    }

    def "two source Schemas and two Models #behavior with equal payload basenames"() {
        given:
        write(directory, 'settings.gradle', "rootProject.name = 'union'; include ':left:schema', ':right:schema', ':model', ':other'")
        write(directory, 'build.gradle', '')
        write(directory, 'left/schema/build.gradle', schema('org.one', '1.0', '.one.groovy'))
        write(directory, 'right/schema/build.gradle', schema(group, version, '.two.groovy'))
        write(directory, 'model/build.gradle', model("project(':left:schema')", "klumGdsl project(':left:schema')"))
        write(directory, 'other/build.gradle', model("project(':right:schema')", "klumGdsl project(':right:schema')"))

        when:
        def result = failure ? runner(directory, 'materializeKlumDslGdsl').buildAndFail() : run(directory, 'materializeKlumDslGdsl')

        then:
        if (failure) {
            assert result.output.contains(failure)
            assert result.output.contains('org.one:schema:1.0')
            assert payloads().empty
        } else {
            assert payloads()*.name == ['environment.gdsl', 'environment.gdsl']
            assert result.tasks.count { it.path.endsWith(':materializeKlumDslGdsl') } == 1
        }

        where:
        behavior             | group     | version | failure
        'coexist'            | 'org.two' | '1.0'   | null
        'reject duplicates'  | 'org.one' | '1.0'   | 'Different GDSL payloads for one Schema GAV'
        'reject versions'    | 'org.one' | '2.0'   | 'Conflicting project-wide Schema versions'
    }

    def "a binary-only last Model opt-out leaves cleanup to the normal root clean task"() {
        given:
        write(directory, 'settings.gradle', "rootProject.name = 'binary-only'")
        write(directory, 'build.gradle', model("'org.binary:binary-schema:1.0'", "klumGdsl 'org.binary:binary-schema'"))
        run(directory, 'materializeKlumDslGdsl')

        when:
        write(directory, 'build.gradle', model("'org.binary:binary-schema:1.0'", '', false))
        def result = run(directory, 'clean')

        then:
        result.task(':clean').outcome == TaskOutcome.SUCCESS
        !new File(directory, 'build/generated/klum-dsl-ide/gdsl').exists()
    }

    def "binary-only Model subproject retains root cleanup after its final opt-out"() {
        given:
        write(directory, 'settings.gradle', "rootProject.name = 'plain-root'; include 'model'")
        write(directory, 'build.gradle', '')
        write(directory, 'model/build.gradle', model("'org.binary:binary-schema:1.0'", "klumGdsl 'org.binary:binary-schema'"))
        run(directory, 'materializeKlumDslGdsl')

        when:
        write(directory, 'model/build.gradle', model("'org.binary:binary-schema:1.0'", '', false))
        def result = run(directory, 'clean')

        then:
        result.task(':clean').outcome == TaskOutcome.SUCCESS
        !new File(directory, 'build/generated/klum-dsl-ide/gdsl').exists()
    }

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/implementation/adr-0025-portable-schema-gdsl-metadata.md#root-base-lifecycle-ownership')
    def "a never-enabled Model beneath a plain root adds only the root Base lifecycle"() {
        given:
        write(directory, 'settings.gradle', "rootProject.name = 'plain-root'; include 'model'")
        write(directory, 'build.gradle', """
tasks.register('verifyRootLifecycle') {
    doLast {
        assert plugins.hasPlugin('base')
        assert ['clean', 'assemble', 'check', 'build'].every { tasks.findByName(it) != null }
        assert !plugins.hasPlugin('java')
        assert !plugins.hasPlugin('com.blackbuild.klum-ast-model')
        assert project.extensions.findByName('java') == null
        assert project.extensions.findByName('sourceSets') == null
        assert ['compileJava', 'compileGroovy', 'jar', 'materializeKlumDslGdsl'].every { tasks.findByName(it) == null }
        assert ['compileClasspath', 'runtimeClasspath'].every { configurations.findByName(it) == null }
        assert configurations.every { it.dependencies.empty }
    }
}
""")
        write(directory, 'model/build.gradle', model("'org.binary:binary-schema:1.0'", "klumGdsl 'org.binary:absent-schema'", false) + """
apply plugin: 'maven-publish'
group = 'org.model'
version = '1.0'
publishing.repositories { maven { name = 'fixture'; url = layout.buildDirectory.dir('repository') } }
configurations.configureEach {
    if (name.startsWith('klumGdsl')) incoming.beforeResolve { throw new GradleException('Unexpected GDSL resolution') }
}
tasks.register('verifyModelClasspaths') {
    doLast {
        assert configurations.findAll { it.name.startsWith('klumGdsl') }*.name == ['klumGdsl']
        assert !configurations.klumGdsl.canBeResolved
        assert configurations.klumGdsl.dependencies*.name == ['absent-schema']
        assert configurations.compileClasspath.files*.name == ['binary-schema-1.0.jar']
        assert configurations.runtimeClasspath.files*.name == ['binary-schema-1.0.jar']
        assert tasks.findByName('materializeKlumDslGdsl') == null
    }
}
""")
        write(directory, 'model/src/main/resources/model.txt', 'ordinary Model resource')

        when:
        def result = run(directory, ':clean', ':verifyRootLifecycle', ':model:verifyModelClasspaths',
                ':model:build', ':model:publishMavenJavaPublicationToFixtureRepository')

        then:
        result.task(':clean').outcome == TaskOutcome.UP_TO_DATE
        result.task(':verifyRootLifecycle').outcome == TaskOutcome.SUCCESS
        result.task(':model:build').outcome == TaskOutcome.SUCCESS
        result.task(':model:publishMavenJavaPublicationToFixtureRepository').outcome == TaskOutcome.SUCCESS
        result.tasks.every { !it.path.endsWith(':materializeKlumDslGdsl') }
        !new File(directory, 'build/generated/klum-dsl-ide/gdsl').exists()
        new JarFile(new File(directory, 'model/build/libs/model-1.0.jar')).withCloseable { jar ->
            assert jar.getEntry('model.txt')
            assert !jar.entries().toList()*.name.any { it.endsWith('.gdsl') }
            true
        }
        def pom = new XmlSlurper().parse(new File(directory, 'model/build/repository/org/model/model/1.0/model-1.0.pom'))
        pom.dependencies.dependency.collect { [it.groupId.text(), it.artifactId.text(), it.version.text(), it.scope.text()] } ==
                [['org.binary', 'binary-schema', '1.0', 'compile']]
    }

    def "normal module-to-project substitution selects metadata through the project capability"() {
        given:
        fixture()
        write(directory, 'model/build.gradle', model("'org.source:schema:1.0'", "klumGdsl 'org.source:schema'") + """
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute module('org.source:schema') using project(':schema')
    }
}
""")

        when:
        run(directory, 'materializeKlumDslGdsl', '--configuration-cache')
        def reused = run(directory, 'materializeKlumDslGdsl', '--configuration-cache')

        then:
        payloads().size() == 1
        reused.output.contains('Configuration cache entry reused.')
        reused.task(':schema:jar') == null
    }

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/adr/0025-portable-schema-gdsl-metadata.md#managed-validation-boundary')
    def "external GDSL resources stay outside the managed source and binary union"() {
        given:
        fixture()
        write(directory, 'model/build.gradle', model("project(':schema'); schema 'org.binary:binary-schema:1.0'",
                "klumGdsl project(':schema'); klumGdsl 'org.binary:binary-schema'") + """
dependencies { implementation files('../external.jar') }
def generated = tasks.register('generateExternalGdsl') {
    outputs.dir(layout.buildDirectory.dir('generated/external-resources'))
    doLast { throw new GradleException('Metadata refresh must not generate normal resources') }
}
sourceSets.main.resources.srcDir(generated)
""")
        String copiedPayload = KlumGdslMetadataFormat.payload(new KlumGdslMetadataFormat.Mapping('environment', '.source.groovy', 'example.Environment'))
        File copiedResource = write(directory, 'model/src/main/resources/environment.gdsl', copiedPayload)
        File customSource = write(directory, 'model/src/main/groovy/custom.gdsl', "throw new AssertionError('User-owned GDSL must not execute')\n")
        File schemaResource = write(directory, 'schema/src/main/resources/environment.gdsl', copiedPayload)
        File externalJar = new File(directory, 'external.jar')
        new ZipOutputStream(externalJar.newOutputStream()).withCloseable { zip ->
            zip.putNextEntry(new ZipEntry('environment.gdsl'))
            zip.write(copiedPayload.getBytes('UTF-8'))
            zip.closeEntry()
            zip.putNextEntry(new ZipEntry('custom.gdsl'))
            zip.write(customSource.bytes)
            zip.closeEntry()
        }
        byte[] originalJar = externalJar.bytes

        when:
        def refreshed = run(directory, 'materializeKlumDslGdsl', '--configuration-cache')
        customSource.text = "throw new AssertionError('External source edits do not invalidate managed metadata')\n"
        def reused = run(directory, 'materializeKlumDslGdsl', '--configuration-cache')

        then:
        refreshed.task(':materializeKlumDslGdsl').outcome == TaskOutcome.SUCCESS
        refreshed.task(':model:generateExternalGdsl') == null
        refreshed.task(':model:processResources') == null
        refreshed.task(':schema:jar') == null
        refreshed.task(':schema:compileJava') == null
        refreshed.task(':schema:compileGroovy') == null
        reused.output.contains('Configuration cache entry reused.')
        reused.task(':materializeKlumDslGdsl').outcome == TaskOutcome.UP_TO_DATE
        payloads().size() == 2
        payloads().every { it.name == 'environment.gdsl' }
        copiedResource.text == copiedPayload
        schemaResource.text == copiedPayload
        externalJar.bytes == originalJar
        !new File(directory, 'build/generated/klum-dsl-ide/gdsl/custom.gdsl').exists()
    }

    def "ordinary source publication and Model build preserve normal outputs and never refresh metadata"() {
        given:
        fixture()
        write(directory, 'schema/src/main/java/example/Environment.java', 'package example; public class Environment {}')
        write(directory, 'model/build.gradle', model("project(':schema')", "klumGdsl 'org.binary:absent-schema'"))
        write(directory, 'model/src/main/resources/custom.gdsl', "println 'custom contributor'\n")

        when:
        def result = run(directory, ':schema:jar', ':schema:sourcesJar', ':model:jar', 'idea')

        then:
        result.task(':materializeKlumDslGdsl') == null
        result.task(':schema:klumGdslJar') == null
        payloads().empty
        new JarFile(new File(directory, 'schema/build/libs/schema-1.0.jar')).withCloseable { jar ->
            assert jar.getEntry('example/Environment.class')
            assert !jar.entries().toList()*.name.any { it.contains('gdsl') }
            true
        }
        new JarFile(new File(directory, 'model/build/libs/model.jar')).withCloseable { jar ->
            assert jar.getEntry('custom.gdsl')
            true
        }
    }

    private void fixture(String projects = 'schema,model') {
        write(directory, 'settings.gradle', """
rootProject.name = 'source-root'
include ${projects.split(',').collect { "'$it'" }.join(',')}
buildCache { local { directory = '${new File(directory, 'cache').absolutePath}' } }
""")
        write(directory, 'build.gradle', '')
        write(directory, 'schema/build.gradle', schema('org.source', '1.0', '.source.groovy'))
    }

    private static String schema(String group, String version, String suffix, String id = 'environment', boolean enabled = true, boolean mappings = true) {
        """
plugins { id 'com.blackbuild.klum-ast-schema' }
group = '$group'
version = '$version'
['api', 'compileOnly', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
tasks.named('compileJava') { classpath = files() }
tasks.named('compileGroovy') { classpath = files(); groovyClasspath = files(); astTransformationClasspath.setFrom(files()) }
tasks.named('javadoc') { classpath = files() }
tasks.named('createClassStubs') { referencedClassesClasspath.setFrom(files()) }
klumSchema.gdsl {
    publish = $enabled
    mappings { ${mappings ? "$id { fileNameSuffix = '$suffix'; modelType = 'example.Environment' }" : ''} }
}
"""
    }

    private String model(String normal, String selection, boolean enabled = true) {
        """
plugins { id 'com.blackbuild.klum-ast-model' }
groovyDependencies.useSpock = false
['api', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
repositories { maven { url = '${repository.toURI()}' } }
klumModel { schemas { schema $normal }; gdsl { enabled = $enabled } }
dependencies { $selection }
"""
    }

    private static int frameworkPayloadCount(File directory) {
        File root = new File(directory, 'build/generated/klum-dsl-ide/gdsl/com/blackbuild/klum/ast/gdsl')
        List<File> files = []
        if (root.exists()) root.eachFileRecurse { if (it.file) files << it }
        files.size()
    }

    private List<File> payloads() {
        File root = new File(directory, 'build/generated/klum-dsl-ide/gdsl/schema-owned')
        List<File> result = []
        if (root.exists()) root.eachFileRecurse { if (it.file) result << it }
        result.sort { it.path }
    }

    private static File write(File root, String path, String text) {
        File file = new File(root, path)
        file.parentFile.mkdirs()
        file.text = text
        file
    }

    private static def run(File project, String... arguments) { runner(project, arguments).build() }

    private static GradleRunner runner(File project, String... arguments) {
        GradleRunner.create().withProjectDir(project).withPluginClasspath()
                .withDebug(!arguments.toList().contains('--configuration-cache'))
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain', '--max-workers=2'])
    }
}
