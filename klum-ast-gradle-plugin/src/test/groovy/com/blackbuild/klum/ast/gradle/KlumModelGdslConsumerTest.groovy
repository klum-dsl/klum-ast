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

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.See
import spock.lang.TempDir

import java.util.jar.JarFile

@Issue('805')
class KlumModelGdslConsumerTest extends Specification {
    @TempDir File directory
    @Shared @TempDir File sharedDirectory
    @Shared File repository
    File consumer

    def setupSpec() {
        repository = new File(sharedDirectory, 'repository')
        [['environment-schema', '1.0'], ['environment-schema', '2.0'], ['replacement-schema', '2.0']].each {
            publishSchema(it[0], it[1])
        }
        publishSchema('shared-schema', '1.0', 'org.one', '.one.groovy', 'example.FirstEnvironment')
        publishSchema('shared-schema', '1.0', 'org.two', '.two.groovy', 'example.SecondEnvironment')
    }

    private void publishSchema(String artifact, String version, String group = 'org.example',
                               String suffix = '.environment.groovy', String model = 'example.Environment') {
        File producer = new File(sharedDirectory, "producer-$group-$artifact-$version")
        producer.mkdirs()
        new File(producer, 'settings.gradle').text = "rootProject.name = '$artifact'"
        new File(producer, 'build.gradle').text = """
plugins { id 'com.blackbuild.klum-ast-schema'; id 'maven-publish' }
group = '$group'
version = '$version'
['api', 'compileOnly', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
tasks.named('compileJava') { classpath = files() }
tasks.named('compileGroovy') { classpath = files(); groovyClasspath = files(); astTransformationClasspath.setFrom(files()) }
tasks.named('javadoc') { classpath = files() }
tasks.named('createClassStubs') { referencedClassesClasspath.setFrom(files()) }
klumSchema.gdsl {
    publish = true
    mappings { environment { fileNameSuffix = '$suffix'; modelType = '$model' } }
}
publishing.repositories { maven { name = 'fixture'; url = '${repository.toURI()}' } }
"""
        String name = model.substring(model.lastIndexOf('.') + 1)
        File source = new File(producer, "src/main/java/example/${name}.java")
        source.parentFile.mkdirs()
        source.text = "package example; public class $name {}"
        runner(producer, 'publishMavenJavaPublicationToFixtureRepository').build()
    }

    def setup() {
        consumer = new File(directory, 'model')
        consumer.mkdirs()
        new File(consumer, 'settings.gradle').text = "rootProject.name = 'catalog-model'"
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/implementation/adr-0025-portable-schema-gdsl-metadata.md#gdsl-2-engineering-contract-and-evidence')
    def "binary Model follows the normal Schema constraint without a second metadata version and caches refresh"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema'", "constraints { api 'org.example:environment-schema:2.0' }")
        new File(consumer, 'settings.gradle') << "\nbuildCache { local { directory = '${new File(directory, 'cache').absolutePath}' } }\n"

        when:
        def first = runner(consumer, 'materializeKlumDslGdsl', '--configuration-cache', '--build-cache').build()
        def reused = runner(consumer, 'materializeKlumDslGdsl', '--configuration-cache', '--build-cache').build()

        then:
        first.task(':materializeKlumDslGdsl').outcome == TaskOutcome.SUCCESS
        reused.output.contains('Configuration cache entry reused.')
        reused.task(':materializeKlumDslGdsl').outcome == TaskOutcome.UP_TO_DATE
        payload().text == KlumGdslMetadataFormat.payload(new KlumGdslMetadataFormat.Mapping('environment', '.environment.groovy', 'example.Environment'))
        !new File(consumer, 'build/generated/sources').exists()
        first.task(':compileJava') == null
        first.task(':compileGroovy') == null

        when:
        new File(consumer, 'build/generated/klum-dsl-ide/gdsl').deleteDir()
        def restored = runner(consumer, 'materializeKlumDslGdsl', '--configuration-cache', '--build-cache').build()

        then:
        restored.task(':materializeKlumDslGdsl').outcome == TaskOutcome.FROM_CACHE
        payload().file
    }

    def "POM-only exact classifier is explicit and remains separate from the normal graph"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema:1.0:gdsl@jar'", '', true)

