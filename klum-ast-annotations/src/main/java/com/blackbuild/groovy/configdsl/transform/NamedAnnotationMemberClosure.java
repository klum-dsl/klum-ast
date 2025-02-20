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
package com.blackbuild.groovy.configdsl.transform;

import groovy.lang.Closure;

/**
 * Dummy annotation to mark a member field accepting a closure as a special value
 * (like Undefine, Ignored, etc.), i.e. the closure will not be executed, but a special
 * handling is done. This allows to mark the member as Class&lt;? extends Closure&gt; instead
 * of simply class, reducing the need for manual validation.
 * @param <T>
 */
public abstract class NamedAnnotationMemberClosure<T> extends Closure<T> {
    protected NamedAnnotationMemberClosure(Object owner, Object thisObject) {
        super(owner, thisObject);
    }

    public Object doCall(Object... ignored) {
        return null;
    }
    public Object doCall(Object ignored) {
        return null;
    }
    public Object doCall() {
        return null;
    }

}
