/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util

import com.blackbuild.groovy.configdsl.transform.Validate
import spock.lang.Specification


class KlumValidationResultTest extends Specification {

    def "empty validation result"() {
        when:
        def result = new KlumValidationResult("path")

        then:
        result.maxLevel == Validate.Level.NONE
        result.message == "path: NONE"
        result.messageWithFullPaths == ""
    }

    def "validation result with problems"() {
        when:
        def result = new KlumValidationResult("path")
        result.addIssue(new KlumValidationIssue("path", "field1", "Error message 1", null, Validate.Level.ERROR))
        result.addIssue(new KlumValidationIssue("path", "method1()", "Warning message 2", null, Validate.Level.WARNING))

        then:
        result.maxLevel == Validate.Level.ERROR
        result.message == """path:
- ERROR #field1: Error message 1
- WARNING #method1(): Warning message 2"""
        result.messageWithFullPaths == """ERROR path#field1: Error message 1
WARNING path#method1(): Warning message 2"""
    }

    def "results are sorted by level and member"() {
        when:
        def result = new KlumValidationResult("path")
        result.addIssue(new KlumValidationIssue("path", "method2()", "Warning message 2", null, Validate.Level.WARNING))
        result.addIssue(new KlumValidationIssue("path", "field1", "Error message 1", null, Validate.Level.ERROR))
        result.addIssue(new KlumValidationIssue("path", "field3", "Info message 3", null, Validate.Level.INFO))
        result.addIssue(new KlumValidationIssue("path", "method2()", "Error message 4", null, Validate.Level.ERROR))

        then:
        result.maxLevel == Validate.Level.ERROR
        result.message == """path:
- ERROR #field1: Error message 1
- ERROR #method2(): Error message 4
- WARNING #method2(): Warning message 2
- INFO #field3: Info message 3"""
        result.messageWithFullPaths == """ERROR path#field1: Error message 1
ERROR path#method2(): Error message 4
WARNING path#method2(): Warning message 2
INFO path#field3: Info message 3"""
    }

    def "filtered output"() {
        when:
        def result = new KlumValidationResult("path")
        result.addIssue(new KlumValidationIssue("path", "method2()", "Warning message 2", null, Validate.Level.WARNING))
        result.addIssue(new KlumValidationIssue("path", "field1", "Error message 1", null, Validate.Level.ERROR))
        result.addIssue(new KlumValidationIssue("path", "field3", "Info message 3", null, Validate.Level.INFO))
        result.addIssue(new KlumValidationIssue("path", "method2()", "Error message 4", null, Validate.Level.ERROR))

        then:
        result.maxLevel == Validate.Level.ERROR
        result.getMessage(Validate.Level.WARNING) == """path:
- ERROR #field1: Error message 1
- ERROR #method2(): Error message 4
- WARNING #method2(): Warning message 2"""
        result.getMessageWithFullPaths(Validate.Level.WARNING) == """ERROR path#field1: Error message 1
ERROR path#method2(): Error message 4
WARNING path#method2(): Warning message 2"""
    }


}
