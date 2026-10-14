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
package com.blackbuild.klum.ast.propagation;

import com.blackbuild.klum.ast.PropagateAnnotation;
import com.blackbuild.klum.ast.Required;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Java 17 annotation library: javac normalizes repeats independently of the Schema compiler. */
public final class ContributionPolicies {
    private ContributionPolicies() {}

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    @Repeatable(Labels.class)
    public @interface Label { String value() default "selected default"; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    public @interface Labels { Label[] value(); }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    @PropagateAnnotation(Required.class) @PropagateAnnotation(Label.class)
    public @interface RepeatedRequests { String value() default "domain default"; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    // This fixture must preserve the explicit request-container spelling under qualification.
    @SuppressWarnings("java:S1710")
    @PropagateAnnotation.List({@PropagateAnnotation(Required.class), @PropagateAnnotation(Label.class)})
    public @interface ContainerRequests { String value() default "domain default"; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    @PropagateAnnotation(Label.class) @Repeatable(Policies.class)
    public @interface Policy { String value() default "domain default"; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    public @interface Policies { Policy[] value() default {@Policy}; }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    public @interface Unrelated { Policy[] value(); }

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    @PropagateAnnotation(Label.class)
    public @interface OtherPolicy {}

    @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.FIELD)
    // Duplicate requests in an explicit container are a separate input from repeated syntax.
    @SuppressWarnings("java:S1710")
    @PropagateAnnotation.List({@PropagateAnnotation(Label.class), @PropagateAnnotation(Label.class)})
    public @interface DuplicateRequests {}

    @Target(ElementType.METHOD)
    public @interface MethodOnly {}
    @PropagateAnnotation(MethodOnly.class)
    public @interface WrongTarget {}

    @Target(ElementType.FIELD)
    public @interface SelectedMember { String required(); }
    @PropagateAnnotation(SelectedMember.class)
    public @interface MissingMember {}

    @PropagateAnnotation(Self.class)
    public @interface Self {}
    @PropagateAnnotation(Self.class)
    public @interface SelfPolicy {}

    @PropagateAnnotation(CycleB.class)
    public @interface CycleA {}
    @PropagateAnnotation(CycleA.class)
    public @interface CycleB {}
    @PropagateAnnotation(CycleA.class)
    public @interface CyclePolicy {}
}
