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
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput
import groovy.yaml.YamlSlurper
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

import java.time.Duration

@Issue('952')
class StructuredContainerCharSequenceTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    private Class schema(String declarations) {
        createSecondaryClass("""
            package containersequence
            import com.blackbuild.klum.ast.*
            import java.time.Duration
            $declarations
        """)
        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        Class.forName('containersequence.Schedule', false, artifacts)
    }

    def cleanup() { artifacts?.close() }

    @Tag('documentary')
    @See('https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#charsequence-container-converters')
    def 'natural duration strings populate Lists and literal keyed Maps'() {
        given:
        def type = schema('''
            @DSL class Schedule {
                List<Duration> timeouts = [Duration.ofSeconds(1)]
                Map<String, Duration> delays = [retry: Duration.ofSeconds(9)]
            }
        ''')
        def document = new YamlSlurper().parseText('''
            timeouts: [PT5S]
            delays: { retry: PT2S }
        ''')

        when:
        def result = type.Create.FromStruct(document, 'schedule.yaml')
        def ordinary = type.Create.With { timeout 'PT5S'; delay 'retry', 'PT2S' }
        def entries = new InputContractResolver().read(type).entries()

        then:
        result.timeouts == [Duration.ofSeconds(1), Duration.ofSeconds(5)]
        result.delays == [retry: Duration.ofSeconds(2)]
        result.timeouts == ordinary.timeouts
        result.delays == ordinary.delays
        entries.timeouts.payload().item().type() == CharSequence
        entries.delays.payload().item().type() == CharSequence
        entries.delays.methods()[0].parameterTypes == [String, CharSequence] as Class[]
        entries.delays.methods()[0].getAnnotation(GeneratedInput.Converter).descriptor() == '(Ljava/lang/CharSequence;)Ljava/time/Duration;'
    }

    def 'ordinary literal and GString calls preserve exact projected signature and argument identity'() {
        given:
        def type = schema('''
            class Value {
                final Object raw
                Value(Object raw) { this.raw = raw }
                static Value parse(CharSequence raw) { new Value(raw) }
                protected static Value parse(String raw) { throw new AssertionError('unprojected String overload') }
            }
            @DSL class Schedule { List<Value> values; Map<String, Value> indexed }
        ''')
        String literal = new String('one')
        def interpolated = "${literal}"

        when:
        def ordinary = type.Create.With {
            value literal
            value interpolated
            indexed 'literal', literal
            indexed 'interpolated', interpolated
        }
        def structured = type.Create.FromStruct([values: [literal], indexed: [literal: literal]])

        then:
        ordinary.values[0].raw.is(literal)
        ordinary.values[1].raw.is(interpolated)
        ordinary.indexed.literal.raw.is(literal)
        ordinary.indexed.interpolated.raw.is(interpolated)
        structured.values[0].raw.is(literal)
        structured.indexed.literal.raw.is(literal)
    }

    def 'converter failure and Templates retain ordinary population and lifecycle'() {
        given:
        def type = schema('''
            class Audit {
                static List events = []
                static final IllegalArgumentException failure = new IllegalArgumentException('bad value')
            }
            class Value {
                final String raw
                Value(String raw) { this.raw = raw }
                static Value parse(CharSequence raw) {
                    Audit.events.add(raw)
                    if (raw == 'bad') throw Audit.failure
                    new Value(raw.toString())
                }
                protected static Value parse(String raw) { throw new AssertionError('unprojected overload') }
            }
            @DSL class Schedule {
                List<Value> values = [new Value('seed')]
                Map<String, Value> indexed = [retry: new Value('seed')]
                @Builder.Method void finish() { Audit.events.add('finish') }
                @PostApply void applied() { Audit.events.add('applied') }
            }
        ''')
        def audit = Class.forName('containersequence.Audit', false, artifacts)
        Throwable observed

        when:
        def partial = type.Create.With {
            try {
                applyStruct([values: ['one'], indexed: [retry: 'two', 'a/b~c': 'bad', later: 'later'], finish: [:]], 'failure.yaml')
            } catch (KlumModelException failure) { observed = failure }
        }

        then:
        observed != null
        observed.message.contains('failure.yaml')
        observed.message.contains('/indexed/a~1b~0c')
        causes(observed).contains(audit.failure)
        partial.values*.raw == ['seed', 'one']
        partial.indexed.retry.raw == 'two'
        audit.events == ['one', 'two', 'bad', 'applied']

        when:
        audit.events.clear()
        def template = type.Create.Template.FromStruct([values: ['one'], indexed: [retry: 'two']])

        then:
        audit.events == ['one', 'two']

        when:
        def recipient
        type.Template.With(template) { recipient = type.Create.One() }

        then:
        audit.events == ['one', 'two', 'applied']
        recipient.values*.raw == template.values*.raw
        recipient.indexed.retry.raw == template.indexed.retry.raw
    }

    private static List<Throwable> causes(Throwable failure) {
        List<Throwable> result = []
        for (Throwable cause = failure; cause != null; cause = cause.cause) result.add(cause)
        result
    }

    def 'strict container strings stop at the reached bad payload: #map / #bad'() {
        given:
        def type = schema('''
            class Audit { static List events = [] }
            class Value {
                final String raw
                Value(String raw) { this.raw = raw }
                static Value parse(CharSequence raw) { Audit.events.add(raw); new Value(raw.toString()) }
            }
            @DSL class Schedule {
                List<Value> values
                Map<String, Value> indexed
                @PostApply void applied() { Audit.events.add('applied') }
            }
        ''')
        def audit = Class.forName('containersequence.Audit', false, artifacts)
        Throwable observed
        def document = map ? [indexed: [first: 'one', 'a/b~c': bad, later: 'later']] : [values: ['one', bad, 'later']]

        when:
        def result = type.Create.With {
            try { applyStruct(document, 'strict.yaml') }
            catch (KlumModelException failure) { observed = failure }
        }

        then:
        observed != null
        observed.message.contains('strict.yaml')
        observed.message.contains(map ? '/indexed/a~1b~0c' : '/values/1')
        audit.events == ['one', 'applied']
        (map ? result.indexed.values()*.raw : result.values*.raw) == ['one']

        where:
        [map, bad] << [[false, true], [null, 12, true, "${12}", new StringBuilder('one'), new StringBuffer('one'), new Object(), [:], []]].combinations()
    }
}
