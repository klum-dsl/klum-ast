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

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.TempDir
import spock.lang.Unroll

@Issue('903')
class ProtectedPublicationTest extends Specification {
    @TempDir File fixture
    @Shared @TempDir File testKitDirectory

    def setup() {
        write('settings.gradle', "rootProject.name = 'protected-publication'; include 'klum-ast-gradle-plugin', 'local-fixture'")
        String production = new File(System.getProperty('klum.releaseBuild')).text
        write('protected-publication.gradle', '''
            import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
            import org.gradle.plugins.signing.Sign
        '''.stripIndent() + production.substring(production.indexOf('def releaseVersion ='),
                production.indexOf("if (hasProperty('buildScan'))")))
        write('build.gradle', '''
            plugins { id 'base' }
            version = '4.1.0-dev'
            tasks.register('verifyVersionedDocumentationRenderer') {
                doLast {
                    if (project.hasProperty('failDocumentation')) throw new GradleException('Rejected documentation')
                    file('documentation-passed').text = 'passed'
                }
            }
            tasks.named('check') {
                dependsOn 'verifyVersionedDocumentationRenderer'
                doLast { if (project.hasProperty('failVerification')) throw new GradleException('Rejected verification') }
            }
            ['initializeSonatypeStagingRepository', 'closeSonatypeStagingRepository',
             'releaseSonatypeStagingRepository', 'closeAndReleaseSonatypeStagingRepository'].each { name ->
                tasks.register(name) {
                    doLast {
                        if (project.hasProperty('failClose') && name == 'closeSonatypeStagingRepository')
                            throw new GradleException('Rejected staging close')
                        file("protected-$name").text = 'executed'
                    }
                }
            }
            tasks.named('closeAndReleaseSonatypeStagingRepository') {
                dependsOn 'closeSonatypeStagingRepository', 'releaseSonatypeStagingRepository'
            }
            evaluationDependsOnChildren()
            apply from: 'protected-publication.gradle'
            tasks.register('alternativePublication') {
                dependsOn ':klum-ast-gradle-plugin:publishToSonatype', ':klum-ast-gradle-plugin:publishPlugins'
            }
            tasks.named('verifyReleaseVersion') { doLast { file('version-passed').text = 'passed' } }
            tasks.named('verifyProtectedReleaseAuthorization') { doLast { file('authorization-passed').text = 'passed' } }
            allprojects {
                tasks.configureEach {
                    if (name in ['initializeSonatypeStagingRepository', 'closeSonatypeStagingRepository',
                                 'releaseSonatypeStagingRepository', 'publishPlugins', 'signMainPublication'] ||
                            name == 'publishMainPublicationToSonatypeRepository') {
                        doLast {
                            ['version-passed', 'documentation-passed', 'authorization-passed'].each { prerequisite ->
                                assert rootProject.file(prerequisite).file
                            }
                        }
                    }
                }
            }
        ''')
        write('klum-ast-gradle-plugin/build.gradle', '''
            import java.net.URI
            import org.gradle.plugins.signing.Sign
            import org.gradle.plugins.signing.signatory.Signatory
            plugins { id 'java-library'; id 'maven-publish'; id 'signing' }
            group = 'fixture'
            publishing {
                publications { main(MavenPublication) { from components.java } }
                repositories {
                    maven { name = 'sonatype'; url = uri('https://publication.invalid/releases') }
                    maven { name = 'development'; url = uri(layout.buildDirectory.dir('development')) }
                }
            }
            if (project.hasProperty('redirectDevelopment')) publishing.repositories.development.url = uri('https://publication.invalid/releases')
            if (project.hasProperty('redirectSonatypeToFile')) publishing.repositories.sonatype.url = uri(layout.buildDirectory.dir('pretend-local'))
            if (project.hasProperty('networkFileDevelopment')) {
                publishing.repositories.development.url = URI.create('file://publication.invalid/releases')
                assert publishing.repositories.development.url.authority == 'publication.invalid'
            }
            if (project.hasProperty('uppercaseDevelopment')) publishing.repositories.development.url =
                    URI.create(publishing.repositories.development.url.toString().replaceFirst('^file:', 'FILE:'))
            // The real repository signing configuration is appended below, after task customization.
            tasks.withType(Sign).configureEach {
                if (project.hasProperty('fixtureSigning')) {
                    signatory = [getName: { 'synthetic' }, getKeyId: { 'synthetic' },
                                 sign: { input, output -> throw new AssertionError('Real signing is forbidden') }] as Signatory
                    actions.clear()
                    doLast { rootProject.file('protected-sign').text = 'executed' }
                }
            }
            tasks.register('publishToSonatype') {
                dependsOn 'publishMainPublicationToSonatypeRepository'
            }
            tasks.register('publishPlugins') {
                doLast { rootProject.file('protected-portal').text = 'executed' }
            }
            tasks.withType(PublishToMavenRepository).configureEach {
                if (repository.name == 'sonatype') dependsOn rootProject.tasks.named('initializeSonatypeStagingRepository')
                actions.clear()
                doLast {
                    if (project.hasProperty('failUpload')) throw new GradleException('Rejected upload')
                    boolean local = repository.name != 'sonatype' && repository.url.scheme.equalsIgnoreCase('file') && repository.url.authority == null
                    rootProject.file(local ? 'local-upload' : 'protected-upload').text = 'executed'
                }
            }
            tasks.withType(PublishToMavenLocal).configureEach {
                actions.clear()
                doLast { rootProject.file('local-maven').text = 'executed' }
            }
        ''')
        String baseConventions = new File(System.getProperty('klum.baseConventions')).text
        new File(fixture, 'klum-ast-gradle-plugin/build.gradle') << baseConventions.substring(
                baseConventions.indexOf('afterEvaluate {'), baseConventions.lastIndexOf('\nlicense {'))
        write('local-fixture/build.gradle', '''
            import org.gradle.plugins.signing.Sign
            import org.gradle.plugins.signing.signatory.Signatory
            plugins { id 'signing'; id 'maven-publish' }
            publishing { repositories { maven { url = uri(layout.buildDirectory.dir('fixture-repository')) } } }
            tasks.register('signFixture', Sign) {
                signatory = [getName: { 'disposable' }, getKeyId: { 'disposable' },
                             sign: { input, output -> throw new AssertionError('Real signing is forbidden') }] as Signatory
                sign file('payload')
                actions.clear()
                doLast { rootProject.file('local-sign').text = 'executed' }
            }
        ''')
        write('local-fixture/payload', 'disposable fixture input')
    }

