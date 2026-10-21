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

@Issue('932')
class StructuredInputAdmissionPlanTest extends AbstractDSLSpec {

    def "classifies direct and inherited WIRE-PLAN entries from compiler provenance"() {
        given:
        def unit = compilationUnit('''
            package admission

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            import com.fasterxml.jackson.annotation.JsonIgnore
            import com.fasterxml.jackson.annotation.JsonProperty

            enum Region { EU, US }

            @DSL class Base {
                String inheritedLabel
                @JsonProperty('renamed') Region region
                @JsonIgnore(true) String secret
                @JsonIgnore(true) @JsonProperty('still-hidden') String ignoredRename
            }

            @DSL class Child {
                @Key String name
                String value
            }

            @DSL class Parent extends Base {
                List<String> labels
                Map<String, String> annotations
                Child child
                Map<String, Child> children

                @Builder.Method
                void promote(String level) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'admission.Parent' })

        then:
        plan.model() == 'admission.Parent'
        plan.builder() == 'admission.Parent_DSL$Builder'
        plan.entries().collect { [it.externalName(), it.role().name(), it.operation(), it.payload().kind().name()] } == [
                ['annotations', 'PROPERTY', 'annotations', 'MAP'],
                ['child', 'OWNED_OBJECT', 'child', 'NESTED_OBJECT'],
                ['children', 'KEYED_OWNED_MAP', 'children', 'NESTED_OBJECT'],
                ['inheritedLabel', 'PROPERTY', 'inheritedLabel', 'SCALAR'],
                ['labels', 'PROPERTY', 'labels', 'LIST'],
                ['promote', 'OPERATION', 'promote', 'SCALAR'],
                ['renamed', 'PROPERTY', 'region', 'ENUM']
        ]
        plan.entries().find { it.externalName() == 'renamed' }.nameOrigin().name() == 'JSON_PROPERTY'
        plan.rejections()*.reason() == ['excluded by @JsonIgnore(true)', 'excluded by @JsonIgnore(true)']
    }

    def "rejects unsupported authored operations relationships and colliding external names deterministically"() {
        given:
        def unit = compilationUnit('''
            package rejection

            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            import com.fasterxml.jackson.annotation.JsonProperty

            @DSL class Child {
                @Key String name
            }

            @DSL class Parent {
                List<Child> unkeyedChildren

                @JsonProperty('duplicate') String first
                @JsonProperty('duplicate') String second

                @Builder.Method
                void reset() { }

                @Builder.Method
                void replace(String first, String second) { }
            }
        ''')

        when:
        unit.compile()
        def plan = StructuredInputAdmissionPlan.planOf(unit.AST.classes.find { it.name == 'rejection.Parent' })

        then:
        plan.entries().empty
        plan.rejections().collect { [it.source(), it.reason()] } == [
                ['rejection.Parent#first', "external-name collision for 'duplicate': rejection.Parent#first, rejection.Parent#second"],
                ['rejection.Parent#replace(java.lang.String, java.lang.String)', '@Builder.Method must declare exactly one payload'],
                ['rejection.Parent#reset()', '@Builder.Method must declare exactly one payload'],
                ['rejection.Parent#second', "external-name collision for 'duplicate': rejection.Parent#first, rejection.Parent#second"],
                ['rejection.Parent#unkeyedChildren', 'has no admitted scalar, enum, List, or Map payload']
        ]
    }

    private CompilationUnit compilationUnit(String schema) {
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('JsonAnnotations.groovy', '''
            package com.fasterxml.jackson.annotation

            @interface JsonIgnore { boolean value() default true }
            @interface JsonProperty { String value() default '' }
        ''')
        unit.addSource('Schema.groovy', schema)
        unit
    }
}
