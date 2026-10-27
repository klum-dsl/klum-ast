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
class JsonExampleTest extends Specification {
    def 'preserve a complete JSON value including numeric lexemes and JSON null: #text'() {
        expect:
        JsonExample.parse(text, 'Service#value', JsonExampleTest, 1).json() == text

        where:
        text << ['{}', '[]', '-1.234e-5', '1E+2', 'null', 'true', 'false', '42', '-0', '0.00E+9999999999999999999999',
                 '9007199254740993.0000000001', '""', '"a  b"', '"\\u0000"',
                 '"\\ud83d\\ude00"', '"😀"', '{"a":[true,null,{"b":"c"}]}', ' \n [1,2] \t',
                 '[' * 32 + 'null' + ']' * 32]
    }

    def 'reject malformed unsafe ambiguous or unbounded JSON without echoing payload: #kind'() {
        when:
        JsonExample.parse(text, 'Service#value', JsonExampleTest, 2)

        then:
        def failure = thrown(IllegalArgumentException)
        failure.message.contains('Service#value')
        failure.message.contains('artifact')
        failure.message.contains('example 2, UTF-16 offset')
        !failure.message.contains('SECRET_PAYLOAD')

        where:
        kind        | text
        'empty'     | ''
        'word'      | 'SECRET_PAYLOAD'
        'comment'   | '/*SECRET_PAYLOAD*/null'
        'trailing'  | 'null SECRET_PAYLOAD'
        'NaN'       | 'NaN'
        'infinity'  | 'Infinity'
        'zero'      | '01'
        'plus'      | '+1'
        'decimal'   | '1.'
        'exponent'  | '1e+'
        'minus'     | '-'
        'comma'     | '[1,]'
        'key'       | '{a:1}'
        'colon'     | '{"a" 1}'
        'duplicate' | '{"SECRET_PAYLOAD":1,"SECRET_PAYLOAD":2}'
        'decoded'   | '{"a":1,"\\u0061":2}'
        'escape'    | '"\\q"'
        'hex'       | '"\\uFFFG"'
        'short'     | '"\\uFFF"'
        'raw'       | '"\nSECRET_PAYLOAD"'
        'bom'       | '\ufeffnull'
        'bom text'  | '"\\ufeff"'
        'high'      | '"\\ud800"'
        'low'       | '"\\udc00"'
        'raw high'  | '"' + ((char) 0xd800) + '"'
        'unclosed'  | '"SECRET_PAYLOAD'
         'bad true'  | 'tru'
        'slash end' | '"' + ((char) 92)
        'unicode end' | '"' + ((char) 92) + 'u'
        'raw low'   | '"' + ((char) 0xdc00) + '"'
        'array end' | '[null'
        'object end' | '{"a":null'
        'object comma' | '{"a":1,}'
        'depth'     | '[' * 33 + 'null' + ']' * 33
    }
}
