/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2019 Stephan Pauxberger
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
import groovy.transform.Undefined;
import groovy.transform.stc.ClosureParams;
import groovy.transform.stc.SimpleType;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

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
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Field {

    FieldType value() default FieldType.DEFAULT;

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

    /**
     * Closure that is used to derive the key from the value. This is only valid for Map types. The closure
     * gets a single parameter of the value type and must return a value of the key type.
     */
    Class keyMapping() default Undefined.class;

    /**
     * Create converter methods for this field. Converter methods have the same name as regular setter / adders, but
     * different parameters. A converter is a closure with zero or more explicit parameters that is called to create the
     * target type (or the element type for collections / maps). Note that for maps of simple types, a key parameter
     * is added to the adder as well.
     *
     * Example:
     *
     * ```groovy
     * .@DSL class Foo {
     *   .@Field(converters = [
     *     {long value -> new Date(value)},
     *     {int date, int month, int year -> new Date(year, month, date)}
     *   ])
     *   Date birthday
     * }
     * ```
     *
     * Creates additional methods:
     *
     * ```groovy
     * Date birthday(long value)
     * Date birthday(int date, int month, year)
     * ```
     *
     * The closures must return an instance of the field (or element) type.
     *
     * Example:
     *
     * ```groovy
     * .@DSL class Foo {
     *   .@Field(annotations = [])
     *   Date birthday
     * }
     * ```
     *
     */
    Class[] converters() default {};

    /**
     * Allows a DelegatesTo annotation to be place on the element parameter for adder methods (in case of
     * collection or map only on the single adder methods).
     */
    DelegatesTo delegatesTo() default @DelegatesTo();

    /**
     * Places the given ClosureParams annotation on the element parameter for adder methods (in case of
     * collection or map only on the single adder methods). This only makes sense for Closure or collection
     * of closure fields.
     */
    ClosureParams closureParams() default @ClosureParams(SimpleType.class);
}
