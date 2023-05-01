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
package com.blackbuild.klum.ast.util.layer3.annotations;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.*;

/**
 * <p>Automatically implements the annotated method by providing access to all matching fields,
 * possibly filtered by the given annotation. This is usually use to provide the API layer of
 * a three layer model.</p>
 *
 * <p>For example:</p>
 *
 * <pre><code>
 * abstract class Database {
 *     {@literal @}Cluster abstract {@literal Map<String, User>} getUsers
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
 *     {@literal @}Cluster abstract {@literal Map<String, User>} getUsers
 *     {@literal @}Cluster(Required) abstract {@literal Map<String, User>} getRequiredUsers
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
 * The annotation can also be used for methods return Maps of Collections. If the (subclass of) Collection
 * is generic, the result is a map of the matching Collections. If the Collection does not use a generic parameter,
 * all collections would be returned
 *
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@GroovyASTTransformationClass("com.blackbuild.klum.ast.util.layer3.ClusterTransformation")
public @interface Cluster {

    Class<? extends Annotation> value() default Undefined.class;

    @interface Undefined {}
}
