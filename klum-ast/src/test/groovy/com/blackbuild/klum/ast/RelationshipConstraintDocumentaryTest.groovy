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

import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('799')
@Tag('documentary')
class RelationshipConstraintDocumentaryTest extends AbstractDSLSpec {

    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Validation.md#domain-defined-relationship-constraints')
    def 'checks a completed child against domain-defined bounds after applying defaults'() {
        given:
        createNonDslClass '''
            package example

            import com.blackbuild.klum.ast.*
            import com.blackbuild.klum.ast.layer3.DefaultValues
            import java.lang.annotation.*

            @DefaultValues
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface PoolDefaults { int slots() }
        '''
        createSecondaryClass '''
            package example

            import com.blackbuild.klum.ast.*
            import java.lang.annotation.*

            @RelationshipConstraint({ PoolBounds bounds, Pool pool ->
                assert pool.slots in bounds.minSlots()..bounds.maxSlots() :
                    "slots must be within ${bounds.minSlots()}..${bounds.maxSlots()}"
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface PoolBounds {
                int minSlots()
                int maxSlots()
            }

            @DSL class Pool { int slots }
            @DSL class Plan {
                @PoolDefaults(slots = 8)
                @PoolBounds(minSlots = 5, maxSlots = 10)
                Pool pool
            }
        '''
        Class<?> planType = getClass('example.Plan')

        when:
        def plan = planType.Create.With { pool {} }

        then:
        plan.pool.slots == 8

        when:
        planType.Create.With { pool { slots 12 } }

        then:
        KlumValidationException error = thrown()
        error.message.contains('#pool:')
        error.message.contains('PoolBounds')
        error.message.contains('slots must be within 5..10')
    }
}
