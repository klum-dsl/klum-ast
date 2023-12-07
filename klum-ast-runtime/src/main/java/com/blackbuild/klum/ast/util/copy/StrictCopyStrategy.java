package com.blackbuild.klum.ast.util.copy;

import java.util.Collection;
import java.util.Map;

public abstract class StrictCopyStrategy implements CopyStrategy {

    private final CopyStrategy delegate;

    protected StrictCopyStrategy(CopyStrategy delegate) {
        this.delegate = delegate;
    }

    @Override
    public <T> T getCopiedValue(T oldValue, T newValue) {
        if (oldValue == null)
            return delegate.getCopiedValue(oldValue, newValue);

        if (oldValue instanceof Collection && ((Collection<?>) oldValue).isEmpty())
            return delegate.getCopiedValue(oldValue, newValue);

        if (oldValue instanceof Map && ((Map<?,?>) oldValue).isEmpty())
            return delegate.getCopiedValue(oldValue, newValue);

        throw new IllegalStateException("Attempt to overwrite non-empty value");
    }
}
