package com.blackbuild.klum.ast.util.copy;

import java.util.Map;

public interface MapCopyStrategy {
    <T extends Map<K, V>, K, V> T copyMap(CopyStrategy memberStrategy, T oldValue, T newValue);

}
