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
package com.blackbuild.klum.ast.util;

import com.blackbuild.klum.ast.process.PhaseDriver;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Helper class that encapsulates lifecycle relevant methods. This reduces the complexity of KlumInstanceProxy.
 */
public class LifecycleHelper {

    private LifecycleHelper() {
        // static only
    }

    /**
     * Executes all methods annotated with the given annotation on the rw instance of the proxy.
     * Also executes all closures annotated with the given annotation.
     * @param proxy the proxy for which the lifecycle methods should be executed
     * @param annotation the annotation that marks the lifecycle methods and closures
     */
    public static void executeLifecycleMethods(KlumBuilder<?> builder, Class<? extends Annotation> annotation) {
        Object rw = builder;
        DslHelper.getMethodsAnnotatedWith(rw.getClass(), annotation)
                .map(Method::getName)
                .distinct()
                .forEach(method -> {
                    try {
                        PhaseDriver.setCurrentMember(method);
                        InvokerHelper.invokeMethod(rw, method, null);
                    } finally {
                        PhaseDriver.setCurrentMember(null);
                    }
                });
        executeLifecycleClosures(builder, annotation);
    }

    /** @deprecated construction lifecycle state now belongs to {@link KlumBuilder}. */
    @Deprecated
    public static void executeLifecycleMethods(KlumInstanceProxy proxy, Class<? extends Annotation> annotation) {
        executeLifecycleMethods(proxy.getBuilder(), annotation);
    }

    /**
     * Executes all closures annotated with the given annotation on the DSL instance of the proxy.
     * @param proxy The proxy for which the lifecycle closures should be executed
     * @param annotation the annotation that marks the lifecycle closures
     */
    public static void executeLifecycleClosures(KlumBuilder<?> builder, Class<? extends Annotation> annotation) {
        DslHelper.getFieldsAnnotatedWith(builder.getClass(), annotation)
                .filter(field -> field.getType().equals(Closure.class))
                .map(Field::getName)
                .forEach(name -> executeLifecycleClosure(builder, name));
    }

    /** @deprecated construction lifecycle state now belongs to {@link KlumBuilder}. */
    @Deprecated
    public static void executeLifecycleClosures(KlumInstanceProxy proxy, Class<? extends Annotation> annotation) {
        executeLifecycleClosures(proxy.getBuilder(), annotation);
    }

    private static void executeLifecycleClosure(KlumBuilder<?> builder, String name) {
        Closure<?> closure = builder.getInstanceAttribute(name);
        try {
            PhaseDriver.setCurrentMember(name);
            ClosureHelper.invokeClosureWithDelegateAsArgument(closure, builder);
        } finally {
            PhaseDriver.setCurrentMember(null);
        }
        builder.setInstanceAttribute(name, null);
    }

    /**
     * Returns a list of lifecycle classes for the given class. A lifecycle class is a public, non-static inner class
     * if the given class that is annotated with a lifecycle annotation. Returns the lifecycle classes of parent classes
     * as well, except for lifecycle classes that explicitly override parent's lifecycle classes.
     * <p>
     *     If Child.InnerChild extends Parent.InnerParent, then only InnerChild is returned, as InnerParent is overshadowed
     *     by it.
     * </p>
     * @param owner The class whose lifecycle classes should be returned
     * @param annotation The lifecycle annotation to look for
     * @return A list of the lifecycle classes, can be empty but never null.
     */
    public static @NotNull List<Class<?>> getLifecycleClasses(Class<?> owner, Class<? extends Annotation> annotation) {
        List<Class<?>> classes = new ArrayList<>();

        Class<?> currentClass = owner;
        while (currentClass != null) {

            Arrays.stream(currentClass.getDeclaredClasses())
                    .filter(clazz -> clazz.isAnnotationPresent(annotation))
                    .filter(LifecycleHelper::isValidInnerClass)
                    .filter(clazz -> noSuperclassOf(clazz, classes))
                    .forEach(classes::add);

            currentClass = currentClass.getSuperclass();
        }
        return classes;
    }

    private static boolean isValidInnerClass(Class<?> clazz) {
        return Modifier.isPublic(clazz.getModifiers())
                && !clazz.isAnonymousClass()
                && !clazz.isInterface()
                && !Modifier.isAbstract(clazz.getModifiers())
                && !Modifier.isStatic(clazz.getModifiers());
    }

    private static boolean noSuperclassOf(Class<?> potentialSuperclass, List<Class<?>> classes) {
        return classes.stream().noneMatch(potentialSuperclass::isAssignableFrom);
    }

    @SuppressWarnings("java:S1126")
    public static boolean isValidLifecycleClassMethod(Method method, Class<? extends Annotation> annotation) {
        if (method.isSynthetic()) return false;
        if (!Modifier.isPublic(method.getModifiers())) return false;
        if (Modifier.isStatic(method.getModifiers())) return false;
        if (method.getParameterCount() != 0) return false;
        if (!method.getDeclaringClass().isAnnotationPresent(annotation)) return false;
        return true;
    }

}
