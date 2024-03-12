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
package com.blackbuild.groovy.configdsl.transform.mutators

import com.blackbuild.groovy.configdsl.transform.ast.mutators.ModelVerificationVisitor
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
    ModelVerificationVisitor visitor

    def withClassCode(String text) {
        def textWithImports = 'import com.blackbuild.groovy.configdsl.transform.*\n' + text

        clazz = new AstBuilder().buildFromString(CompilePhase.INSTRUCTION_SELECTION, textWithImports)[1] as ClassNode
        visitor = new ModelVerificationVisitor(sourceUnit, clazz)
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
    def "valid: #description"() {
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
        0 * errorCollector.addErrorAndContinue(_)

        where:
        fields              | local                     | statement                          || description
        ['String name']     | ['def name']              | 'name = "blub"'                    || 'local variable shades field'
        ['String name']     | []                        | 'def value = "blub"'               || 'direct local variable assignment'
        ['String name']     | []                        | 'def name = "blub"'                || 'direct shading local variable assignment'
        ['String name']     | ['def name', 'def value'] | '(name, value) = ["blub", "bli"]'  || 'multi assignment on local variables'

    }


    @Unroll
    def "invalid: #description"() {
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

        then: "Found at least one illegal assignment"
        (1.._) * errorCollector.addErrorAndContinue(*_)

        where:
        fields              | local                     | statement                          || description
        ['String name']     | []                        | 'this.name = "blub"'               || 'qualified field access'
        ['String name']     | []                        | 'name = "blub"'                    || 'unqualified field access'
        ['String name']     | []                        | '(name, value) = ["blub", "bli"]'  || 'multi assignment'
        ['String name']     | ['def value']             | 'value = name = "blub"'            || 'chain assignment'

    }




}