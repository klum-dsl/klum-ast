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

import com.blackbuild.klum.ast.runtime.internal.StructuredInputSupport;

/** Applies caller-parsed structured data through the Schema's compiler-admitted public Builder operations. */
public final class KlumStructuredInput {
    private KlumStructuredInput() { }

    /**
     * Reads one concrete root through ordinary construction after complete input preflight.
     * @param concreteType exact concrete Schema class, compiled and packaged with its generated metadata
     * @param rootKey explicit String key for a keyed root, or null for an unkeyed root
     * @param parsed caller-parsed domain Map with String keys; nested data follows the admitted Schema grammar
     * @param sourceName nonempty diagnostic identity, never opened as a file or resource
     * @param <T> completed root Model type
     * @return the completed root after normal lifecycle, materialization and validation
     * @throws KlumModelException if the type, metadata or input is unsupported or an input operation fails
     */
    public static <T> T readRoot(Class<T> concreteType, String rootKey, Object parsed, String sourceName) {
        return StructuredInputSupport.readRoot(concreteType, rootKey, parsed, sourceName);
    }

    /**
     * Reads a self-contained root, taking a keyed Schema's construction identity from its admitted key member.
     * @param concreteType exact concrete Schema class with generated input metadata
     * @param parsed caller-parsed domain Map with String keys
     * @param sourceName nonempty opaque diagnostic identity, never opened as a resource
     * @param <T> completed root Model type
     * @return the completed root after normal lifecycle, materialization and validation
     * @throws KlumModelException if metadata or input is unsupported or construction fails
     */
    public static <T> T readRoot(Class<T> concreteType, Object parsed, String sourceName) {
        return StructuredInputSupport.readRoot(concreteType, parsed, sourceName);
    }

    /**
     * Applies one completely preflighted input in supplied iteration order, then runs ordinary PostApply.
     * Existing state predates this call; after execution starts, side effects are not rolled back on failure.
     * @param builder active, same-thread, unsealed ordinary generated Builder
     * @param parsed caller-parsed domain Map with String keys
     * @param sourceName nonempty diagnostic identity, never opened as a file or resource
     * @param <B> precise public Builder type
     * @return the identical Builder, without replaying PostCreate or completing construction
     * @throws KlumModelException if the target, metadata or input is unsupported or an input operation fails
     */
    public static <B extends KlumBuilder<?>> B applyToBuilder(B builder, Object parsed, String sourceName) {
        return StructuredInputSupport.applyToBuilder(builder, parsed, sourceName);
    }
}
