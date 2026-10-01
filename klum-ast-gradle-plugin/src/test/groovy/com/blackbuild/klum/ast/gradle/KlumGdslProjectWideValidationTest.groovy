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

import groovy.json.JsonOutput
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

    def "task-generated recognized resources preserve their provider dependencies for migration checks"() {
        given:
        fixture()
        write(directory, 'model/build.gradle', model("project(':schema')", "klumGdsl project(':schema')") + """
        def generatedRoot = layout.buildDirectory.dir('generated/legacy-resource')
        def legacyPayload = ${JsonOutput.toJson(copy('legacy', '.source.groovy'))}
        def generated = tasks.register('generateLegacyResource') {
            outputs.dir(generatedRoot)
            doLast {
                def output = new File(generatedRoot.get().asFile, 'environment.gdsl')
                output.parentFile.mkdirs()
                output.text = legacyPayload
            }
        }
        sourceSets.main.resources.srcDir(generated)
""")

        when:
        def failed = runner(directory, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.task(':model:generateLegacyResource').outcome == TaskOutcome.SUCCESS
        failed.output.contains('Recognized legacy GDSL copy')
        failed.output.contains('generated/legacy-resource/environment.gdsl')
        payloads().empty
        failed.task(':schema:compileGroovy') == null
        failed.task(':model:processResources') == null
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

    def "recognized #kind source resources require explicit migration and custom contributors survive"() {
        given:
        fixture()
        write(directory, 'model/build.gradle', model("project(':schema')", "klumGdsl project(':schema')"))
        File resource = write(directory, "model/src/main/$location/environment.gdsl", copy(kind, '.source.groovy'))

        when:
        def failed = runner(directory, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains('Recognized legacy GDSL copy')
        failed.output.contains(resource.path)
        failed.output.contains('org.source:schema:1.0 / environment')
        failed.output.contains('remove the copied resource')
        resource.file
        payloads().empty

        when: 'the author removes the recognized copy and retains an arbitrary custom contributor'
        resource.delete()
        File custom = write(directory, 'model/src/main/resources/custom.gdsl', copy('legacy', '.source.groovy') + "println 'custom contributor'\n")
        File external = write(directory, 'external.gdsl', custom.text)
        new File(directory, 'schema/build.gradle') << "\ndependencies { api files('../external.gdsl') }\n"
        run(directory, 'materializeKlumDslGdsl')

        then:
        external.file
        custom.file
        payloads().size() == 1

        where:
        kind        | location
        'legacy'    | 'resources'
        'generated' | 'resources'
        'legacy'    | 'groovy'
    }

    def "recognized legacy binary resources require a rebuilt normal artifact"() {
        given:
        fixture()
        File isolated = new File(directory, 'repository')
        repository.eachFileRecurse { file ->
            if (file.file) writeBytes(isolated, repository.relativePath(file), file.bytes)
        }
        File jar = new File(isolated, 'org/binary/binary-schema/1.0/binary-schema-1.0.jar')
        File staging = new File(directory, 'staging')
        write(staging, 'environment.gdsl', copy('legacy', '.binary.groovy'))
        // An independent normal artifact with the copied contributor; metadata remains unchanged.
        new ZipOutputStream(jar.newOutputStream()).withCloseable { zip ->
            zip.putNextEntry(new ZipEntry('environment.gdsl'))
            zip.write(new File(staging, 'environment.gdsl').bytes)
            zip.closeEntry()
        }
        write(directory, 'model/build.gradle', model("'org.binary:binary-schema:1.0'", "klumGdsl 'org.binary:binary-schema:1.0:gdsl@jar'", true, isolated, true))

        when:
        def failed = runner(directory, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains('binary-schema-1.0.jar!/environment.gdsl')
        failed.output.contains('Recognized legacy GDSL copy')
        failed.output.contains('org.binary:binary-schema:1.0 / environment')
        payloads().empty
        jar.file

        when: 'the author selects a newly published normal Schema with no legacy resource'
        write(directory, 'model/build.gradle', model("'org.binary:binary-schema:2.0'", "klumGdsl 'org.binary:binary-schema'"))
        run(directory, 'materializeKlumDslGdsl')

        then:
        payloads().size() == 2
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

    private String model(String normal, String selection, boolean enabled = true, File repo = repository, boolean pomOnly = false) {
        """
plugins { id 'com.blackbuild.klum-ast-model' }
groovyDependencies.useSpock = false
['api', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
repositories { maven { url = '${repo.toURI()}'; ${pomOnly ? 'metadataSources { mavenPom(); artifact(); ignoreGradleMetadataRedirection() }' : ''} } }
klumModel { schemas { schema $normal }; gdsl { enabled = $enabled } }
dependencies { $selection }
"""
    }

    private static String copy(String kind, String suffix) {
        if (kind == 'generated') return KlumGdslMetadataFormat.payload(new KlumGdslMetadataFormat.Mapping('environment', suffix, 'example.Environment'))
        """contributor(context(scope: scriptScope())) {
    if (place.containingFile.name.endsWith('$suffix'))
        delegatesTo(findClass('example.Environment_DSL.Builder'))
}
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

    private static void writeBytes(File root, String path, byte[] bytes) {
        File file = new File(root, path)
        file.parentFile.mkdirs()
        file.bytes = bytes
    }

    private static def run(File project, String... arguments) { runner(project, arguments).build() }

    private static GradleRunner runner(File project, String... arguments) {
        GradleRunner.create().withProjectDir(project).withPluginClasspath()
                .withDebug(!arguments.toList().contains('--configuration-cache'))
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain', '--max-workers=2'])
    }
}
