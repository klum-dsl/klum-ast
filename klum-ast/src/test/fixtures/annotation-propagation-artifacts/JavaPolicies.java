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
package qualification.javaapi;

import com.blackbuild.klum.ast.Builder;
import com.blackbuild.klum.ast.PropagateAnnotation;
import com.blackbuild.klum.ast.Required;
import com.blackbuild.klum.ast.layer3.AutoCreate;
import com.blackbuild.klum.ast.layer3.Cluster;
import com.blackbuild.klum.ast.layer3.DefaultValues;
import qualification.foreign.ForeignTrigger;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

public final class JavaPolicies {
    private JavaPolicies() {}

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @DefaultValues
    // AP-4 changes requests in a new library, never the emitted Schema binary.
    @PropagateAnnotation(AutoCreate.class)
    @PropagateAnnotation(Marker.class)
    public @interface Repeated { String name() default ""; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD) @DefaultValues
    @PropagateAnnotation.List({@PropagateAnnotation(AutoCreate.class), @PropagateAnnotation(Marker.class)})
    public @interface Container { String name() default ""; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    public @interface Marker {}

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    @PropagateAnnotation(Required.class)
    public @interface RequiredValue {}

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
    @PropagateAnnotation(Builder.Query.class)
    public @interface Query {}

    @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.FIELD, ElementType.METHOD})
    @PropagateAnnotation(Cluster.class)
    public @interface Group {}

    @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
    @PropagateAnnotation(ForeignTrigger.class)
    public @interface ForeignAlias { String value() default "domain"; }
}
