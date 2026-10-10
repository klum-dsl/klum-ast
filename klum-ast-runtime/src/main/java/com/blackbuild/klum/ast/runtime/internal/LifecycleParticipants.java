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
package com.blackbuild.klum.ast.runtime.internal;

import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.KlumModelException;
import com.blackbuild.klum.ast.runtime.LifecycleCreator;
import com.blackbuild.klum.ast.runtime.LifecycleMutator;
import com.blackbuild.klum.ast.runtime.LifecycleCreationHandler;
import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler;
import com.blackbuild.klum.ast.runtime.LifecycleFieldContext;
import com.blackbuild.klum.ast.runtime.LifecycleMutationContext;
import com.blackbuild.klum.ast.runtime.internal.process.PhaseDriver;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.Objects;
import java.util.Optional;

/** Field and type dispatch inside the existing Builder phase visit. */
public final class LifecycleParticipants {
    private LifecycleParticipants() {}

    // Discovery is phase-independent so unsupported precompiled phases reach declaration validation.
    public static boolean hasParticipant(AnnotatedElement field) {
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            if (annotation.annotationType().getAnnotationsByType(LifecycleCreator.class).length != 0
                    || annotation.annotationType().getAnnotationsByType(LifecycleMutator.class).length != 0) return true;
        }
        return false;
    }

    /** Runs only within an existing unsealed Builder visit; never reconstructs ownership. */
    public static void processType(InternalKlumBuilder<?> target, Object container, String name,
                                   Class<? extends Annotation> phase) {
        Class<?> schema = target.getModelType();
        LifecycleParticipantDeclaration.check(schema);
        String previousMember = PhaseDriver.getContext().getMember();
        try {
            PhaseDriver.setCurrentMember(null);
            for (Annotation annotation : schema.getAnnotations()) {
                for (LifecycleMutator marker : annotation.annotationType().getAnnotationsByType(LifecycleMutator.class)) {
                    if (marker.phase() == phase)
                        mutateType(target, container, name, annotation, marker);
                }
            }
        } finally {
            PhaseDriver.setCurrentMember(previousMember);
        }
    }

    @SuppressWarnings("unchecked") // Runtime declaration validation precedes the annotation-specific invocation.
    private static <A extends Annotation> void mutateType(InternalKlumBuilder<?> target, Object container,
                                                         String name, A annotation, LifecycleMutator marker) {
        try {
            LifecycleMutationHandler<A> handler = marker.handler().getConstructor().newInstance();
            handler.mutate(new TypeContext<>(target, container, name, annotation));
        } catch (ReflectiveOperationException | RuntimeException | AssertionError | LinkageError exception) {
            throw failure(target.getModelType().getName(), annotation, marker.handler(), marker.phase(), exception);
        }
    }

    public static void processField(InternalKlumBuilder<?> containing, String name, Class<? extends Annotation> phase) {
        Field field = containing.getModelField(name);
        LifecycleParticipantDeclaration.check(field);
        String previousMember = PhaseDriver.getContext().getMember();
        try {
            PhaseDriver.setCurrentMember(name);
            for (Annotation annotation : field.getDeclaredAnnotations()) {
                for (LifecycleCreator marker : annotation.annotationType().getAnnotationsByType(LifecycleCreator.class)) {
                    if (marker.phase() == phase && containing.getInstanceAttribute(name) == null)
                        create(containing, field, annotation, marker);
                }
            }
            for (Annotation annotation : field.getDeclaredAnnotations()) {
                for (LifecycleMutator marker : annotation.annotationType().getAnnotationsByType(LifecycleMutator.class)) {
                    if (marker.phase() == phase && containing.getInstanceAttribute(name) != null)
                        mutate(containing, field, annotation, marker);
                }
            }
        } finally {
            PhaseDriver.setCurrentMember(previousMember);
        }
    }

    @SuppressWarnings("unchecked") // Runtime declaration validation precedes the annotation-specific invocation.
    private static <A extends Annotation> void create(InternalKlumBuilder<?> containing, Field field,
                                                     A annotation, LifecycleCreator marker) {
        try {
            LifecycleCreationHandler<A> handler = marker.handler().getConstructor().newInstance();
            KlumBuilder<?> result = handler.create(new FieldContext<>(containing, field, annotation));
            if (result != null) containing.setSingleField(field.getName(), result);
        } catch (ReflectiveOperationException | RuntimeException | AssertionError | LinkageError exception) {
            throw failure(field, annotation, marker.handler(), marker.phase(), exception);
        }
    }

    @SuppressWarnings("unchecked") // Runtime declaration validation precedes the annotation-specific invocation.
    private static <A extends Annotation> void mutate(InternalKlumBuilder<?> containing, Field field,
                                                     A annotation, LifecycleMutator marker) {
        try {
            InternalKlumBuilder<?> target = (InternalKlumBuilder<?>) containing.getInstanceAttribute(field.getName());
            if (target.isSealed()) {
                if (marker.onSealed() == LifecycleMutator.SealedPolicy.SKIP) return;
                throw new KlumModelException("Lifecycle mutation requires an unsealed Builder (onSealed=FAIL)");
            }
            LifecycleMutationHandler<A> handler = marker.handler().getConstructor().newInstance();
            handler.mutate(new MutationContext<>(containing, field, annotation, target));
        } catch (ReflectiveOperationException | RuntimeException | AssertionError | LinkageError exception) {
            throw failure(field, annotation, marker.handler(), marker.phase(), exception);
        }
    }

    private static KlumModelException failure(Field field, Annotation annotation, Class<?> handler, Class<? extends Annotation> phase, Throwable exception) {
        return failure(field.getDeclaringClass().getName() + "." + field.getName(), annotation, handler, phase, exception);
    }

    private static KlumModelException failure(String location, Annotation annotation, Class<?> handler, Class<? extends Annotation> phase, Throwable exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getCause() : exception;
        return new KlumModelException("Participant " + annotation.annotationType().getName() + " handler "
                + handler.getName() + " during " + phase.getSimpleName() + " on "
                + location, cause);
    }

    private static class FieldContext<A extends Annotation> implements LifecycleFieldContext<A> {
        private final InternalKlumBuilder<?> containing;
        private final Field field;
        private final A annotation;

        private FieldContext(InternalKlumBuilder<?> containing, Field field, A annotation) {
            this.containing = containing;
            this.field = field;
            this.annotation = annotation;
        }

        @Override public A getAnnotation() { return annotation; }
        @Override public <B extends Annotation> Optional<B> getAnnotation(Class<B> annotationType) {
            return Optional.ofNullable(field.getDeclaredAnnotation(Objects.requireNonNull(annotationType, "annotationType")));
        }
        @Override public KlumBuilder<?> getContainingBuilder() { return containing; }
        @Override public String getFieldName() { return field.getName(); }
        @Override public Class<?> getDeclaredType() { return field.getType(); }
        @Override public FieldType getFieldType() { return DslHelper.getKlumFieldType(field); }
    }

    private static final class TypeContext<A extends Annotation> implements LifecycleMutationContext<A> {
        private final InternalKlumBuilder<?> target;
        private final InternalKlumBuilder<?> containing;
        private final String name;
        private final A annotation;

        private TypeContext(InternalKlumBuilder<?> target, Object container, String name, A annotation) {
            this.target = target;
            this.containing = container instanceof InternalKlumBuilder<?> builder ? builder : null;
            this.name = name;
            this.annotation = annotation;
        }

        @Override public boolean isType() { return true; }
        @Override public A getAnnotation() { return annotation; }
        @Override public <B extends Annotation> Optional<B> getAnnotation(Class<B> annotationType) {
            return Optional.ofNullable(target.getModelType().getAnnotation(Objects.requireNonNull(annotationType, "annotationType")));
        }
        @Override public KlumBuilder<?> getContainingBuilder() { return containing; }
        @Override public String getFieldName() { return name; }
        @Override public Class<?> getDeclaredType() { return target.getModelType(); }
        @Override public FieldType getFieldType() {
            return containing == null || name == null ? null : DslHelper.getField(containing.getModelType(), name)
                    .map(DslHelper::getKlumFieldType).orElse(null);
        }
        @Override public KlumBuilder<?> getTargetBuilder() { return target; }
    }

    private static final class MutationContext<A extends Annotation> extends FieldContext<A> implements LifecycleMutationContext<A> {
        private final KlumBuilder<?> target;

        private MutationContext(InternalKlumBuilder<?> containing, Field field, A annotation, KlumBuilder<?> target) {
            super(containing, field, annotation);
            this.target = target;
        }
        @Override public KlumBuilder<?> getTargetBuilder() { return target; }
    }
}
