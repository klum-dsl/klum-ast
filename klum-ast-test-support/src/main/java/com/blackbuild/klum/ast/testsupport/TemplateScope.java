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
package com.blackbuild.klum.ast.testsupport;

import com.blackbuild.klum.ast.runtime.internal.TemplateScopeBridge;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * A current-thread lifetime for applying existing materialized Templates in a test.
 *
 * <p>Construct an empty scope, add Templates with {@link #with(Object...)}, and close the scope after the test work.
 * Nested scopes restore the preceding active Templates. The scope is intended for test dependency configurations.</p>
 */
public final class TemplateScope implements AutoCloseable {

    private final TemplateScopeBridge.Frame frame;

    /** Opens an empty current-thread Template frame. */
    public TemplateScope() {
        frame = TemplateScopeBridge.open();
    }

    /**
     * Adds materialized Templates to this scope.
     *
     * <p>The supplied array is copied before processing. Later Templates for the same model target replace earlier
     * values in this scope.</p>
     *
     * @param templates existing materialized Templates
     * @return this scope
     */
    public TemplateScope with(Object... templates) {
        Objects.requireNonNull(templates, "templates");
        TemplateScopeBridge.add(frame, Arrays.copyOf(templates, templates.length));
        return this;
    }

    /**
     * Adds all materialized Templates from a collection to this scope.
     *
     * @param templates existing materialized Templates
     * @return this scope
     */
    public TemplateScope with(Collection<?> templates) {
        Objects.requireNonNull(templates, "templates");
        return with(templates.toArray());
    }

    /**
     * Restores the preceding current-thread Template state.
     *
     * <p>A successful close is idempotent. Closing from a different thread or out of nesting order fails without
     * changing the active state.</p>
     */
    @Override
    public void close() {
        TemplateScopeBridge.close(frame);
    }
}
