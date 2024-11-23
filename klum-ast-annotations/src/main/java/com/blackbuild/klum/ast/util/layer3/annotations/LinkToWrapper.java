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
package com.blackbuild.klum.ast.util.layer3.annotations;


import com.blackbuild.groovy.configdsl.transform.NoClosure;
import groovy.lang.Closure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Objects;

/**
 * Helper class to wrap the LinkTo annotation and provide a unified view on the annotation.
 */
@SuppressWarnings("ClassExplicitlyAnnotation")
public class LinkToWrapper implements LinkTo {

    @Nullable private final LinkTo fromClass;
    @NotNull private final LinkTo fromField;

    public LinkToWrapper(Field field) {
        this.fromField = field.getAnnotation(LinkTo.class);
        this.fromClass = field.getDeclaringClass().getAnnotation(LinkTo.class);
    }

    @Override
    public Class<? extends Annotation> annotationType() {
        return LinkTo.class;
    }

    @Override
    public String field() {
        return fromField.field();
    }

    @Override
    public String fieldId() {
        return fromField.fieldId();
    }

    @Override
    public Class<? extends Closure<Object>> provider() {
        if (fromField.provider() != NoClosure.class) return fromField.provider();
        if (fromClass != null) return fromClass.provider();
        return NoClosure.class;
    }

    @Override
    public Class<?> providerType() {
        if (!fromField.providerType().equals(Object.class)) return fromField.providerType();
        if (fromClass != null) return fromClass.providerType();
        return Object.class;
    }

    @Override
    public Strategy strategy() {
        if (!fromField.strategy().equals(Strategy.AUTO)) return fromField.strategy();
        if (fromClass != null) return fromClass.strategy();
        return Strategy.AUTO;
    }

    @Override
    public String nameSuffix() {
        if (!fromField.nameSuffix().equals("")) return fromField.nameSuffix();
        if (fromClass != null) return fromClass.nameSuffix();
        return "";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LinkToWrapper that = (LinkToWrapper) o;
        return Objects.equals(fromClass, that.fromClass) && Objects.equals(fromField, that.fromField);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromClass, fromField);
    }
}
