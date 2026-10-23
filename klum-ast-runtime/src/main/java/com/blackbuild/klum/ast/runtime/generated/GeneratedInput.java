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
package com.blackbuild.klum.ast.runtime.generated;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Compiler linkage for public Builder declarations. Not a Schema-author annotation or extension API. */
public final class GeneratedInput {
    private GeneratedInput() { }

    /** Present on each declaring Builder; interfaces use explicit parent traversal, never inherited annotations. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Schema {
        String key() default "";
        Exclusion[] exclusions() default {};
    }

    /** Original field association on its public getter. Types and owner remain reflected. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Field {
        String value();
        String rename() default "";
        boolean ignored() default false;
        boolean fixedKey() default false;
        String exclusion() default "";
        String detail() default "";
    }

    /** Placement identifies exactly the selected public input/population group, without copied descriptors. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Selection {
        String field();
        String role();
    }

    /** Only sources without a surviving public declaration need a declaring-type target. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    public @interface Exclusion {
        String member();
        String category();
        String detail() default "";
    }
}
