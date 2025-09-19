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

import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.checks.MutuallyExclusive;
import com.blackbuild.klum.cast.checks.NumberOfParameters;
import groovy.lang.Closure;

import java.lang.annotation.*;

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
 * <li>has a type that the container object is derived from (i.e., the object is a legal value for that field)</li>
 * </ul>
 *
 * <p>will be set to the owner value.</p>
 *
 * <p>This means that if an object that already has an existing owner is reused, the owner is not overridden, but silently ignored.
 * I.e., the first object that an object is assigned to is the actual owner.</p>
 *
 * <h3>Closure field</h3>
 * <p>If the annotated field is of type {@link Closure}, the Closure itself will be executed in the owner phase.</p>
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
 * <h2>Transitive Owners</h2>
 * <p>If the attribute {@code transitive} is set, not only the direct container is considered as ancestor, but instead
 * the closest ancestor of the given type (i.e., a grandparent instead of a direct parent). This works for fields as well as
 * methods.</p>
 *
 * <h2>Root Owners</h2>
 * <p>If the attribute {@code root} is set, the ultimate top level object is set, if the type matcher. This works for fields as well as
 * methods.</p>
 *
 * <h2>Converter</h2>
 * <p>If the attribute {@code converter} is set, the converter is executed against the owner object and the result of the
 * closure is assigned to the field or method. In that case, the single parameter of the closure is used as the owner
 * type to match.</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@WriteAccess(WriteAccess.Type.MANUAL)
@NumberOfParameters(1)
@MutuallyExclusive({"root", "transitive"})
@Documented
public @interface Owner {
    /** If set to true, the owner is set to the first ancestor of the given type. */
    boolean transitive() default false;

    /** If set to true, the owner is set to root object, if matching. */
    boolean root() default false;

    /**
     * If set, the field or method matches the closure parameter.
     * When set, the converter is executed against the owner object, and the result of the closure is assigned to the field or method.
     */
    Class<? extends Closure<Object>> converter() default NoClosure.class;
}
