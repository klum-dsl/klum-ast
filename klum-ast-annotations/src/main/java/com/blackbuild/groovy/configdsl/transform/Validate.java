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
package com.blackbuild.groovy.configdsl.transform;

import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.KlumCastValidator;
import com.blackbuild.klum.cast.checks.NotOn;
import com.blackbuild.klum.cast.checks.NumberOfParameters;
import groovy.lang.Closure;

import java.lang.annotation.*;

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
 * clazz.Create.With {
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
 * <p>Using the {@link #level()} member, the validation can be designated as information/warning only.</p>
 *
 * <p>Validation is usually executed as part of the ValidationPhase, which performs validations on all objects in the
 * hierarchy. Validation results of all objects are collected, and if an Error level problem is found, a KlumValidationException is thrown.</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@NumberOfParameters(0)
@KlumCastValidator("com.blackbuild.klum.ast.validation.CheckForPrimitiveBoolean")
@Documented
public @interface Validate {

    /**
     * A closure to be executed to validate the annotated field. If empty, Groovy Truth is used to validate the field.
     * Illegal when annotating a method.
     */
    @NotOn({ElementType.METHOD, ElementType.TYPE})
    Class<? extends Closure> value() default GroovyTruth.class;

    /**
     * A message to be returned when validation fails.
     * Illegal when annotating a method.
     */
    @NotOn({ElementType.METHOD, ElementType.TYPE})
    String message() default "";

    /**
     * Defines the severity of the validation problem. Default is {@link Level#ERROR}. This can be used to create
     * Warning or Info messages instead of errors.
     */
    @NotOn(ElementType.TYPE)
    Level level() default Level.ERROR;

    /**
     * Defines the severity of the validation problem.
     */
    enum Level {
        /** No validation problem, used as a default value */
        NONE,
        /** Informational message, used to indicate that something is not wrong, but might be worth noting. */
        INFO,
        /** Warning message, used to indicate that something is not wrong, but might lead to problems later on. */
        WARNING,
        /** Indicates that the validation problem is a deprecation warning, meaning that the validation target is still valid but should not be used anymore. */
        DEPRECATION,
        /** Indicates that the validation problem is an error, meaning that the validation target is not valid. */
        ERROR;

        public Level combine(Level other) {
            if (other == null) return this;
            return this.worseThen(other) ? this : other;
        }

        public boolean worseThen(Level other) {
            return this.ordinal() > other.ordinal();
        }

        public boolean equalOrWorseThen(Level level) {
            return this.ordinal() >= level.ordinal();
        }

        public static Level fromString(String level) {
            if (level == null || level.isEmpty()) return NONE;

            try {
                return Level.values()[Integer.parseInt(level)];
            } catch (NumberFormatException e) {
                // ignore, is no number
            }

            return Level.valueOf(level.toUpperCase());
        }
    }

    /**
     * Default value for {@link Validate#value()}. Designates the field to be validated against Groovy Truth.
     */
    class GroovyTruth extends NamedAnnotationMemberClosure<Object> {
        public GroovyTruth(Object owner, Object thisObject) {
            super(owner, thisObject);
        }
    }

    /**
     * If used as value for {@link Validate#value()}, configures validation to ignore this field. Makes only sense
     * when Validate is also placed on type.
     */
    class Ignore extends NamedAnnotationMemberClosure<Object> {
        public Ignore(Object owner, Object thisObject) {
            super(owner, thisObject);
        }
    }

    class DefaultImpl implements Validate {

        public static final Validate INSTANCE = new DefaultImpl();

        @Override
        public Class<? extends Closure> value() {
            return GroovyTruth.class;
        }

        @Override
        public String message() {
            return "";
        }

        @Override
        public Level level() {
            return Level.ERROR;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Validate.class;
        }
    }

}
