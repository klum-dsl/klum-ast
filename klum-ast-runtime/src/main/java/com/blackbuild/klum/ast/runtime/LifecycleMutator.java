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
package com.blackbuild.klum.ast.runtime;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Repeatable;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opts a domain field annotation into external Builder mutation.
 * The LP-1 qualification supports only direct DSL fields in AutoLink; other phases remain unqualified.
 * Repeated mutations execute in declaration order, or explicit List value order.
 * Relative order of a singular marker mixed with List, and of different domain annotations, is unspecified.
 * Each invocation constructs a fresh public concrete handler with a public no-arg constructor.
 * Names and signatures are provisional until issue #867 qualification completes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@Repeatable(LifecycleMutator.List.class)
public @interface LifecycleMutator {
    /** The existing lifecycle annotation class; LP-1 supports AutoLink only. */
    Class<? extends Annotation> phase();

    /** Handler whose resolved annotation parameter must equal this domain annotation. */
    // Class literals cannot express the relationship to the annotated annotation type; checked by compiler/runtime.
    @SuppressWarnings("rawtypes")
    Class<? extends LifecycleMutationHandler> handler();
    /** Repeatable declarations on one domain annotation, in container value order. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @interface List {
        LifecycleMutator[] value();
    }
}
