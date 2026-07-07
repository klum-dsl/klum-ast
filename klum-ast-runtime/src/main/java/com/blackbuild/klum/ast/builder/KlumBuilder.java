package com.blackbuild.klum.ast.builder;

/**
 * Minimal builder interface for prototype.
 * Real generated builders will implement a typed variant returning Model types.
 */
public interface KlumBuilder<T> {
    T build();
}
