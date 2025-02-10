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

/**
 * Designates the type of the field. This is used to control special behaviour. Used as a value to {@link Field#value()}
 */
public enum FieldType {

    /**
     * This is default field type, active if either the {@link Field} annotation or its value is not present.
     */
    DEFAULT,

    /**
     * Designates the field as protected. Setters and dsl methods for internal fields are created protected, making them
     * not directly changeable from a configuration. They can only be changed from inside {@link Mutator}
     * or lifecycle methods.
     */
    PROTECTED,

    /**
     * <p>Designates a field as transient. Transient fields are not formally part of the model, but can be used to
     * store additional, changeable state directly inside the model. Transient fields can be accessed directly,
     * without using {@code apply} or a mutator method. The are ignored for the purpose of hashCode or equals.</p>
     *
     * <p>As opposed to the {@code transient} keyword of Java / Groovy, {@code FieldType.TRANSIENT} fields **will** be part of serialization
     * by default.</p>
     */
    TRANSIENT,

    /**
     * <p>Designates that no accessor methods should be created at all, i.e. the regular setters are still part of the
     * RW model and can only be accessed from inside a mutator. Not that the automatic initialization of empty collections /
     * maps is not active for these fields as well, so you need to make sure the field is initialized.</p>
     */
    IGNORED,

    /**
     * Builder fields are the opposite of protected fields. They create public setter and dsl methods,
     * but the field itself is protected. This is useful for helper fields that are not part of the
     * public model, but are used in later phases to help construct the model (for example the
     * {@link com.blackbuild.klum.ast.util.layer3.annotations.LinkTo} annotation).
     */
    BUILDER,

    /**
     * <p>Designates this field as a link to an existing object. Link field are only valid for DSL fields or collections
     * and only create dsl methods for existing objects (i.e. using a instance as parameter), but no methods to
     * create new instances (Map and/or Closure parameters).</p>
     */
    LINK
}
