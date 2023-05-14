/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.klum.ast.util.DslHelper;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import groovy.lang.MetaProperty;
import groovy.lang.PropertyValue;
import groovy.lang.Tuple2;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.codehaus.groovy.tools.Utilities;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.String.format;

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
    public static Optional<String> getPathOfFieldContaining(Object container, @NotNull Object child) {
        Optional<String> singleValuePath = getPathOfSingleField(container, child);
        if (singleValuePath.isPresent()) return singleValuePath;

        Optional<String> collectionPath = getPathOfCollectionMember(container, child);
        if (collectionPath.isPresent()) return collectionPath;

        return getPathOfMapMember(container, child);
    }

    @NotNull
    static Optional<String> getPathOfMapMember(Object container, @NotNull Object child) {
        return ClusterModel.getPropertiesStream(container, Map.class)
                .filter(it -> ClusterModel.isMapOf(container, it, child.getClass()))
                .map(it -> new Tuple2<Object, Optional<?>>(it.getName(), findKeyForValue((Map<?, ?>) it.getValue(), child)))
                .filter(it -> it.getSecond().isPresent())
                .map(it -> toGPath(it.getFirst()) + "." + toGPath(it.getSecond().get()))
                .findFirst();
    }

    @NotNull
    static Optional<String> getPathOfCollectionMember(Object container, @NotNull Object child) {
        return ClusterModel.getPropertiesStream(container, Collection.class)
                .filter(it -> ClusterModel.isCollectionOf(container, it, child.getClass()))
                .map(it -> new Tuple2<>(it.getName(), getIndexInCollection((Collection<?>) it.getValue(), child)))
                .filter(it -> it.getSecond() != -1)
                .map(it -> toGPath(it.getFirst()) + "[" + it.getSecond() + "]")
                .findFirst();
    }

    @NotNull
    static Optional<String> getPathOfSingleField(Object container, @NotNull Object child) {
        return ClusterModel.getPropertiesStream(container, child.getClass())
                .filter(it -> it.getValue() == child)
                .map(PropertyValue::getName)
                .map(StructureUtil::toGPath)
                .findFirst();
    }

    static int getIndexInCollection(Collection<?> container, Object child) {
        if (container instanceof List)
            return ((List<?>) container).indexOf(child);

        int index = 0;
        for (Object element: container) {
            if (element == child)
                return index;
            index++;
        }
        return -1;
    }

    static Optional<?> findKeyForValue(Map<?, ?> map, @NotNull Object value) {
        return map.entrySet().stream()
                .filter(it -> it.getValue() == value)
                .map(Map.Entry::getKey)
                .findFirst();
    }

    static boolean isNoInternalProperty(MetaProperty property) {
        return !property.getName().contains("$");
    }

    /**
     * Returns the full path of the given leaf object relative to its root element. This is determined by traversing
     * the owner fields up until no more owner fields are encountered. Note that corner cases, like an object having two
     * different owners or having only an owner method are not handled. The optional rootPath will be prepended to
     * the path. If leaf is not a DSL object or does not have an owner, an empty string is returned, regardless of the rootPath
     * @param leaf The object whose path is to be determined
     * @param rootPath on optional root element to prepend to the path
     * @return The full path of the object.
     */
    public static @NotNull String getFullPath(@NotNull Object leaf, @Nullable String rootPath) {
        Deque<String> elements = new LinkedList<>();
        addParentPaths(leaf, elements);

        if (elements.isEmpty())
            return "";

        if (rootPath != null)
            elements.addFirst(rootPath);
        return String.join(".", elements);
    }

    public static void addParentPaths(Object leaf, Deque<String> elements) {
        if (!DslHelper.isDslObject(leaf))
            return;

        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(leaf);
        Object owner = proxy.getSingleOwner();
        if (!DslHelper.isDslObject(owner))
            return;

        Optional<String> pathOfFieldContaining = getPathOfFieldContaining(owner, leaf);

        if (!pathOfFieldContaining.isPresent())
            return;
        elements.addFirst(pathOfFieldContaining.get());
        addParentPaths(owner, elements);
    }
}
