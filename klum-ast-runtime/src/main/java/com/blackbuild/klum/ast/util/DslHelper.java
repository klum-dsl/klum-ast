/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.Key;
import groovy.lang.*;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.reflection.CachedField;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.String.format;

public class DslHelper {

    public static final Class<com.blackbuild.groovy.configdsl.transform.Field> FIELD_ANNOTATION = com.blackbuild.groovy.configdsl.transform.Field.class;

    private DslHelper() {}

    public static boolean isDslType(Type type) {
        if (!(type instanceof Class))
            return false;
        Class<?> clazz = (Class<?>) type;
        return clazz.isAnnotationPresent(DSL.class);
    }

    public static Type requireDslType(Type type) {
        if (!isDslType(type))
            throw new IllegalArgumentException(format("Type %s is not a DSL type", type));
        return type;
    }

    public static boolean isDslObject(Object object) {
        return object != null && isDslType(object.getClass());
    }

    public static List<Class<?>> getDslHierarchyOf(Class<?> type) {
        List<Class<?>> result = new ArrayList<>();
        while (isDslType(type)) {
            result.add(0, type);
            type = type.getSuperclass();
        }
        return result;
    }

    public static List<Class<?>> getHierarchyOf(Class<?> type) {
        List<Class<?>> result = new ArrayList<>();
        while (type != null) {
            result.add(type);
            type = type.getSuperclass();
        }
        return result;
    }

    public static Type getElementTypeOfField(Class<?> type, String name) {
        Optional<Field> field = getField(type, name);
        return field.stream().map(DslHelper::getElementType).findFirst().orElseThrow(() -> new MissingFieldException(name, type));
    }

    public static Type getElementType(Field field) {
        Type genericType = field.getGenericType();
        ParameterizedType parameterizedType = (ParameterizedType) genericType;
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        Type typeArgument = typeArguments[typeArguments.length - 1];
        if (typeArgument instanceof WildcardType)
            return ((WildcardType) typeArgument).getUpperBounds()[0];
        return typeArgument;
    }

    @SuppressWarnings("unchecked")
    public static <T> T castTo(Object object, Class<T> targetType) {
        return (T) InvokerHelper.invokeMethod(object, "asType", targetType);
    }

