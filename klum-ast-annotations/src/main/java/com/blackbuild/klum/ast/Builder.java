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
package com.blackbuild.klum.ast;

import com.blackbuild.klum.cast.KlumCastValidated;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Namespace for annotations that describe explicit Builder-only schema behavior.
 *
 * <p>This type is not an annotation and is unrelated to the generated {@code Foo_DSL.Builder} interface. It only groups
 * schema vocabulary whose meaning is specific to construction-time Builders.</p>
 */
public final class Builder {

    private Builder() {
        throw new AssertionError("Builder is an annotation namespace and cannot be instantiated");
    }

    /**
     * Marks a method as Builder-only construction behavior.
     *
     * <p>The method is moved to the generated Builder, can change Builder state, and is absent from the completed DSL
     * Object. This is the canonical replacement for {@link Mutator}; both spellings have identical behavior during the
     * 4.1 migration window and must not be combined on one method.</p>
     */
    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @WriteAccess(WriteAccess.Type.MANUAL)
    @Documented
    public @interface Method {
    }
}
