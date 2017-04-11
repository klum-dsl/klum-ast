/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform.mutators

import com.blackbuild.groovy.configdsl.transform.ast.mutators.MutationCheckerVisitor
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.builder.AstBuilder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.ErrorCollector
import org.codehaus.groovy.control.SourceUnit
import spock.lang.Specification
import spock.lang.Unroll


/**
 * Created by stephan on 07.04.2017.
 */
class MutationCheckerVisitorSpec extends Specification {

    ClassNode clazz
    ErrorCollector errorCollector = Mock(ErrorCollector)
    SourceUnit sourceUnit = Stub(SourceUnit) {
        getErrorCollector() >> errorCollector
    }
    MutationCheckerVisitor visitor = new MutationCheckerVisitor(sourceUnit)

    def withClassCode(String text) {
        def textWithImports = 'import com.blackbuild.groovy.configdsl.transform.*\n' + text

        clazz = new AstBuilder().buildFromString(CompilePhase.CANONICALIZATION, textWithImports)[1] as ClassNode
    }

    def doVisit() {
        visitor.visitClass(clazz)
    }
    
    def "debuggable visit call"() {
        given:
        withClassCode '''
            class Bla {
                String name
            
              def doIt() {
                def temp
                temp = "bla"
                name = "blub"
              }
            }
'''

        when:
        doVisit()

        then:
        noExceptionThrown()

    }

    def "qualified call in mutated method is no error"() {
        given:
        withClassCode '''
            class Bla {
              String name
            
              @Mutator
              def doIt() {
                this.name = "blub"
              }
            }
'''

        when:
        doVisit()

        then:
        0 * errorCollector.addErrorAndContinue(_)
    }

    @Unroll
    def "#description"() {
        given:
        withClassCode """
            class Bla {
              ${fields.join('\n')}
            
              def doIt() {
                ${local.join('\n')}
                $statement
              }
            }
"""

        when:
        doVisit()

        then:
        errors * errorCollector.addErrorAndContinue(_)

        where:
        fields              | local                     | statement                          || errors | description
        ['String name']     | []                        | 'this.name = "blub"'               || 1      | 'qualified field access'
        ['String name']     | []                        | 'name = "blub"'                    || 1      | 'unqualified field access'
        ['String name']     | ['def name']              | 'name = "blub"'                    || 0      | 'local variable shades field'
        ['String name']     | []                        | 'def value = "blub"'               || 0      | 'direct local variable assignment'
        ['String name']     | []                        | 'def name = "blub"'                || 0      | 'direct shading local variable assignment'
        ['String name']     | []                        | '(name, value) = ["blub", "bli"]'  || 2      | 'multi assignment'
        ['String name']     | ['def name', 'def value'] | '(name, value) = ["blub", "bli"]'  || 0      | 'multi assignment on local variables'
        ['String name']     | ['def value']             | 'value = name = "blub"'            || 1      | 'chain assignment'

    }


}