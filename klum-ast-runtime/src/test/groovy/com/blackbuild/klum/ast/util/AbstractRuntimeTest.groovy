package com.blackbuild.klum.ast.util

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

abstract class AbstractRuntimeTest extends Specification {
    @Rule TestName testName = new TestName()
    ClassLoader oldLoader
    GroovyClassLoader loader
    GroovyObject instance
    Class<?> clazz
    CompilerConfiguration compilerConfiguration

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
        def outputDirectory = new File("build/test-classes/${getClass().simpleName}/$safeFilename")
        outputDirectory.deleteDir()
        outputDirectory.mkdirs()
        compilerConfiguration.targetDirectory = outputDirectory

        // language=groovy
        loader.parseClass("""
package com.blackbuild.groovy.configdsl.transform.ast
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.control.CompilePhase
import com.blackbuild.klum.ast.util.DummyAstTransformation
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION) class DSLASTTransformation extends DummyAstTransformation {}
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION) class DelegatesToRWTransformation extends DummyAstTransformation {}
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION) class AddJacksonIgnoresTransformation extends DummyAstTransformation {}
""")
        // language=groovy
        loader.parseClass("""
package com.blackbuild.groovy.configdsl.transform.ast.mutators
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.control.CompilePhase
import com.blackbuild.klum.ast.util.DummyAstTransformation
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION) class ModelVerifierTransformation extends DummyAstTransformation {}
""")
    }

    def getSafeFilename() {
        testName.methodName.replaceAll("\\W+", "_")
    }

    def cleanup() {
        Thread.currentThread().contextClassLoader = oldLoader
    }

    def createInstance(@Language("groovy") String code) {
        createClass(code)
        instance = clazz.newInstance()
    }

    def newInstanceOf(String className, Object[] args = []) {
        return getClass(className).newInstance(args)
    }

    def createClass(@Language("groovy") String code) {
        clazz = loader.parseClass(code)
    }


}