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
import com.blackbuild.klum.ast.layer3.AutoLink;
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

/** Direct field dispatch inside the existing containing-Builder phase visit. */
public final class LifecycleParticipants {
    private LifecycleParticipants() {}

    public static boolean hasParticipant(AnnotatedElement field) {
        for (Annotation annotation : field.getDeclaredAnnotations()) {
            if (annotation.annotationType().isAnnotationPresent(LifecycleCreator.class)
                    || annotation.annotationType().isAnnotationPresent(LifecycleMutator.class)) return true;
        }
        return false;
    }

    public static void processField(InternalKlumBuilder<?> containing, String name) {
        Field field = containing.getModelField(name);
        LifecycleParticipantDeclaration.check(field);
        String previousMember = PhaseDriver.getContext().getMember();
        try {
            PhaseDriver.setCurrentMember(name);
            for (Annotation annotation : field.getDeclaredAnnotations()) {
                LifecycleCreator marker = annotation.annotationType().getAnnotation(LifecycleCreator.class);
                if (marker != null && containing.getInstanceAttribute(name) == null)
                    create(containing, field, annotation, marker);
            }
            for (Annotation annotation : field.getDeclaredAnnotations()) {
                LifecycleMutator marker = annotation.annotationType().getAnnotation(LifecycleMutator.class);
                if (marker != null && containing.getInstanceAttribute(name) != null)
                    mutate(containing, field, annotation, marker);
            }
        } finally {
            PhaseDriver.setCurrentMember(previousMember);
        }
    }

    @SuppressWarnings("unchecked") // Runtime declaration validation precedes the annotation-specific invocation.
    private static <A extends Annotation> void create(InternalKlumBuilder<?> containing, Field field,
                                                     A annotation, LifecycleCreator marker) {
        try {
            LifecycleCreationHandler<A> handler = (LifecycleCreationHandler<A>) marker.handler().getConstructor().newInstance();
            KlumBuilder<?> result = handler.create(new FieldContext<>(containing, field, annotation));
            if (result != null) containing.setSingleField(field.getName(), result);
        } catch (ReflectiveOperationException | RuntimeException | AssertionError | LinkageError exception) {
            throw failure(field, annotation, marker.handler(), exception);
        }
    }

    @SuppressWarnings("unchecked") // Runtime declaration validation precedes the annotation-specific invocation.
    private static <A extends Annotation> void mutate(InternalKlumBuilder<?> containing, Field field,
                                                     A annotation, LifecycleMutator marker) {
        try {
            InternalKlumBuilder<?> target = (InternalKlumBuilder<?>) containing.getInstanceAttribute(field.getName());
            if (target.isSealed()) throw new KlumModelException("Lifecycle mutation requires an unsealed Builder");
            LifecycleMutationHandler<A> handler = (LifecycleMutationHandler<A>) marker.handler().getConstructor().newInstance();
            handler.mutate(new MutationContext<>(containing, field, annotation, target));
        } catch (ReflectiveOperationException | RuntimeException | AssertionError | LinkageError exception) {
            throw failure(field, annotation, marker.handler(), exception);
        }
    }

    private static KlumModelException failure(Field field, Annotation annotation, Class<?> handler, Throwable exception) {
        Throwable cause = exception instanceof InvocationTargetException invocation ? invocation.getCause() : exception;
        return new KlumModelException("Participant " + annotation.annotationType().getName() + " handler "
                + handler.getName() + " during " + AutoLink.class.getSimpleName() + " on "
                + field.getDeclaringClass().getName() + "." + field.getName(), cause);
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

    private static final class MutationContext<A extends Annotation> extends FieldContext<A> implements LifecycleMutationContext<A> {
        private final KlumBuilder<?> target;

        private MutationContext(InternalKlumBuilder<?> containing, Field field, A annotation, KlumBuilder<?> target) {
            super(containing, field, annotation);
            this.target = target;
        }
        @Override public KlumBuilder<?> getTargetBuilder() { return target; }
    }
}
