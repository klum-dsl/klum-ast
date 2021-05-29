package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.Key;
import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaProperty;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.reflection.CachedField;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Arrays.stream;

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
        List<Class<?>> result = new ArrayList<>();
        while (isDslType(type)) {
            result.add(type);
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

    public static Field getField(Class<?> type, String name) {
        return getHierarchyOf(type).stream()
                .map(layer -> getFieldOfHierarchyLayer(layer, name))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst().orElseThrow(() -> new MissingPropertyException(name, type));
    }

    private static Optional<Field> getFieldOfHierarchyLayer(Class<?> layer, String name) {
        MetaProperty metaProperty = InvokerHelper.getMetaClass(layer).getMetaProperty(name);
        if (metaProperty instanceof MetaBeanProperty) {
            CachedField field = ((MetaBeanProperty) metaProperty).getField();
            return field != null ? Optional.of(field.field) : Optional.empty();
        }
        return Optional.empty();
    }

    public static Optional<String> getKeyField(Class<?> type) {
        return stream(getDslAncestor(type).getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Key.class))
                .map(Field::getName)
                .findFirst();
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