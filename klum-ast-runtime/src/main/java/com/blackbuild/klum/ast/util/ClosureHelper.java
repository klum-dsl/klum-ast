/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;

import static java.lang.String.format;

public class ClosureHelper {

    private ClosureHelper() {}


    /**
     * Instantiates a closure. While this method accepts any class as argument, any non closure will
     * lead to an IllegalStateException.
     * @param closureType
     * @return
     */
    public static <T> Closure<T> createClosureInstance(Class<? extends Closure<T>> closureType) {
        if (!Closure.class.isAssignableFrom(closureType))
            throw new IllegalStateException(format("Expected a closure, but got %s instead.", closureType.getName()));

        return (Closure<T>) InvokerHelper.invokeConstructorOf(closureType, new Object[] {null, null});
    }

    public static <T> T invokeClosure(Class<? extends Closure<T>> closureType, Object... arguments) {
        Closure<T> closure = createClosureInstance(closureType);
        return closure.call(arguments);
    }

    /**
     * Instantiates the closure defined by the given type
     * @param closureType
     * @param delegate
     * @param arguments
     * @param <T>
     * @return
     */
    public static <T> T invokeClosureWithDelegate(Class<? extends Closure<T>> closureType, Object delegate, Object... arguments) {
        Closure<T> closure = createClosureInstance(closureType);
        return invokeClosureWithDelegate(closure, delegate, arguments);
    }

    public static <T> T invokeClosureWithDelegate(Closure<T> closure,  Object delegate, Object... arguments) {
        if (delegate != null) {
            closure.setResolveStrategy(Closure.DELEGATE_FIRST);
            closure.setDelegate(delegate);
        }
        return closure.call(arguments);
    }
}
