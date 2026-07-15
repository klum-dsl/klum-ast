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

import groovy.lang.PropertyValue;
import groovy.lang.Tuple2;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.codehaus.groovy.tools.Utilities;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Internal GPath-compatible structural path operations shared by state-specific structure modules. */
public final class StructuralPath {

    private StructuralPath() {
        // static only
    }

    public static String toDefaultFieldName(Class<?> type) {
        return StringGroovyMethods.uncapitalize(type.getSimpleName());
    }

    public static String toGPath(Object value) {
        String text = value.toString();
        return Utilities.isJavaIdentifier(text) ? text : InvokerHelper.inspect(text);
    }

    public static Optional<String> getPathOfFieldContaining(Object container, @NotNull Object child) {
        Optional<String> singleValuePath = getPathOfSingleField(container, child);
        if (singleValuePath.isPresent())
            return singleValuePath;

        Optional<String> collectionPath = getPathOfCollectionMember(container, child);
        if (collectionPath.isPresent())
            return collectionPath;

        return getPathOfMapMember(container, child);
    }

    public static Optional<String> getPathOfMapMember(Object container, @NotNull Object child) {
        //noinspection unchecked
        return ClusterModel.getPropertiesStream(container, Map.class)
                .filter(it -> ClusterModel.isMapOf(container, it, child.getClass()))
                .map(it -> new Tuple2<Object, Optional<String>>(it.getName(), findKeyForValue((Map<String, Object>) it.getValue(), child)))
                .filter(it -> it.getSecond().isPresent())
                .map(it -> toGPath(it.getFirst()) + "." + toGPath(it.getSecond().get()))
                .findFirst();
    }

    public static Optional<String> getPathOfCollectionMember(Object container, @NotNull Object child) {
        return ClusterModel.getPropertiesStream(container, Collection.class)
                .filter(it -> ClusterModel.isCollectionOf(container, it, child.getClass()))
                .map(it -> new Tuple2<>(it.getName(), getIndexInCollection((Collection<?>) it.getValue(), child)))
                .filter(it -> it.getSecond() != -1)
                .map(it -> toGPath(it.getFirst()) + "[" + it.getSecond() + "]")
                .findFirst();
    }

    public static Optional<String> getPathOfSingleField(Object container, @NotNull Object child) {
        return ClusterModel.getPropertiesStream(container, Object.class)
                .filter(it -> it.getValue() == child)
                .map(PropertyValue::getName)
                .map(StructuralPath::toGPath)
                .findFirst();
    }

    public static Deque<String> hierarchyToPath(List<Object> hierarchy) {
        Deque<String> result = new ArrayDeque<>();
        for (int i = hierarchy.size() - 1; i > 0; i--) {
            Object owner = hierarchy.get(i);
            Object child = hierarchy.get(i - 1);
            String path = getPathOfFieldContaining(owner, child)
                    .orElseThrow(() -> new IllegalStateException("Object " + owner + " does not contain " + child));
            result.add(path);
        }
        return result;
    }

    static int getIndexInCollection(Collection<?> container, Object child) {
        if (container instanceof List<?> list)
            return list.indexOf(child);

        int index = 0;
        for (Object element : container) {
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
}
