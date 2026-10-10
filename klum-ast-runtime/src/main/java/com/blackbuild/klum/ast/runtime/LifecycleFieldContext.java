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
 * Context of one Schema declaration occurrence. Valid for the invocation only; retaining it grants no
 * additional Builder/session rights. Lookup refers to the original Schema field, or to the visited Schema type for type mutation.
 * @param <A> the exact domain annotation type
 */
public interface LifecycleFieldContext<A extends Annotation> {
    /** Returns the actual domain annotation on the dispatched declaration. */
    A getAnnotation();
    /** Singular typed lookup on the original Schema field, or inherited lookup on the visited Schema type. */
    <B extends Annotation> Optional<B> getAnnotation(Class<B> annotationType);
    /** The actual containing Builder from traversal, including inherited fields; null for a type invocation at root. */
    // ADR 0028 requires a Schema-neutral Builder boundary for reusable consumer handlers.
    @SuppressWarnings("java:S1452")
    KlumBuilder<?> getContainingBuilder();
    /** The incoming field name; null for a type invocation at root. */
    String getFieldName();
    /** Declared field Model type for field dispatch; concrete Schema type for type dispatch. */
    Class<?> getDeclaredType();
    /** Effective incoming relationship FieldType; null when type traversal has no Schema field. */
    FieldType getFieldType();
}
