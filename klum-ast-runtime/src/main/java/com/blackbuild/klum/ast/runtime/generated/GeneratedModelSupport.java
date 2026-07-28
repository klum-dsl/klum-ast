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
package com.blackbuild.klum.ast.runtime.generated;

import com.blackbuild.klum.ast.runtime.KlumModelException;
import groovy.lang.GroovyObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

/**
 * Generated-code linkage for model Materialization and opaque completed-model state.
 *
 * <p>The reserved hooks are emitted only by the DSL transformation. They preserve the internal
 * construction session, allocation, and validation ordering without exposing companion or Builder
 * mutation operations to clients.</p>
 */
@SuppressWarnings({"unchecked", "java:S100"}) // reserved generated-code ABI hooks
public final class GeneratedModelSupport {

    private static final GeneratedMaterializationToken MATERIALIZATION_TOKEN = new GeneratedMaterializationToken();

    private GeneratedModelSupport() {
    }

    /** Verifies the opaque authority supplied to a generated model constructor. */
    public static void $klum$requireMaterializationToken(GeneratedMaterializationToken token) {
        if (token != MATERIALIZATION_TOKEN)
            throw new KlumModelException("DSL Objects can only be constructed by internal materialization");
    }

    /** Snapshots one non-relationship field while a generated model is materialized. */
    public static Object $klum$snapshotField(GeneratedKlumBuilder<?> builder, String fieldName) {
        return builder.$snapshotField(fieldName);
    }

    /** Creates the opaque state retained by a generated root model. */
    public static GeneratedObjectState $klum$createState(GeneratedKlumBuilder<?> builder, GroovyObject model) {
        return builder.$createCompanion(model);
    }

    /** Allocates a generated model through its synthetic Materialization constructor. */
    public static <M> M $klum$instantiate(GeneratedKlumBuilder<?> builder, Class<? extends M> implementationType) {
        Constructor<?> constructor = Arrays.stream(implementationType.getDeclaredConstructors())
                .filter(candidate -> candidate.getParameterCount() == 2)
                .filter(candidate -> candidate.getParameterTypes()[0].isInstance(builder))
                .filter(candidate -> candidate.getParameterTypes()[1] == GeneratedMaterializationToken.class)
                .findFirst()
                .orElseThrow(() -> new KlumModelException("No internal Builder constructor found for " + implementationType.getName()));
        try {
            if (!constructor.trySetAccessible())
                throw new KlumModelException("Cannot access internal Builder constructor for " + implementationType.getName());
            return (M) constructor.newInstance(builder, MATERIALIZATION_TOKEN);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtimeException)
                throw runtimeException;
            throw new KlumModelException("Could not instantiate internal model implementation " + implementationType.getName(), exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new KlumModelException("Could not instantiate internal model implementation " + implementationType.getName(), exception);
        }
    }
}
