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
package com.blackbuild.klum.ast.jackson

import org.gradle.testkit.runner.GradleRunner
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

@Issue("463")
class JacksonImporterConsumerTest extends Specification {

    @TempDir
    File projectDir

    def "an external Java 17 consumer compiles every importer mode against Jackson 2.14"() {
        given:
        writeBuild('2.14.2', 'java')
        writeModel()
        writeSource('consumer/src/main/java/example/JavaConsumer.java', '''
            package example;

            import com.blackbuild.klum.ast.jackson.KlumJacksonImporter;
            import com.blackbuild.klum.ast.jackson.KlumJacksonInput;
            import com.fasterxml.jackson.databind.ObjectMapper;

            import java.util.Map;

            class JavaConsumer {
                static ExternalValue importValue(ObjectMapper mapper) {
                    KlumJacksonImporter importer = KlumJacksonImporter.using(mapper);
                    ExternalValue root = importer.readRoot(ExternalValue.class, KlumJacksonInput.map(Map.of()));
                    ExternalValue template = importer.readTemplate(ExternalValue.class, KlumJacksonInput.map(Map.of()));
                    ExternalValue_DSL.Builder builder = importer.readBuilder(
                            ExternalValue.Create.getAsBuilder(), KlumJacksonInput.map(Map.of()));
                    ExternalValue_DSL.Builder applied = importer.applyToBuilder(builder, KlumJacksonInput.map(Map.of()));
                    return root;
                }
            }
        ''')

        expect:
        build()
    }

    def "an external static Groovy consumer compiles every importer mode against Jackson 2.21"() {
        given:
        writeBuild('2.21.0', 'groovy')
        writeModel()
        writeSource('consumer/src/main/groovy/example/StaticConsumer.groovy', '''
            package example

            import com.blackbuild.klum.ast.jackson.KlumJacksonImporter
            import com.blackbuild.klum.ast.jackson.KlumJacksonInput
            import com.fasterxml.jackson.databind.ObjectMapper
            import groovy.transform.CompileStatic

            @CompileStatic
            class StaticConsumer {
                static ExternalValue importValue(ObjectMapper mapper) {
                    KlumJacksonImporter importer = KlumJacksonImporter.using(mapper)
                    ExternalValue root = importer.readRoot(ExternalValue, KlumJacksonInput.map([:]))
                    ExternalValue template = importer.readTemplate(ExternalValue, KlumJacksonInput.map([:]))
                    ExternalValue_DSL.Builder builder = importer.readBuilder(
                            ExternalValue.Create.AsBuilder, KlumJacksonInput.map([:]))
                    ExternalValue_DSL.Builder applied = importer.applyToBuilder(builder, KlumJacksonInput.map([:]))
                    root
                }
            }
        ''')

        expect:
        build()
    }

    private void writeBuild(String jacksonVersion, String consumerLanguage) {
        writeSource('settings.gradle', """
            rootProject.name = 'jackson-importer-consumer'
            include 'model', 'consumer'
        """)
        writeSource('model/build.gradle', buildScript(jacksonVersion, 'groovy', null))
        writeSource('consumer/build.gradle', buildScript(jacksonVersion, consumerLanguage, "implementation project(':model')"))
    }

    private static String buildScript(String jacksonVersion, String language, String projectDependency) {
        return """
            plugins {
                id '${language}'
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                ${projectDependency ?: ''}
                implementation files(
                        System.getProperty('klumAstJar'),
                        System.getProperty('klumAnnotationsJar'),
                        System.getProperty('klumRuntimeJar'),
                        System.getProperty('klumJacksonJar'))
                implementation 'com.blackbuild.annodocimal:anno-docimal-ast:0.7.1'
                implementation 'org.codehaus.groovy:groovy:3.0.25'
                implementation "com.fasterxml.jackson.core:jackson-databind:${jacksonVersion}"
            }
        """.stripIndent()
    }

    private void writeModel() {
        writeSource('model/src/main/groovy/example/ExternalValue.groovy', '''
            package example

            import com.blackbuild.groovy.configdsl.transform.DSL

            @DSL
            class ExternalValue {
                String value
            }
        ''')
    }

    private void writeSource(String relativePath, String content) {
        File target = new File(projectDir, relativePath)
        target.parentFile.mkdirs()
        target.text = content.stripIndent().trim() + System.lineSeparator()
    }

    private boolean build() {
        GradleRunner.create()
                .withProjectDir(projectDir)
                .withArguments(':consumer:classes', '--stacktrace')
                .build()
        return true
    }
}
