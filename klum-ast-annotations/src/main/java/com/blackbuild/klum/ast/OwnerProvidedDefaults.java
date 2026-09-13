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
package com.blackbuild.klum.ast;

import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.KlumCastValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a JavaBean contract whose properties receive conservative default values from a compatible owner.
 *
 * <p>The annotated DSL Object and exactly one of its declared {@link Owner} fields must implement the contract.
 * Only properties exposed by JavaBean getters on the contract participate. Multiple declarations are applied as one
 * deduplicated set of contract properties.</p>
 *
 * <p>This annotation adds no generated Builder or completed-model API.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(OwnerProvidedDefaults.Container.class)
@KlumCastValidated
@KlumCastValidator("com.blackbuild.klum.ast.compiler.internal.validation.OwnerProvidedDefaultsCheck")
@Documented
public @interface OwnerProvidedDefaults {

    /** The shared JavaBean contract provided by the owner and consumed by the annotated DSL Object. */
    Class<?> value();

    /** Runtime-retained container for repeated owner-provided-default contracts. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @KlumCastValidator("com.blackbuild.klum.ast.compiler.internal.validation.OwnerProvidedDefaultsCheck")
    @Documented
    @interface Container {
        OwnerProvidedDefaults[] value();
    }
}
