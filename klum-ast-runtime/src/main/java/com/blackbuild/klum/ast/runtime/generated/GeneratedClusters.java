/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast.runtime.generated;

import com.blackbuild.klum.ast.runtime.internal.layer3.ClusterModel;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Map;

/**
 * Generated-code linkage for Layer 3 Cluster accessors emitted by the DSL transformation.
 *
 * <p>This is not a supported handwritten client API or extension SPI. Generated Cluster accessors
 * use these query operations while the reflection and Builder-aware query mechanics remain internal.</p>
 */
public final class GeneratedClusters {

    private GeneratedClusters() {
    }

    /** Returns generated Cluster properties of the requested type, including null values. */
    public static <T> Map<String, T> $klum$getPropertiesOfType(Object container, Class<T> fieldType) {
        return ClusterModel.getPropertiesOfType(container, fieldType);
    }

    /** Returns generated Cluster properties of the requested type and annotation, including null values. */
    public static <T> Map<String, T> $klum$getPropertiesOfType(Object container, Class<T> fieldType,
                                                                Class<? extends Annotation> filter) {
        return ClusterModel.getPropertiesOfType(container, fieldType, filter);
    }

    /** Returns non-null generated Cluster properties of the requested type. */
    public static <T> Map<String, T> $klum$getNonEmptyPropertiesOfType(Object container, Class<T> fieldType) {
        return ClusterModel.getNonEmptyPropertiesOfType(container, fieldType);
    }

    /** Returns non-null generated Cluster properties of the requested type and annotation. */
    public static <T> Map<String, T> $klum$getNonEmptyPropertiesOfType(Object container, Class<T> fieldType,
                                                                        Class<? extends Annotation> filter) {
        return ClusterModel.getNonEmptyPropertiesOfType(container, fieldType, filter);
    }

    /** Returns generated Cluster collections with the requested element type. */
    public static <T> Map<String, Collection<T>> $klum$getCollectionsOfType(Object container, Class<T> fieldType) {
        return ClusterModel.getCollectionsOfType(container, fieldType);
    }

    /** Returns generated Cluster collections with the requested element type and annotation. */
    public static <T> Map<String, Collection<T>> $klum$getCollectionsOfType(Object container, Class<T> fieldType,
                                                                             Class<? extends Annotation> filter) {
        return ClusterModel.getCollectionsOfType(container, fieldType, filter);
    }
}
