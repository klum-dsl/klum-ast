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
 * Opts a domain field annotation into external Builder creation.
 * Direct DSL fields participate in AutoCreate, AutoLink, Default or PostTree.
 * Only one direct creator may claim a field/phase, including built-in creation mechanisms.
 * Each invocation constructs a fresh public concrete handler with a public no-arg constructor.
 * Names and signatures are provisional until issue #867 qualification completes.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@Repeatable(LifecycleCreator.List.class)
public @interface LifecycleCreator {
    /** The existing AutoCreate, AutoLink, Default or PostTree lifecycle annotation class. */
    Class<? extends Annotation> phase();

    /** Handler whose resolved annotation parameter must equal this domain annotation. */
    // Class literals cannot express the relationship to the annotated annotation type; checked by compiler/runtime.
    @SuppressWarnings("rawtypes")
    Class<? extends LifecycleCreationHandler> handler();
    /** Repeatable declarations on one domain annotation, in container value order. */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.ANNOTATION_TYPE)
    @interface List {
        LifecycleCreator[] value();
    }
}
