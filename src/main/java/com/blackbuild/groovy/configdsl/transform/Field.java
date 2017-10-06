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

import groovy.transform.Undefined;

import java.lang.annotation.*;

/**
 * Controls specific behaviour for certain fields. Currently, this annotation only makes sense for
 * collection fields.
 * <p>
 *     This can be used to explicitly name the members of a collection. By default, the member name of collection
 *     is the name of the collection minus a trailing 's', i.e. environments :: environment. The member name is used
 *     as name for the generation of adder methods.
 * </p>
 * <p>
 *     Using {@code @Field}, this can be explicitly overridden, for example for values with different plural rules.
 *     For example, the field {@code libraries} would by default contain the wrong elements name {@code librarie},
 *     which could be changed:
 * </p>
 * <p>{@code @Field(member = 'library') Set<String> libraries}</p>
 * <p><b>Note that the member names must be unique across all collections of a DSL hierarchy.</b></p>
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Field {

    /**
     * Name of the inner methods for collections. If empty (default), use field name stripped of a trailing 's'
     * (i.e. if the field is called environments, the elements are called environment by default. If the field name
     * does not end with an 's', the field name is used as is.
     */
    String members() default "";

    /**
     * Maps names to classes. If used, this value must be a closure which contains only a map with
     * literal String to Class mappings, i.e. `@DSL(alternatives = {child: ChildElement, sub: SubElement})`.
     */
    Class alternatives() default Undefined.class;
}
