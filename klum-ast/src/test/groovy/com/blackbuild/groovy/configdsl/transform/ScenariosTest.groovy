/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform

import groovy.io.FileType
import org.codehaus.groovy.control.CompilationUnit
import spock.lang.IgnoreIf
import spock.lang.Unroll

class ScenariosTest extends AbstractDSLSpec {

    GroovyClassLoader incrementalLoader

    @SuppressWarnings("UnnecessaryQualifiedReference")
    @Unroll("Scenario: #folder.name")
    @IgnoreIf({ ScenariosTest.scenarios.isEmpty() })
    def "test compilation against real models in mock folder"() {
        given:
        incrementalLoader = new GroovyClassLoader()
        incrementalLoader.addURL(compilerConfiguration.targetDirectory.toURI().toURL())

        when:
        getSubDirectories(folder).sort().each {
            println "compiling $it.key"
            compile(it.value)
        }

        then:
        noExceptionThrown()

        when:
        def assertScript = new File(folder, "assert.groovy")
        if (assertScript.isFile()) {
            new GroovyShell(incrementalLoader).run(assertScript, [])
        }

        then:
        noExceptionThrown()

        where:
        folder << scenarios
    }

    def compile(File folder) {
        CompilationUnit unit = new CompilationUnit(compilerConfiguration, null, incrementalLoader)
        folder.eachFileRecurse(FileType.FILES) {
            if (it.name.endsWith(".groovy"))
                unit.addSource(it)
        }
        unit.compile()
    }

    static Collection<File> getScenarios() {
        getSubDirectories(new File("src/test/scenarios/")).values()
    }

    static Map<String, File> getSubDirectories(File root) {
        Map<String, File> result = [:]

        root.eachFile {
            if (it.isDirectory()) {
                result[it.name] = it
            } else if (it.name.endsWith(".link")) {
                result[it.name] = new File(root.toURI().resolve(it.text))
            }
        }

        return result
    }

}