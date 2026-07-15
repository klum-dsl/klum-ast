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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.klum.ast.util.KlumException;
import com.blackbuild.klum.ast.util.KlumBuilder;
import com.blackbuild.klum.ast.util.KlumObjectSupport;
import com.blackbuild.klum.ast.util.KlumModelProxy;
import com.blackbuild.klum.ast.util.KlumSchemaException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static com.blackbuild.klum.ast.util.DslHelper.isDslObject;

/**
 * Legacy static compatibility adapter for structure traversal and paths.
 *
 * @deprecated since 4.0; use {@code KlumObjectSupport.of(completedObject).getStructure()} for completed DSL Objects.
 */
@Deprecated(since = "4.0", forRemoval = false)
@SuppressWarnings("java:S1133") // retained as a source-compatible adapter during the completed-object migration
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
        return StructuralPath.toDefaultFieldName(type);
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
     * Iterates through a data structure and invokes the given visitor for each element.
     * The default implementation of the
     * ModelVisitor only visits DSL objects, but this can be adjusted by overriding the shouldVisit method.
     * <p>
     *     The visitation only goes downwards, ignoring {@link com.blackbuild.groovy.configdsl.transform.Owner} and {@link com.blackbuild.groovy.configdsl.transform.FieldType#LINK}
     *     fields, and ignores object cycles.
     * </p>
     *
     * @param root The root object to start the visitation from
     * @param visitor The visitor to invoke for each element
     */
    public static void visit(Object root, ModelVisitor visitor) {
        if (isCompletedDslObject(root)) {
            KlumObjectSupport.of(root).getStructure().visit(visitor);
            return;
        }
        if (root instanceof KlumBuilder<?> builder) {
            BuilderStructureSupport.visit(builder, visitor);
            return;
        }
        CompositionTraversal.visit(root, visitor, "<root>");
    }

    /**
     * Explicit construction-state traversal used by pre-materialization phases.
     * Sealed wrappers are filtered by {@code BuilderVisitingPhaseAction}.
     */
    public static void visitBuilders(KlumBuilder<?> root, ModelVisitor visitor) {
        BuilderStructureSupport.visit(root, visitor);
    }

    /**
     * Iterates through a data structure and invokes the given visitor for each element.
     * Behaves like {@link #visit(Object, ModelVisitor)} but allows to specify a path representation for the root element..
     * @param root The root object to start the visitation from.
     * @param visitor The visitor to invoke for each element.
     * @param path The path representation of the root element.
     */
    public static void visit(Object root, ModelVisitor visitor, String path) {
        if (isCompletedDslObject(root)) {
            KlumObjectSupport.of(root).getStructure().visit(visitor, path);
            return;
        }
        if (root instanceof KlumBuilder<?> builder) {
            BuilderStructureSupport.visit(builder, visitor, path);
            return;
        }
        CompositionTraversal.visit(root, visitor, path);
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
        if (isCompletedDslObject(container) && ignoredTypes.isEmpty())
            return KlumObjectSupport.of(container).getStructure().findAll(type, path);
        DeepFindVisitor<T> visitor = new DeepFindVisitor<>(type, ignoredTypes);
        visit(container, visitor, path);
        return visitor.result;
    }

    static String toGPath(Object value) {
        return StructuralPath.toGPath(value);
    }

    /**
     * Returns the name of the field of the container containing the given object. If the object is not
     * contained in a field, returns an empty Optional. This if the object is contained in a collection or map, a gson like
     * expression is returned ("container.map.'child-name'" or 'container.list[2]').
     * @param container The container object to search
     * @param child The child object to look for
     * @return The name of the field containing the child object, or an empty Optional if the object is not contained in a field.
     */
    public static Optional<String> getPathOfFieldContaining(Object container, @NotNull Object child) {
        return StructuralPath.getPathOfFieldContaining(container, child);
    }

    @NotNull
    static Optional<String> getPathOfMapMember(Object container, @NotNull Object child) {
        return StructuralPath.getPathOfMapMember(container, child);
    }

    @NotNull
    static Optional<String> getPathOfCollectionMember(Object container, @NotNull Object child) {
        return StructuralPath.getPathOfCollectionMember(container, child);
    }

    @NotNull
    static Optional<String> getPathOfSingleField(Object container, @NotNull Object child) {
        return StructuralPath.getPathOfSingleField(container, child);
    }

    static int getIndexInCollection(Collection<?> container, Object child) {
        return StructuralPath.getIndexInCollection(container, child);
    }

    static <K, V> Optional<K> findKeyForValue(Map<K, V> map, @NotNull V value) {
        return StructuralPath.findKeyForValue(map, value);
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
        if (isCompletedDslObject(leaf))
            return KlumObjectSupport.of(leaf).getStructure().getFullPath(rootPath);
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
        if (isCompletedDslObject(container) && isCompletedDslObject(child))
            return KlumObjectSupport.of(container).getStructure().getRelativePath(child, rootPath);
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
        if (isCompletedDslObject(child)) {
            Object container = KlumObjectSupport.of(child).getStructure().getAncestorOfType(containerType)
                    .orElseThrow(() -> new IllegalArgumentException("Could not find matching ancestor"));
            return KlumObjectSupport.of(container).getStructure().getRelativePath(child, rootPath);
        }
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
        if (isCompletedDslObject(child))
            return KlumObjectSupport.of(child).getStructure().getAncestorOfType(type);
        //noinspection unchecked
        return (Optional<T>) getOwnerHierarchy(child).stream()
                .filter(type::isInstance)
                .findFirst();
    }

    public static Deque<String> hierarchyToPath(List<Object> hierarchy) {
        return StructuralPath.hierarchyToPath(hierarchy);
    }

    /**
     * Returns the owner hierarchy of the given leaf object, starting with the leaf object itself and ending with the root object.
     * Throws an IllegalStateException if an object in the hierarchy contains more than one owner or if the hierarchy contains a cycle.
     * @param leaf The leaf object
     * @return The owner hierarchy of the leaf object
     */
    public static List<Object> getOwnerHierarchy(Object leaf) {
        if (isCompletedDslObject(leaf))
            return KlumObjectSupport.of(leaf).getStructure().getOwnerHierarchy();
        if (leaf instanceof KlumBuilder<?> builder)
            return BuilderStructureSupport.getOwnerHierarchy(builder);
        List<Object> result = new ArrayList<>();
        while (leaf instanceof KlumBuilder || isDslObject(leaf)) {
            if (result.contains(leaf))
                throw new KlumSchemaException("Object " + leaf + " has an owner cycle");
            result.add(leaf);
            if (leaf instanceof KlumBuilder)
                leaf = ((KlumBuilder<?>) leaf).getSingleOwner();
            else
                leaf = KlumModelProxy.getProxyFor(leaf).getSingleOwner();
        }
        return result;
     }

    private static boolean isCompletedDslObject(Object value) {
        if (!isDslObject(value))
            return false;
        try {
            KlumModelProxy.getProxyFor(value);
            return true;
        } catch (KlumException ignored) {
            return false;
        }
    }

    private static class DeepFindVisitor<T> implements ModelVisitor {
        private final List<Class<?>> ignoredTypes;
        private final Class<T> type;
        final Map<String, T> result = new HashMap<>();

        public DeepFindVisitor(Class<T> type, List<Class<?>> ignoredTypes) {
            this.ignoredTypes = ignoredTypes;
            this.type = type;
        }

        @Override
        public Action shouldVisit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
            if (ignoredTypes.stream().anyMatch(it -> it.isInstance(element))) return Action.SKIP;
            if (element.getClass().getPackageName().startsWith("java.")) return Action.SKIP_SUBTREE;
            return Action.HANDLE;
        }

        @Override
        public void visit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
            if (type.isInstance(element)) {
                //noinspection unchecked
                result.put(path, (T) element);
            }
        }
    }
}
