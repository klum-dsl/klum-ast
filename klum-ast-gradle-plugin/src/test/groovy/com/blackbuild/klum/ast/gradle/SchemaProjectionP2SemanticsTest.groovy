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
package com.blackbuild.klum.ast.gradle

import groovy.json.JsonSlurper
import spock.lang.Issue
import spock.lang.Specification

import java.util.regex.Pattern

/** JDK semantic observations, not a production constraint translator or Bean validator. */
@Issue('959')
class SchemaProjectionP2SemanticsTest extends Specification {
    def 'Java full matching in the shared P2 regex corpus: #sample'() {
        expect:
        Pattern.compile(sample.pattern, sample.flags).matcher(sample.text).matches() == sample.java

        where:
        sample << new JsonSlurper().parse(SchemaProjectionP2SemanticsTest.getResource('/schema-projection-p2-patterns.json'))
    }

    def 'UTF16 units disagree with JSON character counts for supplementary text'() {
        given:
        String supplementary = '\uD83D\uDE00'
        String combining = 'e\u0301'

        when:
        int supplementaryUnits = supplementary.length()
        int supplementaryCharacters = supplementary.codePointCount(0, supplementary.length())
        int combiningUnits = combining.length()
        int combiningCharacters = combining.codePointCount(0, combining.length())

        then:
        supplementaryUnits == 2
        supplementaryCharacters == 1
        combiningUnits == 2
        combiningCharacters == 2
    }

    def 'exact decimal comparison preserves exponent scale sign and exclusive equality'() {
        given:
        BigDecimal lower = new BigDecimal('-1.2300E+2')
        BigDecimal upper = new BigDecimal('1E+3')
        BigDecimal exact = new BigDecimal('9007199254740993.0000000001')

        when:
        BigDecimal rounded = new BigDecimal(exact.doubleValue())

        then:
        lower.compareTo(new BigDecimal('-123')) == 0
        upper.compareTo(new BigDecimal('1000.00')) == 0
        exact.compareTo(new BigDecimal('9007199254740993')) > 0
        rounded.compareTo(exact) != 0
    }
}
