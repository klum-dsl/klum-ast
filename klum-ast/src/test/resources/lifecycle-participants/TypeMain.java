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
package participant.fixture;
import com.blackbuild.klum.ast.runtime.KlumSchemaException;
import java.util.Map;

public class TypeMain {
    public static void main(String[] args) {
        if (args.length != 0) {
            verifyBinaryRejection(args[0]);
            System.out.println("binary-type-defense=true");
            return;
        }
        verifyConsumers();
        verifyRepeatableInheritance();
        System.out.println("compiled-type-consumers=true");
    }

    private static void verifyBinaryRejection(String diagnostic) {
        try {
            InheritedSchema.Create.One();
            throw new AssertionError("Expected binary type declaration rejection");
        } catch (RuntimeException failure) {
            Throwable cause = failure;
            while (cause != null && !(cause instanceof KlumSchemaException)) cause = cause.getCause();
            if (cause == null || !cause.getMessage().contains(diagnostic)) throw failure;
        }
    }

    private static void verifyConsumers() {
        if (!TypeWriters.statically().getValue().equals("base:first:second:")) throw new AssertionError("static");
        if (!TypeWriters.dynamically().getValue().equals("override:first:second:")) throw new AssertionError("dynamic");
        if (!BaseSchema.Create.One().getLocal().equals("local")) throw new AssertionError("direct local");
        InheritedSchema inherited = InheritedSchema.Create.One();
        if (inherited.getLocal() != null || !inherited.getOther().equals("other")) throw new AssertionError("inheritance");
        if (!RepeatedSchema.Create.One().getValue().equals("base:first:second:")) throw new AssertionError("container plus inherited singular");
        if (!OnlyRepeatedSchema.Create.One().getValue().isEmpty()) throw new AssertionError("no plural expansion");
        if (!InterfaceSchema.Create.One().getValue().isEmpty()) throw new AssertionError("interface non-inheritance");
        RootSchema root = RootSchema.Create.FromMap(Map.of("child", Map.of()));
        if (!root.getValue().equals("root:first:second:")) throw new AssertionError("root");
        if (!root.getChild().getValue().equals("incoming:first:second:base:first:second:")) throw new AssertionError("parent before type");
    }

    private static void verifyRepeatableInheritance() {
        if (InheritedContainerSchema.class.getDeclaredAnnotation(TypeRules.ManagedList.class) != null
                || InheritedContainerSchema.class.getAnnotation(TypeRules.ManagedList.class).value().length != 2
                || InheritedContainerSchema.class.getAnnotation(TypeRules.Managed.class) != null
                || !InheritedContainerSchema.Create.One().getValue().isEmpty())
            throw new AssertionError("inherited unmarked container without singular expansion");
        if (!OverrideContainerSchema.class.getAnnotation(TypeRules.ManagedList.class).value()[0].value().equals("three")
                || OverrideContainerSchema.class.getAnnotation(TypeRules.Managed.class) != null
                || !OverrideContainerSchema.Create.One().getValue().isEmpty())
            throw new AssertionError("local container replaces inherited container");
        if (!RepeatedSchema.class.getAnnotation(TypeRules.Managed.class).value().equals("base")
                || RepeatedSchema.class.getAnnotation(TypeRules.ManagedList.class).value().length != 2
                || OnlyRepeatedSchema.class.getAnnotation(TypeRules.Managed.class) != null)
            throw new AssertionError("repeatable inheritance");
    }
}
