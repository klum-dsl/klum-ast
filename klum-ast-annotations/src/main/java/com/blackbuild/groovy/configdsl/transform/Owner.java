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

import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.checks.NumberOfParameters;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>Designates a field or method as owner field or method.</p>
 *
 * <p>Owners are automatically set when an instance of the containing class is first to another DSL-Object, either as
 * value of a field or as member of a collection.</p>
 *
 * <h2>Fields</h2>
 *
 * <pre><code>
 * given:
 * {@literal @DSL}
 * class Foo {
 *   Bar bar
 * }
 *
 * {@literal @DSL}
 * class Bar {
 *   {@literal @Owner} Foo container
 * }
 *
 * when:
 * instance = Foo.Create.With {
 *   bar {}
 * }
 *
 * then:
 * instance.bar.container.is(instance)
 * </code></pre>
 *
 * <p>A dsl hierarchy can have any number of {@code Owner} fields. When the object is added to another object,
 * any owner field of that object that:</p>
 *
 * <ul>
 * <li>is not set</li>
 * <li>has a type that the container object is derived from (i.e. the object is a legal value for that field)</li>
 * </ul>
 *
 * <p>will be set to the owner value.</p>
 *
 * <p>This means that if an object that already has an existing owner is reused, the owner is not overridden, but silently ignored.
 * I.e. the first object that an object is assigned to, is the actual owner.</p>
 *
 * <h2>Methods</h2>
 * <p>Can also be used to annotate a single parameter method. All matching methods are also called when the object is
 * added to another object. Other as with fields, these methods are called multiple times, if applicable.</p>
 *
 * <pre><code>
 * given:
 * {@literal @DSL}
 * class Foo {
 *   {@literal @Key} String name
 *   Bar bar
 * }
 *
 * {@literal @DSL}
 * class Bar {
 *   String containerName
 *   {@literal @Owner} void container(Foo foo) {
 *       containerName = foo.name
 *   }
 * }
 *
 * when:
 * instance = Foo.Create.With("bla") {
 *   bar {}
 * }
 *
 * then:
 * instance.bar.containerName == "bla"
 * </code></pre>
 *
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@WriteAccess(WriteAccess.Type.MANUAL)
@NumberOfParameters(1)
@Documented
public @interface Owner {
}
