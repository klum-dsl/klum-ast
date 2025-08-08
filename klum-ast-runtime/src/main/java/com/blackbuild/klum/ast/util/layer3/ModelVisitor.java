/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Visitor for a model tree. Note that the default behavior is to only handle DSL objects, this can be changed by overriding {@link #shouldVisit(String, Object, Object, String)}.
 */
@FunctionalInterface
public interface ModelVisitor {

    /**
     * Visit the given element.
     * @param path a string (GSON-like) representation of the path from the root to this element
     * @param element the element to visit
     * @param container The object containing this element. If the element is a member of a collection or map, the object containing the collection or map.
     * @param nameOfFieldInContainer The name of the field in the container pointing to this object (or its collection/map
     */
    void visit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer);

    /**
     * Checks whether the given element should be visited. The defautlt implementation is to only handle DSL objects.
     * @param path a string (GSON-like) representation of the path from the root to this element
     * @param element the element to visit
     * @param container The object containing this element. If the element is a member of a collection or map, the object containing the collection or map.
     * @param nameOfFieldInContainer The name of the field in the container pointing to this object (or its collection/map
     * @return whether to skip, visit or skip subtree
     */
    default Action shouldVisit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
        return DslHelper.isDslObject(element) ? Action.HANDLE : Action.SKIP;
    }

    enum Action {
        /** Handle the element. */
        HANDLE,
        /** Skip the element, including subelements. */
        SKIP,
        /** Handle the element, but skip subelements. */
        SKIP_SUBTREE,
    }
}
