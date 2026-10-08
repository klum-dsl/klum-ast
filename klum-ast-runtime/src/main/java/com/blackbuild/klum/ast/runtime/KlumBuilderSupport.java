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

import com.blackbuild.klum.ast.runtime.internal.InternalKlumBuilderSupport;

import java.lang.annotation.Annotation;
import java.util.Objects;
import java.util.Optional;

/**
 * Read-only support for a generated Builder. Structure may be acquired early; every ownership
 * request requires the current thread's active Construction session and a phase after OWNER(15).
 * Normal sealing does not end this read lifetime. After construction, use {@link KlumObjectSupport}.
 *
 * @param <T> the completed DSL Object type
 */
public final class KlumBuilderSupport<T> {
    private final KlumBuilder<T> builder;

    private KlumBuilderSupport(KlumBuilder<T> builder) {
        this.builder = builder;
    }

    /**
     * Creates support for a genuine generated Builder without granting permanent read eligibility.
     * @throws NullPointerException if {@code builder} is null
     * @throws KlumModelException if the receiver is an unsupported marker implementation
     */
    public static <T> KlumBuilderSupport<T> of(KlumBuilder<T> builder) {
        Objects.requireNonNull(builder, "builder");
        InternalKlumBuilderSupport.requireBuilder(builder);
        return new KlumBuilderSupport<>(builder);
    }

    /** Returns a live, read-only Structure view; each query checks the current session and phase. */
    public Structure<T> getStructure() {
        return new Structure<>(builder);
    }

    /**
     * Owning-declaration support for one Builder. This view supplies no traversal or mutation.
     * @param <T> the completed DSL Object type
     */
    public static final class Structure<T> {
        private final KlumBuilder<T> builder;

        private Structure(KlumBuilder<T> builder) {
            this.builder = builder;
        }

        /**
         * Returns the accepted owning Schema declaration, or empty for eligible absence.
         * Completed LINK wrappers describe the target's original declaration.
         * @throws KlumModelException unless this receiver belongs to the current active session at phase >15
         * @throws KlumSchemaException if an explicit retained declaration cannot be resolved
         */
        public Optional<KlumSchemaRelationship> getOwningRelationship() {
            return readRelationship("getOwningRelationship");
        }

        /**
         * Returns the requested annotation directly present on the owning declaration.
         * @throws NullPointerException if {@code annotationType} is null
         * @throws KlumModelException unless this receiver belongs to the current active session at phase >15
         * @throws KlumSchemaException if an explicit retained declaration cannot be resolved
         */
        public <A extends Annotation> Optional<A> getOwningRelationshipAnnotation(Class<A> annotationType) {
            Objects.requireNonNull(annotationType, "annotationType");
            return readRelationship("getOwningRelationshipAnnotation")
                    .flatMap(relationship -> relationship.getAnnotation(annotationType));
        }

        private Optional<KlumSchemaRelationship> readRelationship(String operation) {
            return KlumSchemaRelationship.fromDeclaration(
                    InternalKlumBuilderSupport.getOwningRelationship(builder, operation));
        }
    }
}
