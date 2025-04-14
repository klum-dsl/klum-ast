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
package com.blackbuild.klum.ast.util.copy;

import com.blackbuild.groovy.configdsl.transform.cast.NeedsDSLClass;
import com.blackbuild.klum.cast.KlumCastValidated;
import com.blackbuild.klum.cast.KlumCastValidator;
import com.blackbuild.klum.cast.checks.NeedsOneOf;
import com.blackbuild.klum.cast.checks.NeedsType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Handles how values are copied from one object to another.
 */
@Target({ElementType.FIELD, ElementType.TYPE, ElementType.PACKAGE})
@Retention(RetentionPolicy.RUNTIME)
@KlumCastValidated
@NeedsDSLClass
@NeedsOneOf(value = {"singles", "collections", "maps"})
public @interface Overwrite {

    Overwrite.Single singles() default @Overwrite.Single(OverwriteStrategy.Single.INHERIT);
    Overwrite.Collection collections() default @Overwrite.Collection(OverwriteStrategy.Collection.INHERIT);
    Overwrite.Map maps() default @Overwrite.Map(OverwriteStrategy.Map.INHERIT);
    Overwrite.Missing missing() default @Overwrite.Missing(OverwriteStrategy.Missing.FAIL);

    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @NeedsDSLClass
    @KlumCastValidator("com.blackbuild.klum.ast.validation.OverwriteSingleCheck")
    @interface Single {
        OverwriteStrategy.Single value();
    }

    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @NeedsDSLClass
    @NeedsType(java.util.Collection.class)
    @interface Collection {
        OverwriteStrategy.Collection value();
    }

    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @NeedsDSLClass
    @NeedsType(java.util.Map.class)
    @KlumCastValidator("com.blackbuild.klum.ast.validation.OverwriteMapCheck")
    @interface Map {
        OverwriteStrategy.Map value();
    }

    @Target({ElementType.ANNOTATION_TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @KlumCastValidated
    @NeedsDSLClass
    @interface Missing {
        OverwriteStrategy.Missing value();
    }
}
