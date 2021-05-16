package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.Key;
import groovy.lang.GroovyObject;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.util.Optional;

import static java.util.Arrays.stream;

public class DslHelper {

    private DslHelper() {}

    public static boolean isDslType(Type type) {
        if (!(type instanceof Class))
            return false;
        Class<?> clazz = (Class<?>) type;
        return GroovyObject.class.isAssignableFrom(clazz) && clazz.isAnnotationPresent(DSL.class);
    }

    public static Class<?> getDslAncestor(Class<?> type) {
        while (isDslType(type.getSuperclass()))
            type = type.getSuperclass();
        return type;
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
}