    def 'direct staging initialization rejects before its irreversible action'() {
        when:
        def result = runner('initializeSonatypeStagingRepository').buildAndFail()

        then:
        result.output.contains('Use publishCompleteKlumAstProduct')
        protectedMarkers().empty
    }

    @Unroll
    def 'direct protected operation #operation rejects before its action'() {
        when:
        def result = runner(operation, '-PfixtureSigning', '--continue', '--parallel').buildAndFail()

        then:
        result.output.contains('Use publishCompleteKlumAstProduct')
        protectedMarkers().empty

        where:
        operation << [':klum-ast-gradle-plugin:signMainPublication',
                      ':klum-ast-gradle-plugin:publishMainPublicationToSonatypeRepository',
                      ':klum-ast-gradle-plugin:publishToSonatype', 'publishMavenProductToSonatype',
                      'closeSonatypeStagingRepository', 'releaseSonatypeStagingRepository',
                      'closeAndReleaseSonatypeStagingRepository', ':klum-ast-gradle-plugin:publishPlugins',
                      'alternativePublication']
    }

    @Unroll
    def 'failed #prerequisite contains every protected action with execution options #execution'() {
        when:
        def result = runner(':klum-ast-gradle-plugin:publishPlugins', 'releaseSonatypeStagingRepository',
                ':klum-ast-gradle-plugin:publishMainPublicationToSonatypeRepository',
                ':klum-ast-gradle-plugin:signMainPublication', 'publishCompleteKlumAstProduct',
                *(['-PfixtureSigning', failure] + execution)).buildAndFail()

        then:
        result.output.contains(message)
        protectedMarkers().empty

        where:
        [prerequisite, failure, message, execution] << [
                ['authorization', '-Punauthorized', 'Protected final authorization is required'],
                ['version', '-Prelease.version=bad', 'Final versions must match'],
                ['documentation', '-PfailDocumentation', 'Rejected documentation'],
                ['verification', '-PfailVerification', 'Rejected verification']
        ].collectMany { row -> [[], ['--parallel'], ['--continue'], ['--parallel', '--continue']].collect { row + [it] } }
    }

