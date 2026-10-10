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
package participant.container;
import com.blackbuild.klum.ast.runtime.KlumSchemaException;
import java.util.List;
import java.util.Map;
import java.util.Set;
public class ContainerMain {
    // Current generated container getters project the nested Builder as a raw type.
    public static void inspect(Parent_DSL.Builder<?> parent) {
        List<? extends Child_DSL.Builder> list = parent.getListValues();
        Set<? extends Child_DSL.Builder> set = parent.getSetValues();
        Map<String, ? extends Child_DSL.Builder> map = parent.getMapValues();
        for (Child_DSL.Builder child : list) child.name(child.getName() + ":java");
        for (Child_DSL.Builder child : set) child.name(child.getName() + ":java");
        for (Child_DSL.Builder child : map.values()) child.name(child.getName() + ":java");
    }
    public static void main(String[] args) {
        if (args.length != 0) {
            try {
                Parent.Create.One();
                throw new AssertionError("Container declaration accepted");
            } catch (RuntimeException failure) {
                Throwable current = failure;
                while (current != null && !(current instanceof KlumSchemaException)) current = current.getCause();
                if (current == null || (!current.getMessage().contains("requires a non-static direct DSL field") || !current.getMessage().contains(args[1]))) throw failure;
                System.out.println("binary-container-defense=true");
                return;
            }
        }
        Parent javaResult = Parent.Create.FromMap(Map.of("listValues", List.of(Map.of("id", "one", "name", "java")),
                "setValues", Set.of(Map.of("id", "one", "name", "java")), "mapValues", Map.of("one", Map.of("id", "one", "name", "java"))));
        check(javaResult, "java");
        check(StaticWriter.create(), "static");
        check(DynamicWriter.create(), "dynamic");
        System.out.println("container-contracts=true");
    }
    private static void check(Parent parent, String expected) {
        if (!parent.getListValues().get(0).getName().equals(expected + ":java") ||
                !parent.getSetValues().iterator().next().getName().equals(expected + ":java") ||
                !parent.getMapValues().values().iterator().next().getName().equals(expected + ":java"))
            throw new AssertionError("Container Builder typing");
    }
}
