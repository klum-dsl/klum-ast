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
class BoundedAsciiPatternTest extends Specification {
    def 'recognize only the complete bounded grammar: #source'() {
        when:
        def result = BoundedAsciiPattern.recognize(source)
        then:
        result.assertion() == '^' + body + '$'
        result.reason() == null
        where:
        source | body
        'a' | 'a'
        '^a$' | 'a'
        '[A-Za-z0-9_.-]{0,62}' | '[A-Za-z0-9_.-]{0,62}'
        '[-][.]_-' | '[-][.]_-'
        'a?b{0}c{0,0}d{255}' | 'a?b{0}c{0,0}d{255}'
        '[aaA-Z0-9]' | '[aaA-Z0-9]'
        ('a' * 32) | ('a' * 32)
        ('a{255}' * 4 + 'a{4}') | ('a{255}' * 4 + 'a{4}')
        ('[' + 'a' * 254 + ']') | ('[' + 'a' * 254 + ']')
    }

    def 'reject excluded syntax without invoking a regex engine: #source'() {
        when:
        def result = BoundedAsciiPattern.recognize(source)
        then:
        result.assertion() == null
        result.reason() == 'unsupported Pattern source syntax'
        where:
        source << ['', '^$', '^a', 'a$', 'a^b', '^^a$$', '[', '[]', '[--]', '[a-b]', '[A-z]', '[9-0]',
                   '[a-f]', '[a-bc]', '[^a]', '[a&&b]', '[[a]]', '.', 'a*', 'a+', 'a{1,}', 'a{,2}',
                   'a{2,1}', 'a{01}', 'a{-1}', 'a{ 1}', 'a{1}+', 'a??', '(a)', 'a|b', '(?i)a',
                   '\\d', '\\n', '\\Qx\\E', '\\u0061', '\\A', 'é', '\n', '\ud800']
    }

    def 'fall back beyond every admission ceiling: #source'() {
        when:
        def result = BoundedAsciiPattern.recognize(source)
        then:
        result.assertion() == null
        result.reason() == 'Pattern admission ceiling'
        where:
        source << ['a{256}', 'a{99999999999999999999999999}', 'a' * 33, '[' + 'a' * 255 + ']', 'a{255}' * 4 + 'a{5}']
    }
}