    @Unroll
    def 'authorized #stage complete product executes protected actions after all prerequisites succeed'() {
        when:
        def result = runner(':klum-ast-gradle-plugin:publishPlugins', 'releaseSonatypeStagingRepository',
                'publishCompleteKlumAstProduct', '-PfixtureSigning', "-Prelease.stage=$stage".toString(),
                "-Prelease.version=$releaseVersion".toString(), '--parallel', '--continue').build()

        then:
        result.task(':verifyProtectedReleaseAuthorization').outcome.toString() == 'SUCCESS'
        protectedMarkers()*.name.containsAll(['protected-sign', 'protected-upload', 'protected-portal',
                'protected-initializeSonatypeStagingRepository', 'protected-closeSonatypeStagingRepository',
                'protected-releaseSonatypeStagingRepository'])

        where:
        stage       | releaseVersion
        'final'     | '4.1.0'
        'candidate' | '4.1.0-rc.1'
    }

    @Unroll
    def 'local development entry #entry works without release properties or authorization'() {
        when:
        def result = runner(entry, '-PlocalOnly').build()

        then:
        result.task(':verifyReleaseVersion') == null
        result.task(':verifyProtectedReleaseAuthorization') == null
        protectedMarkers().empty

        where:
        entry << [':klum-ast-gradle-plugin:publishToMavenLocal',
                  ':klum-ast-gradle-plugin:publishMainPublicationToDevelopmentRepository',
                  ':klum-ast-gradle-plugin:compileJava', ':klum-ast-gradle-plugin:jar',
                  ':klum-ast-gradle-plugin:generatePomFileForMainPublication', 'check', ':local-fixture:signFixture']
    }

    @Unroll
    def 'protected destination behind #redirection stays guarded'() {
        when:
        runner(operation, redirection, '-PlocalOnly', '--continue', '--parallel').buildAndFail()

        then:
        protectedMarkers().empty

        where:
        redirection                  | operation
        '-PredirectDevelopment'      | ':klum-ast-gradle-plugin:publishMainPublicationToDevelopmentRepository'
        '-PredirectSonatypeToFile'    | ':klum-ast-gradle-plugin:publishMainPublicationToSonatypeRepository'
        '-PnetworkFileDevelopment'   | ':klum-ast-gradle-plugin:publishMainPublicationToDevelopmentRepository'
    }

    def 'local file repository schemes remain case insensitive'() {
        when:
        def result = runner(':klum-ast-gradle-plugin:publishMainPublicationToDevelopmentRepository',
                '-PuppercaseDevelopment', '-PlocalOnly').build()

        then:
        result.task(':verifyProtectedReleaseAuthorization') == null
        new File(fixture, 'local-upload').file
        protectedMarkers().empty
    }

    @Unroll
    def 'shared protected key cannot be used by #entry without release prerequisites'() {
        when:
        runner(entry, '-PfixtureSigning', '-PlocalOnly', '--continue', '--parallel').buildAndFail()

        then:
        protectedMarkers().empty

        where:
        entry << [':klum-ast-gradle-plugin:signMainPublication', ':klum-ast-gradle-plugin:publishToMavenLocal',
                  ':klum-ast-gradle-plugin:publishMainPublicationToDevelopmentRepository']
    }

    def 'mixed local and protected graph cannot exempt a protected key or upload'() {
        when:
        def result = runner(':local-fixture:signFixture', ':klum-ast-gradle-plugin:publishToMavenLocal',
                'publishCompleteKlumAstProduct', '-PfixtureSigning', '-PfailDocumentation',
                '--continue', '--parallel').buildAndFail()

        then:
        result.task(':local-fixture:signFixture').outcome.toString() == 'SUCCESS'
        protectedMarkers().empty
    }

    @Unroll
    def 'production signingKey interface guards #entry even with only local destinations'() {
        given:
        new File(fixture, 'local-fixture/build.gradle') << '''
            // A keyless sentinel exercises the production interface without loading or parsing a key.
            ext.signingKey = 'synthetic-interface-sentinel'
            publishing.publications.create('fixture', MavenPublication) { artifact file('payload') }
            signing.sign(publishing.publications.fixture)
            tasks.named('signFixturePublication', Sign) {
                signatory = tasks.signFixture.signatory
                actions.clear()
                doLast { rootProject.file('protected-key-interface-sign').text = 'executed' }
            }
            tasks.withType(PublishToMavenLocal).configureEach {
                actions.clear()
                doLast { rootProject.file('local-maven').text = 'executed' }
            }
        '''.stripIndent()

        when:
        def result = runner(entry, '-PlocalOnly', '--parallel', '--continue').buildAndFail()

        then:
        result.output.contains('A release requires -Prelease.version')
        result.task(':local-fixture:signFixturePublication') == null
        !new File(fixture, 'local-maven').exists()
        protectedMarkers().empty

        where:
        entry << [':local-fixture:signFixturePublication', ':local-fixture:publishToMavenLocal']
    }

