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
import com.blackbuild.klum.ast.layer3.AutoLink;
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.LifecycleCreator;
import com.blackbuild.klum.ast.runtime.LifecycleCreationHandler;
import com.blackbuild.klum.ast.runtime.LifecycleFieldContext;
import com.blackbuild.klum.ast.runtime.LifecycleMutator;
import com.blackbuild.klum.ast.runtime.LifecycleMutationContext;
import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
public class ContainerRules {
    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    // PARTICIPANT_MARKER
    public @interface Managed {}
    public static class Mutate implements LifecycleMutationHandler<Managed> {
        public void mutate(LifecycleMutationContext<Managed> c) { throw new AssertionError("Container dispatched"); }
    }
    public static class Create implements LifecycleCreationHandler<Managed> {
        public KlumBuilder<?> create(LifecycleFieldContext<Managed> c) { throw new AssertionError("Container dispatched"); }
    }
}
