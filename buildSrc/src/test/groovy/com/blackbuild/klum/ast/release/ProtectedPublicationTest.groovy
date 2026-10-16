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
package com.blackbuild.klum.ast.release

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

@Issue('903')
class ProtectedPublicationTest extends Specification {
    @TempDir File fixture

    def setup() {
        write('settings.gradle', "rootProject.name = 'protected-publication'; include 'klum-ast-gradle-plugin'")
        String production = new File(System.getProperty('klum.releaseBuild')).text
        write('protected-publication.gradle', production.substring(production.indexOf('def releaseVersion ='),
                production.indexOf("if (hasProperty('buildScan'))")))
        write('build.gradle', '''
            plugins { id 'base' }
            version = '4.1.0-dev'
            tasks.register('verifyVersionedDocumentationRenderer') {
                doLast {
                    if (findProperty('failDocumentation')) throw new GradleException('Rejected documentation')
                    file('documentation-passed').text = 'passed'
                }
            }
            tasks.named('check') { dependsOn 'verifyVersionedDocumentationRenderer' }
            ['initializeSonatypeStagingRepository', 'closeSonatypeStagingRepository',
             'releaseSonatypeStagingRepository', 'closeAndReleaseSonatypeStagingRepository'].each { name ->
                tasks.register(name) { doLast { file("protected-$name").text = 'executed' } }
            }
            tasks.named('closeAndReleaseSonatypeStagingRepository') {
                dependsOn 'closeSonatypeStagingRepository', 'releaseSonatypeStagingRepository'
            }
            evaluationDependsOnChildren()
            apply from: 'protected-publication.gradle'
        ''')
        write('klum-ast-gradle-plugin/build.gradle', '''
            plugins { id 'java-library'; id 'maven-publish'; id 'signing' }
            group = 'fixture'
            publishing {
                publications { main(MavenPublication) { from components.java } }
                repositories {
                    maven { name = 'sonatype'; url = uri('https://publication.invalid/releases') }
                    maven { name = 'development'; url = uri(layout.buildDirectory.dir('development')) }
                }
            }
            signing { required = false; sign publishing.publications.main }
            tasks.register('publishToSonatype') {
                dependsOn 'publishMainPublicationToSonatypeRepository'
            }
            tasks.register('publishPlugins') {
                doLast { rootProject.file('protected-portal').text = 'executed' }
            }
            tasks.withType(PublishToMavenRepository).configureEach {
                if (repository.name == 'sonatype') dependsOn rootProject.tasks.named('initializeSonatypeStagingRepository')
                actions.clear()
                doLast { rootProject.file(repository.name == 'development' ? 'local-upload' : 'protected-upload').text = 'executed' }
            }
            tasks.withType(PublishToMavenLocal).configureEach {
                actions.clear()
                doLast { rootProject.file('local-maven').text = 'executed' }
            }
        ''')
    }

    def 'direct staging initialization rejects before its irreversible action'() {
        when:
        def result = runner('initializeSonatypeStagingRepository').buildAndFail()

        then:
        result.output.contains('Use publishCompleteKlumAstProduct')
        protectedMarkers().empty
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create().withProjectDir(fixture)
                .withEnvironment([PATH: System.getenv('PATH'), JAVA_HOME: System.getProperty('java.home'),
                                  HOME: fixture.absolutePath, TMPDIR: System.getProperty('java.io.tmpdir'),
                                  KLUM_AST_RELEASE_AUTHORIZED: 'final'])
                .withArguments(arguments.toList() + ['-Prelease.version=4.1.0', '-Prelease.stage=final',
                        '--offline', '--no-configuration-cache', '--no-build-cache', '--max-workers=2', '--stacktrace'])
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
