/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform

import java.lang.annotation.Annotation

import static groovy.lang.Closure.DELEGATE_FIRST

class TestHelper {

    static void delegatesToPointsTo(Annotation[] annotations, String className) {
        DelegatesTo annotation = annotations.find { it.annotationType() == DelegatesTo }
        assert annotation
        assert annotation.strategy() == DELEGATE_FIRST
        assert annotation.value().canonicalName == className
        assert annotation.genericTypeIndex() == -1
    }

    static void delegatesToPointsToDelegateTarget(Annotation[] annotations) {
        DelegatesTo annotation = annotations.find { it.annotationType() == DelegatesTo }
        assert annotation
        assert annotation.strategy() == DELEGATE_FIRST
        assert annotation.value() == DelegatesTo.Target
        assert annotation.genericTypeIndex() == 0
    }

    static void hasDelegatesToTargetAnnotation(Annotation[] annotations) {
        assert annotations.find { it.annotationType() == DelegatesTo.Target }
    }


}
