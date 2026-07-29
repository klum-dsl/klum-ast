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

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Issue
import spock.lang.Specification

@Issue('391')
class ValidateKlumSchemaModuleTest extends Specification {

    File projectDir
    ValidateKlumSchemaModule task

    def setup() {
        projectDir = File.createTempDir('validate-klum-schema-module-', '')
        def project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        task = project.tasks.create('validateKlumSchemaModule', ValidateKlumSchemaModule)
        task.optionalAdapterModules.set([])
    }

    def cleanup() {
        projectDir?.deleteDir()
    }

    def "does nothing without a module descriptor"() {
        when:
        task.validateDescriptor()

        then:
        noExceptionThrown()
    }

    def "maps the Gradle inputs to a valid generation-aware validation request"() {
        given:
        File descriptor = source('src/main/java/module-info.java', '''
            module example.schema {
                requires com.blackbuild.klum.ast.annotations;
                requires com.blackbuild.klum.ast.runtime;
                requires static com.blackbuild.klum.ast.compiler;
                requires com.blackbuild.klum.ast.jackson;
                requires org.apache.groovy;

                opens example.schema to com.blackbuild.klum.ast.runtime, com.fasterxml.jackson.databind;
            }
        ''')
        File schema = source('src/main/groovy/example/schema/Example.groovy', '''
            package example.schema

            class Example {}
        ''')
        task.descriptorFiles.from(descriptor)
        task.schemaSources.from(schema)
        task.groovyVersion.set('4')
        task.optionalAdapterModules.set(['com.blackbuild.klum.ast.jackson'])

        when:
        task.validateDescriptor()

        then:
        noExceptionThrown()
    }

    def "turns a validation diagnostic into a Gradle exception"() {
        given:
        task.descriptorFiles.from(source('src/main/java/module-info.java', '''
            module example.schema {
                requires com.blackbuild.klum.ast.runtime;
            }
        '''))
        task.schemaSources.from(source('src/main/groovy/example/schema/Example.groovy', '''
            package example.schema
        '''))
        task.groovyVersion.set('5.0')

        when:
        task.validateDescriptor()

        then:
        GradleException exception = thrown()
        exception.message.contains("KlumAST schema module validation failed for ':'")
        exception.message.contains('missing')
    }

    private File source(String path, String contents) {
        File file = new File(projectDir, path)
        file.parentFile.mkdirs()
        file.text = contents.stripIndent()
        file
    }
}
