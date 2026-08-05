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
package com.blackbuild.klum.ast

import org.codehaus.groovy.control.CompilationUnit
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@Issue("661")
class MapConfiguratorOverrideDiagnosticsTest extends AbstractDSLSpec {

    def "void map configurator override remains valid without a diagnostic"() {
        when:
        def unit = compile('''
            @DSL class Mailbox {
                String outboxUrl

                @Mutator
                void outboxUrl(String value) {
                    outboxUrl = value
                }
            }
        ''')

        then:
        !unit.errorCollector.warnings
    }

    def "field-valued override warns while maps still dispatch to the method"() {
        given:
        def source = '''
            @DSL class Mailbox {
                String outboxUrl
                String configuredBy

                @Mutator
                String outboxUrl(String value) {
                    configuredBy = "mutator:$value"
                    value
                }
            }
        '''
        def unit = compile(source)
        createClass(source)

        when:
        def mailbox = clazz.Create.With(outboxUrl: 'https://example.invalid')
        def direct = clazz.Create.With(setOutboxUrl: 'https://direct.invalid')

        then:
        unit.errorCollector.warnings*.message.any { it.contains("Manual configurator 'outboxUrl' shadows map configuration") }
        mailbox.configuredBy == 'mutator:https://example.invalid'
        mailbox.outboxUrl == null
        direct.outboxUrl == 'https://direct.invalid'
    }

    def "Builder return for a relationship override warns"() {
        when:
        def unit = compile('''
            import com.blackbuild.klum.ast.runtime.KlumBuilder

            @DSL class Child { String name }

            @DSL class Parent {
                Child child

                @Mutator
                KlumBuilder<Child> child(Child value) {
                    null
                }
            }
        ''')

        then:
        unit.errorCollector.warnings*.message.any { it.contains("Manual configurator 'child' shadows map configuration") }
    }

    def "non-setter map configurator override fails compilation"() {
        when:
        compile('''
            @DSL class Mailbox {
                String outboxUrl

                @Mutator
                Integer outboxUrl(String value) {
                    0
                }
            }
        ''')

        then:
        def error = thrown(MultipleCompilationErrorsException)
        error.message.contains("Manual configurator 'outboxUrl' shadows map configuration")
        error.message.contains("must return void, 'String', or its Builder type, but returns 'Integer'")
    }

    def "no-field map configurator fallback remains valid"() {
        given:
        def source = '''
            @DSL class Mailbox {
                String configuredBy

                @Mutator
                String outboxUrl(String value) {
                    configuredBy = "mutator:$value"
                    value
                }
            }
        '''
        def unit = compile(source)
        createClass(source)

        when:
        def mailbox = clazz.Create.With(outboxUrl: 'https://example.invalid')

        then:
        !unit.errorCollector.warnings
        mailbox.configuredBy == 'mutator:https://example.invalid'
    }

    def "other manual write-access methods do not receive the mutator diagnostic"() {
        when:
        def unit = compile('''
            @DSL class Mailbox {
                String role

                @Role
                String role(String value) {
                    value
                }
            }
        ''')

        then:
        !unit.errorCollector.warnings
    }

    private CompilationUnit compile(String source) {
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Mailbox.groovy', source)
        unit.compile()
        unit
    }
}
