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
package com.blackbuild.klum.ast.util;

import com.blackbuild.klum.ast.process.AbstractPhaseAction;
import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.util.layer3.ModelVisitor;
import com.blackbuild.klum.ast.util.layer3.StructureUtil;

/**
 * Phase Action that validates the model.
 */
public class ValidationPhase extends AbstractPhaseAction {
    public ValidationPhase() {
        super(DefaultKlumPhase.VALIDATE);
    }

    @Override
    protected void doExecute() {
        new Visitor().execute();
    }

    public static class Visitor implements ModelVisitor {

        private KlumValidationException aggregatedErrors;

        void execute() {
            Object root = PhaseDriver.getInstance().getRootObject();
            StructureUtil.visit(root, this);
            if (aggregatedErrors != null)
                throw aggregatedErrors;
        }

        @Override
        public void visit(String path, Object element, Object container, String nameOfFieldInContainer) {
            KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(element);
            if (proxy.getManualValidation()) return;

            try {
                Validator.validate(element);
            } catch (KlumValidationException e) {
                if (aggregatedErrors == null)
                    aggregatedErrors = new KlumValidationException();
                aggregatedErrors.merge(e);
            }
        }
    }
}
