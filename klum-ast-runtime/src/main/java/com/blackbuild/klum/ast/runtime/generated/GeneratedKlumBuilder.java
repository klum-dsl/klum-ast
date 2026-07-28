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
package com.blackbuild.klum.ast.runtime.generated;

import com.blackbuild.klum.ast.runtime.internal.InternalKlumBuilder;
import com.blackbuild.klum.ast.runtime.KlumPhase;
import groovy.lang.Closure;

/**
 * Generated-code linkage base for Builder implementations emitted by the DSL transformation.
 *
 * <p>This type is not a handwritten client API, mutable Builder extension point, or Builder-phase
 * SPI. Generated Builders retain their model-package {@code Foo_DSL.Builder} contracts; this base
 * only keeps their bytecode independent of the runtime's internal package.</p>
 *
 * @param <M> the completed model type
 */
public abstract class GeneratedKlumBuilder<M> extends InternalKlumBuilder<M> {

    protected GeneratedKlumBuilder(Class<M> modelType) {
        super(modelType);
    }

    @Override
    protected void $assignRelationships() {
        super.$assignRelationships();
    }

    protected final void $copyFromRecipe(Object template) {
        super.copyFromRecipe(template);
    }

    protected final <T> T $setSingleField(String fieldOrMethodName, T value) {
        return super.setSingleField(fieldOrMethodName, value);
    }

    protected final void $scheduleApplyLater(Closure<?> closure) {
        super.scheduleApplyLater(closure);
    }

    protected final void $scheduleApplyLater(KlumPhase phase, Closure<?> closure) {
        super.scheduleApplyLater(phase, closure);
    }

    protected final void $scheduleApplyLater(Integer number, Closure<?> closure) {
        super.scheduleApplyLater(number, closure);
    }

    @Override
    public void applyLater(Closure<?> closure) {
        super.applyLater(closure);
    }

    @Override
    public void applyLater(KlumPhase phase, Closure<?> closure) {
        super.applyLater(phase, closure);
    }

    @Override
    public void applyLater(Integer number, Closure<?> closure) {
        super.applyLater(number, closure);
    }
}
