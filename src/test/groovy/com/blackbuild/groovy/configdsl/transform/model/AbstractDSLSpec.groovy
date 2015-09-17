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
        importCustomizer.addImports(
                "com.blackbuild.groovy.configdsl.transform.DSL",
                "com.blackbuild.groovy.configdsl.transform.Owner",
                "com.blackbuild.groovy.configdsl.transform.Key",
                "com.blackbuild.groovy.configdsl.transform.Field"
        )

        CompilerConfiguration config = new CompilerConfiguration()
        config.addCompilationCustomizers(importCustomizer)
        loader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader(), config)
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