        when:
        def result = runner(consumer, 'materializeKlumDslGdsl', 'jar', 'sourcesJar', 'publishMavenJavaPublicationToFixtureRepository', 'assertIsolation').build()

        then:
        result.task(':materializeKlumDslGdsl').outcome == TaskOutcome.SUCCESS
        payload().file
        new JarFile(new File(consumer, 'build/libs/catalog-model-1.0.jar')).withCloseable { jar ->
            assert !jar.entries().toList()*.name.any { it.contains('gdsl') || it.contains('mappings.json') }
            true
        }
    }

    def "ordinary Model build never resolves opted-in missing metadata"() {
        given:
        fixture("klumGdsl 'org.example:absent-schema'", '')

        when:
        def result = runner(consumer, 'jar', 'sourcesJar', 'assertIsolation').build()

        then:
        result.task(':materializeKlumDslGdsl') == null
        !payload().exists()
    }

    def "classifier mismatch fails before changing prior root bytes"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema:1.0:gdsl@jar'", '', true)
        runner(consumer, 'materializeKlumDslGdsl').build()
        String previous = payload().text
        fixture("klumGdsl 'org.example:environment-schema:2.0:gdsl@jar'", '', true)

        when:
        def failed = runner(consumer, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains('does not match the selected normal Schema')
        failed.output.contains('previous IDE output is stale')
        payload().text == previous
    }

    def "classifier rejects #version versions"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema:$version:gdsl@jar'", '', true)

        when:
        def failed = runner(consumer, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains('exact Schema GAV (no dynamic/range version)')
        !payload().exists()

        where:
        version << ['1.+', '[1.0,2.0)', 'latest.release']
    }

    def "GMM mode never silently retries a classifier in a POM-only repository"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema'", '', true)

        when:
        def failed = runner(consumer, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains("requested capability: feature 'gdsl'")
        !payload().exists()
    }

    def "normal version conflicts substitutions and dependency locks select metadata too"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema'", "api 'org.example:environment-schema:2.0'")
        new File(consumer, 'build.gradle') << """
configurations.compileClasspath.resolutionStrategy.activateDependencyLocking()
"""

        when:
        runner(consumer, 'dependencies', '--configuration', 'compileClasspath', '--write-locks').build()
        def locked = runner(consumer, 'materializeKlumDslGdsl').build()

        then:
        new File(consumer, 'gradle.lockfile').text.contains('org.example:environment-schema:2.0=compileClasspath')
        locked.output.contains('environment-schema-2.0-gdsl.jar:org.example:environment-schema:2.0')

        when:
        fixture("klumGdsl 'org.example:environment-schema'", '')
        new File(consumer, 'build.gradle') << """
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute module('org.example:environment-schema') using module('org.example:environment-schema:2.0')
    }
}
"""
        def substituted = runner(consumer, 'materializeKlumDslGdsl', '--rerun-tasks').build()

        then:
        substituted.output.contains('environment-schema-2.0-gdsl.jar:org.example:environment-schema:2.0')
    }

    def "normal application BOM selects a single matching metadata version"() {
        given:
        File bom = new File(repository, 'org/example/application-bom/1.0/application-bom-1.0.pom')
        bom.parentFile.mkdirs()
        bom.text = '''<project><modelVersion>4.0.0</modelVersion><groupId>org.example</groupId>
<artifactId>application-bom</artifactId><version>1.0</version><packaging>pom</packaging>
<dependencyManagement><dependencies><dependency><groupId>org.example</groupId>
<artifactId>environment-schema</artifactId><version>2.0</version></dependency></dependencies></dependencyManagement></project>'''
        fixture("klumGdsl 'org.example:environment-schema'", "api platform('org.example:application-bom:1.0')")

        when:
        def result = runner(consumer, 'materializeKlumDslGdsl').build()

        then:
        result.output.contains('environment-schema-2.0-gdsl.jar:org.example:environment-schema:2.0')
    }

    def "GMM metadata cannot introduce an independently maintained version"() {
        given:
        fixture(selection, '')

        when:
        def failed = runner(consumer, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains('GMM GDSL selections must omit the version')
        !payload().exists()

        where:
        selection << [
                "klumGdsl 'org.example:environment-schema:2.0'",
                "klumGdsl('org.example:environment-schema') { version { strictly '2.0' } }",
                "klumGdsl('org.example:environment-schema') { version { prefer '2.0' } }",
                "klumGdsl('org.example:environment-schema') { version { reject '2.0' } }"
        ]
    }

    def "absent classifier or variant is an explicit failure without extraction"() {
        given:
        File isolated = new File(directory, 'repository')
        repository.eachFileRecurse { file ->
            if (file.file) {
                File copy = new File(isolated, repository.relativePath(file))
                copy.parentFile.mkdirs()
                copy.bytes = file.bytes
            }
        }
        File artifacts = new File(isolated, 'org/example/environment-schema/1.0')
        if (pomOnly) {
            new File(artifacts, 'environment-schema-1.0-gdsl.jar').delete()
        } else {
            File moduleFile = new File(artifacts, 'environment-schema-1.0.module')
            def module = new JsonSlurper().parse(moduleFile)
            module.variants.removeAll { it.name == 'klumGdslElements' }
            moduleFile.text = JsonOutput.toJson(module)
        }
        fixture(pomOnly ? "klumGdsl 'org.example:environment-schema:1.0:gdsl@jar'" : "klumGdsl 'org.example:environment-schema'", '', pomOnly)
        File build = new File(consumer, 'build.gradle')
        build.text = build.text.replace(repository.toURI().toString(), isolated.toURI().toString())

        when:
        def failed = runner(consumer, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains(pomOnly ? 'environment-schema-1.0-gdsl.jar' : "requested capability: feature 'gdsl'")
        !payload().exists()

        where:
        pomOnly << [false, true]
    }

    def "metadata for an excluded Schema is not accepted without a normal selection"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema'", '')
        new File(consumer, 'build.gradle') << "\nconfigurations.compileClasspath.exclude group: 'org.example', module: 'environment-schema'\n"

        when:
        def failed = runner(consumer, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains('No selected normal Schema for requested GDSL')
        !payload().exists()
    }

    def "module identity substitution follows the normal selected binary origin and caches it"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema'", '')
        new File(consumer, 'build.gradle') << """
configurations.configureEach {
    resolutionStrategy.dependencySubstitution {
        substitute module('org.example:environment-schema') using module('org.example:replacement-schema:2.0')
    }
}
"""

        when:
        def first = runner(consumer, 'materializeKlumDslGdsl', '--configuration-cache').build()
        def reused = runner(consumer, 'materializeKlumDslGdsl', '--configuration-cache').build()

        then:
        first.output.contains('replacement-schema-2.0-gdsl.jar:org.example:replacement-schema:2.0')
        reused.output.contains('Configuration cache entry reused.')
        !payload().exists()
        new File(consumer, 'build/generated/klum-dsl-ide/gdsl/schema-owned/' +
                'org.example'.bytes.encodeHex() + '/' + 'replacement-schema'.bytes.encodeHex() + '/environment.gdsl').file
    }

    def "opt-in materializes only the explicitly selected Schema even if other Schemas have metadata"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema'", "api 'org.example:replacement-schema:2.0'")

        when:
        def result = runner(consumer, 'materializeKlumDslGdsl').build()

        then:
        result.output.contains('environment-schema-1.0-gdsl.jar:org.example:environment-schema:1.0')
        !result.output.contains('replacement-schema-2.0-gdsl.jar')
        payload().file
    }

    def "POM-only classifier resolution reuses the configuration cache"() {
        given:
        fixture("klumGdsl 'org.example:environment-schema:1.0:gdsl@jar'", '', true)

        when:
        runner(consumer, 'materializeKlumDslGdsl', '--configuration-cache').build()
        def reused = runner(consumer, 'materializeKlumDslGdsl', '--configuration-cache').build()

        then:
        reused.output.contains('Configuration cache entry reused.')
        reused.task(':materializeKlumDslGdsl').outcome == TaskOutcome.UP_TO_DATE
        payload().file
    }

    def "GMM must supply exactly one metadata archive per selected Schema with #count advertised payloads"() {
        given:
        File isolated = new File(directory, 'repository')
        repository.eachFileRecurse { file ->
            if (file.file) {
                File copy = new File(isolated, repository.relativePath(file))
                copy.parentFile.mkdirs()
                copy.bytes = file.bytes
            }
        }
        File artifacts = new File(isolated, 'org/example/environment-schema/1.0')
        File moduleFile = new File(artifacts, 'environment-schema-1.0.module')
        def module = new JsonSlurper().parse(moduleFile)
        def metadata = module.variants.find { it.name == 'klumGdslElements' }
        if (count == 0) {
            metadata.files = []
        } else {
            def second = new LinkedHashMap(metadata.files.first())
            second.name = 'second-gdsl.jar'
            second.url = 'second-gdsl.jar'
            new File(artifacts, 'second-gdsl.jar').bytes = new File(artifacts, 'environment-schema-1.0-gdsl.jar').bytes
            metadata.files << second
        }
        moduleFile.text = JsonOutput.toJson(module)
        fixture("klumGdsl 'org.example:environment-schema'", '')
        File build = new File(consumer, 'build.gradle')
        build.text = build.text.replace(repository.toURI().toString(), isolated.toURI().toString())

        when:
        def failed = runner(consumer, 'materializeKlumDslGdsl').buildAndFail()

        then:
        failed.output.contains('requires exactly one metadata archive for org.example:environment-schema:1.0; found ' + count)
        !payload().exists()

        where:
        count << [0, 2]
    }

    def "binary Schemas with identical archive basenames retain independent identities in #mode mode"() {
        given:
        String selections = ['org.one', 'org.two'].withIndex().collect { group, index ->
            String classifier = mode == 'classifier' || (mode == 'mixed' && index == 1) ? ':1.0:gdsl@jar' : ''
            "klumGdsl '$group:shared-schema$classifier'"
        }.join('; ')
        fixture(selections, '', mode == 'classifier', false)
        File build = new File(consumer, 'build.gradle')
        build.text = build.text.replace("schema 'org.example:environment-schema:1.0'",
                "schema 'org.one:shared-schema:1.0'; schema 'org.two:shared-schema:1.0'")
        build << """
tasks.register('reportOrigins') {
    def materialization = tasks.named('materializeKlumDslGdsl')
    inputs.property('normalOrigins', materialization.flatMap { it.normalSchemaCoordinates })
    inputs.property('metadataOrigins', materialization.flatMap { it.schemaMetadataArtifacts }.map { artifacts ->
        artifacts.collect { it.archive.name + ':' + it.coordinates }
    })
    doLast {
        println "normalOrigins=" + inputs.properties.normalOrigins
        inputs.properties.metadataOrigins.each { println it }
    }
}
"""
        new File(consumer, 'settings.gradle') << "\nbuildCache { local { directory = '${new File(directory, 'identity-cache').absolutePath}' } }\n"

        when:
        def first = runner(consumer, 'materializeKlumDslGdsl', 'reportOrigins', '--configuration-cache', '--build-cache').build()
        def reused = runner(consumer, 'materializeKlumDslGdsl', 'reportOrigins', '--configuration-cache', '--build-cache').build()

        then:
        first.task(':materializeKlumDslGdsl').outcome == TaskOutcome.SUCCESS
        first.output.contains('normalOrigins=[org.one:shared-schema:1.0, org.two:shared-schema:1.0]')
        first.output.readLines().findAll { it.startsWith('shared-schema-1.0-gdsl.jar:') }.sort() == [
                'shared-schema-1.0-gdsl.jar:org.one:shared-schema:1.0',
                'shared-schema-1.0-gdsl.jar:org.two:shared-schema:1.0'
        ]
        reused.output.contains('Configuration cache entry reused.')
        reused.task(':materializeKlumDslGdsl').outcome == TaskOutcome.UP_TO_DATE
        assertSharedPayloads(consumer)

        when: 'both the Model directory and resolved archive locations move'
        File relocated = new File(directory, 'relocated-model')
        relocated.mkdirs()
        File relocatedRepository = new File(directory, 'relocated-repository')
        repository.eachFileRecurse { file ->
            if (file.file) {
                File copy = new File(relocatedRepository, repository.relativePath(file))
                copy.parentFile.mkdirs()
                copy.bytes = file.bytes
            }
        }
        ['settings.gradle', 'build.gradle'].each { name ->
            new File(relocated, name).text = new File(consumer, name).text.replace(
                    repository.toURI().toString(), relocatedRepository.toURI().toString())
        }
        def restored = runner(relocated, 'materializeKlumDslGdsl', 'reportOrigins', '--configuration-cache', '--build-cache')
                .withTestKitDir(new File(directory, 'relocated-gradle-home')).build()

        then:
        restored.task(':materializeKlumDslGdsl').outcome == TaskOutcome.FROM_CACHE
        assertSharedPayloads(relocated)

        where:
        mode << ['GMM', 'classifier', 'mixed']
    }

    private static boolean assertSharedPayloads(File model) {
        File root = new File(model, 'build/generated/klum-dsl-ide/gdsl/schema-owned')
        ['org.one', 'org.two'].withIndex().each { group, index ->
            File payload = new File(root, group.bytes.encodeHex().toString() + '/' +
                    'shared-schema'.bytes.encodeHex() + '/environment.gdsl')
            String suffix = index == 0 ? '.one.groovy' : '.two.groovy'
            String type = index == 0 ? 'example.FirstEnvironment' : 'example.SecondEnvironment'
            assert payload.text == KlumGdslMetadataFormat.payload(new KlumGdslMetadataFormat.Mapping('environment', suffix, type))
        }
        List<File> payloads = []
        root.eachFileRecurse { if (it.file) payloads << it }
        assert payloads.size() == 2
        true
    }

    private void fixture(String selection, String normalExtra, boolean pomOnly = false, boolean logOrigins = true) {
        new File(consumer, 'build.gradle').text = """
plugins { id 'com.blackbuild.klum-ast-model'; id 'maven-publish' }
group = 'org.example.model'
version = '1.0'
groovyDependencies.useSpock = false
['api', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
repositories { maven { url = '${repository.toURI()}'; ${pomOnly ? 'metadataSources { mavenPom(); artifact(); ignoreGradleMetadataRedirection() }' : ''} } }
klumModel {
    schemas { schema 'org.example:environment-schema:1.0' }
    gdsl { enabled = true }
}
dependencies { $selection; $normalExtra }
publishing.repositories { maven { name = 'fixture'; url = layout.buildDirectory.dir('repository') } }
${logOrigins ? "gradle.projectsEvaluated { tasks.named('materializeKlumDslGdsl') { doLast { schemaMetadataArtifacts.get().each { artifact -> println artifact.archive.name + ':' + artifact.coordinates } } } }" : ''}

tasks.register('assertIsolation') {
    doLast {
        assert java.sourceSets*.name.sort() == ['main', 'test']
        assert !java.sourceSets.main.allSource.srcDirs.any { it.path.contains('klum-dsl-ide') }
        ['compileClasspath', 'runtimeClasspath', 'testCompileClasspath', 'testRuntimeClasspath'].each { name ->
            assert !configurations[name].hierarchy.any { it.name.startsWith('klumGdsl') }
            assert !configurations[name].files.any { it.name.endsWith('-gdsl.jar') }
        }
        assert !configurations.api.allDependencies.any { it.artifacts.any { it.classifier == 'gdsl' } }
        def gdsl = layout.buildDirectory.dir('generated/klum-dsl-ide/gdsl').get().asFile
        assert idea.module.resourceDirs.contains(gdsl)
        assert idea.module.generatedSourceDirs.contains(gdsl)
        assert !tasks.names.contains('createKlumDslSourceMirrors')
    }
}
"""
    }

    private File payload() {
        new File(consumer, 'build/generated/klum-dsl-ide/gdsl/schema-owned/' +
                'org.example'.bytes.encodeHex() + '/' + 'environment-schema'.bytes.encodeHex() + '/environment.gdsl')
    }

    private static GradleRunner runner(File project, String... arguments) {
        GradleRunner.create().withProjectDir(project).withPluginClasspath()
                .withDebug(!arguments.toList().contains('--configuration-cache'))
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain', '--max-workers=2'])
    }
}
