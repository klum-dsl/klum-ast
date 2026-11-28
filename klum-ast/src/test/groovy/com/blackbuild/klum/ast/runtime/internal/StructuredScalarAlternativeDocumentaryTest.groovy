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
import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Issue('1129')
@Tag('documentary')
@See(['https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Structured-Input.md#integerstring-alternatives',
      'https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Json-Schema.md#integerstring-alternatives'])
class StructuredScalarAlternativeDocumentaryTest extends AbstractDSLSpec {
    private URLClassLoader artifacts

    def cleanup() { artifacts?.close() }

    def 'one address accepts an integer or a String without changing its meaning'() {
        given:
        createSecondaryClass('''
            package documentary
            import com.blackbuild.klum.ast.Builder
            import com.blackbuild.klum.ast.DSL
            import com.blackbuild.klum.ast.Struct
            @DSL class Destination {
                @Struct.Ignore Object value
                @Struct.Operation @Builder.Method void address(Integer input) { value = input }
                @Struct.Operation @Builder.Method void address(String input) { value = input }
                @Struct.Ignore @Builder.Method void address(Number input) { throw new AssertionError('Number helper') }
                @Struct.Ignore @Builder.Method void address(AddressHelper input) { throw new AssertionError('opaque helper') }
            }
            class AddressHelper {}
        ''')

        artifacts = new URLClassLoader([compilerConfiguration.targetDirectory.toURI().toURL()] as URL[], getClass().classLoader)
        def destination = Class.forName('documentary.Destination', false, artifacts)

        String numericInput = new String('8080')

        when:
        def numbered = destination.Create.FromStruct([address: 8080])
        def named = destination.Create.FromStruct([address: 'http'])
        def text = destination.Create.With { applyStruct([address: numericInput]) }

        then:
        numbered.value == 8080
        numbered.value.class == Integer
        named.value == 'http'
        text.value == '8080'
        text.value.class == String
        text.value.is(numericInput)
    }
}
