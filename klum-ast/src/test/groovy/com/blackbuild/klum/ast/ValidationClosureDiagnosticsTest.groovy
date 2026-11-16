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
import org.codehaus.groovy.control.messages.WarningMessage
import spock.lang.Issue

@Issue("1087")
class ValidationClosureDiagnosticsTest extends AbstractDSLSpec {

    def "#annotation compiles without a validation closure warning"() {
        given:
        def unit = validationUnit("""
            import java.lang.annotation.ElementType
            import java.lang.annotation.Target

            @Target(ElementType.FIELD)
            @PropagateAnnotation(Optional)
            @interface DomainOptional {}

            @DSL class Example {
                ${annotation} String value
            }
        """)

        when:
        unit.compile()

        then:
        !unit.errorCollector.warnings

        where:
        annotation << ['@Optional', '@DomainOptional', '@Validate(Validate.Ignore)',
                       '@Validate(Validate.GroovyTruth)', '@Validate({ it?.size() > 1 })']
    }

    def "unsupported validation class still receives an actionable warning"() {
        given:
        def unit = validationUnit('''
            @DSL class Example {
                @Validate(String) String value
            }
        ''')

        when:
        unit.compile()

        then:
        unit.errorCollector.warnings*.message.any {
            it.contains('Only closures are supported for validation') && it.contains('consider using a @Validate method instead')
        }
    }

    private CompilationUnit validationUnit(String source) {
        compilerConfiguration.warningLevel = WarningMessage.LIKELY_ERRORS
        def unit = new CompilationUnit(compilerConfiguration, null, loader)
        unit.addSource('Example.groovy', source)
        unit
    }
}
