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
package fixture.probe;
import fixture.annotation.Binding;
import java.util.Optional;
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport;
import com.blackbuild.klum.ast.runtime.KlumObjectSupport;
import com.blackbuild.klum.ast.runtime.KlumSchemaRelationship;
import com.blackbuild.klum.ast.runtime.KlumModelException;

public final class JavaProbe {
    private static KlumBuilderSupport.Structure<?> retained;
    private static KlumSchemaRelationship declaration;
    public static int reads;

    public static <T> void live(KlumBuilder<T> builder) {
        KlumBuilderSupport<T> support = KlumBuilderSupport.of(builder);
        KlumBuilderSupport.Structure<T> structure = support.getStructure();
        Optional<KlumSchemaRelationship> relationship = structure.getOwningRelationship();
        Optional<Binding> binding = structure.getOwningRelationshipAnnotation(Binding.class);
        require(binding.orElseThrow().value().equals("inherited"), "Java live annotation");
        require(relationship.orElseThrow().getAnnotation(Binding.class).equals(binding), "Java descriptor annotation");
        require(structure.getOwningRelationshipAnnotation(Deprecated.class).isEmpty(), "Java missing annotation");
        retained = structure;
        declaration = relationship.orElseThrow();
        reads++;
    }

    public static <T> KlumSchemaRelationship completed(T model, Class<?> declaringClass) {
        KlumObjectSupport<T> support = KlumObjectSupport.of(model);
        KlumObjectSupport.Structure<T> structure = support.getStructure();
        Optional<Binding> binding = structure.getOwningRelationshipAnnotation(Binding.class);
        KlumSchemaRelationship relationship = structure.getOwningRelationship().orElseThrow();
        require(binding.orElseThrow().value().equals("inherited"), "Java completed annotation");
        require(relationship.getDeclaringClass() == declaringClass, "Original declaring Schema");
        require(relationship.getName().equals("child"), "Inherited field name");
        require(relationship.equals(declaration) && relationship.hashCode() == declaration.hashCode(), "Declaration equality");
        require(structure.getOwningRelationshipAnnotation(Deprecated.class).isEmpty(), "Completed missing annotation");
        require(relationship.getAnnotation(Deprecated.class).isEmpty(), "Descriptor missing annotation");
        require(structure.getDirectOwners().isEmpty(), "Metadata independent of Owner values");
        return relationship;
    }

    public static void expired() {
        require(declaration.getAnnotation(Binding.class).orElseThrow().value().equals("inherited"), "Detached descriptor");
        try {
            retained.getOwningRelationship();
            throw new AssertionError("Expired live view accepted");
        } catch (KlumModelException expected) {
            require(expected.getMessage().contains("active Construction session"), "Expired-view diagnostic");
        }
        try {
            retained.getOwningRelationshipAnnotation(Binding.class);
            throw new AssertionError("Expired annotation query accepted");
        } catch (KlumModelException expected) {
            require(expected.getMessage().contains("active Construction session"), "Expired-annotation diagnostic");
        }
    }

    public static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
