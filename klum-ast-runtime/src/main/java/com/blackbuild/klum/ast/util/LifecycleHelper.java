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
package com.blackbuild.klum.ast.util;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Helper class that encapsulates lifecycle relevant methods. This reduces the complexity of KlumInstanceProxy.
 */
public class LifecycleHelper {

    private LifecycleHelper() {
        // static only
    }

    public static void executeLifecycleMethods(KlumInstanceProxy proxy, Class<? extends Annotation> annotation) {
        Object rw = proxy.getRwInstance();
        DslHelper.getMethodsAnnotatedWith(rw.getClass(), annotation)
                .map(Method::getName)
                .distinct()
                .forEach(method -> InvokerHelper.invokeMethod(rw, method, null));
        executeLifecycleClosures(proxy, annotation);
    }

    public static void executeLifecycleClosures(KlumInstanceProxy proxy, Class<? extends Annotation> annotation) {
        DslHelper.getFieldsAnnotatedWith(proxy.getDSLInstance().getClass(), annotation)
                .filter(field -> field.getType().equals(Closure.class))
                .map(Field::getName)
                .forEach(name -> executeLifecycleClosure(proxy, name));
    }

    private static void executeLifecycleClosure(KlumInstanceProxy proxy, String name) {
        Closure<?> closure = proxy.getInstanceAttribute(name);
        ClosureHelper.invokeClosureWithDelegateAsArgument(closure, proxy.getRwInstance());
        proxy.setInstanceAttribute(name, null);
    }

}
