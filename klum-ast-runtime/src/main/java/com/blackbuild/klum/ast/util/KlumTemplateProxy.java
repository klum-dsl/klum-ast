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

import groovy.lang.GroovyObject;

import java.util.Objects;

/** Persistent identity and immutable recipe state for a materialized Template. */
public final class KlumTemplateProxy implements KlumObjectCompanion {

    @SuppressWarnings("java:S1948") // generated DSL model implementations are always Serializable
    private final GroovyObject object;
    private final String breadcrumbPath;
    private final String modelPath;
    private final TemplateRecipeState recipeState;

    KlumTemplateProxy(GroovyObject object, String breadcrumbPath, String modelPath,
                      TemplateRecipeState recipeState) {
        this.object = Objects.requireNonNull(object);
        this.breadcrumbPath = breadcrumbPath;
        this.modelPath = modelPath;
        this.recipeState = Objects.requireNonNull(recipeState);
    }

    @Override
    public GroovyObject getObject() {
        return object;
    }

    @Override
    public String getBreadcrumbPath() {
        return breadcrumbPath;
    }

    @Override
    public String getModelPath() {
        return modelPath;
    }

    void replayInto(KlumBuilder<?> recipient) {
        recipeState.replayInto(recipient);
    }

    static KlumObjectCompanion companionFor(Object target) {
        if (target instanceof KlumObjectCompanion companion)
            return companion;
        if (!DslHelper.isDslObject(target))
            throw new KlumException("Object of type " + target.getClass().getName() + " is not a completed DSL Object");
        KlumObjectCompanion companion = DslHelper.getFieldValue(target, KlumModelProxy.NAME_IN_MODEL);
        if (companion == null)
            throw new KlumException("Completed DSL Object " + target.getClass().getName() + " has no companion");
        return companion;
    }
}
