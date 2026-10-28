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
package com.blackbuild.klum.ast.runtime.internal;

import com.blackbuild.klum.ast.runtime.KlumModelException;

/** Fresh-JVM, statically dispatched extraction probe; not a runtime API. */
public final class StructuredInputReaderProbe {
    private StructuredInputReaderProbe() { }

    public static void main(String[] arguments) throws Exception {
        boolean rejected = arguments[1].equals("rejected");
        try {
            Class<?> model = Class.forName(arguments[0], false, StructuredInputReaderProbe.class.getClassLoader());
            CompiledBuilderSchema plan = new InputContractResolver().read(model);
            if (rejected) throw new AssertionError("Expected missing-enrichment rejection");
            System.out.println("ACCEPTED:" + plan.entries().keySet());
        } catch (KlumModelException failure) {
            if (!rejected) throw failure;
            System.out.println("REJECTED:" + failure.getMessage());
        }
    }
}
