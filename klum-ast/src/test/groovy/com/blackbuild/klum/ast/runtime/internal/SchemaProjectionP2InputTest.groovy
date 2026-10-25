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

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.KlumStructuredInput
import spock.lang.Issue

/** Current input behavior only; no Bean provider, user callbacks, defaults or converters. */
@Issue('959')
class SchemaProjectionP2InputTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema() {
        createClass '''
            package projectionp2
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Key
            @DSL class Document {
                BigDecimal decimal
                Integer integral
                float roundedFloat
                double roundedDouble
                List<String> words
                Set<String> distinct
                Map<String, String> labels
                Map<String, Child> children
            }
            @DSL class Child { @Key String name; String value }
        '''
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('projectionp2.Document', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    def 'BigDecimal raw numbers retain exact values and scale: #literal'() {
        given:
        def type = schema()
        BigDecimal input = new BigDecimal(literal)

        when:
        def model = KlumStructuredInput.readRoot(type, null, [decimal: input], 'decimal.json')

        then:
        model.decimal == input
        model.decimal.scale() == input.scale()

        where:
        literal << ['1E-7', '1.2300', '-1.2300', '+2.5E+3', '0.000', '9007199254740993.0000000001']
    }

    def 'numeric strings and null stay outside direct numeric input: #value'() {
        given:
        def type = schema()

        when:
        KlumStructuredInput.readRoot(type, null, [decimal: value], 'decimal.json')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/decimal')

        where:
        value << ['1.23', '', null, false]
    }

    def 'binary floating targets can round across a decimal bound'() {
        given:
        def type = schema()
        BigDecimal input = new BigDecimal('0.100000000000000001')

        when:
        def model = KlumStructuredInput.readRoot(type, null, [roundedFloat: input, roundedDouble: input], 'rounding.json')

        then:
        new BigDecimal(Float.toString(model.roundedFloat)).compareTo(input) < 0
        new BigDecimal(Double.toString(model.roundedDouble)).compareTo(input) < 0
    }

    def 'List cardinality and Map keys survive but Set population deduplicates'() {
        given:
        def type = schema()

        when:
        def model = KlumStructuredInput.readRoot(type, null,
                [words: ['same', 'same'], distinct: ['same', 'same'], labels: [a: 'same', b: 'same'],
                 children: [a: [value: 'same'], b: [value: 'same']]], 'containers.json')

        then:
        model.words == ['same', 'same']
        model.distinct == ['same'] as Set
        model.labels.size() == 2
        model.children.keySet() == ['a', 'b'] as Set
        model.children.a.name == 'a'
        model.children.b.name == 'b'
    }

    def 'List inputs do not acquire Set or scalar coercion: #value'() {
        given:
        def type = schema()

        when:
        KlumStructuredInput.readRoot(type, null, [words: value], 'containers.json')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/words')

        where:
        value << [(['a'] as Set), 'a', [1], null]
    }

    def 'ordinary and keyed maps reject non String keys without coercion: #field'() {
        given:
        def type = schema()

        when:
        KlumStructuredInput.readRoot(type, null, [(field): [(1): value]], 'keys.json')

        then:
        def failure = thrown(KlumModelException)
        failure.message.contains('/' + field)

        where:
        field      | value
        'labels'   | 'a'
        'children' | [value: 'a']
    }
}
