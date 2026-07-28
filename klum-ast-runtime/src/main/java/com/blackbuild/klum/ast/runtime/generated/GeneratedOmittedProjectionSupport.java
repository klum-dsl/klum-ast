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

import com.blackbuild.klum.ast.runtime.internal.OmittedProjectionSupport;

/**
 * Generated-code linkage for Builder-producing projections deliberately omitted by the DSL transformation.
 *
 * <p>This is not a supported handwritten client API or dynamic extension SPI. Generated Builder fallback
 * methods use it to retain their established diagnostic while catalog parsing and matching remain runtime-internal.</p>
 */
@SuppressWarnings("java:S100") // reserved generated-code ABI hook
public final class GeneratedOmittedProjectionSupport {

    private GeneratedOmittedProjectionSupport() {
    }

    /** Reports the established diagnostic for one generated omitted Builder-producing projection. */
    public static Object $klum$handle(Object receiver, String methodName, Object arguments, String encodedCatalog) {
        return OmittedProjectionSupport.handle(receiver, methodName, arguments, encodedCatalog);
    }
}
