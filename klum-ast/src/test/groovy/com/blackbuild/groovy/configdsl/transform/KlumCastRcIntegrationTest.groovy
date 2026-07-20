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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.cast.KlumCastValidated
import com.blackbuild.klum.cast.checks.impl.KlumCastCheck
import spock.lang.Issue
import spock.lang.Specification

import java.lang.module.ModuleFinder
import java.nio.file.Path
import java.util.jar.JarFile

@Issue("459")
class KlumCastRcIntegrationTest extends Specification {

    private static final String RC_VERSION = "0.4.0-rc.2"
    private static final String AST_TRANSFORMATION_SERVICE = "META-INF/services/org.codehaus.groovy.transform.ASTTransformation"

    def "uses the published KlumCast RC artifacts with their stable module names"() {
        expect:
        moduleName(jar) == expectedModuleName
        moduleVersion(jar) == RC_VERSION

        where:
        jar                       | expectedModuleName
        jarOf(KlumCastValidated)  | "com.blackbuild.klum.cast.annotations"
        jarOf(KlumCastCheck)      | "com.blackbuild.klum.cast.spi"
        compilerJar()             | "com.blackbuild.klum.cast.compiler"
    }

    def "KlumCast RC artifacts have exclusive package ownership"() {
        given:
        def packageSets = [jarOf(KlumCastValidated), jarOf(KlumCastCheck), compilerJar()]
                .collect { packagesIn(it) }

        expect:
        packageSets[0].disjoint(packageSets[1])
        packageSets[0].disjoint(packageSets[2])
        packageSets[1].disjoint(packageSets[2])
    }

    private static Path jarOf(Class<?> type) {
        Path.of(type.protectionDomain.codeSource.location.toURI())
    }

    private static Path compilerJar() {
        def service = KlumCastRcIntegrationTest.classLoader.getResources(AST_TRANSFORMATION_SERVICE)
                .toList()
                .find { it.toExternalForm().contains("klum-cast-compile") }

        assert service != null: "KlumCast compiler AST transformation is not service-loaded"

        Path.of(URI.create(service.toExternalForm().split("!")[0].substring("jar:".length())))
    }

    private static String moduleName(Path jar) {
        ModuleFinder.of(jar).findAll().first().descriptor().name()
    }

    private static String moduleVersion(Path jar) {
        ModuleFinder.of(jar).findAll().first().descriptor().rawVersion().orElse(null)
    }

    private static Set<String> packagesIn(Path jar) {
        new JarFile(jar.toFile()).withCloseable { archive ->
            archive.entries()
                    .findAll { !it.directory && it.name.endsWith(".class") && it.name != "module-info.class" }
                    .collect { it.name.substring(0, it.name.lastIndexOf('/')).replace('/', '.') }
                    .toSet()
        }
    }
}