    @Unroll
    def 'independent disposable signing is conservatively guarded in a #infrastructure project'() {
        given:
        File localBuild = new File(fixture, 'local-fixture/build.gradle')
        if (infrastructure == 'Sonatype') {
            localBuild << '''
                publishing.repositories.maven {
                    name = 'sonatype'
                    url = uri('https://publication.invalid/releases')
                }
                assert publishing.publications.empty
            '''.stripIndent()
        } else {
            localBuild.text = localBuild.text.replace("plugins { id 'signing'", "plugins { id 'com.gradle.plugin-publish'; id 'signing'")
            localBuild << '''
                assert plugins.hasPlugin('com.gradle.plugin-publish')
                // Only the detached signing task may run; the real Portal client cannot execute.
                gradle.taskGraph.whenReady { graph -> assert !graph.hasTask(':local-fixture:publishPlugins') }
            '''.stripIndent()
        }
        localBuild << '''
            assert tasks.signFixture.signatory.name == 'disposable'
            assert tasks.signFixture.filesToSign.files == [file('payload')] as Set
            assert !project.hasProperty('signingKey')
        '''.stripIndent()
        def buildRunner = runner(':local-fixture:signFixture', '-PlocalOnly', '--parallel', '--continue')
        if (infrastructure == 'Plugin Portal')
            buildRunner.withPluginClasspath([new File(System.getProperty('klum.portalPlugin'))])

        when:
        def result = buildRunner.buildAndFail()

        then:
        result.output.contains('A release requires -Prelease.version')
        result.task(':local-fixture:signFixture') == null
        !new File(fixture, 'local-sign').exists()
        protectedMarkers().empty

        where:
        infrastructure << ['Sonatype', 'Plugin Portal']
    }

    def 'detached task sharing a protected publication signatory remains guarded'() {
        given:
        write('klum-ast-gradle-plugin/payload', 'detached fixture input')
        new File(fixture, 'klum-ast-gradle-plugin/build.gradle') << '''
            afterEvaluate {
                tasks.register('signDetached', Sign) {
                    signatory = tasks.signMainPublication.signatory
                    sign file('payload')
                    actions.clear()
                    doLast { rootProject.file('protected-detached-sign').text = 'executed' }
                }
                assert tasks.signDetached.signatory.is(tasks.signMainPublication.signatory)
            }
        '''.stripIndent()

        when:
        def result = runner(':klum-ast-gradle-plugin:signDetached', '-PfixtureSigning', '-PlocalOnly',
                '--parallel', '--continue').buildAndFail()

        then:
        result.output.contains('A release requires -Prelease.version')
        result.task(':klum-ast-gradle-plugin:signDetached') == null
        protectedMarkers().empty
    }

    @Unroll
    def 'failed #failure prevents later staging release and Portal actions under continue'() {
        when:
        runner('publishCompleteKlumAstProduct', '-PfixtureSigning', failure, '--parallel', '--continue').buildAndFail()

        then:
        !new File(fixture, 'protected-releaseSonatypeStagingRepository').exists()
        !new File(fixture, 'protected-portal').exists()

        where:
        failure << ['-PfailUpload', '-PfailClose']
    }

    private GradleRunner runner(String... arguments) {
        String authorizedStage = arguments.find { it.startsWith('-Prelease.stage=') }?.split('=', 2)?.last() ?: 'final'
        GradleRunner.create().withProjectDir(fixture).withTestKitDir(testKitDirectory)
                .withEnvironment([PATH: System.getenv('PATH'), JAVA_HOME: System.getProperty('java.home'),
                                  TMPDIR: System.getProperty('java.io.tmpdir'),
                                  KLUM_AST_RELEASE_AUTHORIZED: arguments.any { it in ['-Punauthorized', '-PlocalOnly'] } ? '' : authorizedStage])
                .withArguments((arguments.contains('-PlocalOnly') ? [] : ['-Prelease.version=4.1.0', '-Prelease.stage=final']) +
                        arguments.toList() + ['--offline', '--no-configuration-cache', '--no-build-cache', '--max-workers=2', '--stacktrace'])
    }

    private List<File> protectedMarkers() {
        fixture.listFiles().findAll { it.name.startsWith('protected-') && it.name != 'protected-publication.gradle' }
    }

    private void write(String path, String source) {
        File target = new File(fixture, path)
        target.parentFile.mkdirs()
        target.text = source.stripIndent()
    }
}
