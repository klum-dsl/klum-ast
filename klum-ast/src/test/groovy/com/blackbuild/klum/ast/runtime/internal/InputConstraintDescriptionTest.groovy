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
package com.blackbuild.klum.ast.runtime.internal

import com.blackbuild.klum.ast.AbstractDSLSpec
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('959')
class InputConstraintDescriptionTest extends AbstractDSLSpec {
    private URLClassLoader artifacts
    def cleanup() { artifacts?.close() }

    private def facts(String source) {
        createClass("""
            package composition
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*
            $source
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def type = Class.forName('composition.Service', false, artifacts)
        new InputContractResolver().read(type).constraintProfiles().values().first().fields()
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#composed-constraints-and-documentary-meta-annotations')
    def 'document a domain rule without adding enforcement'() {
        when:
        def fields = facts('''
            @DSL class Service { @TrafficPercentage Integer traffic }
            @Struct.ConstraintDescription('Percentage of traffic sent to this service.')
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
            @interface TrafficPercentage {
                String message() default 'traffic'
                Class[] groups() default []
                Class[] payload() default []
            }
        ''')
        def rules = fields.traffic.rules()
        then:
        rules.find { it.annotation() == 'composition.TrafficPercentage' }.description() == 'Percentage of traffic sent to this service.'
        rules.size() == 1
        !rules.first().eligible()
        rules.first().origin() == 'DOCUMENTARY'
        rules.first().owner() == ''
        Class.forName('composition.Service', false, artifacts).Create.FromStruct([traffic: -1], 'service.yaml').traffic == -1
        Class.forName('composition.Service', false, artifacts).Create.FromStruct([:], 'service.yaml').traffic == null

    }

    @Issue('1074')
    def 'retains direct and grouped NotBlank eligibility in compiled constraint facts'() {
        given:
        createSecondaryClass('''
            package jakarta.validation
            import java.lang.annotation.*
            @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.ANNOTATION_TYPE)
            @interface Constraint { Class[] validatedBy() default [] }
        ''')
        createSecondaryClass('''
            package jakarta.validation.constraints
            import jakarta.validation.Constraint
            import java.lang.annotation.*
            @Constraint @Retention(RetentionPolicy.RUNTIME)
            @Target([ElementType.FIELD, ElementType.METHOD, ElementType.ANNOTATION_TYPE])
            @interface NotBlank {
                Class[] groups() default []
                Class[] payload() default []
            }
        ''')

        when:
        def fields = facts('''
            import jakarta.validation.constraints.NotBlank
            @DSL class Service {
                @NotBlank String name
                @NotBlank(groups=Later) String grouped
            }
            interface Later {}
        ''')

        then:
        fields.name.rules().with {
            size() == 1
            first().annotation() == 'jakarta.validation.constraints.NotBlank'
            first().eligible()
            first().origin() == 'DIRECT'
        }
        fields.grouped.rules().with {
            size() == 1
            first().annotation() == 'jakarta.validation.constraints.NotBlank'
            !first().eligible()
            first().origin() == 'DIRECT'
        }
    }

    def 'ICD is restricted to annotation types and constant String text: #declaration'() {
        when:
        createClass("""
            import com.blackbuild.klum.ast.*
            $declaration
        """)
        then:
        thrown(MultipleCompilationErrorsException)
        where:
        declaration << [
            "@DSL class Service { @Struct.ConstraintDescription('field') String value }",
            '@Struct.ConstraintDescription("value ${1}") @interface Rule {}'
        ]
    }
}
