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
class BuiltinConstraintCatalogTest extends Specification {
    def 'select the existing numeric family with exact endpoints: #annotation on #type'() {
        when:
        def result = BuiltinConstraintCatalog.project(entry(type), rule(annotation, facts), false)

        then:
        result.projected()
        result.bounds() == [[(keyword): new BigDecimal(literal)]]
        result.bounds()[0][keyword].toString() == literal
        result.patterns().empty
        result.reason() == null

        where:
        annotation   | type       | facts                                                | keyword            | literal
        'Min'        | Long       | [value: '-9223372036854775808']                        | 'minimum'          | '-9223372036854775808'
        'Max'        | BigInteger | [value: '9223372036854775807']                         | 'maximum'          | '9223372036854775807'
        'DecimalMin' | BigDecimal | [decimalValue: '9007199254740993.0000000001']           | 'minimum'          | '9007199254740993.0000000001'
        'DecimalMax' | BigDecimal | [decimalValue: '1.2300E+100', inclusive: false]         | 'exclusiveMaximum' | '1.2300E+100'
        'DecimalMin' | Byte.TYPE  | [decimalValue: '0.10', inclusive: false]               | 'exclusiveMinimum' | '0.10'
        'DecimalMax' | BigInteger | [decimalValue: '9.50']                                 | 'maximum'          | '9.50'
    }

    def 'select Pattern with its ordered full-match and typed ASCII assertions'() {
        when:
        def result = BuiltinConstraintCatalog.project(entry(String), rule('Pattern', [regexp: '[A-Z]{2}']), false)

        then:
        result.projected()
        result.bounds().empty
        result.patterns() == [[pattern: '^[A-Z]{2}$'], [not: [type: 'string', pattern: '[^ -~]']]]
        result.reason() == null
    }

    def 'return a reasoned unsupported result for #annotation: #reason'() {
        when:
        def result = BuiltinConstraintCatalog.project(entry(type, site), rule(annotation, facts), groupRedefined)

        then:
        !result.projected()
        result.bounds().empty
        result.patterns().empty
        result.reason() == reason

        where:
        annotation   | type       | site                    | facts                      | groupRedefined | reason
        'Size'       | String     | [:]                     | [:]                        | false          | 'outside the supported direct numeric, empty groups/payload subset'
        'Min'        | BigDecimal | [:]                     | [:]                        | false          | 'outside the supported direct numeric, empty groups/payload subset'
        'DecimalMin' | Double     | [:]                     | [:]                        | false          | 'outside the supported direct numeric, empty groups/payload subset'
        'Max'        | Integer    | [role: 'OPERATION']     | [:]                        | false          | 'outside the supported direct numeric, empty groups/payload subset'
        'Min'        | Integer    | [:]                     | [plain: false]             | false          | 'outside the supported direct numeric, empty groups/payload subset'
        'DecimalMax' | BigDecimal | [:]                     | [:]                        | true           | 'group redefinition/sequence'
        'Pattern'    | String     | [schemaKey: true]       | [:]                        | true           | 'unsupported Pattern site/type'
        'Pattern'    | String     | [kind: 'LIST']          | [:]                        | false          | 'unsupported Pattern site/type'
        'Pattern'    | Integer    | [:]                     | [:]                        | false          | 'unsupported Pattern site/type'
        'Pattern'    | String     | [:]                     | [plain: false]             | false          | 'Pattern not a direct singleton, nonempty groups/payload or group redefinition/sequence'
        'Pattern'    | String     | [:]                     | [:]                        | true           | 'Pattern not a direct singleton, nonempty groups/payload or group redefinition/sequence'
        'Pattern'    | String     | [:]                     | [patternFlagsEmpty: false] | false          | 'nonempty Pattern flags'
        'Pattern'    | String     | [:]                     | [regexp: 'a+']             | false          | 'unsupported Pattern source syntax'
        'Pattern'    | String     | [:]                     | [regexp: 'a{256}']         | false          | 'Pattern admission ceiling'
    }

    // Runtime is intentionally testRuntimeOnly in this plugin. Build inert internal facts dynamically rather than
    // adding a production/test dependency or compiling synthetic Schema code for these catalog-seam cases.
    private static Object entry(Class type, Map site = [:]) {
        def payload = fact('Payload', [choice('Kind', site.kind ?: 'SCALAR'), type, null, null, null])
        fact('Entry', ['renamed-capacity', choice('Role', site.role ?: 'PROPERTY'), 'example.Model#capacity',
                       BuiltinConstraintCatalogTest, 'capacity', [], payload, null, null, null, 'native', null,
                       site.schemaKey ?: false, false, []])
    }

    private static Object rule(String annotation, Map facts = [:]) {
        def values = [value: '1', plain: true, decimalValue: '1.0', inclusive: true, regexp: 'a', patternFlagsEmpty: true] + facts
        fact('ConstraintRule', ['jakarta.validation.constraints.' + annotation, values.value, values.plain,
                                '@' + annotation, values.decimalValue, values.inclusive, values.regexp, values.patternFlagsEmpty, 'DIRECT', '', ''])
    }

    private static Object choice(String name, String value) {
        Class.forName('com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema$' + name).enumConstants.find { it.name() == value }
    }

    private static Object fact(String name, List arguments) {
        def constructor = Class.forName('com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema$' + name).declaredConstructors[0]
        constructor.accessible = true
        constructor.newInstance(arguments as Object[])
    }
}
