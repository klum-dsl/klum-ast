/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform;

import com.blackbuild.groovy.configdsl.transform.ast.deprecations.FieldTypeDeprecationTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a field as transient. Transient fields are not formally part of the model, but can be used to
 * store additional, changeable state directly inside the model. Transient fields can be accessed directly,
 * without using `apply` or a mutator method. The are ignored for the purpose of hashCode or equals.
 *
 * As opposed to the `transient` keyword of Java / Groovy, `@Transient` fields **will** be part of serialization
 * by default.
 * @deprecated Use `Field(FieldType.TRANSIENT)` instead.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.SOURCE)
@GroovyASTTransformationClass(classes = FieldTypeDeprecationTransformation.class)
@Deprecated
public @interface Transient {
}
