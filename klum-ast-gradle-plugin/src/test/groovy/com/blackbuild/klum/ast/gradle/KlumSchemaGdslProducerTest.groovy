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
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.See
import spock.lang.TempDir
import spock.lang.Unroll

import java.security.MessageDigest
import java.util.jar.JarFile

@Issue('805')
class KlumSchemaGdslProducerTest extends Specification {
    @TempDir File directory
    File projectDir
    boolean standaloneGradle

    def setup() {
        projectDir = new File(directory, 'producer')
        projectDir.mkdirs()
    }

    @Unroll
    def "publication opt-in #enabled preserves normal artifacts and graphs with publish first #publishFirst"() {
        given:
        fixture(enabled, publishFirst)

        when:
        def result = run('publishMavenJavaPublicationToFixtureRepository', 'assertProducerIsolation')
        File repository = new File(projectDir, 'build/repository/org/example/environment-schema/1.2.0')
        def module = new JsonSlurper().parse(new File(repository, 'environment-schema-1.2.0.module'))
        def pom = new XmlSlurper().parse(new File(repository, 'environment-schema-1.2.0.pom'))
        def variant = module.variants.find { it.name == 'klumGdslElements' }

        then:
        result.task(':materializeKlumDslGdsl') == null
        result.task(':createKlumDslSourceMirrors') == null
        result.task(':generateKlumGdslMetadata')?.outcome == (enabled ? TaskOutcome.SUCCESS : null)
        pom.dependencies.dependency*.artifactId*.text() == ['normal-api']
        repository.listFiles().findAll { it.name.endsWith('.jar') }.size() == (enabled ? 4 : 3)
        ['environment-schema-1.2.0.jar', 'environment-schema-1.2.0-sources.jar', 'environment-schema-1.2.0-javadoc.jar'].each {
            new JarFile(new File(repository, it)).withCloseable { jar ->
                assert !jar.entries().toList()*.name.any { it.endsWith('.gdsl') || it.endsWith('mappings.json') }
            }
        }
        if (enabled) {
            assert variant.attributes == [
                    'artifactType': 'jar',
                    'org.gradle.category': 'documentation',
                    'org.gradle.dependency.bundling': 'external',
                    'org.gradle.docstype': 'klum-gdsl',
                    'org.gradle.usage': 'klum-ide-metadata']
            assert variant.capabilities == [[group: 'org.example', name: 'environment-schema-gdsl', version: '1.2.0']]
            assert !variant.dependencies
            assert !variant.dependencyConstraints
            assert variant.files*.name == ['environment-schema-1.2.0-gdsl.jar']
            assertEnvelope(new File(repository, 'environment-schema-1.2.0-gdsl.jar'), false)
        } else {
            assert variant == null
        }

        where:
        enabled | publishFirst
        false   | false
        false   | true
        true    | false
        true    | true
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/implementation/adr-0025-portable-schema-gdsl-metadata.md#api-ledger-and-authoring-example')
    def "metadata generation is lazy reproducible relocatable cacheable and supports empty retirement"() {
        given:
        standaloneGradle = true
        fixture(true, false)

        when:
        def ordinary = run('jar')
        def first = run('klumGdslJar', '--build-cache', '--configuration-cache')
        File archive = new File(projectDir, 'build/libs/environment-schema-1.2.0-gdsl.jar')
        byte[] bytes = archive.bytes
        def reused = run('klumGdslJar', '--build-cache', '--configuration-cache')

        then:
        ordinary.task(':generateKlumGdslMetadata') == null
        first.task(':compileJava') == null
        first.task(':compileGroovy') == null
        first.task(':generateKlumGdslMetadata').outcome == TaskOutcome.SUCCESS
        reused.output.contains('Configuration cache entry reused.')
        reused.task(':generateKlumGdslMetadata').outcome == TaskOutcome.UP_TO_DATE

        when:
        new File(projectDir, 'build').deleteDir()
        def restored = run('klumGdslJar', '--build-cache')

        then:
        restored.task(':generateKlumGdslMetadata').outcome == TaskOutcome.FROM_CACHE
        restored.task(':klumGdslJar').outcome == TaskOutcome.FROM_CACHE
        archive.bytes == bytes

        when:
        File original = projectDir
        projectDir = new File(directory, 'relocated')
        projectDir.mkdirs()
        ['build.gradle', 'settings.gradle'].each { new File(projectDir, it).bytes = new File(original, it).bytes }
        def relocated = run('klumGdslJar', '--build-cache')

        then:
        relocated.task(':generateKlumGdslMetadata').outcome == TaskOutcome.FROM_CACHE
        new File(projectDir, 'build/libs/environment-schema-1.2.0-gdsl.jar').bytes == bytes

        when:
        run('klumGdslJar', '--rerun-tasks', '--no-build-cache')

        then:
        new File(projectDir, 'build/libs/environment-schema-1.2.0-gdsl.jar').bytes == bytes

        when:
        new File(projectDir, 'build.gradle').text = new File(projectDir, 'build.gradle').text.replace(declaration(), '')
        def empty = run('klumGdslJar')

        then:
        empty.task(':generateKlumGdslMetadata').outcome == TaskOutcome.SUCCESS
        assertEnvelope(new File(projectDir, 'build/libs/environment-schema-1.2.0-gdsl.jar'), true)
    }

    def "mapping declaration order cannot change the canonical metadata archive"() {
        given: 'otherwise identical producers in independent directories with reversed declarations'
        String environment = declaration()
        String deployment = "deployment { fileNameSuffix = '.deployment.groovy'; modelType = 'example.Deployment' }"
        File forwardProducer = projectDir
        fixture(true, false, environment + "\n" + deployment)
        projectDir = new File(directory, 'reverse-producer')
        projectDir.mkdirs()
        fixture(true, false, deployment + "\n" + environment)

        when: 'each producer generates its own archive without using the build cache'
        def forward = runner('klumGdslJar', '--no-build-cache').withProjectDir(forwardProducer).build()
        def reverse = run('klumGdslJar', '--no-build-cache')
        String archivePath = 'build/libs/environment-schema-1.2.0-gdsl.jar'
        File forwardArchive = new File(forwardProducer, archivePath)
        File reverseArchive = new File(projectDir, archivePath)

        then: 'both generators and archives execute independently and produce identical complete JARs'
        forward.task(':generateKlumGdslMetadata').outcome == TaskOutcome.SUCCESS
        reverse.task(':generateKlumGdslMetadata').outcome == TaskOutcome.SUCCESS
        forward.task(':klumGdslJar').outcome == TaskOutcome.SUCCESS
        reverse.task(':klumGdslJar').outcome == TaskOutcome.SUCCESS
        forwardArchive.bytes == reverseArchive.bytes

        and: 'published catalog and payload bytes have canonical IDs and identical verified hashes'
        new JarFile(forwardArchive).withCloseable { forwardJar ->
            new JarFile(reverseArchive).withCloseable { reverseJar ->
                String envelope = 'META-INF/klum-ide/gdsl/v1/'
                byte[] forwardCatalog = forwardJar.getInputStream(forwardJar.getJarEntry(envelope + 'mappings.json')).bytes
                byte[] reverseCatalog = reverseJar.getInputStream(reverseJar.getJarEntry(envelope + 'mappings.json')).bytes
                assert forwardCatalog == reverseCatalog
                def forwardMappings = new JsonSlurper().parse(forwardCatalog).mappings
                def reverseMappings = new JsonSlurper().parse(reverseCatalog).mappings
                assert forwardMappings*.id == ['deployment', 'environment']
                assert reverseMappings*.id == ['deployment', 'environment']
                assert forwardMappings*.payloadSha256 == reverseMappings*.payloadSha256
                forwardMappings.each { mapping ->
                    String entry = envelope + mapping.id + '.gdsl'
                    byte[] forwardPayload = forwardJar.getInputStream(forwardJar.getJarEntry(entry)).bytes
                    byte[] reversePayload = reverseJar.getInputStream(reverseJar.getJarEntry(entry)).bytes
                    assert forwardPayload == reversePayload
                    assert mapping.payloadSha256 == MessageDigest.getInstance('SHA-256').digest(forwardPayload).encodeHex().toString()
                }
                true
            }
        }
    }

    @Unroll
    def "publication rejects #field identity override"() {
        given:
        fixture(true, false)
        new File(projectDir, 'build.gradle') << "\nafterEvaluate { publishing.publications.mavenJava.$field = 'other' }\n"

        when:
        def failure = runner('publishMavenJavaPublicationToFixtureRepository').buildAndFail()

        then:
        failure.output.contains('GDSL mavenJava identity must match Schema project GAV org.example:environment-schema:1.2.0')
        !new File(projectDir, 'build/repository').exists()
        failure.task(':generatePomFileForMavenJavaPublication') == null
        failure.task(':generateMetadataFileForMavenJavaPublication') == null

        where:
        field << ['groupId', 'artifactId', 'version']
    }

    def "producer without Maven Publish still creates a metadata-only archive"() {
        given:
        fixture(true, false)
        File build = new File(projectDir, 'build.gradle')
        build.text = build.text.replace("id 'maven-publish'", '').replace("publishing { repositories { maven { name = 'fixture'; url = layout.buildDirectory.dir('repository') } } }", '')

        when:
        def result = run('klumGdslJar')

        then:
        result.task(':generateKlumGdslMetadata').outcome == TaskOutcome.SUCCESS
        assertEnvelope(new File(projectDir, 'build/libs/environment-schema-1.2.0-gdsl.jar'), false)
    }

    def "custom capability is selectable while attribute-poor normal and documentation requests exclude metadata"() {
        given:
        fixture(true, false)
        new File(projectDir, 'settings.gradle') << "\ninclude 'probe'\n"
        File probe = new File(projectDir, 'probe')
        probe.mkdirs()
        new File(probe, 'build.gradle').text = '''
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.DocsType
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.Bundling
['ordinary', 'sources', 'javadocs', 'metadata'].each { name ->
    configurations.create(name) {
        canBeConsumed = false
        transitive = false
    }
    dependencies.add(name, dependencies.project(path: ':')) {
        if (name == 'metadata') capabilities { requireCapability('org.example:environment-schema-gdsl') }
    }
}
['sources': 'sources', 'javadocs': 'javadoc', 'metadata': 'klum-gdsl'].each { name, docs ->
    configurations[name].attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category, 'documentation'))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType, docs))
        if (name == 'metadata') {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'klum-ide-metadata'))
            attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling, 'external'))
        }
    }
}
tasks.register('probeVariants') {
    dependsOn configurations.ordinary, configurations.sources, configurations.javadocs, configurations.metadata
    doLast {
        assert configurations.ordinary.files*.name == ['environment-schema-1.2.0.jar']
        assert configurations.sources.files*.name == ['environment-schema-1.2.0-sources.jar']
        assert configurations.javadocs.files*.name == ['environment-schema-1.2.0-javadoc.jar']
        assert configurations.metadata.files*.name == ['environment-schema-1.2.0-gdsl.jar']
    }
}
'''

        when:
        def result = run(':probe:probeVariants')

        then:
        result.task(':probe:probeVariants').outcome == TaskOutcome.SUCCESS
        result.task(':klumGdslJar').outcome == TaskOutcome.SUCCESS
    }

    def "producer treats a malicious-looking suffix as data and never evaluates supplied code"() {
        given:
        fixture(true, false)
        File build = new File(projectDir, 'build.gradle')
        build.text = build.text.replace("'.environment.groovy'", '"\'; new File(\'supplied-code-ran\').text=\'x\'; .groovy"')

        when:
        def result = run('klumGdslJar')
        File payload = new File(projectDir, 'build/generated/klum-gdsl-metadata/META-INF/klum-ide/gdsl/v1/environment.gdsl')

        then:
        result.task(':generateKlumGdslMetadata').outcome == TaskOutcome.SUCCESS
        !new File(projectDir, 'supplied-code-ran').exists()
        payload.text.contains("new File(\\'supplied-code-ran\\')")
    }

    @Unroll
    def "producer rejects invalid or overlapping configured mappings #configuration"() {
        given:
        fixture(true, false)
        new File(projectDir, 'build.gradle') << "\nklumSchema.gdsl.mappings { $configuration }\n"

        when:
        def failure = runner('klumGdslJar').buildAndFail()

        then:
        failure.output.contains(reason)
        failure.task(':generateKlumGdslMetadata') == null

        where:
        configuration                                                                 | reason
        "other { fileNameSuffix = '.groovy'; modelType = 'example.Environment' }"        | 'Overlapping GDSL mappings'
        "environment { modelType = 'example.Environment_DSL.Builder' }"                 | 'modelType must be a Model'
        "environment { fileNameSuffix = '../escape.groovy' }"                           | 'fileNameSuffix must be a literal'
        "create('environment')"                                                        | "already exists"
    }

    def "late Schema identity changes are reflected consistently in manifest variant and publication"() {
        given:
        fixture(true, false)
        new File(projectDir, 'build.gradle') << "\nafterEvaluate { group = 'org.changed'; version = '2.0.0' }\n"

        when:
        run('publishMavenJavaPublicationToFixtureRepository', '--configuration-cache')
        def reused = run('publishMavenJavaPublicationToFixtureRepository', '--configuration-cache')
        File repository = new File(projectDir, 'build/repository/org/changed/environment-schema/2.0.0')
        def module = new JsonSlurper().parse(new File(repository, 'environment-schema-2.0.0.module'))
        def metadata = module.variants.find { it.name == 'klumGdslElements' }

        then:
        reused.output.contains('Configuration cache entry reused.')
        module.component.group == 'org.changed'
        module.component.version == '2.0.0'
        metadata.capabilities == [[group: 'org.changed', name: 'environment-schema-gdsl', version: '2.0.0']]
        new JarFile(new File(repository, 'environment-schema-2.0.0-gdsl.jar')).withCloseable { jar ->
            assert jar.manifest.mainAttributes.getValue('Klum-Schema-Coordinates') == 'org.changed:environment-schema:2.0.0'
            true
        }
    }

    def "late Schema identity changes cannot disguise a publication override"() {
        given:
        fixture(true, false)
        new File(projectDir, 'build.gradle') << """
afterEvaluate {
    group = 'org.changed'
    version = '2.0.0'
    publishing.publications.mavenJava {
        groupId = 'org.example'
        version = '1.2.0'
    }
}
"""

        when:
        def failure = runner('publishMavenJavaPublicationToFixtureRepository').buildAndFail()

        then:
        failure.output.contains('GDSL mavenJava identity must match Schema project GAV org.changed:environment-schema:2.0.0')
        failure.task(':generatePomFileForMavenJavaPublication') == null
        failure.task(':generateMetadataFileForMavenJavaPublication') == null
        !new File(projectDir, 'build/repository').exists()
    }

    private void fixture(boolean enabled, boolean publishFirst, String mappingDeclarations = declaration()) {
        new File(projectDir, 'settings.gradle').text = """
rootProject.name = 'environment-schema'
buildCache { local { directory = '${new File(directory, 'cache').absolutePath}' } }
"""
        String plugins = publishFirst ? "id 'maven-publish'; id 'com.blackbuild.klum-ast-schema'" :
                "id 'com.blackbuild.klum-ast-schema'; id 'maven-publish'"
        new File(projectDir, 'build.gradle').text = """
plugins { $plugins }
group = 'org.example'
version = '1.2.0'
// Producer-only fixture: no compiler/runtime dependencies or remote repository.
['api', 'compileOnly', 'testImplementation', 'groovy'].each { configurations[it].dependencies.clear() }
dependencies { api 'org.example:normal-api:2.0' }
tasks.named('compileJava') { classpath = files() }
tasks.named('compileGroovy') { classpath = files(); groovyClasspath = files(); astTransformationClasspath.setFrom(files()) }
tasks.named('javadoc') { classpath = files() }
tasks.named('createClassStubs') { referencedClassesClasspath.setFrom(files()) }
klumSchema { gdsl { ${enabled ? 'publish = true' : ''}; mappings { ${mappingDeclarations} } } }
publishing { repositories { maven { name = 'fixture'; url = layout.buildDirectory.dir('repository') } } }
tasks.register('assertProducerIsolation') {
    doLast {
        def metadata = configurations.findByName('klumGdslElements')
        if (metadata != null) {
            assert metadata.canBeConsumed && !metadata.canBeResolved && !metadata.canBeDeclared
            assert metadata.extendsFrom.empty
            assert metadata.allDependencies.empty
            assert metadata.allDependencyConstraints.empty
            assert metadata.outgoing.capabilities*.name == ['environment-schema-gdsl']
        }
        ['apiElements', 'runtimeElements', 'sourcesElements', 'javadocElements'].each {
            assert !configurations[it].outgoing.artifacts.any { it.classifier == 'gdsl' }
        }
        ['compileClasspath', 'runtimeClasspath', 'testCompileClasspath', 'testRuntimeClasspath'].each {
            assert !configurations[it].hierarchy.contains(metadata)
        }
        assert java.sourceSets*.name.sort() == ['main', 'test']
        assert !java.sourceSets.main.allSource.srcDirs.any { it.path.contains('klum-gdsl-metadata') }
    }
}
"""
        File source = new File(projectDir, 'src/main/java/example/Environment.java')
        source.parentFile.mkdirs()
        source.text = 'package example; /** Producer fixture. */ public class Environment {}'
    }

    private static String declaration() {
        "environment { fileNameSuffix = '.environment.groovy'; modelType = 'example.Environment' }"
    }

    private static boolean assertEnvelope(File archive, boolean empty) {
        new JarFile(archive).withCloseable { jar ->
            assert jar.manifest.mainAttributes.getValue('Klum-Schema-Coordinates') == 'org.example:environment-schema:1.2.0'
            assert jar.manifest.mainAttributes.getValue('Klum-Gdsl-Format') == '1'
            String root = 'META-INF/klum-ide/gdsl/v1/'
            def entries = jar.entries().toList().findAll { !it.directory }*.name.sort()
            assert entries == (['META-INF/MANIFEST.MF', root + 'mappings.json'] + (empty ? [] : [root + 'environment.gdsl'])).sort()
            def catalog = new JsonSlurper().parse(jar.getInputStream(jar.getJarEntry(root + 'mappings.json')))
            if (empty) {
                assert catalog.mappings.empty
            } else {
                def mapping = catalog.mappings.first()
                byte[] payload = jar.getInputStream(jar.getJarEntry(root + 'environment.gdsl')).bytes
                assert catalog.mappings.size() == 1
                assert mapping.id == 'environment'
                assert mapping.modelType == 'example.Environment'
                assert mapping.fileNameSuffix == '.environment.groovy'
                assert mapping.payloadSha256 == MessageDigest.getInstance('SHA-256').digest(payload).encodeHex().toString()
            }
        }
        true
    }

    private def run(String... arguments) {
        runner(arguments).build()
    }

    private GradleRunner runner(String... arguments) {
        // Record coverage in the test worker, retaining standalone Gradle JVMs for configuration-cache controls.
        GradleRunner.create().withProjectDir(projectDir).withPluginClasspath()
                .withDebug(!standaloneGradle && !arguments.toList().contains('--configuration-cache'))
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain', '--max-workers=2'])
    }
}
