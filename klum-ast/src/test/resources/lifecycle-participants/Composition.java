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
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.LifecycleCreator;
import com.blackbuild.klum.ast.runtime.LifecycleCreationHandler;
import com.blackbuild.klum.ast.runtime.LifecycleFieldContext;
import com.blackbuild.klum.ast.runtime.LifecycleMutator;
import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler;
import com.blackbuild.klum.ast.runtime.LifecycleMutationContext;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;
import java.util.Map;

public class Composition {
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @LifecycleCreator(phase = AutoLink.class, handler = Supply.class)
    @LifecycleMutator(phase = AutoLink.class, handler = ZFirst.class)
    @LifecycleMutator(phase = AutoLink.class, handler = ASecond.class)
    public @interface Configured { boolean enabled() default true; }

    public static class Supply implements LifecycleCreationHandler<Configured> {
        public KlumBuilder<?> create(LifecycleFieldContext<Configured> context) {
            return context.getAnnotation().enabled()
                    ? CompositionDomain.Create.AsBuilder().With(Map.of("value", "created")) : null;
        }
    }
    public static class ZFirst implements LifecycleMutationHandler<Configured> {
        public void mutate(LifecycleMutationContext<Configured> context) {
            CompositionDomain_DSL.Builder<CompositionDomain> target = CompositionDomain.Create.narrowBuilder(context.getTargetBuilder());
            target.value(target.getValue() + ":first");
        }
    }
    public static class ASecond implements LifecycleMutationHandler<Configured> {
        public void mutate(LifecycleMutationContext<Configured> context) {
            CompositionDomain_DSL.Builder<CompositionDomain> target = CompositionDomain.Create.narrowBuilder(context.getTargetBuilder());
            target.value(target.getValue() + ":second");
        }
    }
}
