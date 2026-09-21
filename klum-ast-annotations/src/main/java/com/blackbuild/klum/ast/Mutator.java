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

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a method as Builder-only construction behavior. Such methods change Builder state and can be called only
 * while a DSL Object is being configured by a factory, Template, or lifecycle callback. They are moved to the generated
 * Builder and are not exposed on the completed DSL Object.
 *
 * @deprecated since 4.1; use {@link Builder.Method}. Replacing this annotation changes only the spelling: receiver state,
 * visibility, generated signatures, and lifecycle timing remain unchanged. Compilation promotes this annotation to
 * {@code Builder.Method}, which is therefore the annotation retained on newly generated Builder methods.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@GroovyASTTransformationClass("com.blackbuild.klum.ast.compiler.internal.ast.converters.MutatorToBuilderMethodTransformation")
@Documented
@Deprecated(since = "4.1")
public @interface Mutator {
}
