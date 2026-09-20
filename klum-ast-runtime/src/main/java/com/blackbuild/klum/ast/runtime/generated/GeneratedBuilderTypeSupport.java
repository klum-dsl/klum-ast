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
import com.blackbuild.klum.ast.runtime.KlumModelObject;
import com.blackbuild.klum.ast.runtime.internal.InternalKlumBuilder;

/**
 * Generated-runtime linkage for factory-token Model/Builder identity checks.
 *
 * <p>This is not a handwritten client inspection API. Public generated Factory contracts expose only the typed
 * predicates and cast on {@code KlumFactory.BuilderFactoryProvider}; Builder implementation state remains internal.</p>
 */
@SuppressWarnings({"unchecked", "java:S100"}) // exact public Builder cast and reserved generated-code ABI hooks
public final class GeneratedBuilderTypeSupport {

    private GeneratedBuilderTypeSupport() {
    }

    /** Tests ordinary Model assignability in both completed and Builder states. */
    public static boolean $klum$isModelOrBuilder(Class<?> selectedModelType, Object value) {
        return selectedModelType.isInstance(value) || $klum$isBuilder(selectedModelType, value);
    }

    /** Tests Builder declared-Model assignability without exposing the Builder implementation. */
    public static boolean $klum$isBuilder(Class<?> selectedModelType, Object value) {
        return value instanceof InternalKlumBuilder<?> builder
                && selectedModelType.isAssignableFrom(builder.getModelType());
    }

    /** Returns the matching Builder unchanged or reports why the value cannot be narrowed. */
    public static <B> B $klum$asBuilder(Class<?> selectedModelType, Object value) {
        if ($klum$isBuilder(selectedModelType, value))
            return (B) value;

        throw cannotNarrow(selectedModelType, value);
    }

    private static KlumModelException cannotNarrow(Class<?> selectedModelType, Object value) {
        String selectedName = selectedModelType.getName();
        if (value == null)
            return new KlumModelException("Cannot narrow null to the Builder for " + selectedName);

        if (value instanceof InternalKlumBuilder<?> builder)
            return new KlumModelException("Cannot narrow the Builder for " + builder.getModelType().getName()
                    + " to the Builder for " + selectedName + ": the declared Model types are not assignable");

        if (value instanceof KlumModelObject)
            return new KlumModelException("Cannot narrow completed Model " + value.getClass().getName()
                    + " to the Builder for " + selectedName + ": asBuilder accepts only an existing Builder");

        return new KlumModelException("Cannot narrow " + value.getClass().getName() + " to the Builder for "
                + selectedName + ": the value is neither a DSL Object nor a Builder");
    }
}
