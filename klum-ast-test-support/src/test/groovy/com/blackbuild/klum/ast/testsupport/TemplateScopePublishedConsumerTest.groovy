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
package com.blackbuild.klum.ast.testsupport

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.Specification

import java.io.File
import java.util.Map

@Issue("658")
class TemplateScopePublishedConsumerTest extends Specification {

    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "published test support resolves and restores Templates for every supported Groovy consumer"() {
        given:
        File repository = temporaryFolder.newFolder('published-product')
        publishFixture(repository)

        expect: 'the support POM has the narrow runtime implementation boundary and no Groovy or fixture leak'
        String supportPom = supportPom(repository).text
        supportPom =~ /(?s)<artifactId>klum-ast-runtime<\/artifactId>\s*<version>[^<]+<\/version>\s*<scope>runtime<\/scope>/
        !supportPom.contains('<artifactId>groovy</artifactId>')
        !supportPom.contains('test-fixtures')

        and: 'the BOM constrains the published support coordinate'
        bomPom(repository).text.contains('<artifactId>klum-ast-test-support</artifactId>')

        and: 'each clean consumer chooses and executes its own Groovy and Spock lane'
        [
                [generation: 3, groovy: 'org.codehaus.groovy:groovy:3.0.25', spock: '2.4-groovy-3.0'],
                [generation: 4, groovy: 'org.apache.groovy:groovy:4.0.32', spock: '2.4-groovy-4.0'],
                [generation: 5, groovy: 'org.apache.groovy:groovy:5.0.6', spock: '2.4-groovy-5.0']
        ].each { lane ->
            BuildResult result = runConsumer(repository, lane)
            assert result.task(':test').outcome == TaskOutcome.SUCCESS
        }
    }

    private void publishFixture(File repository) {
        File initScript = temporaryFolder.newFile('publish-fixture.init.gradle')
        initScript.text = '''
            def fixtureRepository = gradle.startParameter.projectProperties.templateScopeFixtureRepository
            allprojects {
                pluginManager.withPlugin('maven-publish') {
                    publishing.repositories {
                        maven {
                            name = 'templateScopeFixture'
                            url = uri(fixtureRepository)
                        }
                    }
                }
            }
        '''.stripIndent()

        BuildResult result = GradleRunner.create()
                .withProjectDir(projectDirectory())
                .withArguments(
                        '--init-script', initScript.absolutePath,
                        "-PtemplateScopeFixtureRepository=${repository.toURI()}",
                        ':klum-ast-annotations:publishMavenJavaPublicationToTemplateScopeFixtureRepository',
                        ':klum-ast:publishMavenJavaPublicationToTemplateScopeFixtureRepository',
                        ':klum-ast-runtime:publishMavenJavaPublicationToTemplateScopeFixtureRepository',
                        ':klum-ast-test-support:publishMavenJavaPublicationToTemplateScopeFixtureRepository',
                        ':klum-ast-bom:publishMavenJavaPublicationToTemplateScopeFixtureRepository'
                )
                .build()

        assert result.task(':klum-ast-test-support:publishMavenJavaPublicationToTemplateScopeFixtureRepository').outcome == TaskOutcome.SUCCESS
    }

    private BuildResult runConsumer(File repository, Map<String, ?> lane) {
        File consumer = temporaryFolder.newFolder("consumer-g${lane.generation}")
        new File(consumer, 'settings.gradle').text = "rootProject.name = 'template-scope-consumer-g${lane.generation}'\n"
        new File(consumer, 'build.gradle').text = consumerBuild(repository, lane)
        writeSource(consumer, 'src/test/java/example/JavaScopeUse.java', '''
            package example;

            import com.blackbuild.klum.ast.testsupport.TemplateScope;

            public final class JavaScopeUse {
                private JavaScopeUse() {
                }

                public static TemplateScope open(Object... templates) {
                    return new TemplateScope().with(templates);
                }
            }
        ''')
        writeSource(consumer, 'src/test/groovy/example/TemplateScopeConsumerTest.groovy', '''
            package example

            import com.blackbuild.klum.ast.DSL
            import spock.lang.Issue
            import spock.lang.Specification

            @Issue('658')
            class TemplateScopeConsumerTest extends Specification {

                def 'uses the separately published scope and restores its state'() {
                    given:
                    def baseline = Delivery.Create.Template.With(region: 'eu-central')

                    when:
                    Delivery configured
                    try (def ignored = JavaScopeUse.open(baseline)) {
                        configured = Delivery.Create.One()
                    }

                    then:
                    configured.region == 'eu-central'
                    Delivery.Create.One().region == null
                }
            }

            @DSL
            class Delivery {
                String region
            }
        ''')

        GradleRunner.create()
                .withProjectDir(consumer)
                .withArguments('test', '--stacktrace')
                .build()
    }

    private String consumerBuild(File repository, Map<String, ?> lane) {
        """
            plugins {
                id 'groovy'
            }

            repositories {
                maven { url = uri('${repository.toURI()}') }
                mavenCentral()
            }

            dependencies {
                testImplementation platform('com.blackbuild.klum.ast:klum-ast-bom:${version()}')
                testCompileOnly 'com.blackbuild.klum.ast:klum-ast'
                testImplementation 'com.blackbuild.klum.ast:klum-ast-runtime'
                testImplementation 'com.blackbuild.klum.ast:klum-ast-test-support'
                testImplementation '${lane.groovy}'
                testImplementation 'org.spockframework:spock-core:${lane.spock}'
            }

            tasks.named('test') {
                useJUnitPlatform()
            }
        """.stripIndent()
    }

    private File supportPom(File repository) {
        new File(repository, "com/blackbuild/klum/ast/klum-ast-test-support/${version()}/klum-ast-test-support-${version()}.pom")
    }

    private File bomPom(File repository) {
        new File(repository, "com/blackbuild/klum/ast/klum-ast-bom/${version()}/klum-ast-bom-${version()}.pom")
    }

    private static void writeSource(File root, String relativePath, String source) {
        File file = new File(root, relativePath)
        assert file.parentFile.mkdirs()
        file.text = source.stripIndent().trim() + System.lineSeparator()
    }

    private static File projectDirectory() {
        new File(System.getProperty('klumProjectDirectory'))
    }

    private static String version() {
        System.getProperty('klumVersion')
    }
}
