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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Activates validation for the given field or marks the annotated method as validation method.</p>
 *
 * <h2>On a class</h2>
 * <p>If set on a class, the class behaves as if the annotation was set on all fields of the class not already annotated.
 * In this usage, fields can be exempted by annotating them with Validate(Ignore).</p>
 *
 * <h2>On a field</h2>
 * 
 * <p>If this annotation is set on a field, this field is validated as part of the object validation, During the
 * validation phase. The actual validation can be one of the following:</p>
 * 
 * <table border='1'>
 *     <caption>Valid options to use Validate annotation on a field</caption>
 *     <tr><td>empty</td><td>Validates the content of the field according to groovy truth</td></tr>
 *     <tr><td>empty (for Boolean fields)</td><td>Validates that the content of the field is not null</td></tr>
 *     <tr><td>{@link Validate.Ignore}</td><td>Don't validate this field. This only makes sense if the class itself is
 *     annotated with Validate to exclude the annotated field from validation.</td></tr>
 *     <tr><td>a closure</td><td>The given closure is evaluated called with the field value as parameter. If the result
 *     of the call satisfies Groovy Truth, the field is assumed valid.</td></tr>
 * </table>
 *
 * <p><b>It is illegal to place the annotation on a primitive boolean field.</b></p>
 *
 * <pre><code>
 * {@literal @DSL}
 * class Foo {
 *   {@literal @Validate}
 *   String notEmpty
 *   {@literal @Validate}({ {@literal it.length > 3} })
 *   String minLength
 * }
 * </code></pre>
 *
 * <p>{@link #message()} can be used to provide a custom message for failed validations.</p>
 *
 * <h2>On a method</h2>
 * 
 * <p>On a method, this annotation is used to designate a validation method which is called as part of the validation process.
 * If the method returns without throwing an exception / an {@link AssertionError}, the method is considered to be passed.</p>
 * 
 * <p>Validation methods are commonly used for interdependent fields (field a must have a value matching field b) or
 * to perform a validation that would be to long to comfortably include in a closure</p>
 *
 * <pre><code>
 * given:
 * {@literal @DSL}
 * class Foo {
 *   String value1
 *   String value2
 *
 *   {@literal @Validate}
 *   private def stringLength() {
 *     {@literal assert value1.length() < value2.length()}
 *   }
 * }
 *
 * when:
 * clazz.create {
 *   value1 "abc"
 *   value2 "bl"
 * }
 *
 * then:
 * thrown(IllegalStateException)
 * </code></pre>
 * <p>Validation methods should not change the state of an object, use {@link PostApply} or {@link PostCreate} for that.</p>
 * <p>When using validation on a method or a class, neither a {@link #message()} nor a {@link #value()} must be given.</p>
 *
 * <h1>Order of validation</h1>
 * When validating an object, the following order is executed.
 * <ul>
 *     <li>Validation of the superclass, if the superclass is also a model class</li>
 *     <li>validation of all fields</li>
 *     <li>custom validation methods</li>
 * </ul>
 *
 * <p>if the validation fails for any validation field or method, an {@link IllegalStateException} is thrown.</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Validate {

    /**
     * A closure to be executed to validate the annotated field. If empty, Groovy Truth is used to validate the field.
     * Illegal when annotating a method.
     */
    Class<?> value() default GroovyTruth.class;

    /**
     * A message to be returned when validation fails.
     * Illegal when annotating a method.
     */
    String message() default "";

    /**
     * Default value for {@link Validate#value()}. Designates the field to be validated against Groovy Truth.
     */
    interface GroovyTruth {}

    /**
     * If used as value for {@link Validate#value()}, configures validation to ignore this field. Makes only sense
     * in combination with {@link Validation.Option#VALIDATE_UNMARKED}.
     */
    interface Ignore {}
}
