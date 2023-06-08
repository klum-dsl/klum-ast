/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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

import groovy.lang.DelegatesTo;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Mark an annotation as provider for parameter annotations to be copied to the adder/setter methods. The target
 * annotation must be settable on methods and/or fields.</p>
 *
 * <p>If the target annotation is placed on a (virtual) field, all annotation members of that annotation
 * with target type {@link ElementType#PARAMETER} will be placed on the value parameter of the generated DSL methods.</p>
 *
 * <pre><code>
 * {@literal @}interface MyAnnotation {
 *     MyParamAnnotation value()
 * }
 * {@literal @}DSL class Foo {
 *
 *   {@literal @}MyAnnotation({@literal @}MyParamAnnotation("text"))
 *   String field
 *
 * }
 * </code></pre>
 *
 * <p>Will have the setter method generated as follows:</p>
 *
 * <pre><code>
 * String field({@literal @}MyParamAnnotation("text") String value)
 * </code></pre>
 *
 * <p>Note that any default values of target annotation will currently <b>not</b> be copied over.</p>
 *
 * <p>Since this is especially useful for Closure fields and the {@code {@literal @DelegatesTo}}/{@code {@literal @ClosureParams}} annotations,
 * for this case the annotation {@link ClosureHint} is provided containing members for both annotations.</p>
 *
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ParameterAnnotation {

    /**
     * <p>Allows a {@link DelegatesTo} and or {@link ClosureParams} annotation to be placed on the element parameter
     * for adder methods (in case of collection or map only on the single adder methods).</p>
     *
     * <pre><code>
     * {@literal @}DSL class Foo {
     *
     *   {@literal @}ParameterAnnotation.ClosureHint(delegatesTo=@DelegatesTo(Foo))
     *   String field
     * }
     * </code></pre>
     * Will have the setter method generated as follows:
     *
     * <pre><code>
     * String field(@DelegatesTo(Foo) String value)
     * </code></pre>
     */
    @ParameterAnnotation
    @Target({ElementType.FIELD, ElementType.METHOD})
    @Retention(RetentionPolicy.CLASS)
    @interface ClosureHint {
        DelegatesTo delegate() default @DelegatesTo;
        ClosureParams params() default @ClosureParams(SimpleType.class);
    }
}
