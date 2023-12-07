package com.blackbuild.klum.ast.util;

import java.util.Collection;
import java.util.Map;

public interface CopyStrategy {

    <T> T getCopiedValue(T oldValue, T newValue);

    <T> T copyDslObject(T oldValue, T newValue);
    <T> T copySingleValue(T oldValue, T newValue);
    <T extends Collection<E>, E> T copyCollection(T oldValue, T newValue);
    <T extends Map<K, V>, K, V> T copyMap(T oldValue, T newValue);
}
