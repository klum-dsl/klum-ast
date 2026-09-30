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
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import spock.lang.AutoCleanup
import spock.lang.Issue
import uk.org.webcompere.systemstubs.properties.SystemProperties

@Issue('799')
class ConstraintValuesBeanValidationTest extends AbstractDSLSpec {

    @AutoCleanup('teardown') SystemProperties sysProps = new SystemProperties()

    def setup() { sysProps.setup() }

    def 'Bean Validation and source field constraints retain separate results'() {
        given:
        createClass('''
            package pk
            import com.blackbuild.klum.ast.ConstraintValues
            import jakarta.validation.constraints.Min
            import java.lang.annotation.*

            @DSL class Plan {
                @Bounds(minimum = 6) List<Pool> pools
            }

            @ConstraintValues({ Bounds bounds, Pool pool ->
                assert pool.slots >= bounds.minimum() : 'schema bound failed'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds { int minimum() }

            @DSL class Pool {
                @Min(5L) int slots
            }
        ''')
        sysProps.set('klum.validation.skipVerify', 'true')

        when:
        def plan = clazz.Create.With {
            pool { slots 3 }
        }
        def sourceIssues = KlumObjectSupport.of(plan).validation.result.issues.toList()
        def childIssues = KlumObjectSupport.of(plan.pools[0]).validation.result.issues.toList()

        then:
        sourceIssues.size() == 1
        sourceIssues[0].member == 'pools'
        sourceIssues[0].message.contains('schema bound failed')
        sourceIssues[0].message.contains('index 0')
        childIssues.any { it.member == 'slots' }
        childIssues.every { it.member != 'pools' }
    }
}
