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
package com.blackbuild.klum.ast.runtime.internal;

import com.blackbuild.klum.ast.runtime.DefaultKlumPhase;
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.KlumModelException;
import com.blackbuild.klum.ast.runtime.KlumPhase;
import com.blackbuild.klum.ast.runtime.internal.process.PhaseDriver;

/** Internal cross-package linkage for the public read-only Builder facade. */
public final class InternalKlumBuilderSupport {
    private InternalKlumBuilderSupport() {
        // static only
    }

    public static void requireBuilder(KlumBuilder<?> builder) {
        if (!(builder instanceof InternalKlumBuilder<?>))
            throw new KlumModelException("KlumBuilderSupport requires a generated Builder; receiver: "
                    + builder.getClass().getName());
    }

    public static <T> Class<T> getModelType(KlumBuilder<T> receiver) {
        requireBuilder(receiver);
        return ((InternalKlumBuilder<T>) receiver).getModelType();
    }

    public static SchemaRelationshipDeclaration getOwningRelationship(KlumBuilder<?> receiver, String operation) {
        requireBuilder(receiver);
        InternalKlumBuilder<?> builder = (InternalKlumBuilder<?>) receiver;
        KlumPhase phase = PhaseDriver.getCurrentPhase();
        try {
            PhaseDriver.requireCurrentConstructionSession(builder);
        } catch (KlumModelException exception) {
            throw invalidState(builder, operation, phase, "no active same-session context", exception);
        }
        if (phase == null || phase.getNumber() <= DefaultKlumPhase.OWNER.getNumber())
            throw invalidState(builder, operation, phase, "premature ownership request", null);
        return builder.readOwningRelationship();
    }

    private static KlumModelException invalidState(InternalKlumBuilder<?> builder, String operation, KlumPhase phase,
                                                    String reason, Throwable cause) {
        String observedPhase = phase == null ? "none" : phase.getName() + "(" + phase.getNumber() + ")";
        return new KlumModelException(operation
                + " requires the current active Construction session and a phase after OWNER(15); current phase: "
                + observedPhase + "; receiver: " + builder.getModelType().getName() + "; " + reason, cause);
    }
}
