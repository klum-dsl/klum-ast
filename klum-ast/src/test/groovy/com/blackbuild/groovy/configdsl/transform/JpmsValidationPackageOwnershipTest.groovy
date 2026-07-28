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

@Issue("391")
class JpmsValidationPackageOwnershipTest extends Specification {

    private static final String RUNTIME_VALIDATION_PACKAGE = 'com.blackbuild.klum.ast.runtime.internal.validation'
    private static final String COMPILER_VALIDATION_PACKAGE = 'com.blackbuild.klum.ast.compiler.internal.validation'

    def "the runtime artifact exclusively owns the runtime validation package"() {
        expect:
        packages(runtimeJar()).contains(RUNTIME_VALIDATION_PACKAGE)
        !packages(compilerJar()).contains(RUNTIME_VALIDATION_PACKAGE)
        packages(compilerJar()).contains(COMPILER_VALIDATION_PACKAGE)
        !packages(runtimeJar()).contains(COMPILER_VALIDATION_PACKAGE)
    }

    private static Set<String> packages(Path jar) {
        ModuleFinder.of(jar).findAll().first().descriptor().packages()
    }

    private static Path runtimeJar() {
        jarPath('klumRuntimeJar')
    }

    private static Path compilerJar() {
        jarPath('klumCompilerJar')
    }

    private static Path jarPath(String property) {
        Path.of(System.getProperty(property))
    }
}
