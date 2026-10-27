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

import com.blackbuild.klum.ast.ConstraintProjection;
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
        /** Versioned completeness marker for constraint projection; zero denotes older artifacts. */
        int constraintVersion() default 0;
        /** Independent completeness for exact direct decimal facts; zero denotes older artifacts. */
        int decimalVersion() default 0;
        /** Completeness of effective field artifact policy; zero denotes older artifacts. */
        int projectionVersion() default 0;
        /** Independent completeness of declared naming/absence facts; zero denotes legacy identity contracts. */
        int namingVersion() default 0;
        /** Explicit presence is separate from a binding IDENTITY selection. */
        boolean namingExplicit() default false;
        /** Compiler-folded finite profile name; always emitted with namingVersion=1. */
        String namingProfile() default "IDENTITY";
        /** Completeness of folded literal names/exclusions, independent of type profiles. */
        int literalVersion() default 0;
        boolean groupRedefined() default false;
        String key() default "";
        boolean keyInput() default false;
        /** Only when the declaring key getter is absent from the public Builder contract. */
        Key keyMember() default @Key("");
        Exclusion[] exclusions() default {};
    }

    /** Construction identity on its declaring public getter, separate from writable input operations. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Key {
        String value();
        /** Compiler-folded origin; explicitly emitted with literalVersion=1. */
        String nameOrigin() default "";
        String rename() default "";
        boolean ignored() default false;
        /** Explicit source declaration fact; absence in older bytecode means unknown, never inferred. */
        boolean sourceDeprecated() default false;
    }

    /** Original field association on its public getter. Types and owner remain reflected. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Field {
        String value();
        /** Compiler-folded origin; explicitly emitted with literalVersion=1. */
        String nameOrigin() default "";
        String rename() default "";
        boolean ignored() default false;
        /** Explicit source declaration fact; absence in older bytecode means unknown, never inferred. */
        boolean sourceDeprecated() default false;
        /** Inert original field declarations, never executable-validation annotations on this getter. */
        Constraint[] constraints() default {};
        /** Explicit compiler-folded field policy, independent of structural admission. */
        ConstraintProjection.Policy constraintProjection() default ConstraintProjection.Policy.PROJECT;
        boolean fixedKey() default false;
        boolean schemaKey() default false;
        String exclusion() default "";
        String detail() default "";
    }

    /** Source provenance on the exact projection of a Schema-authored Builder operation. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Operation {
        boolean sourceDeprecated();
        String nameOrigin() default "";
        String rename() default "";
        boolean ignored() default false;
    }

    /** Internal compiler facts; not an annotation for Schema authors or a projection SPI. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    public @interface Constraint {
        String annotation();
        long value() default 0L;
        String decimalValue() default "";
        boolean inclusive() default true;
        boolean plain();
        String declaration();
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
