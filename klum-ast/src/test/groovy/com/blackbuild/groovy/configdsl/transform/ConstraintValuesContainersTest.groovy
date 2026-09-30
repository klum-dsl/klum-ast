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
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.AutoCleanup
import spock.lang.Issue
import uk.org.webcompere.systemstubs.properties.SystemProperties

@Issue('799')
class ConstraintValuesContainersTest extends AbstractDSLSpec {

    @AutoCleanup('teardown') SystemProperties sysProps = new SystemProperties()

    def setup() { sysProps.setup() }

    def 'container constraints evaluate each completed target at the source field'() {
        given:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @ConstraintValues({ Minimum minimum, Pool pool ->
                Pool.minimumChecks++
                assert pool.slots >= minimum.value() : 'below minimum'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Minimum { int value() }

            @ConstraintValues({ Maximum maximum, Pool pool ->
                Pool.maximumChecks++
                assert pool.slots <= maximum.value() : 'above maximum'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Maximum { int value() }

            @DSL class Pool {
                static int minimumChecks
                static int maximumChecks
                static int validationCount
                String name
                int slots
                @Validate void countValidation() { validationCount++ }
            }

            @DSL class Plan {
                @Minimum(5) @Maximum(1) @Validate(Validate.Ignore)
                @Field(members = 'ownedPool') List<Pool> owned

                @Minimum(5) @Field(value = FieldType.LINK, members = 'linkedPool')
                List<Pool> linked

                @Minimum(5) @Field(value = FieldType.LINK, keyMapping = { it.name }) Map<String, Pool> indexed

                @Minimum(5) @Field(FieldType.OPTIONAL_LINK) List<Pool> optional
                @Minimum(5) @Field(value = FieldType.OPTIONAL_LINK, keyMapping = { it.name }) Map<String, Pool> optionalMap
            }
        '''
        def planType = getClass('constraints.Plan')
        def poolType = getClass('constraints.Pool')
        def external = poolType.Create.With { name 'external'; slots 2 }
        def targetResult = KlumObjectSupport.of(external).validation.result
        def targetPath = KlumObjectSupport.of(external).modelPath
        int targetValidations = poolType.validationCount
        sysProps.set('klum.validation.skipVerify', 'true')

        when:
        def plan = planType.Create.With {
            ownedPool { slots 2 }
            linked = [external, external]
            indexed = [first: external, second: external]
            optional = [external, null]
            optionalMap = [present: external, absent: null]
        }
        def issues = KlumObjectSupport.of(plan).validation.result.issues.toList()

        then:
        plan.linked[0].is(external)
        plan.linked[1].is(external)
        plan.indexed.first.is(external)
        plan.optional[0].is(external)
        issues.size() == 8
        issues*.member.count('owned') == 2
        issues*.member.count('linked') == 2
        issues*.member.count('indexed') == 2
        issues*.member.count('optional') == 1
        issues*.member.count('optionalMap') == 1
        issues*.message.any { it.contains('Minimum') && it.contains('index 0') }
        issues*.message.any { it.contains('Maximum') && it.contains('index 0') }
        issues*.message.any { it.contains("key 'first'") }
        issues*.message.any { it.contains("key 'second'") }
        issues.every { it.breadcrumbPath.contains('Plan') && it.level.name() == 'ERROR' }
        poolType.minimumChecks == 7
        poolType.maximumChecks == 1
        poolType.validationCount == targetValidations + 1
        KlumObjectSupport.of(external).validation.result.is(targetResult)
        KlumObjectSupport.of(external).modelPath == targetPath
    }

    def 'container callbacks are checked against their declared element type'() {
        when:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @ConstraintValues({ Narrow n, KafkaPool pool -> true })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Narrow {}

            @DSL abstract class Pool {}
            @DSL class KafkaPool extends Pool {}
            @DSL class Plan {
                @Narrow List<Pool> pools
                @Narrow @Field(FieldType.LINK) Map<String, Pool> mapped
            }
        '''

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.count('cannot accept declared relationship constraints.Pool') == 2
    }

    def 'owned map and PostTree link values are checked after materialization'() {
        given:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @ConstraintValues({ Bounds bounds, Pool pool ->
                assert pool.slots >= bounds.minimum() : 'resolved pool is too small'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds { int minimum() }

            @DSL class Pool {
                String name
                int slots
                @Owner Plan owner
            }

            @DSL class Plan {
                Pool source
                @Bounds(minimum = 5)
                @Field(members = 'mapPool', keyMapping = { it.name })
                Map<String, Pool> ownedMap
                @Bounds(minimum = 5)
                @Field(FieldType.LINK) List<Pool> late

                @PostTree void fillLate() { late = [source] }
            }
        '''
        sysProps.set('klum.validation.skipVerify', 'true')
        def poolType = getClass('constraints.Pool')

        when:
        def plan = getClass('constraints.Plan').Create.With {
            source { name 'source'; slots 2 }
            ownedMap = [map: poolType.Create.AsBuilder().With { name 'map'; slots 3 }]
        }
        def issues = KlumObjectSupport.of(plan).validation.result.issues.toList()

        then:
        plan.late[0].is(plan.source)
        plan.ownedMap.map.owner.is(plan)
        issues.size() == 2
        issues*.member as Set == ['ownedMap', 'late'] as Set
        issues.find { it.member == 'ownedMap' }.message.contains("key 'map'")
        issues.find { it.member == 'late' }.message.contains('index 0')
        issues.every { it.breadcrumbPath.contains('Plan') }
    }

    def 'same-root cyclic links are evaluated without traversing their targets again'() {
        given:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @ConstraintValues({ Bounds bounds, Graph graph ->
                Graph.constraintChecks++
                assert graph.limit >= bounds.minimum() : 'limit too small'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds { int minimum() }

            @DSL class Graph {
                static int constraintChecks
                static int validationCount
                int limit
                @Bounds(minimum = 5) @Field(FieldType.OPTIONAL_LINK) List<Graph> cycle
                @Validate void countValidation() { validationCount++ }
            }
        '''
        def graphType = getClass('constraints.Graph')
        sysProps.set('klum.validation.skipVerify', 'true')

        when:
        def graph = graphType.Create.With {
            limit 2
            cycle = [delegate, delegate]
        }
        def issues = KlumObjectSupport.of(graph).validation.result.issues.toList()

        then:
        graph.cycle.every { it.is(graph) }
        issues*.member == ['cycle', 'cycle']
        issues*.message.any { it.contains('index 0') }
        issues*.message.any { it.contains('index 1') }
        graphType.constraintChecks == 2
        graphType.validationCount == 1
    }

    def 'Set failures use distinct non-positional element context without revalidating linked targets'() {
        given:
        createNonDslClass '''
            package constraints
            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @ConstraintValues({ Bounds bounds, Pool pool ->
                Pool.checks[pool.name] = (Pool.checks[pool.name] ?: 0) + 1
                assert pool.slots >= bounds.minimum() : 'too few slots'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds { int minimum() }

            @DSL class Pool {
                static Map<String, Integer> checks = [:]
                static int validationCount
                String name
                int slots
                @Validate void countValidation() { validationCount++ }
            }

            @DSL class Plan {
                @Bounds(minimum = 5) @Field(FieldType.LINK) Set<Pool> linked
            }
        '''
        def poolType = getClass('constraints.Pool')
        def first = poolType.Create.With { name 'first'; slots 2 }
        def second = poolType.Create.With { name 'second'; slots 2 }
        def firstResult = KlumObjectSupport.of(first).validation.result
        def secondResult = KlumObjectSupport.of(second).validation.result
        int validationsBefore = poolType.validationCount
        sysProps.set('klum.validation.skipVerify', 'true')

        when:
        def plan = getClass('constraints.Plan').Create.With {
            linked = [first, second] as Set
        }
        def issues = KlumObjectSupport.of(plan).validation.result.issues.toList()

        then:
        plan.linked.containsAll([first, second])
        poolType.checks == [first: 1, second: 1]
        issues.size() == 2
        issues*.member == ['linked', 'linked']
        issues*.message.every { it.contains('element @') && !it.contains('index ') }
        issues*.message.toSet().size() == 2
        issues.every { it.breadcrumbPath.contains('Plan') }
        poolType.validationCount == validationsBefore
        KlumObjectSupport.of(first).validation.result.is(firstResult)
        KlumObjectSupport.of(second).validation.result.is(secondResult)
    }
}
