/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast.compiler.internal.ast

import com.blackbuild.klum.ast.AbstractDSLSpec
import org.codehaus.groovy.control.CompilationUnit
import spock.lang.Issue

@Issue('1028')
class StructuredDerivedMapContractTest extends AbstractDSLSpec {
    def 'compiler has exact source provenance but freezes only a population exclusion for derived Map'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Palette.groovy', '''
            package derivedcontract
            import com.blackbuild.klum.ast.*
            @DSL class Palette {
                @Field(keyMapping = { throw new AssertionError('key mapping executed') })
                Map<String, String> values
            }
        ''')

        when:
        unit.compile()
        def model = unit.AST.classes.find { it.name == 'derivedcontract.Palette' }
        def field = model.getDeclaredField('values')
        def implementation = DslAstHelper.getBuilderClassOf(model)
        def adder = implementation.getDeclaredMethods('value').find {
            it.parameters*.originType*.name == ['java.lang.String']
        }
        def projection = GeneratedDslSupport.publicProjectionOf(adder)
        def plan = StructuredInputAdmissionPlan.planOf(model)

        then:
        adder.getNodeMetaData(AbstractMethodBuilder.SOURCE_FIELD_METADATA_KEY).is(field)
        projection != null
        projection.parameters*.originType*.name == ['java.lang.String']
        !projection.annotations.any { it.classNode.name.endsWith('GeneratedInput$Selection') }
        plan.entries().empty
        plan.rejections()*.source() == ['derivedcontract.Palette#values']
        plan.rejections()[0].reason().contains('value(java.lang.String, java.lang.String)')
    }
}
