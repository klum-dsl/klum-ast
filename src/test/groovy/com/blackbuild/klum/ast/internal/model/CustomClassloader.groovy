package com.blackbuild.klum.ast.internal.model

import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.junit.rules.ExternalResource

class CustomClassloader extends ExternalResource {

    private ClassLoader oldLoader
    private ClassLoader testLoader
    private CompilationCustomizer compilationCustomizer

    CustomClassloader(GroovyClassLoader testLoader) {
        this.testLoader = testLoader
    }

    CustomClassloader() {
        this.testLoader = new GroovyClassLoader(Thread.currentThread().getContextClassLoader())

    }

    CustomClassloader add(CompilationCustomizer customizer) {

    }

    @Override
    protected void before() throws Throwable {
        oldLoader = Thread.currentThread().contextClassLoader
        Thread.currentThread().contextClassLoader = testLoader
    }

    @Override
    protected void after() {
        Thread.currentThread().contextClassLoader = oldLoader
    }
}
