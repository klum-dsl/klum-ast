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
import com.blackbuild.klum.cast.checks.NeedsOneOf;
import com.blackbuild.klum.cast.checks.OnlyOn;
import groovy.lang.Closure;

import java.lang.annotation.*;

/**
 * <p>Designates a default value for the given field. This automatically sets the field to
 * that default value when the value of the annotated field is empty (as defined by Groovy Truth) during
 * the default lifecycle phase..</p>
 *
 * <p>The default target as decided by the members must be exactly one of:</p>
 * <ul>
 *  <li>{@code field}: the value of the field with the given name</li>
 *  <li>{@code delegate}: the value of an identically named field of the given delegate field.
 *  This is especially useful in with the {@link Owner} annotation to create composite like tree structures.</li>
 *  <li>{@code closure}: execute the closure (in the context of {@code this}) and use the result</li>
 * </ul>
 *
 * <pre><code>
 * given:
 * {@literal @DSL}
 * class Container {
 *   String name
 *   Element element
 * }
 *
 * {@literal @DSL}
 * class Element {
 *   {@literal @Owner} Container owner
 *
 *   {@literal @Default(delegate} = 'owner')
 *   String name
 * }
 *
 * when: "No name is set for the inner element"
 * instance = Container.Create.With {
 * name "outer"
 *   element {}
 * }
 *
 * then: "the name of the outer instance is used"
 * instance.element.name == "outer"
 *
 * when:
 * instance = Container.Create.With {
 *   name "outer"
 *   element {
 *     name "inner"
 *   }
 * }
 *
 * then:
 * instance.element.name == "inner"
 * }
 * </code></pre>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@WriteAccess
@NeedsDSLClass
@NeedsOneOf(whenOn = ElementType.FIELD, value = {"field", "delegate", "code"}, exclusive = true)
@Documented
public @interface Default {

    /**
     * <p>Delegates to the field with the given name, if the annotated field is empty.</p>
     *
     * <code>{@literal @Default(field = 'other') String aValue}</code>
     * <p>leads to</p>
     * <code>aValue ?: other</code>
     */
    @OnlyOn(ElementType.FIELD) String field() default "";

    /**
     * <p>Delegates to the given closure, if the annotated field is empty.</p>
     *
     * <code>{@literal @Default(code = { name.toLowerCase() }) String aValue}</code>
     * <p>leads to</p>
     * <code>aValue ?: name.toLowerCase()</code>
     */
    @OnlyOn(ElementType.FIELD) Class<? extends Closure<Object>> code() default Undefined.class;

    /**
     * <p>Delegate to a field with the same name on the targeted field, if the annotated field is empty</p>
     *
     * <code>{@literal @Default(delegate = 'other') String aValue}</code>
     * <p>leads to</p>
     * <code>aValue ?: parent.aValue</code>
     */
    @OnlyOn(ElementType.FIELD) String delegate() default "";
}
