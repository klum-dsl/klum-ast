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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a field as owner field. Owner fields is automatically set when an instance of the
 * containing class is first added to another DSL-Object, either as value of a field or as member of a collection.
 * ```groovy
 * given:
 * .@DSL
 * class Foo {
 *   Bar bar
 * }
 *
 * .@DSL
 * class Bar {
 *   &#064;Owner Foo owner
 * }
 *
 * when:
 * instance = Foo.create {
 *   bar {}
 * }
 *
 * then:
 * instance.bar.owner.is(instance)
 * ```
 *
 * A dsl hierarchy can have any number of `Owner` fields. When the object is added to another object,
 * any owner field of that object that:
 *
 * - is not set
 * - has a type that the container object is derived from (i.e. the object is a legal value for that field)
 *
 * will be set to the owner value.
 *
 * This means that if an object that already has an existing owner is reused, the owner is not overridden, but silently ignored.
 * I.e. the first object that an object is assigned to, is the actual owner.
 *
 * Can also be used to annotate a single parameter method. All matching methods are also called when the object is
 * added to another object.
 *
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Owner {
}
