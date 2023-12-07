package com.blackbuild.klum.ast.util.copy;

import java.util.Collection;
import java.util.Map;

import static com.blackbuild.klum.ast.util.DslHelper.isDslObject;

public abstract class CompositeCopyStrategy implements CopyStrategy {

    private final SingleValueCopyStrategy singleValueCopyStrategy;
    private final CollectionCopyStrategy collectionCopyStrategy;
    private final MapCopyStrategy mapCopyStrategy;

    protected CompositeCopyStrategy(SingleValueCopyStrategy singleValueCopyStrategy, CollectionCopyStrategy collectionCopyStrategy, MapCopyStrategy mapCopyStrategy) {
        this.singleValueCopyStrategy = singleValueCopyStrategy;
        this.collectionCopyStrategy = collectionCopyStrategy;
        this.mapCopyStrategy = mapCopyStrategy;
    }

    @Override
    public <T> T getCopiedValue(T oldValue, T newValue) {
        if (isDslObject(newValue))
            return singleValueCopyStrategy.copyDslObject(oldValue, newValue);
        else if (newValue instanceof Collection)
            return (T) collectionCopyStrategy.copyCollection(this, (Collection) oldValue, (Collection) newValue);
        else if (newValue instanceof Map)
            return (T) mapCopyStrategy.copyMap(this, (Map<Object, Object>) oldValue, (Map<Object, Object>) newValue);
        else
            return singleValueCopyStrategy.copySingleValue(oldValue, newValue);
    }
}
