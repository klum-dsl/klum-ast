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

import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.ast.process.AbstractPhaseAction;
import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.PhaseDriver;
import com.blackbuild.klum.ast.util.layer3.ModelVisitor;
import com.blackbuild.klum.ast.util.layer3.StructureUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Phase Action that validates the model.
 */
public class VerifyPhase extends AbstractPhaseAction {

    public VerifyPhase() {
        super(DefaultKlumPhase.VERIFY);
    }

    @Override
    protected void doExecute() {
        new Visitor().execute();
    }

    public static class Visitor implements ModelVisitor {

        private final List<KlumValidationResult> aggregatedErrors = new ArrayList<>();
        private Validate.Level currentMaxLevel = Validate.Level.NONE;

        void execute() {
            executeOn(PhaseDriver.getInstance().getRootObject(), Validator.getFailLevel());
        }

        List<KlumValidationResult> executeOn(Object root, Validate.Level failOnLevel) {
            StructureUtil.visit(root, this);
            if (currentMaxLevel.equalOrWorseThan(failOnLevel))
                throw new KlumValidationException(aggregatedErrors);
            return aggregatedErrors;
        }

        @Override
        public void visit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
            KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(element);
            KlumValidationResult result = proxy.getMetaData(KlumValidationResult.METADATA_KEY, KlumValidationResult.class);
            if (result != null && result.has(Validate.Level.INFO)) {
                aggregatedErrors.add(result);
                currentMaxLevel = currentMaxLevel.combine(result.getMaxLevel());
            }
        }

    }
}
