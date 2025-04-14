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
package com.blackbuild.klum.ast.util.copy;

/**
 * Defines the various strategies for different targets. Note that the collection and map strategies are combined
 * with dsl or basic object, depending on the element type
 */
public interface OverwriteStrategy {

    /**
     * Defines the strategy for a single dsl object.
     */
    enum Single {
        /** No explicit overwrite strategy is set. */
        INHERIT,
        /** The object is fully replaced. */
        REPLACE,
        /** The object is replaced even if the copy source is null. */
        ALWAYS_REPLACE,
        /** The object is set only when there was no previous value. */
        SET_IF_NULL,
        /** The object is merged with the existing object. */
        MERGE
    }

    /**
     * Defines the strategy for a collection.
     */
    enum Collection {
        /** No explicit overwrite strategy is set. */
        INHERIT,
        /**
         * The members of the copy source are added to the target collection. Potential item replacements are handled
         * by the target collection (like {@link java.util.Set})
         */
        ADD,
        /** The collection is fully replaced if the replacement is not empty. */
        REPLACE,
        /** The collection is fully replaced only if target has now elements. */
        SET_IF_EMPTY,
        /** The map is replaced even if the copy source is empty. */
        ALWAYS_REPLACE,
    }

    /**
     * Defines the strategy for a map.
     */
    enum Map {
        /** No explicit overwrite strategy is set. */
        INHERIT,
        /** The map is fully replaced if the replacement is not empty. */
        FULL_REPLACE,
        /** The map is replaced even if the copy source is empty. */
        ALWAYS_REPLACE,
        /** The map is replaced only if the target map is empty. */
        SET_IF_EMPTY,
        /** The members of the copy source are added to the target map. Members with the same keys are replaced. */
        MERGE_KEYS,
        /** The members of the copy source are added to the target map. Members with the same keys are merged. Only valid for DSL elements. */
        MERGE_VALUES,
        /** The members of the copy source are added to the target map. Members with the existing keys are ignored. */
        ADD_MISSING
    }

    /**
     * Strategies for handling fields that are missing in the target object.
     */
    enum Missing {
        /** No explicit strategy is set. */
        INHERIT,
        /** Fail if the target field is missing. */
        FAIL,
        /** Silently ignore missing target fields. */
        IGNORE
    }
}