    public static Optional<Field> getField(Class<?> type, String name) {
        return getHierarchyOf(type).stream()
                .map(layer -> getFieldOfHierarchyLayer(layer, name))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public static Optional<CachedField> getCachedField(Class<?> type, String name) {
        return getHierarchyOf(type).stream()
                .map(layer -> getCachedFieldOfHierarchyLayer(layer, name))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public static FieldType getKlumFieldType(Field field) {
        com.blackbuild.groovy.configdsl.transform.Field fieldAnnotation = field.getAnnotation(com.blackbuild.groovy.configdsl.transform.Field.class);
        if (fieldAnnotation == null) return FieldType.DEFAULT;
        return fieldAnnotation.value();
    }

    public static <T extends Annotation> T getFieldAnnotation(Class<?> type, String fieldName, Class<T> annotationType) {
        Optional<Field> field = getField(type, fieldName);
        if (!field.isPresent())
            throw new IllegalArgumentException(format("Type %s does not have a field named %s", type, fieldName));
        return field.get().getAnnotation(annotationType);
    }

    public static boolean isClosure(Class<?> type) {
        return Closure.class.isAssignableFrom(type);
    }

    public static <T extends Annotation> Optional<T> getOptionalFieldAnnotation(Class<?> type, String fieldName, Class<T> annotationType) {
        return getField(type, fieldName)
                .map(value -> value.getAnnotation(annotationType));
    }

    public static Optional<Method> getMethod(Class<?> type, String name, Class<?>... args) {
        return getHierarchyOf(type).stream()
                .map(layer -> getMethodOfHierarchyLayer(layer, name, args))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    public static Optional<? extends AnnotatedElement> getFieldOrMethod(Class<?> type, String name, Class<?> argumentType) {
        Optional<Field> field = getField(type, name);
        if (field.isPresent())
            return field;

        return getMethod(type, name, argumentType);
    }

    private static Optional<Field> getFieldOfHierarchyLayer(Class<?> layer, String name) {
        return getCachedFieldOfHierarchyLayer(layer, name).map(DslHelper::getRealField);
    }

    private static Optional<CachedField> getCachedFieldOfHierarchyLayer(Class<?> layer, String name) {
        MetaProperty metaProperty = InvokerHelper.getMetaClass(layer).getMetaProperty(name);
        if (!(metaProperty instanceof MetaBeanProperty)) return Optional.empty();

        return Optional.ofNullable(((MetaBeanProperty) metaProperty).getField());
    }

    // groovy 3 makes Field.field private, so we need a workaround
    private static Field getRealField(CachedField cachedField) {
        return cachedField != null ? (Field) InvokerHelper.getAttribute(cachedField, "field") : null;
    }

    private static Optional<Method> getMethodOfHierarchyLayer(Class<?> layer, String name, Class<?>[] args) {
        try {
            return Optional.of(layer.getMethod(name,args));
        } catch (NoSuchMethodException e) {
            return Optional.empty();
        }
    }

    public static Optional<Field> getKeyField(Class<?> type) {
        return getFieldsAnnotatedWith(type, Key.class).findFirst();
    }

    public static boolean isKeyed(Class<?> type) {
        return getKeyField(type).isPresent();
    }

    public static <T> Class<T> requireKeyed(Class<T> type) {
        if (!isKeyed(type))
            throw new IllegalArgumentException(format("Type %s is not keyed.", type));
        return type;
    }

    public static <T> Class<T> requireNotKeyed(Class<T> type) {
        if (isKeyed(type))
            throw new IllegalArgumentException(format("Type %s is keyed.", type));
        return type;
    }

    public static List<Class<?>> getRwHierarchyOf(Class<?> rwType) {
        List<Class<?>> result = new ArrayList<>();
        while (rwType.getEnclosingClass() != null) {
            result.add(rwType);
            rwType = rwType.getSuperclass();
        }
        Collections.reverse(result);
        return result;
    }

    public static boolean isInstantiable(Class<?> type) {
        return !type.isInterface() && ((type.getModifiers() & Opcodes.ACC_ABSTRACT) == 0);
    }

    public static Stream<Method> getMethodsAnnotatedWith(Class<?> type, Class<? extends Annotation> annotation) {

        return getDslOrRwHierarchyOf(type)
                .stream()
                .map(Class::getDeclaredMethods)
                .flatMap(array -> Arrays.stream(array).sorted(Comparator.comparing(Method::getName)))
                .filter(method -> method.isAnnotationPresent(annotation));
    }

    @NotNull
    private static List<Class<?>> getDslOrRwHierarchyOf(Class<?> type) {
        return type.isAnnotationPresent(DSL.class) ? getDslHierarchyOf(type) : getRwHierarchyOf(type);
    }

    public static Stream<Field> getFieldsAnnotatedWith(Class<?> type, Class<? extends Annotation> annotation) {

        return getDslOrRwHierarchyOf(type)
                .stream()
                .map(Class::getDeclaredFields)
                .flatMap(Arrays::stream)
                .filter(field -> field.isAnnotationPresent(annotation));
    }

    public static <T> Optional<Method> getVirtualSetter(Class<?> rwType, String methodName, Class<T> type) {
        List<Method> methods = getMethodsAnnotatedWith(rwType, FIELD_ANNOTATION)
                .filter(method -> method.getName().equals(methodName))
                .filter(method -> method.getParameterTypes()[0].isAssignableFrom(type))
                .collect(Collectors.toList());

        if (methods.isEmpty())
            return Optional.empty();

        if (methods.size() == 1)
            return Optional.of(methods.get(0));

        throw new IllegalStateException(format("Found more than one virtual setter matching %s(%s): %s", methodName, type.getName(), methods));
    }

    static Object getAttributeValue(String name, Object instance) {
        Optional<CachedField> cachedField = getCachedField(instance.getClass(), name);

        // cannot use .map, because value can be null
        if (cachedField.isPresent())
            return cachedField.get().getProperty(instance);

        throw new MissingPropertyException(name, instance.getClass());
    }


    public static Class<?> getClassFromType(Type type) {
        if (type instanceof Class)
            return (Class<?>) type;
        if (type instanceof WildcardType)
            return (Class<?>) ((WildcardType) type).getUpperBounds()[0];
        if (type instanceof ParameterizedType)
            return (Class<?>) ((ParameterizedType) type).getRawType();
        throw new IllegalArgumentException("Unknown Type: " + type);
    }
}
