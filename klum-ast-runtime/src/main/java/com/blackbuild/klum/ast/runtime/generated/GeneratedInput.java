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

import com.blackbuild.klum.ast.Struct;
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
        /** Required protocol version; producers explicitly emit the supported value. */
        int version() default 0;
        /** Explicit presence is separate from a binding IDENTITY selection. */
        boolean namingExplicit() default false;
        /** Compiler-folded finite profile name; always emitted with the current protocol. */
        String namingProfile() default "IDENTITY";
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
        /** Compiler-folded origin; explicitly emitted by current producers. */
        String nameOrigin() default "";
        String rename() default "";
        boolean ignored() default false;
        /** Required explicit source declaration fact, never inferred from generated annotations. */
        boolean sourceDeprecated() default false;
        /** Ordered documentary strings; explicitly emitted even when the source has no examples. */
        String[] examples() default {};
    }

    /** Original field association on its public getter. Types and owner remain reflected. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Field {
        /** Explicit completeness of the selected converter association (zero or one). */
        int converterCount() default -1;
        String value();
        /** Compiler-folded origin; explicitly emitted by current producers. */
        String nameOrigin() default "";
        String rename() default "";
        boolean ignored() default false;
        /** Required explicit source declaration fact, never inferred from generated annotations. */
        boolean sourceDeprecated() default false;
        /** Ordered documentary strings; explicitly emitted even when the source has no examples. */
        String[] examples() default {};
        /** Inert original field declarations, never executable-validation annotations on this getter. */
        Constraint[] constraints() default {};
        /** Explicit cardinality detects stripped declaration or composing rows, including documentary-only rows. */
        int constraintCount() default -1;
        /** Explicit compiler-folded field policy, independent of structural admission. */
        Struct.ConstraintProjection.Policy constraintProjection() default Struct.ConstraintProjection.Policy.PROJECT;
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
        /** Separate from any field facts on this same public declaration. */
        String[] examples() default {};
        String nameOrigin() default "";
        String rename() default "";
        boolean ignored() default false;
    }

    /** Internal compiler facts; not an annotation for Schema authors or a projection SPI. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({})
    public @interface Constraint {
        String annotation();
        /** Explicit origin: DIRECT, DOCUMENTARY, COMPOSING_AND or COMPOSING_UNSUPPORTED. */
        String origin() default "";
        /** Canonical outer declaration for a first-level composing constraint, empty for DIRECT. */
        String owner() default "";
        /** Literal Struct.ConstraintDescription on the directly used annotation type. */
        String description() default "";
        long value() default 0L;
        String decimalValue() default "";
        boolean inclusive() default true;
        /** Explicit decoded source for each direct Jakarta Pattern, including unsupported forms. */
        String regexp() default "";
        /** Explicit effective flag emptiness; absence is never interpreted as empty. */
        boolean patternFlagsEmpty() default false;
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

    /** Exact compiler-selected domain executable; never an invitation to discover converters. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Converter {
        String owner();
        String name();
        String descriptor();
        int parameter();
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
