package com.blackbuild.klum.ast.util;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;

import java.util.Map;

@SuppressWarnings("java:S100")
public class KlumUnkeyedFactory<T extends GroovyObject> extends KlumFactory<T> {
    public KlumUnkeyedFactory(Class<T> type) {
        super(DslHelper.requireNotKeyed(type));
    }
    public T Empty() {
        return With(null, null);
    }

    public T With(Closure<?> body) {
        return With(null, body);
    }

    public T With(Map<String, Object> values) {
        return With(values, null);
    }

    public T With(Map<String, Object> values, Closure<?> body) {
        return FactoryHelper.create(type, values, null, body);
    }


}
