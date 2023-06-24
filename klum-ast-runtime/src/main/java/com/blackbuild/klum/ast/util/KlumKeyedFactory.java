package com.blackbuild.klum.ast.util;

import groovy.lang.Closure;
import groovy.lang.GroovyObject;

import java.util.Map;

import static com.blackbuild.klum.ast.util.DslHelper.*;

@SuppressWarnings("java:S100")
public class KlumKeyedFactory<T extends GroovyObject> extends KlumFactory<T> {

    public KlumKeyedFactory(Class<T> type) {
        super(requireKeyed(type));
    }

    public T With(String key) {
        return With(key, null, null);
    }

    public T With(String key, Closure<?> body) {
        return With(key, null, body);
    }

    public T With(String key, Map<String, Object> values) {
        return With(key, values, null);
    }

    public T With(String key, Map<String, Object> values, Closure<?> body) {
        return FactoryHelper.create(type, values, key, body);
    }

}
