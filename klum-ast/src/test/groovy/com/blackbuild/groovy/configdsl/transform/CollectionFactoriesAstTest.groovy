package com.blackbuild.groovy.configdsl.transform


import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.InnerClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.intellij.lang.annotations.Language
import spock.lang.Specification

import static org.codehaus.groovy.ast.ClassHelper.CLOSURE_TYPE
import static org.codehaus.groovy.ast.ClassHelper.MAP_TYPE
import static org.codehaus.groovy.ast.tools.GeneralUtils.param
import static org.codehaus.groovy.ast.tools.GeneralUtils.params

class CollectionFactoriesAstTest extends Specification {

    ClassNode clazz
    SourceUnit sourceUnit = Stub(SourceUnit)

    def withClassCode(@Language("Groovy") String text) {
        def textWithImports = 'import com.blackbuild.groovy.configdsl.transform.*\n' + text
        clazz = new AstBuilder().buildFromString(CompilePhase.CANONICALIZATION, textWithImports)[1] as ClassNode
    }



    def "alternative factories' delegation methods with default values are unlooped"() {
        when:
        withClassCode '''
@DSL class Foo {
    List<Bar> bars
}

@DSL class Bar {}
'''
        InnerClassNode bars = clazz.getInnerClasses().find { it.name == 'Foo$_bars' }

        then: 'each default value is resolved'
        bars.getDeclaredMethod("bar", params(param(MAP_TYPE, "")))
        bars.getDeclaredMethod("bar", params(param(MAP_TYPE, ""), param(CLOSURE_TYPE, "")))
        bars.getDeclaredMethod("bar", param(CLOSURE_TYPE, ""))
    }



}