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

import java.lang.annotation.Annotation;

/**
 * A non-null Builder target to configure through its generated public contract.
 * Field targets fail by default or skip when sealed according to {@link LifecycleMutator#onSealed()}.
 * Type dispatch follows existing traversal, which skips sealed aggregation targets.
 * @param <A> the exact domain annotation type
 */
public interface LifecycleMutationContext<A extends Annotation> extends LifecycleFieldContext<A> {
    /** True for a Schema-type invocation; false for direct field invocation. */
    default boolean isType() { return false; }

    /** Returns the visited Builder for type dispatch, or the existing/just-created field target. */
    // ADR 0028 requires a Schema-neutral Builder boundary for reusable consumer handlers.
    @SuppressWarnings("java:S1452")
    KlumBuilder<?> getTargetBuilder();
}
