package com.blackbuild.klum.ast.util.copy;

import java.util.Collection;
import java.util.Map;

import static com.blackbuild.klum.ast.util.DslHelper.isDslObject;

public abstract class AbstractCopyStrategy implements CopyStrategy, SingleValueCopyStrategy, CollectionCopyStrategy, MapCopyStrategy {
    @Override
    public <T> T getCopiedValue(T oldValue, T newValue) {
        if (isDslObject(newValue))
            return copyDslObject(oldValue, newValue);
        else if (newValue instanceof Collection)
            return (T) copyCollection(this, (Collection) oldValue, (Collection) newValue);
        else if (newValue instanceof Map)
            return (T) copyMap(this, (Map) oldValue, (Map) newValue);
        else
            return copySingleValue(oldValue, newValue);
    }
}
