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
package com.blackbuild.klum.ast.util.layer3.annotations;

import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.checks.*;
import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.*;
import java.util.Map;

/**
 * Automatically converts the annotated field into a getter method providing access to all matching fields,
 * possibly filtered by the given annotation.
 *
 * <p>if placed on a (potentially abstract) method, that method is replaced with such a getter.</p>
 *
 * <p>This is usually used to provide the API layer of a three layer model.</p>
 *
 * <p>For example:</p>
 *
 * <pre><code>
 * abstract class Database {
 *     {@literal @}Cluster {@literal Map<String, User>} users
 * }
 *
 * class MyApplicationDatabase extends Database {
 *     User ddl
 *     User dml
 *     User monitoring
 * }
 * </code></pre>
 *
 * <p>Will implement the <code>getUsers()</code> method to return a Map with the keys being
 * 'ddl', 'dml' and 'monitoring' and the values containing the actual values of those fields,
 * including <code>null</code> values.</p>
 *
 * <p>Note that in addition to abstract methods, methods with an empty body or a body containing
 * just 'null' or an empty Map can also be annotated. This prevents IDEs from complaining if
 * using API layer and schema layer in the same project.</p>
 *
 * <p>Using the <code>value</code> field, an annotation can be provided. In that case, only the fields
 * of the class that have this annotation are returned. Note that the given annotation
 * must have <code>{@link RetentionPolicy#RUNTIME}</code>.</p>
 *
 * <pre><code>
 * abstract class Database {
 *     {@literal @}Cluster {@literal Map<String, User>} users
 *     {@literal @}Cluster(Required) {@literal Map<String, User>} requiredUsers
 * }
 *
 * class MyApplicationDatabase extends Database {
 *     {@literal @}Required User ddl
 *     {@literal @}Required User dml
 *     User monitoring
 * }
 * </code></pre>
 *
 * <p>In that example, <code>getUsers()</code> still returns all user fields, while
 * <code>getRequiredUsers()</code> only returns 'ddl' and 'dml'.</p>
 *
 * <h2>Cluster Factories</h2>
 *
 * <p>In addition to the getter, a cluster factory is created (much like a collection factory), containing only the dsl methods
 * of the respective cluster field</p>
 *
 * So with the above example, the following is correct:
 *
 * <pre><code>
 * MyApplicationDatabase.Create.With {
 *     users {
 *         ddl {...}
 *         dml {...}
 *         monitoring {...}
 *     }
 * }
 * </code></pre>
 *
 * <p>Using the {@link Cluster#bounded()} attribute, the setter methods can be restricted to be only available inside a factory
 * (note that this is done by making the methods protected, not completely removing them).</p>
 *
 * <p>The annotation can also be used for methods return Maps of Collections. If the (subclass of) Collection
 * is generic, the result is a map of the matching Collections. If the Collection does not use a generic parameter,
 * all collections would be returned.</p>
 *
 * <p>The annotation can also be set on classes or packages. If so, the bounded member is set for all cluster fields of a
 * a class/all classes of its package. Other members are not allowed on classes/packages.</p>
 */
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@GroovyASTTransformationClass({
        "com.blackbuild.klum.ast.util.layer3.ClusterFieldTransformation",
        "com.blackbuild.klum.ast.util.layer3.ClusterTransformation"
        })
@KlumCastValidated
@NeedsType(Map.class)
@NeedsReturnType(Map.class)
@NeedsGenerics
@NeedsOneOf(value = "bounded", whenOn = {ElementType.TYPE, ElementType.PACKAGE})
public @interface Cluster {

    /**
     * If set, filters the results by the given annotation.
     * @return The annotation to filter on.
     */
    @NotOn({ElementType.TYPE, ElementType.PACKAGE}) Class<? extends Annotation> value() default Undefined.class;

    /**
     * If set to false, null values are not included in the result.
     * @return To return or ignore null values.
     */
    @NotOn({ElementType.TYPE, ElementType.PACKAGE}) boolean includeNulls() default true;

    /**
     * If set to true, the setter methods for matching fields are only created inside a named factory.
     * @return Whether the setter methods are only created inside a factory.
     */
    boolean bounded() default false;

    @interface Undefined {}
}
