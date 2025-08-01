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

import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import groovy.lang.*;

import java.util.Set;

public class BreadCrumbVerbInterceptor implements Interceptor {

    private static final BreadCrumbVerbInterceptor INSTANCE = new BreadCrumbVerbInterceptor();

    private static final Set<String> IGNORED_METHODS = Set.of("getMethod", "getMetaClass", "setMetaClass", "invokeMethod", "getProperty", "setProperty", "ctor");

    @Override
    @SuppressWarnings("java:S3516") // only relevant if doInvoke() returns false, which it never does
    public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
        if (IGNORED_METHODS.contains(methodName)) return null;

        if (object.getClass().getName().endsWith("$_Factory")) {
            BreadcrumbCollector.getInstance().setVerb(DslHelper.shortNameFor(object.getClass().getDeclaringClass()) + "." + methodName);
        } else {
            BreadcrumbCollector.getInstance().setVerb(methodName);
        }

        return null;
    }

    @Override
    public Object afterInvoke(Object object, String methodName, Object[] arguments, Object result) {
        return result;
    }

    @Override
    public boolean doInvoke() {
        return true;
    }

    public static void registerClass(Class<?> clazz) {
        MetaClassRegistry registry = GroovySystem.getMetaClassRegistry();
        MetaClass existingMetaClass = registry.getMetaClass(clazz);
        if (existingMetaClass instanceof ProxyMetaClass) return;

        // Note that this explicitly violates the documentation of ProxyMetaClass, which states that it should
        // only be used as instance metaclass. However, a) the interceptor is stateless and b)
        // ClosureMetaClass explicitly checks for ProxyMetaClasses in the registry and only uses the MetaClass in
        // that instance, otherwise it completely bypasses the metaclass and calls the Method Object directly.
        // This holds true up to Groovy 4, need to reevaluate with Groovy 5
        ProxyMetaClass metaClass = new ProxyMetaClass(registry, clazz, existingMetaClass);
        metaClass.setInterceptor(INSTANCE);
        registry.setMetaClass(clazz, metaClass);
    }
}
