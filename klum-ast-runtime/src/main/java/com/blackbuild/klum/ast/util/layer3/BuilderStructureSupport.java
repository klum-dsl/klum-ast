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

import com.blackbuild.klum.ast.util.KlumBuilder;
import com.blackbuild.klum.ast.util.KlumSchemaException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Internal composition support for mutable Builders before materialization. */
public final class BuilderStructureSupport {

    private BuilderStructureSupport() {
        // static only
    }

    public static void visit(KlumBuilder<?> root, ModelVisitor visitor) {
        visit(root, visitor, "<root>");
    }

    public static void visit(KlumBuilder<?> root, ModelVisitor visitor, String rootPath) {
        CompositionTraversal.visit(root, visitor, rootPath);
    }

    public static List<Object> getOwnerHierarchy(KlumBuilder<?> leaf) {
        List<Object> result = new ArrayList<>();
        Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Object current = leaf;
        while (current instanceof KlumBuilder<?>) {
            if (!seen.add(current))
                throw new KlumSchemaException("Object " + current + " has an owner cycle");
            result.add(current);
            current = ((KlumBuilder<?>) current).getSingleOwner();
        }
        return List.copyOf(result);
    }

    public static <T> Optional<T> getAncestorOfType(KlumBuilder<?> child, Class<T> type) {
        return getOwnerHierarchy(child).stream().filter(type::isInstance).map(type::cast).findFirst();
    }
}
