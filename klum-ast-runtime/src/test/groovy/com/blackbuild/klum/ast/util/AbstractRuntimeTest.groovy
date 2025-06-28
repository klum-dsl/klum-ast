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
package com.blackbuild.klum.ast.util

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.runtime.InvokerHelper
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

abstract class AbstractRuntimeTest extends Specification {
    @Rule TestName testName = new TestName()
    ClassLoader oldLoader
    GroovyClassLoader loader
    Object instance
    Class<?> clazz
    CompilerConfiguration compilerConfiguration

    def setup() {
        oldLoader = Thread.currentThread().contextClassLoader
        def importCustomizer = new ImportCustomizer()
        importCustomizer.addStarImports(
                "com.blackbuild.groovy.configdsl.transform",
                "com.blackbuild.klum.ast.util",
                "com.blackbuild.klum.ast",
                this.getClass().getPackage().name
        )

        compilerConfiguration = new CompilerConfiguration()
        compilerConfiguration.addCompilationCustomizers(importCustomizer)
        loader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), compilerConfiguration)
        Thread.currentThread().contextClassLoader = loader
        def outputDirectory = new File("build/test-classes/${getClass().simpleName}/$safeFilename")
        outputDirectory.deleteDir()
        outputDirectory.mkdirs()
        compilerConfiguration.targetDirectory = outputDirectory

        createDummyTransformations("com.blackbuild.groovy.configdsl.transform.ast",
                "DSLASTTransformation",
                "DelegatesToRWTransformation",
                "AddJacksonIgnoresTransformation",
                "FieldAstValidator")
        createDummyTransformations("com.blackbuild.groovy.configdsl.transform.ast.mutators",
                "ModelVerifierTransformation")
    }

    void createDummyTransformations(String packageName, String... classNames) {
        def typeDef = """
package $packageName
import org.codehaus.groovy.transform.GroovyASTTransformation
import com.blackbuild.klum.ast.util.DummyAstTransformation
"""
        for (className in classNames)
            typeDef += "@GroovyASTTransformation class $className extends DummyAstTransformation {}\n"
        loader.parseClass(typeDef)
    }

    def getSafeFilename() {
        testName.methodName.replaceAll("\\W+", "_")
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = oldLoader
    }

    def createInstance(@Language("groovy") String code) {
        createClass(code)
        instance = InvokerHelper.invokeNoArgumentsConstructorOf(clazz)
    }

    def newInstanceOf(String className, Object[] args = []) {
        return InvokerHelper.invokeConstructorOf(getClass(className), args)
    }

    def createClass(@Language("groovy") String code) {
        clazz = loader.parseClass(code)
    }

    Class<?> getClass(String classname) {
        loader.loadClass(classname)
    }

}