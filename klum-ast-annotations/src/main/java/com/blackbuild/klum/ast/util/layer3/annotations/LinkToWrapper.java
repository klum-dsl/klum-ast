package com.blackbuild.klum.ast.util.layer3.annotations;


import groovy.lang.Closure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

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
    public Class<? extends Closure<Object>> owner() {
        if (!fromField.owner().equals(None.class)) return fromField.owner();
        if (fromClass != null) return fromClass.owner();
        return None.class;
    }

    @Override
    public Class<?> ownerType() {
        if (!fromField.ownerType().equals(Object.class)) return fromField.ownerType();
        if (fromClass != null) return fromClass.ownerType();
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
}
