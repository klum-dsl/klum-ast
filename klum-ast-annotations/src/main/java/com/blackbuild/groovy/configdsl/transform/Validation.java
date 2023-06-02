/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Configures the default validation for a given model class. If present and option() is VALIDATE_UNMARKED,
 * all fields as considered as having an {@literal @Validate} annotation.</p>
 *
 * <p>the same effect can be reached by simply annotating the class with {@link Validate} directly.</p>
 *
 * <p>Historically, this annotation could be used to further configure the validation process, but this
 * has become obsolete with the introduction of model phases.</p>
 *
 * @see Validate
 * @deprecated Use {@link Validate} on the class instead.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Deprecated
@GroovyASTTransformationClass("com.blackbuild.groovy.configdsl.transform.ast.converters.ValidationToValidateTransformation")
public @interface Validation {

    /**
     * Configures which fields are validated. When set to default {@link Option#IGNORE_UNMARKED}, only fields
     * explitcly marked with {@link Validate} are validated, when set to {@link Option#VALIDATE_UNMARKED}, all fields
     * not explicitly ignored (by {@code Validate(IGNORE)} are validated.
     */
    Option option() default Option.IGNORE_UNMARKED;

    /**
     * Not used anymore.
     */
    @Deprecated
    Mode mode() default Mode.AUTOMATIC;

    /**
     * Validation mode, either automatically (call validation after apply), or manually.
     */
    @Deprecated
    enum Mode {
        /**
         * Valdation is performed automatically after the apply method has been executed.
         */
        AUTOMATIC,

        /**
         * Validation is not called automatically.
         */
        MANUAL
    }

    /**
     * Controls how to handle fields not marked with {@link Validate}
     */
    enum Option {
        /**
         * Ignore all fields not explicitly marked as validating.
         */
        IGNORE_UNMARKED,

        /**
         * Validate all unmarked (and not ignored) fields against Groovy truth.
         */
        VALIDATE_UNMARKED
    }
}