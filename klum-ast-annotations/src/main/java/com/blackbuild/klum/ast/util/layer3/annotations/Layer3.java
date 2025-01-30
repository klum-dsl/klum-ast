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


import com.blackbuild.groovy.configdsl.transform.cast.NeedsDSLClass;
import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.KlumCastValidator;

import java.lang.annotation.*;

/**
 * Designated a class as part of a layer 3 DSL (usually from the api layer).
 * Has currently only an effect if the fixedKey member is set.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Inherited // This is currently not used, see https://issues.apache.org/jira/browse/GROOVY-6765
@KlumCastValidated
@NeedsDSLClass
@KlumCastValidator("com.blackbuild.klum.ast.validation.Layer3Validator")
@Documented
public @interface Layer3 {
    /**
     * Lets the key of instances of this class be automatically taken from the name if the field to which the instance is assigned.
     */
    boolean fixedKey() default false;
}
