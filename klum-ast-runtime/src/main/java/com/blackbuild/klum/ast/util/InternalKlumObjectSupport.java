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

import com.blackbuild.klum.ast.validation.KlumValidationResult;

/**
 * Internal cross-package lifecycle linkage for completed DSL Object state.
 *
 * <p>This class is public only because validation and process implementations live in sibling runtime packages.
 * It is not supported client API and deliberately exposes no companion or generic metadata operations.</p>
 */
public final class InternalKlumObjectSupport {

    private InternalKlumObjectSupport() {
        // static only
    }

    /** Returns stored validation state for a Builder or completed model without creating it. */
    public static KlumValidationResult getValidationResult(Object instance) {
        if (instance instanceof KlumBuilder<?> builder)
            return builder.getMetaData(KlumValidationResult.METADATA_KEY, KlumValidationResult.class);
        return KlumModelProxy.getProxyFor(instance)
                .getMetaData(KlumValidationResult.METADATA_KEY, KlumValidationResult.class);
    }

    /** Returns lifecycle validation state for a Builder or completed model, creating it when absent. */
    public static KlumValidationResult getOrCreateValidationResult(Object instance) {
        KlumValidationResult existing = getValidationResult(instance);
        if (existing != null)
            return existing;

        KlumValidationResult created = new KlumValidationResult(DslHelper.getModelAndBreadcrumbPath(instance));
        if (instance instanceof KlumBuilder<?> builder)
            builder.setMetaData(KlumValidationResult.METADATA_KEY, created);
        else
            KlumModelProxy.getProxyFor(instance).setMetaData(KlumValidationResult.METADATA_KEY, created);
        return created;
    }

    /** Marks one completed-model validator implementation as executed. */
    public static boolean markValidatorExecuted(Object instance, Class<?> validatorType) {
        return KlumModelProxy.getProxyFor(instance).markValidatorExecuted(validatorType);
    }

    /** Initializes a completed root's model path when materialization has not supplied one. */
    public static void setModelPathIfAbsent(Object instance, String path) {
        KlumModelProxy.getProxyFor(instance).setModelPathIfAbsent(path);
    }
}
