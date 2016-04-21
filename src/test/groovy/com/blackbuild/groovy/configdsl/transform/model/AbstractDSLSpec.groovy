package com.blackbuild.groovy.configdsl.transform.model

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import spock.lang.Specification

class AbstractDSLSpec extends Specification {

    protected ClassLoader oldLoader
    protected GroovyClassLoader loader
    protected def instance
    protected Class<?> clazz

    def setup() {
        oldLoader = Thread.currentThread().contextClassLoader
        def importCustomizer = new ImportCustomizer()
        importCustomizer.addStarImports(
                "com.blackbuild.groovy.configdsl.transform"
        )

        CompilerConfiguration config = new CompilerConfiguration()
        config.addCompilationCustomizers(importCustomizer)
        loader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), config)
        Thread.currentThread().contextClassLoader = loader
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = oldLoader
    }

    def createInstance(String code) {
        createClass(code)
        instance = clazz.newInstance()
    }

    def newInstanceOf(String className) {
        return getClass(className).newInstance()
    }

    def createClass(String code) {
        clazz = loader.parseClass(code)
    }

    def createSecondaryClass(String code) {
        return loader.parseClass(code)
    }

    def create(String classname, Closure closure) {
        getClass(classname).create(closure)
    }

    def create(String classname, String key, Closure closure) {
        getClass(classname).create(key, closure)
    }

    def Class<?> getClass(String classname) {
        loader.loadClass(classname)
    }


}
