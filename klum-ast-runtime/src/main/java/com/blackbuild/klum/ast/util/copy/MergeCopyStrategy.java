package com.blackbuild.klum.ast.util.copy;

public interface MergeCopyStrategy {
    MapCopyStrategy MAP = new Map();

    class Map implements MapCopyStrategy {
        @Override
        public <T extends java.util.Map<K, V>, K, V> T copyMap(CopyStrategy memberStrategy, T oldValue, T newValue) {
            newValue.forEach((key, value) -> oldValue.put(key, memberStrategy.getCopiedValue(oldValue.get(key), value)));
            return oldValue;
        }
    }
}