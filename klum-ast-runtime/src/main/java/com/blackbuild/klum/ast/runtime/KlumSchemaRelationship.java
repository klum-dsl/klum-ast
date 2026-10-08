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

import com.blackbuild.klum.ast.runtime.internal.SchemaRelationshipDeclaration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable identity of an owning Schema field, independent of receiver, owner values and location.
 * Inherited relationships identify the original declaring Schema class. Annotation lookup is direct;
 * it does not expand repeatable or meta-annotations or read the field value.
 * Instances are created by the framework and remain usable after construction ends.
 */
public final class KlumSchemaRelationship {
    private final Class<?> declaringClass;
    private final String name;

    private KlumSchemaRelationship(SchemaRelationshipDeclaration declaration) {
        declaringClass = declaration.getDeclaringClass();
        name = declaration.getName();
        resolveField();
    }

    static Optional<KlumSchemaRelationship> fromDeclaration(SchemaRelationshipDeclaration declaration) {
        return Optional.ofNullable(declaration).map(KlumSchemaRelationship::new);
    }

    /** Returns the Schema class that declares this field, preserving Class/classloader identity. */
    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    /** Returns the field name, without a collection index, map key or path. */
    public String getName() {
        return name;
    }

    /**
     * Returns the requested runtime annotation directly present on the owning field.
     * @throws NullPointerException if {@code annotationType} is null
     * @throws KlumSchemaException if the retained declaration cannot be resolved
     */
    public <A extends Annotation> Optional<A> getAnnotation(Class<A> annotationType) {
        Objects.requireNonNull(annotationType, "annotationType");
        return Optional.ofNullable(resolveField().getAnnotation(annotationType));
    }

    private Field resolveField() {
        try {
            return declaringClass.getDeclaredField(name);
        } catch (NoSuchFieldException | SecurityException exception) {
            throw new KlumSchemaException("Cannot resolve owning Schema relationship "
                    + declaringClass.getName() + "." + name, exception);
        }
    }

    /** Equality uses declaring Class identity and member name only. */
    @Override
    public boolean equals(Object other) {
        return other instanceof KlumSchemaRelationship relationship
                && declaringClass == relationship.declaringClass && name.equals(relationship.name);
    }

    @Override
    public int hashCode() {
        return 31 * declaringClass.hashCode() + name.hashCode();
    }
}
