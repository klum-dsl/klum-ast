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
import fixture.annotation.Binding;
import fixture.base.RootBase;
import fixture.leaf.Child;
import fixture.leaf.Child_DSL;
import fixture.leaf.Root;
import fixture.leaf.Root_DSL;
import fixture.probe.JavaProbe;
import fixture.probe.StaticReader;
import fixture.probe.DynamicReader;
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.KlumBuilderSupport;
import com.blackbuild.klum.ast.runtime.KlumObjectSupport;
import groovy.lang.Closure;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        Root_DSL.Factory factory = Root.Create;
        Root javaRoot = factory.With(new Closure<Void>(null) {
            public Void doCall() {
                Root_DSL.Builder<Root> builder = (Root_DSL.Builder<Root>) getDelegate();
                Child_DSL.Builder<Child> child = builder.child(Child.Create, new Closure<Void>(this) {
                    public Void doCall() {
                        ((Child_DSL.Builder<Child>) getDelegate()).name("java");
                        return null;
                    }
                });
                typedBuilder(child);
                return null;
            }
        });
        Root staticRoot = StaticWriter.create();
        Root dynamicRoot = DynamicWriter.create();
        for (Root root : new Root[] {javaRoot, staticRoot, dynamicRoot}) {
            JavaProbe.completed(root.getChild(), RootBase.class);
            StaticWriter.read(root);
            DynamicWriter.read(root);
            JavaProbe.require(KlumObjectSupport.of(root).getStructure().getOwningRelationship().isEmpty(), "Root absence");
            JavaProbe.require(KlumObjectSupport.of(root).getStructure().getOwningRelationshipAnnotation(Binding.class).isEmpty(), "Root annotation absence");
        }
        JavaProbe.require(JavaProbe.reads == 3 && StaticReader.getReads() == 3 && DynamicReader.getReads() == 3, "All live readers ran");
        JavaProbe.require(javaRoot.getChild().getName().equals("java"), "Java Builder setter linkage");
        JavaProbe.require(staticRoot.getChild().getName().equals("static"), "Static Builder setter linkage");
        JavaProbe.require(dynamicRoot.getChild().getName().equals("dynamic"), "Dynamic Builder setter linkage");
        JavaProbe.require(KlumBuilder.class.getDeclaredMethods().length == 0, "Zero-operation marker");
        for (Class<?> builder : new Class<?>[] {Root_DSL.Builder.class, Child_DSL.Builder.class}) {
            JavaProbe.require(Arrays.stream(builder.getMethods()).noneMatch(method -> Set.of("getStructure", "getOwningRelationship", "getOwningRelationshipAnnotation").contains(method.getName())), "Facade operations leaked onto generated Builder");
        }
        JavaProbe.require(Modifier.isPrivate(RootBase.class.getDeclaredField("child").getModifiers()), "Private owning Schema field");
        JavaProbe.require(RootBase.class.getDeclaredField("child").getAnnotation(Binding.class).value().equals("inherited"), "Exact annotation Class identity");
        JavaProbe.require(Arrays.stream(Child.class.getMethods()).noneMatch(method -> Set.of("getStructure", "getOwningRelationship", "getOwningRelationshipAnnotation").contains(method.getName())), "Metadata properties leaked onto Model");
        for (Class<?> surface : new Class<?>[] {Root_DSL.Factory.class, Root_DSL.Builder.class, Child_DSL.Builder.class,
                KlumBuilderSupport.class, KlumBuilderSupport.Structure.class, KlumObjectSupport.Structure.class}) {
            for (Method method : surface.getMethods()) {
                JavaProbe.require(!method.getGenericReturnType().getTypeName().contains(".internal."), "Internal public return type");
                JavaProbe.require(Arrays.stream(method.getGenericParameterTypes()).noneMatch(type -> type.getTypeName().contains(".internal.")), "Internal public parameter type");
            }
        }
        JavaProbe.expired();
        System.out.println("metadata-consumers=true");
    }
    private static void typedBuilder(Child_DSL.Builder<Child> builder) {
        KlumBuilder<Child> marker = builder;
        KlumBuilderSupport<Child> support = KlumBuilderSupport.of(marker);
        JavaProbe.require(support.getStructure() != null, "Public generated Builder generic linkage");
    }
}
