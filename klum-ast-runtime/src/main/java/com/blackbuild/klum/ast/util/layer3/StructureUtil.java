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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.ast.util.KlumSchemaException;
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
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static com.blackbuild.klum.ast.util.DslHelper.isDslObject;

/**
 * Utility class for working with data structures. Provides methods to iterate through data structures and find
 * specific ancestors or GPath expressions.
 */
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

    public static void visit(Object container, ModelVisitor visitor) {
        visit(container, visitor, "<root>");
    }

    public static void visit(Object container, ModelVisitor visitor, String path) {
        doVisit(container, visitor, new ArrayList<>(), path, null, null);
    }

    private static void doVisit(Object element, ModelVisitor visitor, List<Object> alreadyVisited, String path, Object container, String nameOfFieldInContainer) {
        if (element == null) return;
        if (element instanceof Collection)
            doVisitCollection((Collection<?>) element, visitor, alreadyVisited, path, container, nameOfFieldInContainer);
        else if (element instanceof Map)
            doVisitMap((Map<?, ?>) element, visitor, alreadyVisited, path, container, nameOfFieldInContainer);
        else
            doVisitObject(element, visitor, alreadyVisited, path, container, nameOfFieldInContainer);
    }

    private static void doVisitObject(Object element, ModelVisitor visitor, List<Object> alreadyVisited, String path, Object container, String nameOfFieldInContainer) {
        if (!isDslObject(element)) return;
        if (alreadyVisited.stream().anyMatch(v -> v == element)) return;
        try {
            visitor.visit(path, element, container, nameOfFieldInContainer);
        } catch (KlumVisitorException e) {
            throw e;
        } catch (Exception e) {
            throw new KlumVisitorException("Error visiting " + path, element, e);
        }
        alreadyVisited.add(element);
        ClusterModel.getFieldPropertiesStream(element)
                .forEach(property -> doVisit(property.getValue(), visitor, alreadyVisited, path + "." + property.getName(), element, property.getName()));
    }

    private static void doVisitMap(Map<?, ?> map, ModelVisitor visitor, List<Object> alreadyVisited, String path, Object container, String nameOfFieldInContainer) {
        map.forEach((key, value) -> doVisit(value, visitor, alreadyVisited,path + "." + toGPath(key), container, nameOfFieldInContainer));
    }

    private static void doVisitCollection(Collection<?> collection, ModelVisitor visitor, List<Object> alreadyVisited, String path, Object container, String nameOfFieldInContainer) {
        AtomicInteger index = new AtomicInteger();
        collection.forEach(member -> doVisit(member, visitor, alreadyVisited, path + "[" + index.getAndIncrement() + "]", container, nameOfFieldInContainer));
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
        //noinspection unchecked
        return ClusterModel.getPropertiesStream(container, Map.class)
                .filter(it -> ClusterModel.isMapOf(container, it, child.getClass()))
                .map(it -> new Tuple2<Object, Optional<String>>(it.getName(), findKeyForValue((Map<String, Object>) it.getValue(), child)))
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
    public static Optional<String> getPathOfSingleField(Object container, @NotNull Object child) {
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

    static <K, V> Optional<K> findKeyForValue(Map<K, V> map, @NotNull V value) {
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
     * different owners or having only an owner method are not handled.
     *
     * @param leaf The object whose path is to be determined
     * @return The full path of the object.
     */
    public static @NotNull String getFullPath(@NotNull Object leaf) {
        return getFullPath(leaf, null);
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
        return createPath(leaf, null, rootPath);
    }

    static String createPath(Object child, Predicate<Object> stopCondition, String rootPath) {
        final List<Object> ownerHierarchy = getOwnerHierarchy(child);

        List<Object> truncatedList;
        if (stopCondition != null) {
            int indexOfAncestor = IntStream.range(0, ownerHierarchy.size())
                    .filter(i -> stopCondition.test(ownerHierarchy.get(i)))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Could not find matching ancestor"));
            truncatedList = ownerHierarchy.subList(0, indexOfAncestor + 1);
        } else {
            truncatedList = ownerHierarchy;
        }

        Deque<String> elements = hierarchyToPath(truncatedList);

        if (rootPath != null)
            elements.addFirst(rootPath);

        return String.join(".", elements);
    }

    /**
     * Returns the path from the container to the child object, but searching upwards through the owner objects
     * of each layer until the container is found. If the root is reached without finding the container, or if the
     * ancestor structure is invalid, an IllegalArgumentException is thrown.
     * @param container The container object to search
     * @param child The target of the path
     * @return The path from the container to the child object
     */
    public static String getRelativePath(Object container, Object child) {
        return getRelativePath(container, child, null);
    }

    /**
     * Returns the path from the container to the child object, but searching upwards through the owner objects
     * of each layer until the container is found. If the root is reached without finding the container, or if the
     * ancestor structure is invalid, an IllegalArgumentException is thrown.
     * @param container The container object to search
     * @param child The target of the path
     * @param rootPath on optional root element to prepend to the path
     * @return The path from the container to the child object
     */
    public static String getRelativePath(Object container, Object child, String rootPath) {
        return createPath(child, container::equals, rootPath);
    }

    /**
     * Returns the path from the container to the child object, but searching upwards through the owner objects
     * of each layer until an ancestor of the given type is found. If the root is reached without finding the container, or if the
     * ancestor structure is invalid, an IllegalArgumentException is thrown.
     * @param containerType The type of ancestor to be searched for
     * @param child The target of the path
     * @return The path from the container to the child object
     */
    public static String getRelativePath(Class<?> containerType, Object child) {
        return getRelativePath(containerType, child, null);
    }

    /**
     * Returns the path from the container to the child object, but searching upwards through the owner objects
     * of each layer until an ancestor of the given type is found. If the root is reached without finding the container, or if the
     * ancestor structure is invalid, an IllegalArgumentException is thrown.
     * @param containerType The type of ancestor to be searched for
     * @param child The target of the path
     * @param rootPath on optional root element to prepend to the path
     * @return The path from the container to the child object
     */
    public static String getRelativePath(Class<?> containerType, Object child, String rootPath) {
        return createPath(child, containerType::isInstance, rootPath);
    }

    /**
     * Returns the ancestor of the given type for the given child object. If the child object is not a DSL object or has
     * no ancestor of the given type, an empty optional is returned.
     * @param child The child whose ancestor is to be found
     * @param type The type of ancestor to be found
     * @return The ancestor of the given type, or an empty optional if none was found
     * @param <T> The type of ancestor to be found
     */
    public static <T> Optional<T> getAncestorOfType(Object child, Class<T> type) {
        //noinspection unchecked
        return (Optional<T>) getOwnerHierarchy(child).stream()
                .filter(type::isInstance)
                .findFirst();
    }

    public static Deque<String> hierarchyToPath(List<Object> hierarchy) {
        Deque<String> result = new ArrayDeque<>();
        for (int i = hierarchy.size() - 1;  i > 0; i--) {
            Object owner = hierarchy.get(i);
            Object child = hierarchy.get(i - 1);
            String path = getPathOfFieldContaining(owner, child).orElseThrow(() -> new IllegalStateException("Object " + owner + " does not contain " + child));
            result.add(path);
        }
        return result;
    }

    /**
     * Returns the owner hierarchy of the given leaf object, starting with the leaf object itself and ending with the root object.
     * Throws an IllegalStateException if an object in the hierarchy contains more than one owner or if the hierarchy contains a cycle.
     * @param leaf The leaf object
     * @return The owner hierarchy of the leaf object
     */
    public static List<Object> getOwnerHierarchy(Object leaf) {
        List<Object> result = new ArrayList<>();
        while (isDslObject(leaf)) {
            if (result.contains(leaf))
                throw new KlumSchemaException("Object " + leaf + " has an owner cycle");
            result.add(leaf);
            leaf = KlumInstanceProxy.getProxyFor(leaf).getSingleOwner();
        }
        return result;
     }

}
