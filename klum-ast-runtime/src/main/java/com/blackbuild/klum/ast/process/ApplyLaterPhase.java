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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ApplyLaterPhase extends VisitingPhaseAction {

    ApplyLaterPhase(int phase) {
        super(new Phase(phase));
    }

    @Override
    protected void doVisit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
        KlumInstanceProxy.getProxyFor(element).executeApplyLaterClosures(getPhaseNumber());
    }

    static class Phase implements KlumPhase {
        private final int phaseNumber;

        Phase(int phaseNumber) {
            this.phaseNumber = phaseNumber;
        }

        @Override
        public int getNumber() {
            return phaseNumber;
        }

        @Override
        public String getName() {
            return "ApplyLaterPhase(" + phaseNumber + ")";
        }
    }
}
