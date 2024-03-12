/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.WriteAccess;
import com.blackbuild.klum.cast.KlumCastValidated;
import groovy.lang.Closure;

import java.lang.annotation.*;
import java.util.Collections;
import java.util.Map;

/**
 * Auto create the object of this field, if still null during the auto create phase.
 * The value must be a literal Map containing the values to be passed to the create method.
 * <p>
 * For keyed fields, the key must be passed as {@link #key()} parameter. Additionally, the
 * concrete type of the object to be created can be passed as {@link #type()} parameter. If
 * the annotated field is an abstract type, the type parameter is mandatory.
 * <p>
 * When placed on a field of type closure, the behaviour is slightly different. In that case, the closure
 * is called during the lifecycle phase.
 * <p>
 * AutoCreate can also be used on methods. In that case, the method is handled as a regular
 * lifecycle method.
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@WriteAccess(WriteAccess.Type.LIFECYCLE)
@Documented
public @interface AutoCreate {

    String DEFAULT_KEY = "$default";

    /**
     * Closure that contains exactly one Map literal, which is passed to the
     * create method as named parameters.
     */
    Class<? extends Closure<Map<String, Object>>> value() default None.class;

    String key() default DEFAULT_KEY;

    Class<?> type() default Object.class;

    /** Marker class for default value. */
    class None extends Closure<Map<String, Object>> {
        public None(Object owner, Object thisObject) {
            super(owner, thisObject);
        }

        @SuppressWarnings("unused")
        public Map<String, Object> doCall() {
            return Collections.emptyMap();
        }
    }
}