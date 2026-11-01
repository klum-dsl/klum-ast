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
package com.blackbuild.klum.ast.validation.bean

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.Validate
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import spock.lang.Issue

/** Current-runtime evidence for D2; this test does not implement or simulate a schema projector. */
@Issue('959')
class RawInputConstraintCharacterizationTest extends AbstractDSLSpec {
    private URLClassLoader artifactLoader

    private Class schema(String declarations, String name = 'Capacity') {
        loader.parseClass("""
            package rawconstraint
            import com.blackbuild.klum.ast.*
            import jakarta.validation.constraints.*
            $declarations
        """)
        artifactLoader = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName("rawconstraint.$name", false, artifactLoader)
    }

    def cleanup() { artifactLoader?.close() }

    private Class percentageSchema(String extra = '') {
        schema("""
            @DSL class Capacity {
                static int creations
                @Min(0L) @Max(100L) Integer percentage
                @PostCreate void created() { creations++ }
                $extra
            }
        """)
    }

    def 'unmodified direct bounds accept endpoints omission and mathematical integers: #input'() {
        given:
        def type = percentageSchema()

        when:
        def model = KlumStructuredInput.readRoot(type, null, input, 'capacity.json')

        then:
        model.percentage == expected
        type.creations == 1
        KlumObjectSupport.of(model).validation.result.issues.empty

        where:
        input                 | expected
        [:]                   | null
        [percentage: 0]       | 0
        [percentage: 100]     | 100
        [percentage: 100.0G]  | 100
    }

    def 'out of bound integral input passes payload checks and fails final validation: #value'() {
        given:
        def type = percentageSchema()

        when:
        KlumStructuredInput.readRoot(type, null, [percentage: value], 'capacity.json')

        then:
        def failure = thrown(KlumValidationException)
        type.creations == 1
        failure.message.contains('percentage')
        !failure.message.contains('capacity.json')

        where:
        value << [-1, 101]
    }

    @Issue('952')
    def 'invalid raw kinds and machine overflow reject when reached after root creation: #value'() {
        given:
        def type = percentageSchema()

        when:
        KlumStructuredInput.readRoot(type, null, [percentage: value], 'capacity.json')

        then:
        def failure = thrown(KlumModelException)
        !(failure instanceof KlumValidationException)
        failure.message.contains('capacity.json')
        failure.message.contains('/percentage')
        type.creations == 1

        where:
        value << [null, false, '', '50', 0.5G, 2147483648G, -2147483649G]
    }

    def 'lifecycle can repair values a raw authoring bound would reject: #value'() {
        given:
        def type = percentageSchema('@PostTree void replace() { percentage = 50 }')

        when:
        def model = KlumStructuredInput.readRoot(type, null, [percentage: value], 'repair.json')

        then:
        model.percentage == 50
        KlumObjectSupport.of(model).validation.result.issues.empty

        where:
        value << [-1, 101]
    }

    def 'lifecycle can invalidate a value a raw authoring bound would accept'() {
        given:
        def type = percentageSchema('@PostTree void replace() { percentage = 101 }')

        when:
        KlumStructuredInput.readRoot(type, null, [percentage: 50], 'replace.json')

        then:
        thrown(KlumValidationException)
        type.creations == 1
    }

