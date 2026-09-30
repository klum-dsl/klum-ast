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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.RelationshipConstraint
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Unroll
import uk.org.webcompere.systemstubs.properties.SystemProperties

@Issue('799')
class RelationshipConstraintTest extends AbstractDSLSpec {

    @AutoCleanup('teardown') SystemProperties sysProps = new SystemProperties()

    def setup() { sysProps.setup() }

    def 'relationship constraints read completed values and attribute each source field'() {
        given:
        createNonDslClass '''
            package constraints

            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @RelationshipConstraint({ Bounds bounds, Pool pool -> pool.slots >= bounds.minimum() })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds { int minimum() }

            @RelationshipConstraint({ AssertBounds bounds, Pool pool ->
                assert pool.slots >= bounds.minimum() : 'pool is too small'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface AssertBounds { int minimum() }

            @DSL abstract class Pool {
                static int validationCount
                int slots
                @Validate void countValidation() { validationCount++ }
            }
            @DSL class KafkaPool extends Pool {}

            @DSL class Plan {
                @Bounds(minimum = 5) @Validate(Validate.Ignore) Pool owned
                @Bounds(minimum = 5) @Field(FieldType.LINK) Pool external
                @AssertBounds(minimum = 5) @Field(FieldType.LINK) Pool repeated
                @Bounds(minimum = 5) @Field(FieldType.OPTIONAL_LINK) Pool optional
            }
        '''
        def planType = getClass('constraints.Plan')
        def poolType = getClass('constraints.Pool')
        def kafkaType = getClass('constraints.KafkaPool')
        def external = kafkaType.Create.With { slots 2 }
        int validationsBefore = poolType.validationCount
        def targetResult = KlumObjectSupport.of(external).validation.result
        def targetIssues = targetResult.issues.toList()
        def targetPath = KlumObjectSupport.of(external).modelPath
        sysProps.set('klum.validation.skipVerify', 'true')

        when:
        def plan = planType.Create.With {
            owned(kafkaType) { slots 2 }
            delegate.external = external
            delegate.repeated = external
            delegate.optional = external
        }
        def issues = KlumObjectSupport.of(plan).validation.result.issues.toList()

        then:
        plan.owned.class == kafkaType
        plan.external.is(external)
        plan.repeated.is(external)
        plan.optional.is(external)
        issues*.member.toSet() == ['owned', 'external', 'repeated', 'optional'] as Set
        issues.every { it.breadcrumbPath.contains('Plan') && it.level.name() == 'ERROR' }
        issues.find { it.member == 'repeated' }.message.contains('AssertBounds')
        issues.find { it.member == 'repeated' }.message.contains('pool is too small')
        issues.findAll { it.member != 'repeated' }.every { it.message.contains('Bounds') && it.message.contains('false') }
        poolType.validationCount == validationsBefore + 1
        KlumObjectSupport.of(external).validation.result.is(targetResult)
        targetResult.issues.toList() == targetIssues
        KlumObjectSupport.of(external).modelPath == targetPath

        when:
        KlumObjectSupport.of(plan).validation.verify()

        then:
        thrown(KlumValidationException)

        and: 'the annotation is visible but no validation member enters the public API'
        planType.getDeclaredField('owned').getAnnotation(getClass('constraints.Bounds')).minimum() == 5
        getClass('constraints.Bounds').getAnnotation(RelationshipConstraint).value() != null
        !planType.declaredFields*.name.any { it.toLowerCase().contains('constraint') }
        !planType.methods*.name.any { it.toLowerCase().contains('constraint') }
        !getBuilderClass(planType.name).methods*.name.any { it.toLowerCase().contains('constraint') }
    }

