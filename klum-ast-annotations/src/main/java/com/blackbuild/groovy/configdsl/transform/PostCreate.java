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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * <p>Designates a method to be executed after create has been called and the templates have been applied. These methods
 * are called before named parameters or the creation closure is applied, but after an {@link Owner} has been set.
 * There can be an arbitrary number of {@link PostCreate} methods in a class. Like other lifecycle methods,
 * {@link PostCreate} methods must not be private, and they can be overridden, it is advised to make
 * them protected.</p>
 * <p>A common usage for post create methods is to extract data from the owner that is needed to further configure the
 * object, which might not be feasible using default values (the following example <i>could</i> be done with Default
 * values, though.</p>
 *
 * <pre><code>
 * given:
 * {@literal @DSL}
 * class Container {
 *   Foo foo
 *   String name
 * }
 *
 * {@literal @DSL}
 * class Foo {
 *   {@literal @Owner} Container owner
 *   String childName
 *
 *   {@literal @PostCreate}
 *   def setDefaultValueOfChildName() {
 *     // set a value derived from owner
 *     childName = "$owner.name::child"
 *   }
 * }
 *
 * when:
 * instance = clazz.create {
 *   name "parent"
 *   foo {}
 * }
 *
 * then:
 * instance.foo.childName == "parent::child"
 * </code></pre>
 *
 * <p>Lifecycle methods are called in the order of the model hierarchy, i.e. first lifecycle methods of the ancestor
 * model are called, then of the next level and so one. Overridden lifecycle methods are called in the place where they
 * were originally defined, i.e. if a method is defined in {@code Parent} and overridden in {@code Child}, the
 * overridden method is called along with the {@code Parent}'s methods.</p>
 */
@Target({FIELD, METHOD})
@Retention(RUNTIME)
@Inherited
@WriteAccess
@Documented
public @interface PostCreate {
}
