package com.blackbuild.klum.ast.util.layer3;

import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaProperty;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.codehaus.groovy.tools.Utilities;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;
import static org.codehaus.groovy.runtime.DefaultGroovyMethods.getMetaClass;

public class StructureUtil {

    private StructureUtil() {
        //no instances
    }

    /**
     * Returns the default name for a field of the given type. This is the uncapitalized simple name of the type. I.e.
     * if a class Database had a single field of type UtilSchema, then the default name of such a field would be "utilSchema".
     *
     * @param type The class
     * @return the uncapitalized named of the class.
     */
    public static String toDefaultFieldName(Class<?> type) {
        return StringGroovyMethods.uncapitalize(type.getSimpleName());
    }

    /**
     * Returns the default name for the type of the given object.
     *
     * @param object The object to determine the default name
     * @return The uncapitalized name of object's class
     * @see #toDefaultFieldName(Class)
     */
    public static String toDefaultFieldName(Object object) {
        return toDefaultFieldName(object.getClass());
    }

    /**
     * Iterates through a data structure and returns all fields of the given type.
     *
     * @param container The container from which to extract the types
     * @param type      The target type to retrieve
     * @return a map of strings to objects
     */
    public static <T> Map<String, T> deepFind(Object container, Class<T> type) {
        return deepFind(container, type, Collections.emptyList());
    }

    /**
     * Iterates through a data structure and returns all fields of the given type.
     *
     * @param container    The container from which to extract the types
     * @param type         The target type to retrieve
     * @param ignoredTypes All types in this list are completely ignored, i.e. not visited
     * @return a map of strings to objects
     */
    public static <T> Map<String, T> deepFind(Object container, Class<T> type, List<Class<?>> ignoredTypes) {
        return deepFind(container, type, ignoredTypes, "");
    }

    /**
     * Iterates through a data structure and returns all fields of the given type.
     *
     * @param container    The container from which to extract the types
     * @param type         The target type to retrieve
     * @param ignoredTypes All types in this list are completely ignored, i.e. not visited
     * @param path         The prefix to attach to the path
     * @return a map of strings to objects
     */
    public static <T> Map<String, T> deepFind(Object container, Class<T> type, List<Class<?>> ignoredTypes, String path) {
        return doDeepFind(container, type, ignoredTypes, path, new ArrayList<>());
    }

    protected static <T> Map<String, T> doDeepFind(Object container, Class<T> type, List<Class<?>> ignoredTypes, String path, List<Object> visited) {
        Map<String, T> result = new HashMap<>();
        if (container == null
                || ignoredTypes.stream().anyMatch(it -> it.isInstance(container))
                || visited.stream().anyMatch(it -> it == container))
            return result;

        visited.add(container);

        if (type.isInstance(container)) {
            //noinspection unchecked
            result.put(path, (T) container);
            return result;
        }

        if (container instanceof Collection) {
            AtomicInteger index = new AtomicInteger();
            ((Collection<?>) container).forEach(member -> result.putAll(doDeepFind(member, type, ignoredTypes, path + "[" + index.getAndIncrement() + "]", visited)));
        } else if (container instanceof Map) {
            ((Map<?, ?>) container).forEach((key, value) -> result.putAll(doDeepFind(value, type, ignoredTypes, path + "." + toGPath(key), visited)));
        } else {
            getNonIgnoredProperties(container).forEach((name, value) -> result.putAll(doDeepFind(value, type, ignoredTypes, path + "." + name, visited)));
        }

        return result;
    }

    public static String getRelativePath(Object container, Object child, List<Class<?>> ignoredTypes) {
        Map<String, ?> stringMap = deepFind(container, child.getClass(), ignoredTypes);
        // return the first key of stringMap that has child as value
        return stringMap.entrySet().stream()
                .filter(it -> it.getValue() == child)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(format("Object %s is not contained in %s", child, container)));
    }

    static String toGPath(Object value) {
        String text = value.toString();
        return Utilities.isJavaIdentifier(text) ? text : InvokerHelper.inspect(text);
    }

    static Map<String, Object> getNonIgnoredProperties(Object container) {
        Map<String, Object> result = new HashMap<>();
        Class<?> type = container.getClass();

        while (type != null) {
            Arrays.stream(type.getDeclaredFields())
                    .filter(it -> !it.getName().contains("$"))
                    .forEach(it -> result.put(it.getName(), InvokerHelper.getProperty(container, it.getName())));
            type = type.getSuperclass();
        }

        return result;
    }

    /**
     * Returns the name of the field of the container containing the given object. If the object is not
     * contained in a field, returns an empty Optional.
     * @param container The container object to search
     * @param child The child object to look for
     * @return The name of the field containing the child object, or an empty Optional if the object is not contained in a field.
     */
    public static Optional<String> getNameOfFieldContaining(Object container, @NotNull Object child) {
        Objects.requireNonNull(child);
        Optional<MetaProperty> field = getMetaClass(container).getProperties().stream()
                .filter(StructureUtil::isNoInternalProperty)
                .filter(MetaBeanProperty.class::isInstance)
                .filter(it -> ((MetaBeanProperty) it).getGetter() != null)
                .filter(it -> it.getType().isInstance(child))
                .filter(it -> child == it.getProperty(container))
                .findFirst();

        if (field.isPresent())
            return field.map(MetaProperty::getName).map(StructureUtil::toGPath);

        return Optional.empty(); // TODO fix
    }

    static boolean isNoInternalProperty(MetaProperty property) {
        return !property.getName().contains("$");
    }
}
