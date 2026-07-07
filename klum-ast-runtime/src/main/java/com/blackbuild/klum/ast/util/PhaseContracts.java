/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
 *
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
