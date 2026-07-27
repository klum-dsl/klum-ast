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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.blackbuild.groovy.configdsl.transform

import groovy.lang.GroovyObjectSupport
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Issue
import spock.lang.Specification

import java.lang.module.ModuleFinder
import java.nio.file.Path
import javax.tools.ToolProvider

@Issue("391")
class JpmsGroovyModuleIdentityTest extends Specification {

    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "a named module must require the matching Groovy module identity"() {
        given:
        String matchingModuleName = moduleName(groovyJar())
        String incompatibleModuleName = matchingModuleName == "org.codehaus.groovy"
                ? "org.apache.groovy"
                : "org.codehaus.groovy"

        expect:
        compilesAgainst(matchingModuleName)
        !compilesAgainst(incompatibleModuleName)
        !compilesAgainst(null)
    }

    def "a consumer cannot donate its Groovy readability to a named module"() {
        given:
        String groovyModuleName = moduleName(groovyJar())
        File librarySourceDirectory = temporaryFolder.newFolder("library-source")
        File libraryClassesDirectory = temporaryFolder.newFolder("library-classes")
        File libraryPackageDirectory = new File(librarySourceDirectory, "example/tracer")
        assert libraryPackageDirectory.mkdirs()
        File boundary = new File(libraryPackageDirectory, "Boundary.java")
        boundary.text = """
            package example.tracer;

            import groovy.lang.GroovyObjectSupport;

            public class Boundary extends GroovyObjectSupport {
            }
        """.stripIndent().trim() + System.lineSeparator()
        new File(librarySourceDirectory, "module-info.java").text = """
            module example.tracer {
                exports example.tracer;
            }
        """.stripIndent().trim() + System.lineSeparator()

        and: "the module descriptor is deliberately compiled separately from the Groovy-referencing class"
        assert ToolProvider.systemJavaCompiler.run(null, null, null,
                "-classpath", groovyJar().toString(),
                "-d", libraryClassesDirectory.toString(),
                boundary.toString()) == 0
        assert ToolProvider.systemJavaCompiler.run(null, null, null,
                "-d", libraryClassesDirectory.toString(),
                new File(librarySourceDirectory, "module-info.java").toString()) == 0

        and: "the consumer itself reads Groovy and the library"
        File consumerSourceDirectory = temporaryFolder.newFolder("consumer-source")
        File consumerClassesDirectory = temporaryFolder.newFolder("consumer-classes")
        File consumerPackageDirectory = new File(consumerSourceDirectory, "example/consumer")
        assert consumerPackageDirectory.mkdirs()
        new File(consumerSourceDirectory, "module-info.java").text = """
            module example.consumer {
                requires example.tracer;
                requires ${groovyModuleName};
            }
        """.stripIndent().trim() + System.lineSeparator()
        File main = new File(consumerPackageDirectory, "Main.java")
        main.text = """
            package example.consumer;

            public class Main {
                public static void main(String[] args) throws Exception {
                    Class.forName("example.tracer.Boundary").getConstructor().newInstance();
                }
            }
        """.stripIndent().trim() + System.lineSeparator()
        String modulePath = [libraryClassesDirectory, groovyJar()].join(File.pathSeparator)
        assert ToolProvider.systemJavaCompiler.run(null, null, null,
                "--module-path", modulePath,
                "-d", consumerClassesDirectory.toString(),
                new File(consumerSourceDirectory, "module-info.java").toString(),
                main.toString()) == 0

        when:
        Process process = new ProcessBuilder(
                javaExecutable(),
                "--module-path", [consumerClassesDirectory, libraryClassesDirectory, groovyJar()].join(File.pathSeparator),
                "-m", "example.consumer/example.consumer.Main")
                .redirectErrorStream(true)
                .start()
        process.inputStream.text

        then:
        process.waitFor() != 0
    }

    private boolean compilesAgainst(String groovyModuleName) {
        File sourceDirectory = temporaryFolder.newFolder((groovyModuleName ?: "no-groovy-requires").replace('.', '-'))
        File classesDirectory = new File(sourceDirectory, "classes")
        File packageDirectory = new File(sourceDirectory, "example/tracer")
        assert packageDirectory.mkdirs()

        new File(sourceDirectory, "module-info.java").text = """
            module example.tracer {
                ${groovyModuleName == null ? "" : "requires ${groovyModuleName};"}
            }
        """.stripIndent().trim() + System.lineSeparator()
        new File(packageDirectory, "Boundary.java").text = """
            package example.tracer;

            import groovy.lang.GroovyObjectSupport;

            public class Boundary extends GroovyObjectSupport {
            }
        """.stripIndent().trim() + System.lineSeparator()

        ToolProvider.systemJavaCompiler.run(
                null,
                null,
                null,
                "--module-path", groovyJar().toString(),
                "-d", classesDirectory.toString(),
                new File(sourceDirectory, "module-info.java").toString(),
                new File(packageDirectory, "Boundary.java").toString()) == 0
    }

    private static Path groovyJar() {
        Path.of(GroovyObjectSupport.protectionDomain.codeSource.location.toURI())
    }

    private static String moduleName(Path jar) {
        ModuleFinder.of(jar).findAll().first().descriptor().name()
    }

    private static String javaExecutable() {
        Path.of(System.getProperty("java.home"), "bin", "java").toString()
    }
}
