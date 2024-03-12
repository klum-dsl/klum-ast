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

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class BreadcrumbCollector {

    // Thread singleton
    private static final ThreadLocal<BreadcrumbCollector> instance = new ThreadLocal<>();

    @NotNull
    public static BreadcrumbCollector getInstance() {
        if (instance.get() == null)
            instance.set(new BreadcrumbCollector());
        return instance.get();
    }

    private final Deque<Breadcrumb> breadcrumbs = new ArrayDeque<>();

    private BreadcrumbCollector() {
    }

    public void enter(String breadcrumb, String classifier) {
       if (classifier != null)
           enter(breadcrumb + "(" + classifier + ")");
       else
        enter(breadcrumb);
    }

    public void enter(String breadcrumb) {
        if (breadcrumbs.isEmpty())
            breadcrumbs.push(new Breadcrumb(breadcrumb));
        else
            breadcrumbs.push(breadcrumbs.peek().createChildCrumb(breadcrumb));
    }

    public void replace(String breadcrumb) {
        Objects.requireNonNull(breadcrumbs.peek()).changePath(breadcrumb);
    }

    public void qualify(String qualifier) {
        if (qualifier == null) return;
        Breadcrumb head = Objects.requireNonNull(breadcrumbs.peek());
        head.changePath(head.getPath() + "(" + qualifier + ")");
    }

    public void leave() {
        breadcrumbs.pop();
        if (breadcrumbs.isEmpty())
            instance.remove();
    }

    public String getLastPathElement() {
        return breadcrumbs.peek() != null ? breadcrumbs.peek().getPath() : null;
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


    static class Breadcrumb {
        private String path;
        private final Map<String, AtomicInteger> children = new HashMap<>();

        Breadcrumb(String path) {
            this.path = path;
        }

        void changePath(String path) {
            this.path = path;
        }

        Breadcrumb createChildCrumb(String path) {
            int count = children.computeIfAbsent(path, p -> new AtomicInteger()).incrementAndGet();
            String effectivePath = count > 1 ? path + "(" + count + ")" : path;
            return new Breadcrumb(effectivePath);
        }

        public String getPath() {
            return path;
        }
    }
}