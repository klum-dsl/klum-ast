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
import spock.lang.Issue
import spock.lang.See
import spock.lang.Specification
import spock.lang.Tag
import spock.lang.TempDir

@Issue('886')
class GroovyDependenciesRuntimeTest extends Specification {

    @TempDir File projectDir

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Gradle-Plugins.md#runtime-groovy-modules')
    def "versionless runtime modules use the selected Groovy BOM (#version, inherited: #inherited)"() {
        given:
        File consumer = prepareConsumer(version, inherited)
        new File(consumer, 'build.gradle') << """
            dependencies {
                implementation '$group:groovy-yaml'
                runtimeOnly '$group:groovy-nio'
                testImplementation '$group:groovy-dateutil'
            }
            tasks.register('verifyDependencies') {
                doLast {
                    ['compileClasspath', 'runtimeClasspath'].each { name ->
                        def modules = configurations[name].resolvedConfiguration.resolvedArtifacts*.moduleVersion*.id
                        assert modules.find { it.group == '$group' && it.name == 'groovy-yaml' }?.version == '$version'
                        assert modules.any { it.name == 'groovy-nio' } == (name == 'runtimeClasspath')
                        if (name == 'runtimeClasspath')
                            assert modules.find { it.name == 'groovy-nio' }?.version == '$version'
                        assert !modules.any { it.name == 'groovy-dateutil' || it.group == 'org.spockframework' }
                    }
                    def testModules = configurations.testRuntimeClasspath.resolvedConfiguration.resolvedArtifacts*.moduleVersion*.id
                    assert testModules.find { it.name == 'groovy-dateutil' }?.version == '$version'
                    assert testModules.find { it.name == 'groovy' }?.version == '$version'
                    assert testModules.find { it.name == 'spock-core' }?.version == '2.4-groovy-$generation.0'
                }
            }
        """.stripIndent()

        when:
        BuildResult result = run(inherited ? ':consumer:verifyDependencies' : 'verifyDependencies')

        then:
        result.output.contains('BUILD SUCCESSFUL')

        where:
        [version, group, generation, inherited] << [
                ['3.0.25', 'org.codehaus.groovy', '3'],
                ['4.0.32', 'org.apache.groovy', '4'],
                ['5.0.6', 'org.apache.groovy', '5']
        ].collectMany { row -> [row + false, row + true] }
    }

    def "Groovy core remains compile-only and Spock stays test-only (#version, skipSpock: #skipSpock)"() {
        given:
        prepareConsumer(version, false)
        new File(projectDir, 'build.gradle') << """
            groovyDependencies { skipSpock = $skipSpock }
            tasks.register('verifyDependencies') {
                doLast {
                    def compileModules = configurations.compileClasspath.resolvedConfiguration.resolvedArtifacts*.moduleVersion*.id
                    assert compileModules.find { it.name == 'groovy' }?.version == '$version'
                    assert !compileModules.any { it.group == 'org.spockframework' }
                    assert configurations.runtimeClasspath.files.empty
                    assert configurations.groovy.resolvedConfiguration.resolvedArtifacts*.name == ['groovy']
                    def testModules = configurations.testRuntimeClasspath.resolvedConfiguration.resolvedArtifacts*.moduleVersion*.id
                    assert testModules.find { it.name == 'groovy' }?.version == '$version'
                    assert testModules.any { it.name == 'spock-core' } == ${!skipSpock}
                    assert configurations.spock.resolvedConfiguration.resolvedArtifacts.any { it.name == 'spock-core' } == ${!skipSpock}
                }
            }
        """.stripIndent()

        when:
        BuildResult result = run('verifyDependencies')

        then:
        result.output.contains('BUILD SUCCESSFUL')

        where:
        [version, skipSpock] << ['3.0.25', '4.0.32', '5.0.6'].collectMany { [ [it, false], [it, true] ] }
    }

    private File prepareConsumer(String version, boolean inherited) {
        new File(projectDir, 'settings.gradle').text = "rootProject.name = 'groovy-runtime'\n" +
                (inherited ? "include 'consumer'\n" : '')
        new File(projectDir, 'build.gradle').text = """
            plugins { id 'com.blackbuild.convention.groovy' }
            groovyDependencies { groovyVersion = '$version' }
        """.stripIndent()
        File consumer = inherited ? new File(projectDir, 'consumer') : projectDir
        if (inherited) {
            consumer.mkdirs()
            new File(consumer, 'build.gradle').text = """
                plugins {
                    id 'groovy'
                    id 'com.blackbuild.convention.groovy'
                }
            """.stripIndent()
        } else {
            new File(consumer, 'build.gradle') << "apply plugin: 'groovy'\n"
        }
        new File(consumer, 'build.gradle') << "repositories { mavenCentral() }\n"
        consumer
    }

    private BuildResult run(String task) {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(task, '--stacktrace', '--console=plain')
                .withPluginClasspath()
                .build()
    }
}
