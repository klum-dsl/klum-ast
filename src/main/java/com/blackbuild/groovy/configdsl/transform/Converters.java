/*
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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Handles implicit and explicit converter creation. KlumAST creates converter methods from all "factory-like" methods
 * in the target class. A factory-like method is a public static method that returns the specified type or one of its
 * subclasses and whose name matches one of the prefixes: `from`, `of`, `create`, `parse`.
 *
 * Note that the annotation on a field completely replaces the annotation on the class for that field.
 */
@Target({ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited // This is currently not used, see https://issues.apache.org/jira/browse/GROOVY-6765
@Documented
public @interface Converters {

    /**
     * Prefixes of static methods that are considered factories.
     */
    String[] includeMethods() default {};

    /**
     * Prefixes of static methods that should not be considered factories. If both includes and excludes are defined,
     * excludes wins over includes, i.e. methods that are included can later be excluded.
     */
    String[] excludeMethods() default {};

    /**
     * If set to false, the default prefixes are ignored. In that case, only explicitly defined includes (or ALL methods,
     * if no includes are set!) are considered.
     */
    boolean excludeDefaultPrefixes() default false;

    /**
     * Additional classes that are searched for factory methods.
     */
    Class[] value() default {};

    /**
     * If set to true, public constructors of a target class are used for converters as well.
     */
    boolean includeConstructors() default false;
}
