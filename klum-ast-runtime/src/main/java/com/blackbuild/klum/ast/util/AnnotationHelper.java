/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.String.format;

/**
 * Helper class for annotations. Provides various functions concerning the discovery and handling of annotations.
 */
public class AnnotationHelper {

    private AnnotationHelper() {}

    /**
     * Tries to retrieve the given Annotation from the given target. Checks in the following order:
     *
     * <ol>
     *     <li>On the target itself</li>
     *     <li>If the target is a member on the owning class of the target</li>
     *     <li>for each DSL class in hierarchy, check the class and the classes package</li>
     * </ol>
     * <p>
     * The first found instance of the annotation is returned. If no annotation is found, null is returned.
     *
     * @param target         the target to retrieve the annotation from
     * @param annotationType the annotation type to retrieve
     * @return the annotation or {@link Optional#empty()} if no annotation is found
     */
    public static <T extends Annotation> Optional<T> getMostSpecificAnnotation(AnnotatedElement target, Class<T> annotationType) {
        return getMostSpecificAnnotation(target, annotationType, a -> true);
    }

    /**
     * Tries to retrieve the given Annotation from the given target. Checks in the following order:
     *
     * <ol>
     *     <li>On the target itself</li>
     *     <li>If the target is a member on the owning class of the target</li>
     *     <li>for each DSL class in hierarchy, check the class and the classes package</li>
     * </ol>
     *
     * The first found instance of the annotation that matches the given filter is returned. If no annotation is found, null is returned.
     *
     * @param target the target to retrieve the annotation from
     * @param annotationType the annotation type to retrieve
     * @param filter the filter to apply to the annotation
     * @return the annotation or {@link Optional#empty()} if no annotation is found
     * @param <T> the annotation type
     */
    public static <T extends Annotation> Optional<T> getMostSpecificAnnotation(AnnotatedElement target, Class<T> annotationType, Predicate<T> filter) {
        if (target instanceof Field || target instanceof Executable) {
            return getMostSpecificAnnotationFromMember(target, annotationType, filter);
        }
        if (target instanceof Class)
            return getMostSpecificAnnotationFromClass((Class<?>) target, annotationType, filter);
        throw new IllegalArgumentException(format("Cannot get annotation from %s", target));
    }

    private static <T extends Annotation> Optional<T> getMostSpecificAnnotationFromMember(AnnotatedElement target, Class<T> annotationType, Predicate<T> filter) {
        T result = getNestedAnnotation(target, annotationType);
        if (result != null && filter.test(result)) return Optional.of(result);

        return getMostSpecificAnnotationFromClass(((Member) target).getDeclaringClass(), annotationType, filter);
    }

    private static <T extends Annotation> Optional<T> getMostSpecificAnnotationFromClass(Class<?> target, Class<T> annotationType, Predicate<T> filter) {
        if (target == null) return Optional.empty();
        T result = getNestedAnnotation(target, annotationType);
        if (result != null && filter.test(result)) return Optional.of(result);

        result = getNestedAnnotation(target.getPackage(), annotationType);
        if (result != null && filter.test(result)) return Optional.of(result);

        return getMostSpecificAnnotationFromClass(target.getSuperclass(), annotationType, filter);
    }

    /**
     * Returns the annotation of the given type that is directly present on the given target. If the annotation is not present,
     * check all annotations of the target that are in turn annotated with the given annotation type.
     * @param target the target to retrieve the annotation from
     * @param annotationType the annotation type to retrieve
     * @return the found annotation or null
     * @param <T> the annotation type
     */
    public static <T extends Annotation> T getNestedAnnotation(AnnotatedElement target, Class<T> annotationType) {
        T result = target.getAnnotation(annotationType);
        if (result != null) return result;

        if (!isMetaAnnotation(annotationType)) return null;

        for (Annotation annotation : target.getAnnotations()) {
            result = annotation.annotationType().getAnnotation(annotationType);
            if (result != null) return result;
        }

        return null;
    }

    /**
     * Returns all annotations which are themselves annotated with a meta annotation on the given target.
     * @param target the target to retrieve the annotation from
     * @param metaAnnotation the meta annotation that must be present on the result annotations
     * @return the found annotation or null
     */
    public static Stream<Annotation> getMetaAnnotated(AnnotatedElement target, Class<? extends Annotation> metaAnnotation) {
        return Stream.of(target.getAnnotations())
                .filter(annotation -> annotation.annotationType().isAnnotationPresent(metaAnnotation));
    }

    public static Map<String, Object> getNonDefaultMembers(Annotation annotation) {
        Map<String, Object> nonDefaultValues = new HashMap<>();

        for (Method method : annotation.annotationType().getDeclaredMethods()) {
            try {
                Object defaultValue = method.getDefaultValue();
                Object actualValue = method.invoke(annotation);

                if (defaultValue == null || !defaultValue.equals(actualValue))
                    nonDefaultValues.put(method.getName(), actualValue);
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new IllegalStateException("Error accessing annotation member", e);
            }
        }

        return nonDefaultValues;
    }

    /**
     * Returns true if the given annotation is a meta annotation, i.e. it's targets include {@link ElementType#ANNOTATION_TYPE} or are not set at all.
     * @param type the annotation type to check
     * @return true if the annotation is a meta annotation
     */
    public static boolean isMetaAnnotation(Class<? extends Annotation> type) {
        Target target = type.getAnnotation(Target.class);
        if (target == null) return true;
        for (ElementType elementType : target.value()) {
            if (elementType == ElementType.ANNOTATION_TYPE)
                return true;
        }
        return false;
    }

    public static <T extends Annotation> Optional<T> getAnnotation(Field field, Class<T> annotationType) {
        return Optional.ofNullable(field.getAnnotation(annotationType));
    }
}