    def 'multiple domain constraints on one relationship each report their own failure'() {
        given:
        createNonDslClass '''
            package constraints

            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @RelationshipConstraint({ MinimumSlots minimum, Pool pool ->
                Pool.minimumChecks++
                assert pool.slots >= minimum.value() : 'too few slots'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface MinimumSlots { int value() }

            @RelationshipConstraint({ MaximumSlots maximum, Pool pool ->
                Pool.maximumChecks++
                assert pool.slots <= maximum.value() : 'too many slots'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface MaximumSlots { int value() }

            @DSL class Pool {
                static int minimumChecks
                static int maximumChecks
                static int validationCount
                int slots
                @Validate void countValidation() { validationCount++ }
            }

            @DSL class Plan {
                @MinimumSlots(10) @MaximumSlots(5) @Field(FieldType.LINK) Pool pool
            }
        '''
        def planType = getClass('constraints.Plan')
        def poolType = getClass('constraints.Pool')
        def target = poolType.Create.With { slots 7 }
        int validationsBefore = poolType.validationCount
        def targetResult = KlumObjectSupport.of(target).validation.result
        def targetIssues = targetResult.issues.toList()
        def targetPath = KlumObjectSupport.of(target).modelPath
        sysProps.set('klum.validation.skipVerify', 'true')

        when:
        def plan = planType.Create.With { delegate.pool = target }
        def issues = KlumObjectSupport.of(plan).validation.result.issues.toList()

        then:
        plan.pool.is(target)
        poolType.minimumChecks == 1
        poolType.maximumChecks == 1
        issues.size() == 2
        issues*.member == ['pool', 'pool']
        issues.every { it.level.name() == 'ERROR' && it.breadcrumbPath.contains('Plan') }
        issues.count { it.message.contains('MinimumSlots') && it.message.contains('too few slots') } == 1
        issues.count { it.message.contains('MaximumSlots') && it.message.contains('too many slots') } == 1
        poolType.validationCount == validationsBefore
        KlumObjectSupport.of(target).validation.result.is(targetResult)
        targetResult.issues.toList() == targetIssues
        KlumObjectSupport.of(target).modelPath == targetPath
    }

    def 'null optional relationships skip constraints and reporter calls do not invert normal completion'() {
        given:
        createNonDslClass '''
            package constraints

            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.runtime.KlumSchemaSupport
            import java.lang.annotation.*

            @RelationshipConstraint({ ReporterBounds bounds, Pool pool ->
                KlumSchemaSupport.klumValidation.errorAt('optional', 'reported')
                false
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface ReporterBounds {}

            @DSL class Pool { int slots }
            @DSL class Plan {
                @ReporterBounds @Field(FieldType.OPTIONAL_LINK) Pool optional
            }
        '''
        def planType = getClass('constraints.Plan')
        def poolType = getClass('constraints.Pool')
        def external = poolType.Create.With { slots 6 }

        when:
        def absent = planType.Create.With { }

        then:
        absent.optional == null
        KlumObjectSupport.of(absent).validation.result.issues.empty

        when:
        sysProps.set('klum.validation.skipVerify', 'true')
        def present = planType.Create.With { delegate.optional = external }

        then:
        KlumObjectSupport.of(present).validation.result.issues*.message == ['reported']
    }

    def 'valid owned and linked relationships accept a broad callback parameter'() {
        given:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @RelationshipConstraint({ Bounds b, Object model -> model != null })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds {}

            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Unrelated {}

            @DSL class Pool { int slots }
            @DSL class Plan {
                @Bounds Pool owned
                @Bounds @Field(FieldType.LINK) Pool linked
                @Bounds @Field(FieldType.OPTIONAL_LINK) Pool optional
                @Unrelated String ordinary
            }
        '''
        def planType = getClass('constraints.Plan')
        def poolType = getClass('constraints.Pool')
        def external = poolType.Create.With { slots 8 }

        when:
        def plan = planType.Create.With {
            owned { slots 9 }
            delegate.linked = external
            ordinary = 'accepted'
        }

        then:
        plan.owned.slots == 9
        plan.linked.is(external)
        plan.optional == null
        plan.ordinary == 'accepted'
        KlumObjectSupport.of(plan).validation.result.issues.empty
        RelationshipConstraint.getDeclaredMethod('value').genericReturnType.typeName == 'java.lang.Class<?>'
        !RelationshipConstraint.annotations*.annotationType()*.simpleName.any { it.startsWith('KlumCast') }
    }

    def 'required optional field remains a separate validation concern'() {
        given:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @RelationshipConstraint({ Bounds b, Pool pool -> false })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds {}

            @DSL class Pool {}
            @DSL class Plan {
                @Bounds @Required @Field(FieldType.OPTIONAL_LINK) Pool optional
            }
        '''
        sysProps.set('klum.validation.skipVerify', 'true')

        when:
        def plan = getClass('constraints.Plan').Create.With { }

        then:
        def issues = KlumObjectSupport.of(plan).validation.result.issues.toList()
        issues*.member == ['optional']
        issues.every { !it.message.contains('Relationship constraint @Bounds') }
    }

