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
package com.blackbuild.klum.ast.util;

import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.PhaseDriver;

/**
 * Minimal prototype for enforcing phase contracts at runtime.
 * Enabled via system property `klum.strictPhaseContracts=true`.
 */
public final class PhaseContracts {

    private static final boolean STRICT = Boolean.parseBoolean(System.getProperty("klum.strictPhaseContracts", "false"));

    private PhaseContracts() {}

    /**
     * Check whether a mutation of the given member is allowed in the current phase.
     * Throws KlumModelException when strict mode is enabled and the current phase is at or after POST_TREE.
     */
    public static void checkMutable(String member) {
        if (!STRICT) return;
        try {
            com.blackbuild.klum.ast.process.KlumPhase phase = PhaseDriver.getContext().getPhase();
            if (phase != null && phase.getNumber() >= DefaultKlumPhase.POST_TREE.getNumber()) {
                throw new KlumModelException("Illegal mutation of '" + member + "' during phase " + phase.getName());
            }
        } catch (KlumModelException e) {
            throw e;
        } catch (Exception e) {
            throw new KlumModelException(e);
        }
    }
}
