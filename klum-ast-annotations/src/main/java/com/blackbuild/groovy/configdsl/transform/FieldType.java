/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
     * store additional, changeable state directly inside the model. Transient fields remain mutable on completed DSL
     * Objects and are ignored for the purpose of hashCode or equals.</p>
     *
     * <p>As opposed to the {@code transient} keyword of Java / Groovy, {@code FieldType.TRANSIENT} fields **will** be part of serialization
     * by default.</p>
     */
    TRANSIENT,

    /**
     * <p>Designates that no accessor methods should be created at all. The field remains available to generated Builder
     * code and can be accessed from inside a mutator. Note that automatic initialization of empty collections /
     * maps is not active for these fields as well, so you need to make sure the field is initialized.</p>
     */
    IGNORED,

    /**
     * Builder fields are construction-only state. They exist on the generated Builder, with public setter and DSL methods,
     * but are omitted from the completed DSL Object. This is useful for helper fields used by later construction phases
     * to produce model state (for example the
     * {@link com.blackbuild.klum.ast.util.layer3.annotations.LinkTo} annotation).
     */
    BUILDER,

    /**
     * <p>Designates this field as a link to an existing object. Link field are only valid for DSL fields or collections
     * and only create dsl methods for existing objects (i.e. using a instance as parameter), but no methods to
     * create new instances (Map and/or Closure parameters).</p>
     */
    LINK,

    /**
     * A relationship which accepts either owned composition or an aggregation link. A fresh Builder from the current
     * construction session is claimed as composition; an already claimed Builder or a completed DSL Object is retained
     * as an aggregation target. {@link com.blackbuild.klum.ast.util.layer3.annotations.LinkTo} selects this mode unless
     * the field explicitly declares {@link #LINK}.
     */
    OPTIONAL_LINK
}
