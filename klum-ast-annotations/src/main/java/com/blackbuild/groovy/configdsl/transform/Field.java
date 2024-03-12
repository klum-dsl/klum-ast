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
package com.blackbuild.groovy.configdsl.transform;

import com.blackbuild.groovy.configdsl.transform.cast.NeedsDSLClass;
import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.KlumCastValidator;
import com.blackbuild.klum.cast.checks.NumberOfParameters;
import groovy.transform.Undefined;

import java.lang.annotation.*;

/**
 * <p>Controls specific behaviour for certain fields.</p>
 *
 * <p>This can be used to explicitly name the members of a collection. By default, the member name of collection
 * is the name of the collection minus a trailing 's', i.e. environments :: environment. The member name is used
 * as name for the generation of adder methods.</p>
 *
 * <p>Using {@code @Field}, this can be explicitly overridden, for example for values with different plural rules.
 * For example, the field {@code libraries} would by default contain the wrong elements name {@code librarie},
 * which could be changed:</p>
 *
 * <p>{@code @Field(member = 'library') Set<String> libraries}</p>
 *
 * <p><b>Note that the member names must be unique across all collections of a DSL hierarchy.</b></p>
 *
 * <p>In addition to fields, setter like methods (i.e. methods with a single parameter) can also be annotated with {@code @Field},
 * making them 'virtual fields'. For virtual fields, the same dsl methods are generated as for actual fields. The name
 * of the methods is the same as the method name. The annotated method is automatically converted into a Mutator method.</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@WriteAccess(WriteAccess.Type.MANUAL)
@KlumCastValidated
@NumberOfParameters(1)
@NeedsDSLClass
@KlumCastValidator("com.blackbuild.groovy.configdsl.transform.ast.FieldAstValidator")
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
     * literal String to Class mappings, i.e. {@code @DSL(alternatives = {child: ChildElement, sub: SubElement})}.
     */
    Class alternatives() default Undefined.class;

    /**
     * Closure that is used to derive the key from the value. This is only valid for Map types. The closure
     * gets a single parameter of the value type and must return a value of the key type.
     */
    Class keyMapping() default Undefined.class;

    /**
     * Allows to set the key for a single Keyed Object field to a value defined
     * by the owner. This member can contain either Closure executed against the owner
     * object or the special entry {@link FieldName}, which takes the name of the field.
     */
    Class key() default Undefined.class;

    /**
     * <p>Create converter methods for this field. Converter methods have the same name as regular setter / adders, but
     * different parameters. A converter is a closure with zero or more explicit parameters that is called to create the
     * target type (or the element type for collections / maps). Note that for maps of simple types, a key parameter
     * is added to the adder as well.</p>
     *
     * <p>Example:</p>
     *
     * <pre><code>
     * {@literal @}DSL class Foo {
     *   {@literal @}Field(converters = [
     *     {long value {@literal ->} new Date(value)},
     *     {int date, int month, int year {@literal ->} new Date(year, month, date)}
     *   ])
     *   Date birthday
     * }
     * </code></pre>
     *
     * <p>Creates additional methods:</p>
     *
     * <pre><code>
     * Date birthday(long value)
     * Date birthday(int date, int month, year)
     * </code></pre>
     *
     * <p>The closures must return an instance of the field (or element) type.</p>
     *
     * <p>Example:</p>
     *
     * <pre><code>
     * {@literal @}DSL class Foo {
     *   {@literal @}Field(annotations = [])
     *   Date birthday
     * }
     * </code></pre>
     *
     */
    Class[] converters() default {};

    /**
     * Allows to set the base type for the given field. DSL methods will be generated for the base type instead of the
     * actual type. This is useful for interfaces or abstract classes.
     */
    Class<?> defaultImpl() default Undefined.class;

    /**
     * Marker interface used to designate a field to use the field name of the owner
     * as key.
     */
    interface FieldName {}
}
