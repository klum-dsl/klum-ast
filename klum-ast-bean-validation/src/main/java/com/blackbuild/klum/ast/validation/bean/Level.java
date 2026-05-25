/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.klum.ast.validation.bean;

import com.blackbuild.groovy.configdsl.transform.Validate;
import jakarta.validation.Payload;

import java.util.Map;

/**
 * Mapping interface for validation levels. Since JSR380 has no direct concept of levels, we use groups to simulate levels.
 * However, groups need to be interfaces. Use the inner classes of this class as group in your validation annotations.
 *
 * <pre><code>
 * {@literal @}Min(value = 20L, groups = Level.INFO)
 * int value
 * </code></pre>
 */
public final class Level {

    private static final Map<Class<? extends Payload>, Validate.Level> MAPPING = Map.of(
            NONE.class, Validate.Level.NONE,
            INFO.class, Validate.Level.INFO,
            WARNING.class, Validate.Level.WARNING,
            DEPRECATION.class, Validate.Level.DEPRECATION,
            ERROR.class, Validate.Level.ERROR
    );

    private Level() {
        /* This utility class should not be instantiated */
    }

    static Validate.Level getLevelForPayload(Class<? extends Payload> payload) {
        return MAPPING.getOrDefault(payload, Validate.Level.NONE);
    }

    public static class NONE implements Payload {}
    public static class INFO implements Payload {}
    public static class WARNING implements Payload {}
    public static class DEPRECATION implements Payload {}
    public static class ERROR implements Payload {}
}
