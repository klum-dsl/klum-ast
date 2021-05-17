package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.Key;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    public static List<String> getMethodsAnnotatedWith(Class<?> rwClass, Class<? extends Annotation> annotation) {
        return getRwHierarchyOf(rwClass)
                .stream()
                .map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .filter(method -> method.isAnnotationPresent(annotation))
                .map(Method::getName)
                .distinct()
                .collect(Collectors.toList());
    }
}
