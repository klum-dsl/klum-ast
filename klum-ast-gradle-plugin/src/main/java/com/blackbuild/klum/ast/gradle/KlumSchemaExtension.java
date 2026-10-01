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
package com.blackbuild.klum.ast.gradle;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;

import javax.inject.Inject;

/** Schema-owned configuration; editor metadata is separate from normal Schema artifacts. */
public abstract class KlumSchemaExtension extends KlumExtension {
    private final KlumSchemaGdslExtension gdsl;

    // Gradle 8.14's decorated ObjectFactory injection requires a public constructor.
    @Inject
    @SuppressWarnings("java:S5993")
    public KlumSchemaExtension(ObjectFactory objects) {
        gdsl = objects.newInstance(KlumSchemaGdslExtension.class);
    }

    public KlumSchemaGdslExtension getGdsl() {
        return gdsl;
    }

    public void gdsl(Action<? super KlumSchemaGdslExtension> action) {
        action.execute(gdsl);
    }
}
