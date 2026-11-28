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
package fixture.consumer
import fixture.child.Child
import fixture.historical.Converted
import fixture.historical.IntegerOnly
import fixture.historical.Selected
import fixture.historical.Unmarked
class DynamicClient {
    static void verify() {
        def numbered = Child.Create.FromStruct([address: new BigDecimal('8080.0')])
        assert numbered.value.class == Integer && numbered.integerCalls == 1 && numbered.stringCalls == 0
        def text = Child.Create.With { applyStruct([address: '8080']) }
        assert text.value.class == String && text.stringCalls == 1 && text.integerCalls == 0
        assert Selected.Create.FromStruct([address: '8080']).value.class == String
        assert Selected.Create.With { address 8080 }.value.class == Integer
        assert IntegerOnly.Create.FromStruct([address: 8080L]).value == 8080
        // Ordinary generated converter helpers have a separate JPMS boundary; this control qualifies classpaths.
        if (!Converted.module.named) {
            assert Converted.Create.With { address 8080 }.address == '8080'
        }
        assert Converted.Create.FromStruct([address: '8080']).address == '8080'
        [ { Selected.Create.FromStruct([address: 8080]) },
          { Converted.Create.FromStruct([address: 8080]) },
          { Unmarked.Create.FromStruct([address: 'http']) },
          { IntegerOnly.Create.FromStruct([address: '8080']) },
          { Child.Create.FromStruct([address: 1.5d]) } ].each { call ->
            try { call(); assert false: 'unexpected admission' }
            catch (RuntimeException expected) { assert expected.message }
        }
        try { Child.Create.FromStruct([address: 13]); assert false }
        catch (RuntimeException expected) {
            def cause = expected
            while (cause.cause) cause = cause.cause
            assert cause.message == 'child body failed'
        }
    }
}
