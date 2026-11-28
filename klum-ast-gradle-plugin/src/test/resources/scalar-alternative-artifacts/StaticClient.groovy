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
import groovy.transform.CompileStatic
import fixture.child.Child
import fixture.parent.Destination
@CompileStatic class StaticClient {
    static void verify() {
        Child numbered = Child.Create.FromStruct([address: 8080L])
        assert numbered.value == 8080 && numbered.value.class == Integer
        assert numbered.integerCalls == 1 && numbered.stringCalls == 0
        Destination named = Destination.Create.FromStruct([address: 'http'])
        assert named.value == 'http' && named.stringCalls == 1 && named.integerCalls == 0
        Child text = Child.Create.With { applyStruct([address: '8080']) }
        assert text.value == '8080' && text.value.class == String
        assert text.stringCalls == 1 && text.integerCalls == 0
    }
}
