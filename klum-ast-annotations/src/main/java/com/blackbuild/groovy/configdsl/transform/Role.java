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
import com.blackbuild.klum.cast.checks.NeedsType;
import com.blackbuild.klum.cast.checks.NumberOfParameters;
import com.blackbuild.klum.cast.checks.ParameterTypes;

import java.lang.annotation.*;

/**
 * Designates this field as a role field. A role is the name of the field of the owner, where this object is referenced.
 *
 * <pre><code>
 * {@literal @}DSL
 *  class Database {
 *      {@literal @}Role User ddl
 *      {@literal @}Role User dml
 *      {@literal @}Role User monitoring
 *  }
 *
 *  {@literal @}DSL
 *  class User {
 *    {@literal @}Key String name
 *    {@literal @}Owner Database database
 *    {@literal @}Role String role
 *  }
 *
 * when:
 * instance = Database.Create.With {
 *     ddl("user1")
 *     dml("user2")
 *     monitoring("user3")
 * }
 *
 * then:
 * instance.ddl.role == "ddl"
 * instance.dml.role == "dml"
 * instance.monitoring.role == "monitoring"
 * </code></pre>
 *
 * <p>Note that while it is technically valid to use {@link Role} without a matching {@link Owner} field, the role
 * will always be null, so this usually only makes sense in special, inheritance related cases.</p>
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@WriteAccess(WriteAccess.Type.MANUAL)
@NumberOfParameters(1)
@NeedsType(String.class)
@ParameterTypes(String.class)
@Documented
public @interface Role {
    /** if set, role will only be set if the owner instance is of this type */
    Class<?> value() default Object.class;
}
