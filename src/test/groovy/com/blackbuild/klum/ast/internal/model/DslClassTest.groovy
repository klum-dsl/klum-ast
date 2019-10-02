package com.blackbuild.klum.ast.internal.model

import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.CompileUnit
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.codehaus.groovy.control.messages.SyntaxErrorMessage
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.rules.TestName
import spock.lang.Specification

class DslClassTest extends Specification {

    @Rule TestName testName = new TestName()
    CompilerConfiguration compilerConfiguration

    def setup() {
        def importCustomizer = new ImportCustomizer().addStarImports(
                "com.blackbuild.groovy.configdsl.transform"
        )

        compilerConfiguration = new CompilerConfiguration()
        compilerConfiguration.addCompilationCustomizers(importCustomizer)
    }


    CompileUnit compileScript(@Language("groovy") String script, CompilePhase phase = CompilePhase.CLASS_GENERATION) {
        def cu = new CompilationUnit(compilerConfiguration)
        cu.addSource("script-${System.nanoTime()}.groovy", script)
        cu.compile(phase.phaseNumber)
        return cu.AST
    }

    DslClass getDslClass(@Language("groovy") String script, String classname, CompilePhase phase = CompilePhase.CLASS_GENERATION) {
        return DslClass.getOrFail(compileScript(script, phase).getClass(classname))
    }

    DslClass getDslClass(@Language("groovy") String script, CompilePhase phase = CompilePhase.CLASS_GENERATION) {
        def classes = compileScript(script, phase).classes.findAll { !(it instanceof InnerClassNode) }
        assert classes.size() == 1 : "Need exactly one class"
        return DslClass.getOrFail(classes.first() as ClassNode)
    }

    String getErrorMessage(MultipleCompilationErrorsException exception) {
        assert exception.errorCollector.errorCount == 1
        assert exception.errorCollector.getError(0) instanceof SyntaxErrorMessage
        SyntaxErrorMessage message = exception.errorCollector.getError(0) as SyntaxErrorMessage
        return message.cause.originalMessage
    }

    def "DslClass is created"() {
        when:
        def container = getDslClass '''
            @DSL
            class Dummy {}
        '''

        then:
        container != null
        container.superClass == null
    }

    def "getKey"() {
        when:
        def container = getDslClass '''
            @DSL
            class WithoutKey {
            }
        '''

        then:
        container.keyField == null

        when:
        container = getDslClass '''
            @DSL
            class WithKey {
                @Key String theKey
            }
        '''

        then:
        container.keyField.name == "theKey"
    }

    def "illegal: have more than one key in a class"() {
        when:
        compileScript '''
            @DSL class Dummy {
                @Key String name
                @Key String value
            }'''

        then:
        def exception = thrown(MultipleCompilationErrorsException)
        getErrorMessage(exception) == "Class 'Dummy' has more than one field annotated with 'com.blackbuild.groovy.configdsl.transform.Key', expected at most one"
    }

    def "illegal: the superclass and the class to have both a key field"() {
        when:
        compileScript '''
            @DSL class Parent {
                @Key String name
            }
            @DSL class Dummy extends Parent {
                @Key String childName
            }
'''

        then:
        def exception = thrown(MultipleCompilationErrorsException)
        getErrorMessage(exception) == "Class 'Dummy' defines a key field 'childName', but its ancestor 'Parent' already defines the key 'name'"
    }

    def "illegal: the super super class and the class to have both a key field"() {
        when:
        compileScript '''
            @DSL class GrandParent {
                @Key String name
            }
            @DSL class Parent extends GrandParent {}
            @DSL class Dummy extends Parent {
                @Key String childName
            }
'''

        then:
        def exception = thrown(MultipleCompilationErrorsException)
        getErrorMessage(exception) == "Class 'Dummy' defines a key field 'childName', but its ancestor 'GrandParent' already defines the key 'name'"
    }

    def "illegal: a class to have a key field while its ancestor does not"() {
        when:
        compileScript '''
            @DSL class Parent {}
            @DSL class Dummy extends Parent {
                @Key String childName
            }
'''

        then:
        def exception = thrown(MultipleCompilationErrorsException)
        getErrorMessage(exception) == "All non abstract classes must be either keyed or non-keyed. 'Dummy' has a key 'childName', while its non-abstract superclass 'Parent' has none"
    }

    def "illegal: key field with non string type"() {
        when:
        compileScript '''
            @DSL class Dummy {
                @Key Integer id
            }'''

        then:
        def exception = thrown(MultipleCompilationErrorsException)
        getErrorMessage(exception) == 'Keyfields must be Strings'
    }


}
