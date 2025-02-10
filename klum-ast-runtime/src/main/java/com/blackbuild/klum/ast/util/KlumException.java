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

import com.blackbuild.klum.ast.process.KlumPhase;
import com.blackbuild.klum.ast.process.PhaseDriver;

/**
 * Base exception for errors in the Klum Framework.
 */
public class KlumException extends RuntimeException {

    private final KlumPhase phase = PhaseDriver.getCurrentPhase();
    private final String phaseActionType = PhaseDriver.getCurrentPhaseActionType();

    public KlumException() {
    }

    public KlumException(String message, Throwable cause) {
        super(message, cause);
    }

    public KlumException(String message) {
        super(message);
    }

    public KlumException(Throwable cause) {
        super(cause);
    }

    @Override
    public String getMessage() {
        if (phase == null) return super.getMessage();
        return super.getMessage() + "(" + phase.getDisplayName() + ")";
    }

    public String getBasicMessage() {
        return super.getMessage();
    }

    public KlumPhase getPhase() {
        return phase;
    }

    public String getPhaseActionType() {
        return phaseActionType;
    }
}
