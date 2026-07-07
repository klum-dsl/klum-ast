package com.blackbuild.klum.ast.builder;

import com.blackbuild.klum.ast.KlumRwObject;

/**
 * Compatibility adapter that exposes an existing KlumRwObject as a builder.
 * Prototype only: build() returns the underlying RW instance.
 */
public class KlumBuilderAdapter implements KlumBuilder<Object> {
    private final KlumRwObject rw;

    public KlumBuilderAdapter(KlumRwObject rw) {
        this.rw = rw;
    }

    @Override
    public Object build() {
        return rw;
    }
}
