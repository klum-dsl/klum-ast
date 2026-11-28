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
package fixture.parent
import groovy.transform.CompileStatic
import com.blackbuild.klum.ast.Builder
import com.blackbuild.klum.ast.DSL
import com.blackbuild.klum.ast.Struct
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Pattern
@CompileStatic @DSL class Destination {
    @Struct.Ignore Object value
    @Struct.Ignore int integerCalls
    @Struct.Ignore int stringCalls
    /** Numeric address. */
    @Struct.Operation @Builder.Method void address(@Min(1L) Integer input) {
        integerCalls++; value = input
        if (input == 13) throw new IllegalStateException('integer body failed')
    }
    /** Named address. */
    @Struct.Examples(['http', '8080'])
    @Struct.Operation @Builder.Method void address(@Pattern(regexp='[a-z0-9]{1,12}') String input) {
        stringCalls++; value = input
    }
    @Struct.Ignore @Builder.Method void address(Number input) { throw new AssertionError('Number helper') }
    @Struct.Ignore @Builder.Method void address(AddressHelper input) { throw new AssertionError('opaque helper') }
}
class AddressHelper {}
