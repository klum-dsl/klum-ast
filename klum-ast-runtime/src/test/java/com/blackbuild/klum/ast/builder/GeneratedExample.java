/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
 *
 */
package com.blackbuild.klum.ast.builder;

import com.blackbuild.klum.ast.KlumRwObject;

/**
 * A tiny generated-example to demonstrate migrating to typed builders.
 */
public class GeneratedExample {

    public static class RW implements KlumRwObject {
        public RW() {}
    }

    public static KlumBuilder<RW> builder() {
        return new KlumBuilderAdapter<>(new RW());
    }
}
