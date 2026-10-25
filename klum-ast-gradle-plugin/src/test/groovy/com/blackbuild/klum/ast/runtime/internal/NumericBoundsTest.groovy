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

import spock.lang.Issue
import spock.lang.Specification

@Issue('959')
class NumericBoundsTest extends Specification {
    def 'exact integer survival for #lower to #upper with inclusivity #lowerInclusive #upperInclusive'() {
        given:
        def bounds = new NumericBounds([:])
        bounds.add(new BigDecimal(lower), lowerInclusive, true)
        bounds.add(new BigDecimal(upper), upperInclusive, false)

        expect:
        bounds.possible(true) == survives

        where:
        lower       | lowerInclusive | upper       | upperInclusive | survives
        '1.00'      | true           | '1E+0'      | true           | true
        '1.00'      | false          | '1E+0'      | true           | false
        '1.00'      | true           | '1E+0'      | false          | false
        '0.1'       | true           | '0.9'       | true           | false
        '-0.9'      | true           | '-0.1'      | true           | false
        '-0.1'      | false          | '0.1'       | false          | true
        '0'         | false          | '1'         | false          | false
        '-1'        | false          | '0'         | false          | false
        '1'         | false          | '2'         | false          | false
        '-2'        | false          | '-1'        | false          | false
        '1'         | false          | '2.1'       | false          | true
        '0'         | false          | '2'         | false          | true
        '1E+1000000'| false          | '2E+1000000'| false          | true
        '0'         | false          | '1E+1000000'| false          | true
        '1'         | false          | '1E+1000000'| false          | true
        '-1E+1000000'| false         | '-1'        | false          | true
        '1E-2147483647' | true       | '0.1'       | true           | false
        '-0.1'      | true           | '-1E-2147483647' | true       | false
        '-1E-2147483647' | false     | '1E-2147483647' | false      | true
    }

    def 'equal endpoints choose exclusion independently of authored scale and order: #reverse'() {
        given:
        def schema = [:]
        def bounds = new NumericBounds(schema)
        def endpoints = reverse ? [false, true] : [true, false]

        when:
        endpoints.each { bounds.add(new BigDecimal('2.00'), it, true) }
        endpoints.each { bounds.add(new BigDecimal('3E+0'), it, false) }
        bounds.write(schema)

        then:
        schema == [exclusiveMinimum: 2.00G, exclusiveMaximum: 3G]
        !bounds.possible(true)
        bounds.possible(false)

        where:
        reverse << [false, true]
    }
}