    def 'literal initializer is a fallback rather than an input const: #input'() {
        given:
        def type = schema('''
            @DSL class Capacity {
                @Min(0L) @Max(100L) Integer percentage = 75
            }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(type, null, input, 'literal.json')

        then:
        model.percentage == expected

        where:
        input             | expected
        [:]               | 75
        [percentage: 0]   | 0
        [percentage: 100] | 100
    }

    def 'omission can fail final presence validation without raw required semantics'() {
        given:
        def type = percentageSchema('@NotNull String name; @Required String description')

        when:
        KlumStructuredInput.readRoot(type, null, [:], 'absent.json')

        then:
        def failure = thrown(KlumValidationException)
        type.creations == 1
        failure.message.contains('name')
        failure.message.contains('description')
    }

    def 'defaults fill absence and Groovy false values but preserve boolean false'() {
        given:
        loader.parseClass '''
            package rawconstraint
            import com.blackbuild.klum.ast.layer3.DefaultValues
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            @Retention(RetentionPolicy.RUNTIME) @DefaultValues
            @interface Literals { int percentage(); String label(); boolean enabled(); }
        '''
        def type = schema('''
            @DSL @Literals(percentage=75, label='filled', enabled=true)
            class Capacity {
                @Min(0L) @Max(100L) Integer percentage
                String label
                Boolean enabled
            }
        ''')

        when:
        def omitted = KlumStructuredInput.readRoot(type, null, [:], 'absent.json')
        def supplied = KlumStructuredInput.readRoot(type, null,
                [percentage: 0, label: '', enabled: false], 'false-values.json')

        then:
        omitted.percentage == 75
        omitted.label == 'filled'
        omitted.enabled
        supplied.percentage == 75
        supplied.label == 'filled'
        supplied.enabled == false
    }

    def 'default coercion does not admit the same raw numeric String'() {
        given:
        loader.parseClass '''
            package rawconstraint
            import com.blackbuild.klum.ast.layer3.DefaultValues
            import java.lang.annotation.Retention
            import java.lang.annotation.RetentionPolicy
            @Retention(RetentionPolicy.RUNTIME) @DefaultValues
            @interface Literal { String percentage(); }
        '''
        def type = schema('''
            @DSL @Literal(percentage='50') class Capacity {
                @Min(0L) @Max(100L) Integer percentage
            }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(type, null, [:], 'default.json')

        then:
        model.percentage == 50

        when:
        KlumStructuredInput.readRoot(type, null, [percentage: '50'], 'raw.json')

        then:
        thrown(KlumModelException)
    }

    def 'authored conversion operation is not the constrained property'() {
        given:
        def type = percentageSchema('@Builder.Method void fromText(String value) { percentage = Integer.valueOf(value) }')

        when:
        def model = KlumStructuredInput.readRoot(type, null, [fromText: '50'], 'operation.json')

        then:
        model.percentage == 50

        when:
        KlumStructuredInput.readRoot(type, null, [fromText: '101'], 'operation.json')

        then:
        thrown(KlumValidationException)
    }

    def 'nested owned input and later active Builder application share final validation'() {
        given:
        def type = schema('''
            @DSL class Capacity { Child child }
            @DSL class Child { @Min(0L) @Max(100L) Integer percentage }
        ''')

        when:
        def model = type.Create.With {
            KlumStructuredInput.applyToBuilder(delegate, [child: [percentage: 101]], 'first.json')
            KlumStructuredInput.applyToBuilder(delegate, [child: [percentage: 50]], 'second.json')
        }

        then:
        model.child.percentage == 50
        KlumObjectSupport.of(model.child).validation.result.issues.empty

        when:
        KlumStructuredInput.readRoot(type, null, [child: [percentage: 101]], 'nested.json')

        then:
        thrown(KlumValidationException)
    }

    def 'inherited renamed admitted properties retain final constraints and ignored names reject'() {
        given:
        // Literal annotation declarations match the existing reader tests; no Jackson runtime is used.
        loader.parseClass '''
            package com.fasterxml.jackson.annotation
            @interface JsonProperty { String value() default '' }
            @interface JsonIgnore { boolean value() default true }
        '''
        def type = schema('''
            import com.fasterxml.jackson.annotation.JsonProperty
            import com.fasterxml.jackson.annotation.JsonIgnore
            @DSL class Base {
                @JsonProperty('percent') @Min(0L) @Max(100L) Integer percentage
                @JsonIgnore @Min(0L) Integer hidden
            }
            @DSL class Capacity extends Base {}
        ''')

        when:
        def model = KlumStructuredInput.readRoot(type, null, [percent: 100], 'renamed.json')

        then:
        model.percentage == 100
        model.hidden == null

        when:
        KlumStructuredInput.readRoot(type, null, [percent: 101], 'renamed.json')

        then:
        thrown(KlumValidationException)

        when:
        KlumStructuredInput.readRoot(type, null, [(name): 50], 'excluded.json')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/' + name)

        where:
        name << ['percentage', 'hidden']
    }

    def 'custom groups are not active in the adapters default validation call'() {
        given:
        def type = schema('''
            interface Later {}
            @DSL class Capacity { @Min(value=0L, groups=Later) Integer percentage }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(type, null, [percentage: -1], 'group.json')

        then:
        model.percentage == -1
        KlumObjectSupport.of(model).validation.result.issues.empty
    }

    def 'warning payload is a stored validation issue rather than a raw schema error'() {
        given:
        def type = schema('''
            import com.blackbuild.klum.ast.validation.bean.Level
            @DSL class Capacity { @Min(value=0L, payload=Level.WARNING) Integer percentage }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(type, null, [percentage: -1], 'warning.json')
        def result = KlumObjectSupport.of(model).validation.result

        then:
        model.percentage == -1
        result.issues*.level == [Validate.Level.WARNING]
        result.issues*.member == ['percentage']
    }
    def 'supported integral targets retain the percentage endpoints: #target'() {
        given:
        def type = schema("""
            @DSL class Capacity { @Min(0L) @Max(100L) $target percentage }
        """)

        when:
        def low = KlumStructuredInput.readRoot(type, null, [percentage: 0], 'low.json')
        def high = KlumStructuredInput.readRoot(type, null, [percentage: 100], 'high.json')

        then:
        low.percentage == 0
        high.percentage == 100

        where:
        target << ['byte', 'Byte', 'short', 'Short', 'int', 'Integer', 'long', 'Long', 'BigInteger']
    }

    def 'empty String and false are supplied values while their null forms reject'() {
        given:
        def type = schema('''
            @DSL class Capacity { String label; Boolean enabled }
        ''')

        when:
        def model = KlumStructuredInput.readRoot(type, null, [label: '', enabled: false], 'values.json')

        then:
        model.label == ''
        model.enabled == false

        when:
        KlumStructuredInput.readRoot(type, null, [(member): null], 'null.json')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/' + member)

        where:
        member << ['label', 'enabled']
    }

    def 'explicit Default group still validates at runtime though deferred by the tiny profile'() {
        given:
        def type = schema('''
            import jakarta.validation.groups.Default
            @DSL class Capacity { @Min(value=0L, groups=Default) Integer percentage }
        ''')

        when:
        KlumStructuredInput.readRoot(type, null, [percentage: -1], 'explicit-default.json')

        then:
        thrown(KlumValidationException)
    }

}
