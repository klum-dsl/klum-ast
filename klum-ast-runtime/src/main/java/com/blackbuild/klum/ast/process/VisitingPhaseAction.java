/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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

import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.ast.util.TemplateManager;
import com.blackbuild.klum.ast.util.layer3.ModelVisitor;
import com.blackbuild.klum.ast.util.layer3.StructureUtil;
import groovy.lang.Closure;

/**
 * Represents an action that is executed in a phase. The action is executed for each element in the model.
 */
public abstract class VisitingPhaseAction extends AbstractPhaseAction implements ModelVisitor {

    protected VisitingPhaseAction(KlumPhase phase) {
        super(phase);
    }

    /**
     * Executes the phase on the root element of the model.
     */
    @Override
    protected void doExecute() {
        Object root = PhaseDriver.getInstance().getRootObject();
        StructureUtil.visit(root, this);
    }

    protected void withCurrentTemplates(Object element, Runnable runnable) {
        TemplateManager.doWithTemplates(KlumInstanceProxy.getProxyFor(element).getCurrentTemplates(), new Closure<Void>(null) {
            @Override
            public Void call() {
                runnable.run();
                return null;
            }
        });
    }
}
