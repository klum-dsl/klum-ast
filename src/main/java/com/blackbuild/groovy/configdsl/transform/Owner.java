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

import java.lang.annotation.*;

/**
 * Designates a field as owner field. The owner is automatically set when an instance of the
 * containing class is first added to another DSL-Object, either as value of a field or as member of a collection.
 * <pre><code>
 * given:
 * &#064;DSL
 * class Foo {
 *   Bar bar
 * }
 *
 * &#064;DSL
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
 * </code></pre>
 * <p>There are two caveats</p>
 * <ul>
 *     <li>no validity checks are performed during transformation time, leading to runtime ClassCastExceptions
 *     if the owner type is incorrect. This allows for the fact that one type of model might be part of different
 *     owners.</li>
 *     <li>If an object that already has an existing owner is reused, the owner is not overridden, but silently ignored.
 *     I.e. the first object that an object is assigned to, is the actual owner.</li>
 * </ul>
 * <p><b>Currently, only one owner field is allowed in a model hierarchy.</b></p>
 * <p><b>The setting of the owner is determined statically during transformation, i.e. if the owner class (Container) has
 * a field of type {@code Parent} and the owner field is defined in the class {@code Child}, the owner field of a Child instance
 * will never be set when added to a Container instance</b></p>
 *
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Owner {
}
