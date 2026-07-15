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
package com.blackbuild.klum.ast.process;

import com.blackbuild.klum.ast.util.KlumBuilder;
import com.blackbuild.klum.ast.util.TemplateManager;
import com.blackbuild.klum.ast.util.layer3.BuilderStructureSupport;
import com.blackbuild.klum.ast.util.layer3.ModelVisitor;
import groovy.lang.Closure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Visits mutable Builders before {@link DefaultKlumPhase#INSTANTIATE}. */
public abstract class BuilderVisitingPhaseAction extends AbstractPhaseAction implements ModelVisitor {

    protected BuilderVisitingPhaseAction(KlumPhase phase) {
        super(phase);
        if (phase.getNumber() >= DefaultKlumPhase.INSTANTIATE.getNumber())
            throw new IllegalArgumentException("Builder phases must run before INSTANTIATE");
    }

    @Override
    protected void doExecute() {
        Object root = PhaseDriver.getInstance().getRootObject();
        if (!(root instanceof KlumBuilder))
            throw new IllegalStateException("Builder phase " + getPhase().getDisplayName() + " received a completed model");
        BuilderStructureSupport.visit((KlumBuilder<?>) root, this);
    }

    @Override
    public Action shouldVisit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
        if (!(element instanceof KlumBuilder))
            return Action.SKIP;
        return ((KlumBuilder<?>) element).isSealed() ? Action.SKIP : Action.HANDLE;
    }

    @Override
    public final void visit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
        try {
            PhaseDriver.getContext().setInstance(element);
            doVisit(path, (KlumBuilder<?>) element, container, nameOfFieldInContainer);
        } finally {
            PhaseDriver.getContext().setInstance(null);
        }
    }

    protected abstract void doVisit(@NotNull String path, @NotNull KlumBuilder<?> builder, @Nullable Object container, @Nullable String nameOfFieldInContainer);

    protected void withCurrentTemplates(KlumBuilder<?> builder, Runnable runnable) {
        TemplateManager.doWithTemplates(builder.getCurrentTemplates(), new Closure<Void>(null) {
            @Override
            public Void call() {
                runnable.run();
                return null;
            }
        });
    }
}
