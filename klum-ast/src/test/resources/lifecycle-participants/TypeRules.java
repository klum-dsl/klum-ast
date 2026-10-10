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

import com.blackbuild.klum.ast.layer3.AutoLink;
import com.blackbuild.klum.ast.runtime.LifecycleMutator;
import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler;
import com.blackbuild.klum.ast.runtime.LifecycleMutationContext;
import java.lang.annotation.Inherited;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

public class TypeRules {
    @Inherited @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD})
    @Repeatable(ManagedList.class)
    @LifecycleMutator(phase = AutoLink.class, handler = ZFirst.class)
    @LifecycleMutator(phase = AutoLink.class, handler = ASecond.class)
    public @interface Managed { String value(); }

    @Inherited @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD})
    public @interface ManagedList { Managed[] value(); }

    @Inherited @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
    @LifecycleMutator(phase = AutoLink.class, handler = OtherHandler.class)
    public @interface Other {}

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
    @LifecycleMutator(phase = AutoLink.class, handler = LocalHandler.class)
    public @interface Local {}

    public static class ZFirst implements LifecycleMutationHandler<Managed> {
        public void mutate(LifecycleMutationContext<Managed> c) {
            TypeDomain_DSL.Builder<TypeDomain> b = TypeDomain.Create.narrowBuilder(c.getTargetBuilder());
            if (c.isType()) {
                if (!c.getAnnotation(Managed.class).orElseThrow().equals(c.getAnnotation())) throw new AssertionError("type lookup");
                if (c.getContainingBuilder() == null && c.getFieldName() != null) throw new AssertionError("root context");
            }
            b.value(b.getValue() + c.getAnnotation().value() + ":first");
        }
    }
    public static class ASecond implements LifecycleMutationHandler<Managed> {
        public void mutate(LifecycleMutationContext<Managed> c) {
            TypeDomain_DSL.Builder<TypeDomain> b = TypeDomain.Create.narrowBuilder(c.getTargetBuilder());
            b.value(b.getValue() + ":second:");
        }
    }
    public static class OtherHandler implements LifecycleMutationHandler<Other> {
        public void mutate(LifecycleMutationContext<Other> c) { TypeDomain.Create.narrowBuilder(c.getTargetBuilder()).other("other"); }
    }
    public static class LocalHandler implements LifecycleMutationHandler<Local> {
        public void mutate(LifecycleMutationContext<Local> c) { TypeDomain.Create.narrowBuilder(c.getTargetBuilder()).local("local"); }
    }
}
