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

import com.blackbuild.klum.ast.FieldType;

import java.lang.annotation.Annotation;
import java.util.Optional;

/**
 * Context of one direct Schema field occurrence. Valid for the invocation only; retaining it grants no
 * additional Builder/session rights. Metadata and lookup refer to the original Schema declaration.
 * Provisional LP-1 API (issue #867).
 * @param <A> the exact domain annotation type
 */
public interface LifecycleFieldContext<A extends Annotation> {
    /** Returns the actual domain annotation on this field. */
    A getAnnotation();
    /** Singular typed lookup on the original Schema field declaration. */
    <B extends Annotation> Optional<B> getAnnotation(Class<B> annotationType);
    /** The actual containing Builder, including inherited field occurrences. */
    // ADR 0028 requires a Schema-neutral Builder boundary for reusable consumer handlers.
    @SuppressWarnings("java:S1452")
    KlumBuilder<?> getContainingBuilder();
    /** The annotated relationship's name in the containing Schema. */
    String getFieldName();
    /** Declared DSL Model type; it may differ from the target's concrete Model type. */
    Class<?> getDeclaredType();
    /** Effective relationship FieldType. */
    FieldType getFieldType();
}
