package com.blackbuild.klum.ast.util.copy;

import java.util.Collection;

public interface CollectionCopyStrategy {
    <T extends Collection<E>, E> T copyCollection(CopyStrategy memberStrategy, T oldValue, T newValue);

}
