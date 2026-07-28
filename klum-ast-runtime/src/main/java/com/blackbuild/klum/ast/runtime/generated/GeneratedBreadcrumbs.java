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

import com.blackbuild.klum.ast.runtime.internal.BreadCrumbVerbInterceptor;
import com.blackbuild.klum.ast.runtime.internal.process.BreadcrumbCollector;
import groovy.lang.Closure;

/**
 * Generated-code linkage for construction-path instrumentation emitted by the DSL transformation.
 *
 * <p>This is not a supported handwritten client API or extension SPI. Generated factories and Builders
 * register their verb provider here, while generated collection and Cluster closures use the scoped
 * breadcrumb operation. The collector and interceptor mechanics remain runtime-internal.</p>
 */
@SuppressWarnings("java:S100") // reserved generated-code ABI hooks
public final class GeneratedBreadcrumbs {

    private GeneratedBreadcrumbs() {
    }

    /** Registers one generated factory or Builder class as a construction-path verb provider. */
    public static void $klum$registerVerbProvider(Class<?> type) {
        BreadCrumbVerbInterceptor.registerClass(type);
    }

    /** Executes a generated nested factory closure inside one construction-path breadcrumb scope. */
    public static <T> T $klum$withBreadcrumb(String verb, String type, String qualifier, Closure<T> action) {
        return BreadcrumbCollector.withBreadcrumb(verb, type, qualifier, action);
    }
}
