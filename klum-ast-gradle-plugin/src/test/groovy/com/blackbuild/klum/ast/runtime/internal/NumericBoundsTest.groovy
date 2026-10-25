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

import java.math.RoundingMode

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
        '1.00'      | false          | '1E+0'      | false          | false
        '-1.00'     | true           | '-1E+0'     | true           | true
        '-1.00'     | false          | '-1E+0'     | true           | false
        '-0.00'     | true           | '+0E+3'     | true           | true
        '-0.00'     | false          | '+0E+3'     | false          | false
        '2'         | true           | '1'         | true           | false
        '-1'        | true           | '-2'        | true           | false
        '-2.1'      | false          | '-1.9'      | false          | true
        '-1.1'      | false          | '-0.9'      | false          | true
        '-0.9'      | false          | '0.9'       | false          | true
        '0.1'       | true           | '1.1'       | true           | true
        '1E+1'      | false          | '11'        | false          | false
        '1E+1'      | false          | '12'        | false          | true
        '-11'       | false          | '-1E+1'     | false          | false
        '-12'       | false          | '-1E+1'     | false          | true
        '1E+1000000'| true           | '10E+999999'| true           | true
        '1E+1000000'| false          | '10E+999999'| true           | false
        '-2E+1000000'| false         | '-1E+1000000'| false         | true
        '-1E+1000000'| true          | '-10E+999999'| true          | true
        '2E+1000000'| true           | '1E+1000000'| true           | false
        '-1E+1000000'| true          | '-2E+1000000'| true          | false
        '1E+2147483647'| false       | '2E+2147483647'| false       | true
        '-2E+2147483647'| false      | '-1E+2147483647'| false      | true
        '0'         | false          | '1E+2147483647'| false       | true
        '-1E+2147483647'| false      | '0'         | false          | true
        '1E-2147483647'| true        | '2E-2147483647'| true        | false
        '-2E-2147483647'| true       | '-1E-2147483647'| true       | false
        '0'         | false          | '1E-2147483647'| true        | false
        '-1E-2147483647'| true       | '0'         | false          | false
        '0'         | true           | '1E-2147483647'| false       | true
        '-1E-2147483647'| false      | '0'         | true           | true
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

    def 'one-sided and unbounded intervals retain an integer: #lower #upper'() {
        given:
        def bounds = new NumericBounds([:])
        if (lower != null) bounds.add(new BigDecimal(lower), false, true)
        if (upper != null) bounds.add(new BigDecimal(upper), false, false)

        expect:
        bounds.possible(true)

        where:
        lower               | upper
        null                | null
        '1E+2147483647'      | null
        null                | '-1E+2147483647'
        '1E-2147483647'      | null
        null                | '-1E-2147483647'
    }

    def 'integer feasibility agrees with an exact reference over bounded deterministic decimals'() {
        given:
        def values = ['-10', '-3.1', '-3', '-2.99', '-2', '-1.1', '-1', '-0.99', '-0.1', '-0.00',
                      '0E+3', '0.1', '0.99', '1', '1.00', '1.01', '1.9', '2', '2.1', '2.99',
                      '3', '3.1', '9', '10', '11', '-2E+1', '-1E+1', '1E+1', '2E+1'].collect { new BigDecimal(it) }
        def mismatches = []
        int checked = 0

        when:
        values.each { lower ->
            values.each { upper ->
                [true, false].each { lowerInclusive ->
                    [true, false].each { upperInclusive ->
                        def bounds = new NumericBounds([:])
                        bounds.add(lower, lowerInclusive, true)
                        bounds.add(upper, upperInclusive, false)
                        boolean expected = reference(lower, lowerInclusive, upper, upperInclusive)
                        if (bounds.possible(true) != expected) {
                            mismatches << [lower, lowerInclusive, upper, upperInclusive, expected]
                        }
                        checked++
                    }
                }
            }
        }

        then:
        checked == 3364
        mismatches.empty
    }

    def 'decimal endpoints retain machine restrictions for #bits bit integers'() {
        given:
        BigInteger limit = BigInteger.ONE.shiftLeft(bits - 1)
        BigInteger minimum = -limit
        BigInteger maximum = limit - BigInteger.ONE
        def low = new BigDecimal(minimum)
        def high = new BigDecimal(maximum)
        def cases = [
                [low, true, low, true, true],
                [low, false, low, true, false],
                [high, true, high, true, true],
                [high, false, null, true, false],
                [null, true, low, false, false],
                [low - 0.9G, true, low - 0.1G, true, false],
                [high + 0.1G, true, high + 0.9G, true, false],
                [high - 1.5G, false, high - 0.5G, false, true],
                [low - 1G, true, high + 1G, true, true]
        ]
        def mismatches = []

        when:
        cases.each { lower, lowerInclusive, upper, upperInclusive, expected ->
            def bounds = new NumericBounds([minimum: minimum, maximum: maximum])
            if (lower != null) bounds.add(lower, lowerInclusive, true)
            if (upper != null) bounds.add(upper, upperInclusive, false)
            if (bounds.possible(true) != expected) mismatches << [lower, lowerInclusive, upper, upperInclusive, expected]
        }

        then:
        mismatches.empty

        where:
        bits << [8, 16, 32, 64]
    }

    def 'integral Min and Max conjoin with decimal endpoints: #decimalLower #decimalUpper'() {
        given:
        def bounds = new NumericBounds([minimum: -128G, maximum: 127G])
        bounds.add(new BigDecimal('2'), true, true)
        bounds.add(new BigDecimal('4'), true, false)
        bounds.add(new BigDecimal(decimalLower), lowerInclusive, true)
        bounds.add(new BigDecimal(decimalUpper), upperInclusive, false)

        expect:
        bounds.possible(true) == survives

        where:
        decimalLower | lowerInclusive | decimalUpper | upperInclusive | survives
        '1.1'        | false          | '3.1'        | false          | true
        '1.1'        | true           | '2.00'       | false          | false
        '2.00'       | false          | '3E+0'       | false          | false
        '2.00'       | false          | '3E+0'       | true           | true
        '3.1'        | true           | '3.9'        | true           | false
    }

    /** Deliberately simple and limited to the small corpus above, not used for huge-exponent cases. */
    private static boolean reference(BigDecimal lower, boolean lowerInclusive, BigDecimal upper, boolean upperInclusive) {
        BigInteger first = lowerInclusive
                ? lower.setScale(0, RoundingMode.CEILING).toBigIntegerExact()
                : lower.setScale(0, RoundingMode.FLOOR).toBigIntegerExact() + BigInteger.ONE
        BigInteger last = upperInclusive
                ? upper.setScale(0, RoundingMode.FLOOR).toBigIntegerExact()
                : upper.setScale(0, RoundingMode.CEILING).toBigIntegerExact() - BigInteger.ONE
        return first <= last
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
