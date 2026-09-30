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
package com.blackbuild.klum.ast.jackson

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumObjectSupport
import com.fasterxml.jackson.databind.ObjectMapper
import spock.lang.AutoCleanup
import spock.lang.Issue
import uk.org.webcompere.systemstubs.properties.SystemProperties

@Issue('799')
class ConstraintValuesJacksonTest extends AbstractDSLSpec {

    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
    @AutoCleanup('teardown') SystemProperties sysProps = new SystemProperties()

    def setup() { sysProps.setup() }

    def 'Jackson populated collection and map values are constrained after import'() {
        given:
        createClass('''
            package pk
            import com.blackbuild.klum.ast.ConstraintValues
            import java.lang.annotation.*

            @DSL class Plan {
                @Bounds(minimum = 5) List<Pool> pools
                @Bounds(minimum = 5) Map<String, Pool> indexed
            }

            @ConstraintValues({ Bounds bounds, Pool pool ->
                assert pool.slots >= bounds.minimum() : 'imported pool is too small'
            })
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.FIELD)
            @interface Bounds { int minimum() }

            @DSL class Pool {
                @Key String name
                int slots
            }
        ''')
        sysProps.set('klum.validation.skipVerify', 'true')

        when:
        def plan = mapper.readValue('''{
            "pools":[{"name":"first","slots":2},{"name":"second","slots":7}],
            "indexed":{"primary":{"slots":3},"secondary":{"slots":8}}
        }''', clazz)
        def issues = KlumObjectSupport.of(plan).validation.result.issues.toList()

        then:
        plan.pools*.slots == [2, 7]
        plan.indexed.primary.slots == 3
        issues.size() == 2
        issues*.member as Set == ['pools', 'indexed'] as Set
        issues.find { it.member == 'pools' }.message.contains('index 0')
        issues.find { it.member == 'indexed' }.message.contains("key 'primary'")
        issues*.breadcrumbPath == ['<root>()', '<root>()']
    }
}
