package com.blackbuild.klum.ast.util;

import org.codehaus.groovy.runtime.InvokerHelper;

import java.util.Collection;
import java.util.Map;

import static com.blackbuild.klum.ast.util.DslHelper.isDslObject;
import static com.blackbuild.klum.ast.util.KlumInstanceProxy.getProxyFor;

public class DefaultCopyStrategy implements CopyStrategy {

    @Override
    public <T> T getCopiedValue(T oldValue, T newValue) {
        if (isDslObject(newValue))
            return copyDslObject(oldValue, newValue);
        else if (newValue instanceof Collection)
            return (T) copyCollection((Collection) oldValue, (Collection) newValue);
        else if (newValue instanceof Map)
            return (T) copyMap((Map) oldValue, (Map) newValue);
        else
            return copySingleValue(oldValue, newValue);
    }

    @Override
    public <T> T copyDslObject(T oldValue, T newValue) {
        return (T) getProxyFor(newValue).cloneInstance();
    }

    @Override
    public <T> T copySingleValue(T oldValue, T newValue) {
        if (newValue instanceof Cloneable)
            return (T) InvokerHelper.invokeMethod(newValue, "clone", null);
        return newValue;
    }

    @Override
    public <T extends Collection<E>, E> T copyCollection(T oldValue, T newValue) {
        T result = (T) InvokerHelper.invokeConstructorOf(newValue.getClass(), null);
        newValue.stream().map((E newElement) -> getCopiedValue(null, newElement)).forEach(result::add);
        return result;
    }

    @Override
    public <T extends Map<K, V>, K, V> T copyMap(T oldValue, T newValue) {
        T result = (T) InvokerHelper.invokeConstructorOf(newValue.getClass(), null);
        newValue.forEach((key, value) -> result.put(key, getCopiedValue(oldValue.get(key), value)));
        return result;
    }
}
