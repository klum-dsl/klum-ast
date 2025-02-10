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

import com.blackbuild.klum.ast.process.BreadcrumbCollector
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.runtime.InvokerHelper
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestName
import spock.lang.Specification

import java.lang.reflect.Method

class AbstractDSLSpec extends Specification {

    @Rule TestName testName = new TestName()
    @Rule TemporaryFolder tempFolder = new TemporaryFolder()
    ClassLoader oldLoader
    GroovyClassLoader loader
    def instance
    Class<?> clazz
    Class<?> rwClazz
    CompilerConfiguration compilerConfiguration
    Map<String, Class<?>> classPool = [:]

    def setup() {
        oldLoader = Thread.currentThread().contextClassLoader
        def importCustomizer = new ImportCustomizer()
        importCustomizer.addStarImports(
                "com.blackbuild.groovy.configdsl.transform"
        )

        compilerConfiguration = new CompilerConfiguration()
        compilerConfiguration.addCompilationCustomizers(importCustomizer)
        loader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), compilerConfiguration)
        Thread.currentThread().contextClassLoader = loader
        def outputDirectory = new File("build/test-classes/$GroovySystem.version/${getClass().simpleName}/$safeFilename")
        outputDirectory.deleteDir()
        outputDirectory.mkdirs()
        compilerConfiguration.targetDirectory = outputDirectory
        compilerConfiguration.optimizationOptions.groovydoc = Boolean.TRUE
        BreadcrumbCollector.getInstance(specificationContext.currentIteration.name)
    }

    def getSafeFilename() {
        testName.methodName.replaceAll("\\W+", "_")
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = oldLoader
        assert !BreadcrumbCollector.INSTANCE.get()?.breadcrumbs
        BreadcrumbCollector.INSTANCE.remove()
    }

    def propertyMissing(String name) {
        if (classPool.containsKey(name))
            return classPool[name]
        throw new MissingPropertyException(name, getClass())
    }

    def createInstance(@Language("groovy") String code) {
        instance = createClass(code).Create.One()
    }

    def newInstanceOf(String className) {
        return getClass(className).getDeclaredConstructor().newInstance()
    }

    def newInstanceOf(String className, Object... args) {
        return InvokerHelper.invokeConstructorOf(getClass(className), args)
    }

    Class<?> createClass(@Language("groovy") String code) {
        this.clazz = parseClass(code)
        rwClazz = getRwClass(this.clazz.name)
        return this.clazz
    }

    private Class parseClass(String code) {
        def clazz = loader.parseClass(code)
        updateClassPool()
        return clazz
    }

    private Class parseClass(String code, String filename) {
        def clazz = loader.parseClass(code, filename)
        updateClassPool()
        return clazz
    }

    private void updateClassPool() {
        loader.loadedClasses.each {
            classPool[it.name.tokenize(".").last()] = it
        }
    }

    def createNonDslClass(@Language("groovy") String code) {
        this.clazz = parseClass(code)
    }

    def createSecondaryClass(@Language("groovy") String code) {
        return parseClass(code)
    }

    def createSecondaryClass(@Language("groovy") String code, String filename) {
        return parseClass(code, filename)
    }

    File scriptFile(String filename, @Language("groovy") String code) {
        File file = new File(tempFolder.root, filename)
        file.parentFile.mkdirs()
        file.text = code
        return file
    }

    def create(String classname, Closure closure = {}) {
        getClass(classname).Create.With(closure)
    }

    def create(String classname, String key, Closure closure = {}) {
        getClass(classname).Create.With(key, closure)
    }

    Class<?> getClass(String classname) {
        if (classPool.containsKey(classname)) {
            return classPool[classname]
        }
        return loader.loadClass(classname)
    }

    boolean isDeprecated(Method method) {
        method.getAnnotation(Deprecated) != null
    }

    List<Method> allMethodsNamed(String name) {
        this.clazz.methods.findAll { it.name == name }
    }

    Class getRwClass(String name) {
        getClass(name + '$_RW')
    }

    boolean rwClassHasMethod(String methodName, Class... parameterTypes) {
        hasMethod(rwClazz, methodName, parameterTypes)
    }

    boolean rwClassHasNoMethod(String methodName, Class... parameterTypes) {
        hasNoMethod(rwClazz, methodName, parameterTypes)
    }

    boolean hasNoMethod(Class type, String methodName, Class... parameterTypes) {
        return !hasMethod(type, methodName, parameterTypes)
    }

    boolean hasMethod(Class type, String methodName, Class... parameterTypes) {
        try {
            type.getMethod(methodName, parameterTypes)
            return true
        } catch (NoSuchMethodException ignore) {
            return false
        }
    }

}
