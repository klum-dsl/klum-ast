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
package com.blackbuild.klum.ast.runtime;

import java.util.Map;

/**
 * Public construction capability implemented by generated DSL Builders.
 *
 * <p>Schema-specific configuration is exposed by the generated {@code Foo_DSL.Builder}
 * interface. Structured application is a common capability; lifecycle and materialization remain internal.</p>
 *
 * @param <T> completed DSL Object type produced by the Builder
 */
public interface KlumBuilder<T> {
    /**
     * Applies a caller-parsed keyless body to this active, same-thread, unsealed ordinary Builder.
     * Uses a generated diagnostic label and runs one PostApply on success, without completing construction.
     * @param body structured domain Map; the existing Builder identity cannot be supplied or changed
     */
    void applyStruct(Map<?, ?> body);

    /**
     * Applies operations in supplied iteration order, running one PostApply on success.
     * Does not replay PostCreate or complete construction. Failure may leave earlier configuration in place.
     * Template definition and sealed or inactive Builders are rejected.
     * @param body structured domain Map; the existing Builder identity cannot be supplied or changed
     * @param sourceName nonempty opaque diagnostic context, never opened or used to derive identity
     */
    void applyStruct(Map<?, ?> body, String sourceName);
}
