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
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Repeatable;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Optional;

/** Public namespace for external Builder lifecycle participation. */
public final class LifecycleParticipant {
    private LifecycleParticipant() {}

    /**
     * Opts a domain field annotation into external Builder creation.
     * Direct DSL fields participate in AutoCreate, AutoLink, Default or PostTree.
     * Only one direct creator may claim a field/phase, including built-in creation mechanisms.
     * Each invocation constructs a fresh public concrete handler with a public no-arg constructor.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @Repeatable(Creator.List.class)
    public @interface Creator {
        /** The existing AutoCreate, AutoLink, Default or PostTree lifecycle annotation class. */
        Class<? extends Annotation> phase();

        /** Handler whose resolved annotation parameter must equal this domain annotation. */
        // Class literals cannot express the relationship to the annotated annotation type; checked by compiler/runtime.
        @SuppressWarnings("rawtypes")
        Class<? extends CreationHandler> handler();
        /** Repeatable declarations on one domain annotation, in container value order. */
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.ANNOTATION_TYPE)
        @interface List {
            Creator[] value();
        }
    }

    /**
     * Opts a domain field or Schema-type annotation into external Builder mutation.
     * Direct DSL fields and visited Schema types participate in AutoCreate, AutoLink, Default or PostTree.
     * Repeated mutations execute in declaration order, or explicit List value order.
     * Relative order of a singular marker mixed with List, and of different domain annotations, is unspecified.
     * Each invocation constructs a fresh public concrete handler with a public no-arg constructor.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @Repeatable(Mutator.List.class)
    public @interface Mutator {
        /** The existing AutoCreate, AutoLink, Default or PostTree lifecycle annotation class. */
        Class<? extends Annotation> phase();

        /** Handler whose resolved annotation parameter must equal this domain annotation. */
        // Class literals cannot express the relationship to the annotated annotation type; checked by compiler/runtime.
        @SuppressWarnings("rawtypes")
        Class<? extends MutationHandler> handler();

        /** Determines whether this mutation rejects or omits invocation on a sealed target. */
        SealedPolicy onSealed() default SealedPolicy.FAIL;

        /** Policy for completed LINK targets; no policy grants mutation privileges. */
        enum SealedPolicy {
            /** Reject invocation before constructing the handler. */
            FAIL,
            /** Omit invocation before constructing the handler. */
            SKIP
        }

        /** Repeatable declarations on one domain annotation, in container value order. */
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.ANNOTATION_TYPE)
        @interface List {
            Mutator[] value();
        }
    }

    /**
     * Supplies an unset direct relationship during its containing Builder's field visit.
     * A non-null result uses ordinary checked assignment, ownership and session rules.
     * @param <A> the exact domain annotation type, also when resolved through inheritance
     */
    @FunctionalInterface
    public interface CreationHandler<A extends Annotation> {
        /** Returns a Builder or null to leave the relationship unset. */
        // ADR 0028 requires a Schema-neutral Builder boundary for reusable consumer handlers.
        @SuppressWarnings("java:S1452")
        KlumBuilder<?> create(InvocationContext<A> context);
    }

    /**
     * Configures a non-null field target after creation, or a visited Schema Builder before its fields.
     * Repeated mutations within one domain annotation follow declaration/container value order.
     * Order between different domain annotations or mixed singular/container markers is unspecified.
     * Namespaced public contract (issue #883); behavior is defined by issue #867.
     * @param <A> the exact domain annotation type, also when resolved through inheritance
     */
    @FunctionalInterface
    public interface MutationHandler<A extends Annotation> {
        /** Configures the target through public generated Builder contracts; cannot replace it through context. */
        void mutate(MutationContext<A> context);
    }

    /**
     * Context of one Schema declaration occurrence. Valid for the invocation only; retaining it grants no
     * additional Builder/session rights. Lookup refers to the original Schema field, or to the visited Schema type for type mutation.
     * @param <A> the exact domain annotation type
     */
    public interface InvocationContext<A extends Annotation> {
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

    /**
     * A non-null Builder target to configure through its generated public contract.
     * Field targets fail by default or skip when sealed according to {@link Mutator#onSealed()}.
     * Type dispatch follows existing traversal, which skips sealed aggregation targets.
     * @param <A> the exact domain annotation type
     */
    public interface MutationContext<A extends Annotation> extends InvocationContext<A> {
        /** True for a Schema-type invocation; false for direct field invocation. */
        default boolean isType() { return false; }

        /** Returns the visited Builder for type dispatch, or the existing/just-created field target. */
        // ADR 0028 requires a Schema-neutral Builder boundary for reusable consumer handlers.
        @SuppressWarnings("java:S1452")
        KlumBuilder<?> getTargetBuilder();
    }
}
