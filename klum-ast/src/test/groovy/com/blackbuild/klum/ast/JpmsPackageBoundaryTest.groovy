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
package com.blackbuild.klum.ast

import spock.lang.Issue
import spock.lang.Specification

import java.lang.module.ModuleFinder
import java.nio.file.Path
import java.util.jar.JarFile

@Issue("391")
class JpmsPackageBoundaryTest extends Specification {

    private static final Set<String> LEGACY_PREFIXES = [
            'com.blackbuild.groovy.configdsl',
            'com.blackbuild.klum.ast.ast',
            'com.blackbuild.klum.ast.doc',
            'com.blackbuild.klum.ast.util',
            'com.blackbuild.klum.ast.process',
            'com.blackbuild.klum.ast.validation',
            'com.blackbuild.klum.ast.runtime.internal.reflect',
            'com.blackbuild.klum.common'
    ] as Set

    def "migrated artifacts own distinct final package families"() {
        given:
        Map<String, Set<String>> packagesByArtifact = artifacts().collectEntries { name, jar ->
            [(name): packages(jar)]
        }

        expect:
        packagesByArtifact.annotations.contains('com.blackbuild.klum.ast')
        !packagesByArtifact.runtime.contains('com.blackbuild.klum.ast')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.validation')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.internal')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.internal.layer3')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.internal.process')
        packagesByArtifact.runtime.contains('com.blackbuild.klum.ast.runtime.internal.validation')
        packagesByArtifact.compiler.containsAll([
                'com.blackbuild.klum.ast.compiler.internal.ast',
                'com.blackbuild.klum.ast.compiler.internal.ast.converters',
                'com.blackbuild.klum.ast.compiler.internal.ast.mutators',
                'com.blackbuild.klum.ast.compiler.internal.common',
                'com.blackbuild.klum.ast.compiler.internal.doc',
                'com.blackbuild.klum.ast.compiler.internal.layer3',
                'com.blackbuild.klum.ast.compiler.internal.reflect',
                'com.blackbuild.klum.ast.compiler.internal.validation'
        ])
        packagesByArtifact.compiler.every { packageName ->
            packageName.startsWith('com.blackbuild.klum.ast.compiler.internal')
        }
        packagesByArtifact.every { artifact, packageNames ->
            packageNames.every { packageName ->
                LEGACY_PREFIXES.every { prefix ->
                    !packageName.startsWith(prefix) ||
                            artifact == 'beanValidation' && packageName.startsWith('com.blackbuild.klum.ast.validation.bean')
                }
            }
        }
        packageOwners(packagesByArtifact).every { packageName, owners -> owners.size() == 1 }
    }

    def "runtime service resources use final public service names"() {
        expect:
        resourceNames(runtimeJar()) == [
                'META-INF/services/com.blackbuild.klum.ast.runtime.PhaseAction',
                'META-INF/services/com.blackbuild.klum.ast.runtime.validation.InstanceValidator'
        ] as Set
        resourceNames(beanValidationJar()) == [
                'META-INF/services/com.blackbuild.klum.ast.runtime.validation.InstanceValidator'
        ] as Set
    }

    private static Map<String, Set<String>> packageOwners(Map<String, Set<String>> packagesByArtifact) {
        Map<String, Set<String>> owners = new LinkedHashMap<>()
        packagesByArtifact.each { artifact, packageNames ->
            packageNames.each { packageName ->
                owners.computeIfAbsent(packageName) { new LinkedHashSet<String>() }.add(artifact)
            }
        }
        owners
    }

    private static Set<String> packages(Path jar) {
        ModuleFinder.of(jar).findAll().first().descriptor().packages()
    }

    private static Set<String> resourceNames(Path jar) {
        new JarFile(jar.toFile()).withCloseable { archive ->
            archive.entries().findAll { entry ->
                entry.name.startsWith('META-INF/services/') && !entry.directory
            }.collect { it.name } as Set
        }
    }

    private static Map<String, Path> artifacts() {
        [
                annotations   : jarPath('klumAnnotationsJar'),
                runtime       : runtimeJar(),
                compiler      : jarPath('klumCompilerJar'),
                jackson       : jarPath('klumJacksonJar'),
                beanValidation: beanValidationJar()
        ]
    }

    private static Path runtimeJar() {
        jarPath('klumRuntimeJar')
    }

    private static Path beanValidationJar() {
        jarPath('klumBeanValidationJar')
    }

    private static Path jarPath(String property) {
        Path.of(System.getProperty(property))
    }
}
