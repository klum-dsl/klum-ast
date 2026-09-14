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
package com.blackbuild.klum.ast.runtime.internal;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Internal frame restoration support for the published Template test lifetime token.
 */
public final class TemplateScopeBridge {

    private static final ThreadLocal<Deque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);

    private TemplateScopeBridge() {
    }

    /** Opens an empty frame for the current thread. */
    public static Frame open() {
        TemplateManager manager = TemplateManager.getInstance();
        Frame frame = new Frame(Thread.currentThread(), manager.getCurrentTemplates());
        FRAMES.get().addLast(frame);
        return frame;
    }

    /** Adds a defensive value snapshot to an active frame. */
    public static void add(Frame frame, Object[] templates) {
        requireActiveFrame(frame);
        Map<Class<?>, Object> additions = new LinkedHashMap<>();
        Arrays.stream(Objects.requireNonNull(templates, "templates"))
                .forEach(template -> additions.put(TemplateManager.getRealType(Objects.requireNonNull(template, "template")), template));
        frame.templates.putAll(additions);
        restoreEffectiveTemplates(FRAMES.get());
    }

    /** Closes the current thread's topmost frame. */
    public static void close(Frame frame) {
        if (frame.closed)
            return;
        requireOwner(frame);

        Deque<Frame> frames = FRAMES.get();
        if (frames.peekLast() != frame)
            throw new IllegalStateException("Template scopes must close in nesting order");

        frames.removeLast();
        frame.closed = true;
        if (frames.isEmpty()) {
            TemplateManager manager = TemplateManager.getInstance();
            manager.setTemplates(frame.precedingTemplates);
            manager.deregister();
            FRAMES.remove();
        } else {
            restoreEffectiveTemplates(frames);
        }
    }

    private static void requireActiveFrame(Frame frame) {
        if (frame.closed)
            throw new IllegalStateException("Template scope is closed");
        requireOwner(frame);
        if (!FRAMES.get().contains(frame))
            throw new IllegalStateException("Template scope is not active");
    }

    private static void requireOwner(Frame frame) {
        if (frame.owner != Thread.currentThread())
            throw new IllegalStateException("Template scope belongs to another thread");
    }

    private static void restoreEffectiveTemplates(Deque<Frame> frames) {
        Map<Class<?>, Object> effectiveTemplates = new LinkedHashMap<>(frames.peekFirst().precedingTemplates);
        frames.forEach(frame -> effectiveTemplates.putAll(frame.templates));
        TemplateManager.getInstance().setTemplates(effectiveTemplates);
    }

    /** Opaque state held by the test-support facade. */
    public static final class Frame {

        private final Thread owner;
        private final Map<Class<?>, Object> precedingTemplates;
        private final Map<Class<?>, Object> templates = new LinkedHashMap<>();
        private boolean closed;

        private Frame(Thread owner, Map<Class<?>, Object> precedingTemplates) {
            this.owner = owner;
            this.precedingTemplates = precedingTemplates;
        }
    }
}
