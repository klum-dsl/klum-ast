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
package fixture.consumer;
import fixture.child.Child;
import fixture.parent.Destination;
import fixture.historical.Selected;
import fixture.historical.IntegerOnly;
import java.util.Map;
public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            // An absent payload must still reject incomplete reachable contracts.
            Child.Create.FromStruct(Map.of());
            throw new AssertionError("corrupt contract accepted");
        }
        Child numbered = Child.Create.FromStruct(Map.of("address", 8080));
        if (!Integer.valueOf(8080).equals(numbered.getValue()) || numbered.getIntegerCalls() != 1
                || numbered.getStringCalls() != 0) throw new AssertionError("Java integer arm");
        Destination text = Destination.Create.FromStruct(Map.of("address", "8080"));
        if (!(text.getValue() instanceof String) || text.getStringCalls() != 1
                || text.getIntegerCalls() != 0) throw new AssertionError("Java String arm");
        if (!(Selected.Create.FromStruct(Map.of("address", "8080")).getValue() instanceof String))
            throw new AssertionError("historical selected String");
        if (!Integer.valueOf(8080).equals(IntegerOnly.Create.FromStruct(Map.of("address", 8080L)).getValue()))
            throw new AssertionError("historical integer");
        for (String name : new String[]{"com.blackbuild.klum.ast.compiler.internal.ast.DSLASTTransformation",
                "com.blackbuild.klum.ast.gradle.KlumAstSchemaPlugin"}) {
            try { Class.forName(name); throw new AssertionError("build tool at runtime: " + name); }
            catch (ClassNotFoundException expected) { }
        }
        StaticClient.verify();
        DynamicClient.verify();
        System.out.println("scalar-artifact-consumers=true");
    }
}
