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
import spock.lang.Specification

@Issue('391')
class KlumSchemaModuleValidationTest extends Specification {

    File projectDir

    def setup() {
        projectDir = File.createTempDir('klum-schema-module-', '')
        new File(projectDir, 'settings.gradle').text = "rootProject.name = 'schema-module-validation'\n"
        new File(projectDir, 'build.gradle').text = '''
            plugins {
                id 'com.blackbuild.klum-ast-schema'
            }

            groovyDependencies {
                groovyVersion = 4
            }
        '''.stripIndent()
        File sources = new File(projectDir, 'src/main/groovy/example/schema')
        sources.mkdirs()
        new File(sources, 'Example.groovy').text = '''
            package example.schema

            class Example {}
        '''.stripIndent()
    }

    def cleanup() {
        projectDir?.deleteDir()
    }

    def "valid Groovy 4 Schema descriptor is accepted and validation is part of check and publication"() {
        given:
        new File(projectDir, 'build.gradle') << '''
            apply plugin: 'maven-publish'
            dependencies {
                api 'com.blackbuild.klum.ast:klum-ast-jackson'
            }
        '''.stripIndent()
        descriptor '''
            module example.schema {
                requires com.blackbuild.klum.ast.annotations;
                requires com.blackbuild.klum.ast.runtime;
                requires static com.blackbuild.klum.ast.compiler;
                requires com.blackbuild.klum.ast.jackson;
                requires org.apache.groovy;

                opens example.schema to com.blackbuild.klum.ast.runtime, com.fasterxml.jackson.databind;
            }
        '''

        when:
        BuildResult validation = run('validateKlumSchemaModule')
        BuildResult check = run('check', '--dry-run')
        BuildResult publication = run('publishToMavenLocal', '--dry-run')

        then:
        validation.output.contains('BUILD SUCCESSFUL')
        check.output.contains(':validateKlumSchemaModule SKIPPED')
        publication.output.contains(':validateKlumSchemaModule SKIPPED')
    }

    def "invalid Groovy 5 Schema descriptor receives copyable required-module and opens remediation"() {
        given:
        new File(projectDir, 'build.gradle').text = new File(projectDir, 'build.gradle').text.replace('groovyVersion = 4', 'groovyVersion = 5')
        new File(projectDir, 'build.gradle') << '''
            dependencies {
                api 'com.blackbuild.klum.ast:klum-ast-bean-validation'
            }
        '''.stripIndent()
        descriptor '''
            module example.schema {
                requires com.blackbuild.klum.ast.annotations;
                requires com.blackbuild.klum.ast.compiler;
            }
        '''

        when:
        BuildResult result = runAndFail('validateKlumSchemaModule')

        then:
        result.output.contains('missing `requires com.blackbuild.klum.ast.runtime;`')
        result.output.contains('missing `requires static com.blackbuild.klum.ast.compiler;`')
        result.output.contains('missing `requires org.apache.groovy;`')
        result.output.contains('missing `opens example.schema to com.blackbuild.klum.ast.runtime;`')
        result.output.contains('missing `requires com.blackbuild.klum.ast.validation.bean;`')
        result.output.contains('missing `opens example.schema to org.hibernate.validator;`')
        result.output.contains('module-info.java remains user-owned')
    }

    def "Groovy 3 named Schema receives classpath-only remediation"() {
        given:
        new File(projectDir, 'build.gradle').text = new File(projectDir, 'build.gradle').text.replace('groovyVersion = 4', 'groovyVersion = 3')
        descriptor '''
            module example.schema {
                requires com.blackbuild.klum.ast.runtime;
            }
        '''

        when:
        BuildResult result = runAndFail('validateKlumSchemaModule')

        then:
        result.output.contains('Groovy 3 is a supported classpath-only configuration')
        result.output.contains('Remove module-info.java and compile/run this Schema on the ordinary classpath')
        result.output.contains('will not rewrite module-info.java or add JVM module-path workaround flags')
    }

    private void descriptor(String contents) {
        File descriptor = new File(projectDir, 'src/main/java/module-info.java')
        descriptor.parentFile.mkdirs()
        descriptor.text = contents.stripIndent()
    }

    private BuildResult run(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain'])
                .withPluginClasspath()
                .build()
    }

    private BuildResult runAndFail(String... arguments) {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(arguments.toList() + ['--stacktrace', '--console=plain'])
                .withPluginClasspath()
                .buildAndFail()
    }
}
