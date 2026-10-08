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
import fixture.leaf.Root
import fixture.leaf.Root_DSL
import fixture.leaf.Child
import fixture.leaf.Child_DSL
import fixture.probe.StaticReader
import fixture.probe.DynamicReader
import groovy.transform.CompileStatic

@CompileStatic class StaticWriter {
    static Root create() {
        Root.Create.With {
            Root_DSL.Builder<Root> rootBuilder = (Root_DSL.Builder<Root>) delegate
            Child_DSL.Builder<Child> childBuilder = rootBuilder.child(Child.Create) { name 'static' }
            assert Child.Create.isBuilder(childBuilder)
        }
    }
    static void read(Root root) { StaticReader.completed(root.child) }
}
class DynamicWriter {
    static Root create() { Root.Create.With { child(Child.Create) { name 'dynamic' } } }
    static void read(Root root) { DynamicReader.completed(root.child) }
}
