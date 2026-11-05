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
    def 'compiler persists the exact native derived Map adder through its existing selection'() {
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
        projection.annotations.any { it.classNode.name.endsWith('GeneratedInput$Selection') }
        plan.rejections().empty
        plan.entries().size() == 1
        plan.entries()[0].role().name() == 'PROPERTY'
        plan.entries()[0].mapKeyType() == String.name
        plan.entries()[0].payload().kind().name() == 'LIST'
        plan.entries()[0].operation().descriptorGroup() == ['value(java.lang.String):java.lang.String']
    }
    def 'same compilation override drops inherited native Map selection'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Palette.groovy', '''
            package derivedcontract
            import com.blackbuild.klum.ast.*
            @DSL class Palette {
                @Field(keyMapping = { it.toLowerCase() }) Map<String, String> values
            }
            @DSL class Child extends Palette {
                @Struct.Ignore @Builder.Method String value(String raw) { raw }
            }
        ''')

        when:
        unit.compile()
        def model = unit.AST.classes.find { it.name == 'derivedcontract.Child' }
        def plan = StructuredInputAdmissionPlan.planOf(model)

        then:
        plan.entries().empty
    }

}