    def 'a null truth expression fails while an authored assertion may complete with a false return'() {
        given:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @RelationshipConstraint({ TruthBounds b, Pool p -> null })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface TruthBounds {}

            @RelationshipConstraint({ AssertBounds b, Pool p ->
                assert p != null
                false
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface AssertBounds {}

            @DSL class Pool {}
            @DSL class Plan {
                @TruthBounds Pool truth
                @AssertBounds Pool asserted
            }
        '''
        sysProps.set('klum.validation.skipVerify', 'true')

        when:
        def plan = getClass('constraints.Plan').Create.With {
            truth {}
            asserted {}
        }

        then:
        def issues = KlumObjectSupport.of(plan).validation.result.issues.toList()
        issues*.member == ['truth']
        issues.first().message.contains('TruthBounds')
        issues.first().message.contains('Relationship constraint expression evaluated false')
    }

    @Unroll
    def 'compiler rejects #description'() {
        when:
        createNonDslClass """
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*
            ${declaration}
            @DSL class Pool { int slots }
            @DSL class KafkaPool extends Pool {}
            @DSL class Plan { ${usage} }
        """

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains(expected)

        where:
        description             | declaration | usage | expected
        'a scalar target'       | bounds('{ Bounds b, Pool p -> true }') | '@Bounds String value' | 'must declare a non-static DSL relationship target'
        'a scalar collection'   | bounds('{ Bounds b, Pool p -> true }') | '@Bounds List<String> values' | 'must declare a non-static DSL relationship target'
        'a scalar map'          | bounds('{ Bounds b, Pool p -> true }') | '@Bounds Map<String, String> values' | 'must declare a non-static DSL relationship target'
        'a narrower target'     | bounds('{ Bounds b, KafkaPool p -> true }') | '@Bounds Pool value' | 'cannot accept declared relationship constraints.Pool'
        'a wrong first parameter' | bounds('{ String b, Pool p -> true }') | '@Bounds Pool value' | 'cannot accept annotation constraints.Bounds'
        'a one parameter callback' | bounds('{ Pool p -> true }') | '@Bounds Pool value' | 'exactly two authored parameters'
        'a non-closure callback' | bounds('String') | '@Bounds Pool value' | 'must denote a Groovy Closure'
        'a static field'        | bounds('{ Bounds b, Pool p -> true }') | '@Bounds static Pool value' | 'must declare a non-static DSL relationship target'
    }

    def 'compiler requires runtime retention and field-only annotation placement'() {
        when:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @RelationshipConstraint({ Bounds b, Pool p -> true })
            @Retention(RetentionPolicy.CLASS)
            @Target([ElementType.FIELD, ElementType.TYPE])
            @interface Bounds {}

            @DSL class Pool {}
            @DSL class Plan { @Bounds Pool value }
        '''

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('must have @Retention(RUNTIME)')
        error.message.contains('must have @Target(FIELD)')
    }

    def 'unused invalid marked declaration is diagnosed without scanning unrelated annotations'() {
        when:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @Retention(RetentionPolicy.CLASS)
            @Target(ElementType.TYPE)
            @interface Unrelated {}

            @RelationshipConstraint(String)
            @Retention(RetentionPolicy.CLASS)
            @Target([ElementType.FIELD, ElementType.PARAMETER])
            @interface Invalid {}
        '''

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('Relationship constraint annotation constraints.Invalid must have @Retention(RUNTIME)')
        error.message.contains('Relationship constraint annotation constraints.Invalid must have @Target(FIELD)')
        error.message.contains('@RelationshipConstraint.value must denote a Groovy Closure')
        !error.message.contains('Unrelated')
    }

    def 'a static-imported FIELD target remains a valid annotation declaration'() {
        when:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*
            import static java.lang.annotation.ElementType.FIELD

            @RelationshipConstraint({ Bounds b, Pool p -> true })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(FIELD)
            @interface Bounds {}

            @DSL class Pool {}
            @DSL class Plan { @Bounds Pool value }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def 'separately compiled domain annotations still receive declared-target checks'() {
        given:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @RelationshipConstraint({ Bounds b, Pool p -> p != null })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds {}

            @DSL abstract class Pool {}
            @DSL class KafkaPool extends Pool {}
        '''

        when:
        createSecondaryClass '''
            package consumer
            import com.blackbuild.klum.ast.*
            import constraints.Bounds
            import constraints.Pool
            @DSL class Plan { @Bounds @Field(FieldType.LINK) Pool value }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)

        when:
        createSecondaryClass '''
            package consumer
            import com.blackbuild.klum.ast.*
            import constraints.Pool
            import constraints.KafkaPool
            import java.lang.annotation.*

            @RelationshipConstraint({ Narrow b, KafkaPool p -> true })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Narrow {}
            @DSL class InvalidPlan { @Narrow @Field(FieldType.LINK) Pool value }
        '''

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('cannot accept declared relationship constraints.Pool')
    }

    private static String bounds(String callback) {
        """@RelationshipConstraint(${callback})
           @Retention(RetentionPolicy.RUNTIME)
           @Target(ElementType.FIELD)
           @interface Bounds {}"""
    }
}
