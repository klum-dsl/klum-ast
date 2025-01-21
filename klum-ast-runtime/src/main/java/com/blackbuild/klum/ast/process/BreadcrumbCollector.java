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
package com.blackbuild.klum.ast.process;

import groovy.lang.Closure;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class BreadcrumbCollector {

    // Thread singleton
    private static final ThreadLocal<BreadcrumbCollector> instance = new ThreadLocal<>();
    private final Deque<Breadcrumb> breadcrumbs = new ArrayDeque<>();

    private String currentVerb;
    private String currentType;
    private String currentQualifier;
    
    private BreadcrumbCollector() {
    }

    public static <T> T withBreadcrumb(Supplier<T> action) {
        BreadcrumbCollector collector = BreadcrumbCollector.getInstance();
        try {
            collector.enter();
            return action.get();
        } finally {
            collector.leave();
        }
    }

    public static <T> T withBreadcrumb(String type, String qualifier, Supplier<T> action) {
        BreadcrumbCollector collector = BreadcrumbCollector.getInstance();
        try {
            collector.setType(type).setQualifier(qualifier).enter();
            return action.get();
        } finally {
            collector.leave();
        }
    }

    public static <T> T withBreadcrumb(Closure<T> action) {
        BreadcrumbCollector collector = BreadcrumbCollector.getInstance();
        try {
            collector.enter();
            return action.call();
        } finally {
            collector.leave();
        }
    }

    public void enter() {
        if (breadcrumbs.isEmpty())
            breadcrumbs.push(new Breadcrumb(currentVerb, currentType, currentQualifier));
        else
            breadcrumbs.push(breadcrumbs.peek().createChildCrumb(currentVerb, currentType, currentQualifier));
        clearCurrentCrumb();
    }

    private void clearCurrentCrumb() {
        currentVerb = null;
        currentType = null;
        currentQualifier = null;
    }

    @NotNull
    public static BreadcrumbCollector getInstance() {
        if (instance.get() == null)
            instance.set(new BreadcrumbCollector());
        return instance.get();
    }

    public void leave() {
        breadcrumbs.pop();
        if (breadcrumbs.isEmpty())
            remove();
    }

    void remove() {
        instance.remove();
    }

    public BreadcrumbCollector setVerb(String verb) {
        if (currentVerb == null)
            currentVerb = verb;
        return this;
    }

    public BreadcrumbCollector setType(String type) {
        if (currentType == null)
            currentType = type;
        return this;
    }

    public BreadcrumbCollector setQualifier(String qualifier) {
        if (currentQualifier == null)
            currentQualifier = qualifier;
        return this;
    }

    // join the paths from tail to head, because the breadcrumbs are stacked in reverse order.
    public String getFullPath() {
        StringBuilder sb = new StringBuilder();
        breadcrumbs.descendingIterator().forEachRemaining(b -> {
            sb.append("/");
            sb.append(b.getPath());
        });
        return sb.toString();
    }

    public static class Breadcrumb {
        private final Map<String, AtomicInteger> children = new HashMap<>();
        private String path;

        Breadcrumb(String verb, String type, String qualifier) {
            this.path = createPath(verb, type, qualifier);
        }

        private static @NotNull String createPath(String verb, String type, String qualifier) {
            StringBuilder builder = new StringBuilder();
            if (verb != null) builder.append(verb);
            if (type != null) builder.append(":").append(type);
            if (qualifier != null) builder.append("(").append(qualifier).append(")");
            return builder.toString();
        }

        Breadcrumb(String path, int quantifier) {
            if (quantifier > 1)
                this.path = path + "(" + quantifier + ")";
            else
                this.path = path;
        }

        void changePath(String path) {
            this.path = path;
        }

        Breadcrumb createChildCrumb(String verb, String type, String qualifier) {
            String basePath = createPath(verb, type, qualifier);
            int count = children.computeIfAbsent(basePath, p -> new AtomicInteger()).incrementAndGet();
            return new Breadcrumb(basePath, count);
        }

        public String getPath() {
            return path;
        }

        @Override
        public String toString() {
            return path;
        }
    }
}