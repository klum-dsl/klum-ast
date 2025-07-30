/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util.layer3.annotations;

import groovy.lang.Closure;
import groovy.transform.TypeChecked;
import groovy.transform.TypeCheckingMode;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.*;

/**
 * Marks a field with a closure that is automatically applied during the default phase.
 * This uses the mechanism described in {@link DefaultValues}. Note that since this is technically a Default closure,
 * it must check itself whether a value is already set, if it should not be overridden.
 */
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@DefaultValues(valueTarget = "apply")
@Documented
@GroovyASTTransformationClass("com.blackbuild.klum.ast.util.layer3.ApplyDefaultTransformation")
public @interface DefaultApply {

    /**
     * The closure to apply. Delegate of the closure will be value of the field annotated with this annotation.
     */
    @TypeChecked(TypeCheckingMode.SKIP)
    Class<? extends Closure<Object>> value();
}