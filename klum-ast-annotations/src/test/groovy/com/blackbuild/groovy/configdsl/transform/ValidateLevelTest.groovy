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
package com.blackbuild.groovy.configdsl.transform

import spock.lang.Specification


class ValidateLevelTest extends Specification {

    def "test validate level"() {
        expect:
        left.combine(right) == expected

        where:
        left                | right               || expected
        Validate.Level.NONE  | Validate.Level.NONE  || Validate.Level.NONE
        Validate.Level.NONE  | Validate.Level.ERROR || Validate.Level.ERROR
        Validate.Level.NONE  | Validate.Level.WARNING  || Validate.Level.WARNING
        Validate.Level.NONE  | Validate.Level.INFO  || Validate.Level.INFO
        Validate.Level.ERROR | Validate.Level.NONE  || Validate.Level.ERROR
    }

    def "from String matches numbers, label (upper and lower)"() {
        expect:
        Validate.Level.fromString(input) == expected

        where:
        input          || expected
        "0"            || Validate.Level.NONE
        "1"            || Validate.Level.INFO
        "2"            || Validate.Level.WARNING
        "3"            || Validate.Level.DEPRECATION
        "4"            || Validate.Level.ERROR
        "none"         || Validate.Level.NONE
        "info"         || Validate.Level.INFO
        "warning"      || Validate.Level.WARNING
        "deprecation"  || Validate.Level.DEPRECATION
        "error"        || Validate.Level.ERROR
        "ERROR"        || Validate.Level.ERROR
        "WARNING"      || Validate.Level.WARNING
        "INFO"         || Validate.Level.INFO
        "NONE"         || Validate.Level.NONE
        "DEPRECATION"  || Validate.Level.DEPRECATION
    }

}