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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Designates a field as the key of the containing class. The main usage of this is that this field is automatically used
 * when an instance of the class is put into a map.
 *
 * In a hierarchy of model objects, the ancestor model defines whether the hierarchy is keyed or not.
 *
 * it is illegal
 *
 * * to put this annotation on a field of any other class than the ancestor of a hierarchy
 * * to put this annotation on more than one field in a class.
 *
 * Marking a field as key has the following consequences:
 *
 * * a constructor is created with a single argument of the type of the key field, the default constructor is
 *     removed
 * * the {@code create} method and all adder / setter methods creating an instance of this type take an additional
 *     argument of the annotated type
 * * for this field, no dsl setter methods are created
 *
 * ```groovy
 * given:
 * {@literal @DSL}
 * class Foo {
 *   {@literal @Key} String name
 * }
 *
 * when:
 * instance = Foo.create("Dieter") {}
 *
 * then:
 * instance.name == "Dieter"
 * ```
 *
 * Example with map
 * ----------------
 * ```groovy
 *  given:
 * {@literal @DSL}
 *  class Foo {
 *    {@literal Map<String, Bar> bars}
 *  }
 *
 * {@literal @DSL}
 *  class Bar {
 *    {@literal @Key} String name
 *    String url
 *  }
 *
 *  when:
 *  instance = Foo.create {
 *    bars {
 *      bar("Dieter") { url "1" }
 *      bar("Klaus") { url "2" }
 *    }
 *  }
 *
 *  then:
 *  instance.bars.Dieter.url == "1"
 *  instance.bars.Klaus.url == "2"
 *
 * ```
 *
 * Currently only fields of type String are allowed to be keys.
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Key {
}
