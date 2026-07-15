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

import com.blackbuild.klum.ast.util.DslHelper;
import com.blackbuild.klum.ast.util.KlumException;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Internal identity-cycle-safe traversal of composition fields. */
public final class CompositionTraversal {

    private CompositionTraversal() {
        // static only
    }

    public static void visit(Object root, ModelVisitor visitor, String rootPath) {
        doVisit(root, visitor, Collections.newSetFromMap(new IdentityHashMap<>()), rootPath, null, null);
    }

    static Map<String, Object> getCompositionProperties(Object container) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Class<?> type = container.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getName().contains("$") || Modifier.isStatic(field.getModifiers()) || field.isSynthetic()
                        || DslHelper.isOwner(field) || DslHelper.isLink(field))
                    continue;
                result.put(field.getName(), DslHelper.getFieldValue(container, field.getName()));
            }
        }
        return result;
    }

    private static void doVisit(Object element, ModelVisitor visitor, Set<Object> alreadyVisited, String path,
                                Object container, String nameOfFieldInContainer) {
        if (element == null)
            return;
        if (element instanceof Collection<?> collection) {
            int index = 0;
            for (Object member : collection)
                doVisit(member, visitor, alreadyVisited, path + "[" + index++ + "]", container, nameOfFieldInContainer);
            return;
        }
        if (element instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet())
                doVisit(entry.getValue(), visitor, alreadyVisited, path + "." + StructuralPath.toGPath(entry.getKey()), container, nameOfFieldInContainer);
            return;
        }

        ModelVisitor.Action action = visitor.shouldVisit(path, element, container, nameOfFieldInContainer);
        if (action == ModelVisitor.Action.SKIP || !alreadyVisited.add(element))
            return;
        try {
            visitor.visit(path, element, container, nameOfFieldInContainer);
        } catch (KlumVisitorException exception) {
            throw exception;
        } catch (KlumException exception) {
            throw new KlumVisitorException("Error visiting " + path + ": " + exception.getMessage(), element, exception);
        } catch (Exception exception) {
            throw new KlumVisitorException("Error visiting " + path, element, exception);
        }
        if (action == ModelVisitor.Action.SKIP_SUBTREE)
            return;
        getCompositionProperties(element).forEach((name, value) ->
                doVisit(value, visitor, alreadyVisited, path + "." + name, element, name));
    }
}
