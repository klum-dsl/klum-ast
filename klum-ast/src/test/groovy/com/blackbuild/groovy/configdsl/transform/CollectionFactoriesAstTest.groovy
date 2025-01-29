/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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