package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.Key;
import groovy.lang.Closure;
import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaProperty;
import groovy.lang.MissingFieldException;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.reflection.CachedField;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.String.format;

public class DslHelper {

    private DslHelper() {}

    public static boolean isDslType(Type type) {
        if (!(type instanceof Class))
            return false;
        Class<?> clazz = (Class<?>) type;
        return clazz.isAnnotationPresent(DSL.class);
    }

    public static Class<?> getDslAncestor(Class<?> type) {
        while (isDslType(type.getSuperclass()))
            type = type.getSuperclass();
        return type;
    }

    public static List<Class<?>> getDslHierarchyOf(Class<?> type) {
        List<Class<?>> result = new LinkedList<>();
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

    public static Class<?> getElementType(Class<?> type, String name) {
        Optional<Field> field = getField(type, name);
        if (!field.isPresent())
            throw new MissingFieldException(name,type);
        Type genericType = field.get().getGenericType();
        ParameterizedType parameterizedType = (ParameterizedType) genericType;
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        Type typeArgument = typeArguments[typeArguments.length - 1];
        if (typeArgument instanceof Class)
            return (Class<?>) typeArgument;
        if (typeArgument instanceof WildcardType)
            return (Class<?>) ((WildcardType) typeArgument).getUpperBounds()[0];
        throw new IllegalArgumentException(format("ElementType %s is neither class nor Wildcard", typeArgument));
    }



    public static Optional<Field> getField(Class<?> type, String name) {
        return getHierarchyOf(type).stream()
                .map(layer -> getFieldOfHierarchyLayer(layer, name))
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
        MetaProperty metaProperty = InvokerHelper.getMetaClass(layer).getMetaProperty(name);
        if (!(metaProperty instanceof MetaBeanProperty)) return Optional.empty();

        CachedField field = ((MetaBeanProperty) metaProperty).getField();
        return Optional.ofNullable(getRealField(field));
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
        return getFieldsAnnotatedWith(type, Key.class).stream().findFirst();
    }

    public static boolean isKeyed(Class<?> type) {
        return getKeyField(type).isPresent();
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

    public static List<Method> getMethodsAnnotatedWith(Class<?> type, Class<? extends Annotation> annotation) {

        return getDslOrRwHierarchyOf(type)
                .stream()
                .map(Class::getDeclaredMethods)
                .flatMap(array -> Arrays.stream(array).sorted(Comparator.comparing(Method::getName)))
                .filter(method -> method.isAnnotationPresent(annotation))
                .collect(Collectors.toList());
    }

    @NotNull
    private static List<Class<?>> getDslOrRwHierarchyOf(Class<?> type) {
        return type.isAnnotationPresent(DSL.class) ? getDslHierarchyOf(type) : getRwHierarchyOf(type);
    }

    public static List<Field> getFieldsAnnotatedWith(Class<?> type, Class<? extends Annotation> annotation) {

        return getDslOrRwHierarchyOf(type)
                .stream()
                .map(Class::getDeclaredFields)
                .flatMap(Arrays::stream)
                .filter(field -> field.isAnnotationPresent(annotation))
                .collect(Collectors.toList());
    }
}
