package com.blackbuild.groovy.configdsl.transform.model

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import spock.lang.Specification

class AbstractDSLSpec extends Specification {

    protected GroovyClassLoader loader
    protected def instance
    protected Class<?> clazz

    def setup() {
        def importCustomizer = new ImportCustomizer()
        importCustomizer.addImports("com.blackbuild.groovy.configdsl.transform.DSLConfig", "com.blackbuild.groovy.configdsl.transform.DSLField")

        CompilerConfiguration config = new CompilerConfiguration()
        config.addCompilationCustomizers(importCustomizer)
        loader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), config)
    }

    def createInstance(String code) {
        createClass(code)
        instance = clazz.newInstance()
    }

    def newInstanceOf(String className) {
        return loader.loadClass(className).newInstance()
    }

    def createClass(String code) {
        clazz = loader.parseClass(code)
    }

    def create(String classname, Closure closure) {
        loader.loadClass(classname).create(closure)
    }

    def create(String classname, String key, Closure closure) {
        loader.loadClass(classname).create(key, closure)
    }

}
