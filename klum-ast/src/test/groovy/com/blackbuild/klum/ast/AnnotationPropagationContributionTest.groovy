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
package com.blackbuild.klum.ast

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('890')
class AnnotationPropagationContributionTest extends AbstractDSLSpec {
    private static final String DEFINITIONS = '''
        import java.lang.annotation.*
        @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
        @Repeatable(Labels) @interface Label { String value() default 'selected default' }
        @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
        @interface Labels { Label[] value() }
        @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
        @PropagateAnnotation(Required) @PropagateAnnotation(Label)
        @interface RepeatedRequests { String value() default 'domain default' }
        @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
        @PropagateAnnotation.List([@PropagateAnnotation(Required), @PropagateAnnotation(Label)])
        @interface ContainerRequests { String value() default 'domain default' }
        @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
        @PropagateAnnotation(Label) @Repeatable(Policies)
        @interface Policy { String value() default 'domain default' }
        @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
        @interface Policies { Policy[] value() default [@Policy] }
        @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
        @interface Unrelated { Policy[] value() }
        @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
        @PropagateAnnotation(Label) @interface OtherPolicy {}
        @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
        @PropagateAnnotation.List([@PropagateAnnotation(Label), @PropagateAnnotation(Label)])
        @interface DuplicateRequests {}
        @Target(ElementType.METHOD) @interface MethodOnly {}
        @PropagateAnnotation(MethodOnly) @interface WrongTarget {}
        @Target(ElementType.FIELD) @interface SelectedMember { String required() }
        @PropagateAnnotation(SelectedMember) @interface MissingMember {}
        @PropagateAnnotation(Self) @interface Self {}
        @PropagateAnnotation(Self) @interface SelfPolicy {}
        @PropagateAnnotation(CycleB) @interface CycleA {}
        @PropagateAnnotation(CycleA) @interface CycleB {}
        @PropagateAnnotation(CycleA) @interface CyclePolicy {}
    '''

    /** Each test consumes either source metadata, emitted Groovy metadata, or javac metadata. */
    private String library(String kind) {
        if (kind == 'Java binary')
            return 'import com.blackbuild.klum.ast.propagation.ContributionPolicies.*'
        if (kind == 'Groovy binary') {
            createSecondaryClass(DEFINITIONS)
            return ''
        }
        return DEFINITIONS
    }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Annotation-Propagation.md#repeatable-requests-and-containers')
    def 'repeated and explicit request containers retain domains and selected defaults (#kind, #policy)'() {
        given:
        String definitions = library(kind)

        when:
        createSecondaryClass("""
            $definitions
            @DSL class Order { @$policy('configured domain') String name }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        def annotations = Order.getDeclaredField('name').declaredAnnotations
        annotations.find { it.annotationType().simpleName == policy }.value() == 'configured domain'
        annotations.find { it.annotationType().simpleName == 'Label' }.value() == 'selected default'
        annotations.find { it.annotationType() == Validate }.message() == ''
        Order.Create.With { name 'orders' }.name == 'orders'

        where:
        [kind, policy] << [['Groovy source', 'Groovy binary', 'Java binary'],
                          ['RepeatedRequests', 'ContainerRequests']].combinations()
    }

    def 'genuine applied containers retain configured or default domain members (#kind, #use)'() {
        given:
        String definitions = library(kind)

        when:
        createSecondaryClass("""
            $definitions
            @DSL class Order { $use String name }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        def field = Order.getDeclaredField('name')
        def container = field.declaredAnnotations.find { it.annotationType().simpleName == 'Policies' }
        container.value()*.value() == [retained]
        field.declaredAnnotations.find { it.annotationType().simpleName == 'Label' }.value() == 'selected default'

        where:
        [kind, use, retained] << ['Groovy source', 'Groovy binary', 'Java binary'].collectMany { kind ->
            [[kind, "@Policies([@Policy('configured')])", 'configured'],
             [kind, '@Policies', 'domain default']]
        }
    }

    def 'every propagated duplicate fails even when selected annotation is repeatable (#kind, #use)'() {
        given:
        String definitions = library(kind)

        when:
        createSecondaryClass("""
            $definitions
            @DSL class Order { $use String name }
        """)

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.errorCollector.errorCount == 1
        error.message.contains('duplicate propagated annotation')
        error.message.contains('already supplied by')
        error.message.contains('Label on Order.name')
        error.message.contains('Policy') || error.message.contains('DuplicateRequests')

        where:
        [kind, use] << [['Groovy source', 'Groovy binary', 'Java binary'], [
                '@Label @Policy', '@Labels([@Label]) @Policy', '@Policy @OtherPolicy',
                '@Policy @Policy', '@Policies([@Policy, @Policy])', '@DuplicateRequests'
        ]].combinations()
    }

    def 'explicit-only repeatables and unrelated annotation arrays keep ordinary behavior (#kind)'() {
        given:
        String definitions = library(kind)

        when:
        createSecondaryClass("""
            $definitions
            @DSL class Order {
                @Label('one') @Label('two') String direct
                @Labels([@Label('three'), @Label('four')]) String container
                @Unrelated([@Policy]) String inert
            }
        """)

        then:
        notThrown(MultipleCompilationErrorsException)
        def labels = Order.getDeclaredField('direct').declaredAnnotations.find { it.annotationType().simpleName == 'Labels' }
        labels.value()*.value() == ['one', 'two']
        def explicit = Order.getDeclaredField('container').declaredAnnotations.find { it.annotationType().simpleName == 'Labels' }
        explicit.value()*.value() == ['three', 'four']
        Order.getDeclaredField('inert').declaredAnnotations*.annotationType()*.simpleName == ['Unrelated']

        where:
        kind << ['Groovy source', 'Groovy binary', 'Java binary']
    }

    def 'legality and one-hop failures retain domain selected and declaration identities (#kind, #policy)'() {
        given:
        String definitions = library(kind)

        when:
        createSecondaryClass("""
            $definitions
            @DSL class Order { @$policy String name }
        """)

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.errorCollector.errorCount == 1
        error.message.contains(policy)
        error.message.contains(selected)
        error.message.contains('Order.name')
        error.message.contains(problem)
        !error.message.contains('ClassCastException')

        where:
        [kind, policy, selected, problem] << ['Groovy source', 'Groovy binary', 'Java binary'].collectMany { kind ->
            [[kind, 'WrongTarget', 'MethodOnly', 'target FIELD'],
             [kind, 'MissingMember', 'SelectedMember', "member 'required' has no default"],
             [kind, 'SelfPolicy', 'Self', 'nested propagation'],
             [kind, 'CyclePolicy', 'CycleA', 'nested propagation']]
        }
    }
}
