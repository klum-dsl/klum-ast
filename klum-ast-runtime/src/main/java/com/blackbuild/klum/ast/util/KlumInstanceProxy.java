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

import com.blackbuild.klum.ast.KlumModelObject;
import groovy.lang.GroovyObject;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builder-only compatibility adapter for code written against the former RW proxy.
 *
 * <p>New runtime code belongs on {@link KlumBuilder} or {@link KlumModelProxy}.
 * Looking up this adapter for a completed model is intentionally rejected.</p>
 *
 * @deprecated since 4.0; use {@link KlumBuilder} for construction state and
 * {@link KlumModelProxy} for completed-model companion state
 */
@Deprecated(since = "4.0", forRemoval = true)
@SuppressWarnings({"unused", "java:S1133", "java:S1452"}) // compatibility adapter until its documented 4.x removal
public final class KlumInstanceProxy {

    /** Legacy generated-layout constants retained while #394 is unresolved. */
    public static final String NAME_OF_RW_FIELD_IN_MODEL_CLASS = "$rw";
    public static final String NAME_OF_PROXY_FIELD_IN_MODEL_CLASS = "$proxy";
    public static final String NAME_OF_MODEL_FIELD_IN_RW_CLASS = "this$0";

    private final KlumBuilder<?> builder;

    public KlumInstanceProxy(GroovyObject target) {
        if (!(target instanceof KlumBuilder))
            throw completedModelLookupFailure(target);
        this.builder = (KlumBuilder<?>) target;
    }

    private KlumInstanceProxy(KlumBuilder<?> builder) {
        this.builder = builder;
    }

    KlumBuilder<?> getBuilder() {
        return builder;
    }

    public static KlumInstanceProxy getProxyFor(Object target) {
        if (target instanceof KlumInstanceProxy)
            return (KlumInstanceProxy) target;
        if (target instanceof KlumBuilder)
            return new KlumInstanceProxy((KlumBuilder<?>) target);
        if (target instanceof KlumModelObject)
            throw completedModelLookupFailure(target);
        throw new KlumException("Object of type " + target.getClass().getName() + " is neither a Builder nor a DSL Object");
    }

    private static KlumException completedModelLookupFailure(Object target) {
        return new KlumException("KlumInstanceProxy is Builder-only; completed DSL Object "
                + target.getClass().getName()
                + " must use KlumModelProxy for model metadata or a generated factory for construction");
    }

    protected GroovyObject getRwInstance() {
        return builder;
    }

    public Object getDSLInstance() {
        return builder;
    }

    /** Read-only compatibility for code that inspected the active Builder template context. */
    public Map<Class<?>, Object> getCurrentTemplates() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(builder.getCurrentTemplates()));
    }
}
