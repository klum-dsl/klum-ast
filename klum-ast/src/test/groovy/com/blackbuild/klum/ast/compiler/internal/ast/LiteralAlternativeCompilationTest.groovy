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
import org.codehaus.groovy.control.Phases
import spock.lang.Issue

@Issue('952')
class LiteralAlternativeCompilationTest extends AbstractDSLSpec {
    @Issue(['952', '1030'])
    def 'does not infer structured alternatives outside the compiler-literal family: #type #aliases #options'() {
        given:
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Excluded.groovy', '''
            package literalexcluded
            import com.blackbuild.klum.ast.*
            @DSL abstract class Element { @Key String name; String value }
            @DSL class ConcreteElement extends Element {}
            @DSL class OtherElement extends Element {}
            @DSL class GenericConcrete<T> extends Element {}
            @DSL abstract class GenericElement<T> extends Element {}
            @DSL class ConcreteGeneric extends GenericElement<String> {}
        ''' + """
            @DSL class Catalog {
                @Field(alternatives = { $aliases } $options) $type elements
            }
        """)

        when: 'classification is a compiler decision before bytecode generation'
        unit.compile(Phases.CANONICALIZATION)
        def catalog = unit.AST.classes.find { it.name == 'literalexcluded.Catalog' }
        def plan = StructuredInputAdmissionPlan.planOf(catalog)

        then:
        plan != null
        plan.entries().every { it.role().name() != 'LITERAL_ALTERNATIVE_MAP' }
        if (type == 'Map<String, ConcreteElement>') {
            assert plan.entries().find { it.externalName() == 'elements' }.role().name() == 'KEYED_OWNED_MAP'
        } else {
            assert !plan.entries().any { it.externalName() == 'elements' }
        }

        where:
        type                                  | aliases                                               | options
        'Map<String, Element>'                | '[fixed: ConcreteElement, other: GenericConcrete]'      | ''
        'List<Element>'                       | '[fixed: ConcreteElement, other: OtherElement]'         | ''
        'Set<Element>'                        | '[fixed: ConcreteElement, other: OtherElement]'         | ''
        'Map<String, Element>'                | '[:]'                                                 | ''
        'Map<String, Element>'                | '[fixed: ConcreteElement]'                             | ', keyMapping = { it.name }'
        'List<Element>'                       | '[fixed: ConcreteElement]'                             | ''
        'Map<Integer, Element>'               | '[fixed: ConcreteElement]'                             | ''
        'Map<String, ConcreteElement>'        | '[fixed: ConcreteElement]'                             | ''
        'Map<String, Element>'                | '[fixed: GenericConcrete]'                             | ''
        'Map<String, GenericElement<String>>' | '[fixed: ConcreteGeneric]'                             | ''
    }
}
