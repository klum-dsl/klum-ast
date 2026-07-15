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
package com.blackbuild.klum.ast.util;

import groovy.lang.Closure;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Immutable serialized recipe actions retained only by a Template companion. */
final class TemplateRecipeState implements Serializable {

    private final Map<Integer, List<Closure<?>>> actions;

    private TemplateRecipeState(Map<Integer, List<Closure<?>>> actions) {
        Map<Integer, List<Closure<?>>> copy = new TreeMap<>();
        actions.forEach((phase, closures) -> copy.put(phase, List.copyOf(closures)));
        this.actions = Map.copyOf(copy);
    }

    static TemplateRecipeState capture(Map<Integer, List<Closure<?>>> actions) {
        return new TemplateRecipeState(KlumBuilder.dehydrateApplyLaterClosures(actions));
    }

    void replayInto(KlumBuilder<?> recipient) {
        actions.forEach((phase, closures) -> closures.forEach(closure ->
                recipient.scheduleApplyLater(phase, (Closure<?>) closure.clone())));
    }
}
