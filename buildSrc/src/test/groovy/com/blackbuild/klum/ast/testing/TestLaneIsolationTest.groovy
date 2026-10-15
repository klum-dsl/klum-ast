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
package com.blackbuild.klum.ast.testing

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS
import static org.gradle.testkit.runner.TaskOutcome.UP_TO_DATE

@Issue('895')
class TestLaneIsolationTest extends Specification {
    @TempDir File fixture

    def setup() {
        // Reuse the real pinned catalog and external-plugin versions, not a second compatibility policy.
        String settings = new File(System.getProperty('klum.laneFixtureSettings')).text
        write('settings.gradle', settings.substring(0, settings.indexOf("rootProject.name =")) + '''
            rootProject.name = 'lane-isolation'
            include 'klum-ast'
        ''')
        write('klum-ast/build.gradle', '''
            plugins { id 'groovy'; id 'java-test-fixtures' }
        ''')
        write('LICENSE', 'Fixture license')
        write('build.gradle', '''
            plugins {
                id 'com.github.hierynomus.license' apply false
                id 'klum-ast.multigroovy-conventions'
            }
            tasks.withType(Test).configureEach {
                systemProperty 'expected.generation', name == 'test' ? '3' : name.charAt(6).toString()
            }
            // Apply overrides after the convention's registration, as consuming modules do.
            def probe = providers.gradleProperty('probe').orElse('').get()
            if (probe == 'sourceSet') tasks.named('verifyTestLaneIsolation') {
                sourceSets.test.compileClasspath += files(sourceSets.groovy4Tests.output.classesDirs)
            }
            if (probe == 'compile') tasks.named('compileTestGroovy') { classpath += files(sourceSets.groovy4Tests.output.classesDirs) }
            if (probe == 'resources') {
                sourceSets.groovy4Tests.output.resourcesDir = file('foreign-resources')
                tasks.named('test') {
                    dependsOn tasks.named('processGroovy4TestsResources')
                    classpath += files(sourceSets.groovy4Tests.output.resourcesDir)
                }
            }
            if (probe == 'runtime') tasks.named('test') { classpath += files(sourceSets.groovy4Tests.output.classesDirs) }
            if (probe == 'classes') tasks.named('test') { testClassesDirs += files(sourceSets.groovy4Tests.output.classesDirs) }
            if (probe == 'results') tasks.named('test') { reports.junitXml.outputLocation = layout.buildDirectory.dir('foreign-results') }
            if (probe == 'missingResults') {
                def removeResults = tasks.register('removeLaneResults', Delete) {
                    dependsOn tasks.named('test')
                    delete layout.buildDirectory.dir('test-results/test')
                }
                tasks.named('verifyTestLaneIsolation') { dependsOn removeResults }
            }
            if (probe == 'groovy') configurations.testRuntimeClasspath.resolutionStrategy.force('org.codehaus.groovy:groovy:3.0.24')
            if (probe == 'spock') configurations.testRuntimeClasspath.resolutionStrategy.force('org.spockframework:spock-junit4:2.3-groovy-3.0')
        ''')
        write('src/test/groovy/example/LaneTest.groovy', '''
            package example
            import spock.lang.Issue
            import spock.lang.Specification
            @Issue('895')
            class LaneTest extends Specification {
                def 'executes with its independent Groovy generation'() {
                    expect:
                    GroovySystem.version.startsWith(System.getProperty('expected.generation') + '.')
                }
            }
        ''')
    }

    def 'stores and reuses the real three-lane graph and recompiles changed sources in each lane'() {
        when:
        BuildResult first = runner().build()
        BuildResult reused = runner().build()

        then:
        first.output.contains('Configuration cache entry stored.')
        reused.output.contains('Configuration cache entry reused.')
        reused.task(':verifyTestLaneIsolation').outcome == SUCCESS
        ['test', 'groovy4Tests', 'groovy5Tests'].every { reused.task(":$it").outcome == UP_TO_DATE }
        ['test', 'test-g4', 'test-g5'].every { file("build/classes/groovy/$it/example/LaneTest.class").file }
        ['test', 'groovy4Tests', 'groovy5Tests'].every { file("build/test-results/$it/TEST-example.LaneTest.xml").text.contains('tests="1"') }

        when:
        file('src/test/groovy/example/LaneTest.groovy').text += '\n// Source content invalidates all three independent compilers.\n'
        BuildResult changed = runner().build()

        then:
        changed.output.contains('Configuration cache entry reused.')
        ['compileTestGroovy', 'compileGroovy4TestsGroovy', 'compileGroovy5TestsGroovy', 'verifyTestLaneIsolation'].every {
            changed.task(":$it").outcome == SUCCESS
        }
    }

    def 'retains diagnostic for late #probe configuration on strict store and reuse'() {
        given:
        runner().build()

        when:
        BuildResult stored = runner("-Pprobe=$probe").buildAndFail()
        BuildResult reused = runner("-Pprobe=$probe").buildAndFail()

        then:
        stored.output.contains('Configuration cache entry stored.')
        reused.output.contains('Configuration cache entry reused.')
        stored.output.contains(diagnostic)
        reused.output.contains(diagnostic)

        where:
        probe            | diagnostic
        'sourceSet'      | "Groovy 3 source-set compile classpath contains another lane's output"
        'compile'        | "Groovy 3 Groovy compile classpath contains another lane's output"
        'resources'      | "Groovy 3 test runtime classpath contains another lane's output"
        'runtime'        | "Groovy 3 test runtime classpath contains another lane's output"
        'classes'        | 'Groovy 3 executes unexpected test classes'
        'results'        | 'Groovy 3 writes JUnit results outside'
        'missingResults' | 'Groovy 3 did not produce JUnit results in'
        'groovy'         | 'Groovy 3 lane contains mismatched Groovy modules'
        'spock'          | 'Groovy 3 lane contains mismatched Spock modules'
    }

    private GradleRunner runner(String... arguments) {
        GradleRunner.create().withProjectDir(fixture).withPluginClasspath()
                .withArguments(['verifyTestLaneIsolation', '--configuration-cache', '--configuration-cache-problems=fail',
                                '--no-build-cache', '--no-parallel', '--max-workers=2', '--stacktrace'] + arguments.toList())
    }

    private File write(String path, String content) {
        File target = file(path)
        target.parentFile.mkdirs()
        target.text = content.stripIndent()
        target
    }

    private File file(String path) {
        new File(fixture, path)
    }
}
