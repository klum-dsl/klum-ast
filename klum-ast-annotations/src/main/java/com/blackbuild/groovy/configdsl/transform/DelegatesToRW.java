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

import groovy.transform.Undefined;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be used to mark a closure method to be delegating to an RW class. This works
 * similar to {@code DelegatesTo}, but since the RW class does not yet exist during parsing of the schema, it
 * is not possible to simply write {@code {@link groovy.lang.DelegatesTo}(MyClass._RW)}. Can take an optional
 * value of the target class whose RW class should be delegated to.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.SOURCE)
@Documented
public @interface DelegatesToRW {

    /**
     * The type to which the closure argument should delegate. If not set, the type owning the method
     * is taken.
     */
    Class value() default Undefined.class;

}
