package com.blackbuild.klum.ast.util.copy;

import org.codehaus.groovy.runtime.InvokerHelper;

import static com.blackbuild.klum.ast.util.KlumInstanceProxy.getProxyFor;

public interface FullOverwriteCopyStrategy {

    SingleValueCopyStrategy SINGLE_VALUE = new SingleValue();
    CollectionCopyStrategy COLLECTION = new Collection();
    MapCopyStrategy MAP = new Map();

    class SingleValue implements SingleValueCopyStrategy {
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
    }

    class Collection implements CollectionCopyStrategy {
        @Override
        public <T extends java.util.Collection<E>, E> T copyCollection(CopyStrategy memberStrategy, T oldValue, T newValue) {
            T result;
            if (oldValue != null && oldValue.isEmpty())
                result = oldValue;
            else
                result = (T) InvokerHelper.invokeConstructorOf(newValue.getClass(), null);

            newValue.stream().map((E newElement) -> memberStrategy.getCopiedValue(null, newElement)).forEach(result::add);
            return result;
        }
    }

    class Map implements MapCopyStrategy {
        @Override
        public <T extends java.util.Map<K, V>, K, V> T copyMap(CopyStrategy memberStrategy, T oldValue, T newValue) {
            T result;
            if (oldValue != null && oldValue.isEmpty())
                result = oldValue;
            else
                result = (T) InvokerHelper.invokeConstructorOf(newValue.getClass(), null);

            newValue.forEach((key, value) -> result.put(key, memberStrategy.getCopiedValue(null, value)));
            return result;
        }
    }
}