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

import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.generated.GeneratedOmittedProjectionSupport;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.MetaClassImpl;
import groovy.lang.MissingMethodException;
import groovy.util.DelegatingScript;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodRankHelper;
import org.codehaus.groovy.runtime.metaclass.ClosureMetaMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Corrects only terminal dynamic configuration dispatch, after Groovy has exhausted its fallbacks. */
public final class BuilderDispatchSupport {

    private BuilderDispatchSupport() {
    }

    private static final ThreadLocal<DispatchContext> CURRENT = new ThreadLocal<>();

    public static <T> T call(Closure<T> body) {
        return execute(body.getDelegate(), body.getClass(), "doCall", body::call);
    }

    public static Object run(DelegatingScript script, InternalKlumBuilder<?> builder) {
        script.setDelegate(builder);
        return execute(builder, script.getClass(), "run", script::run);
    }

    private static <T> T execute(Object delegate, Class<?> source, String entryMethod, Supplier<T> action) {
        DispatchContext previous = CURRENT.get();
        DispatchContext context = new DispatchContext(delegate);
        CURRENT.set(context);
        try {
            return action.get();
        } catch (MissingMethodException failure) {
            // Groovy 4/5's outer static fallback may erase the selected helper's stack. Its inner miss must
            // still agree with the operation requested at the existing Builder invocation instrumentation.
            if (context.method != null && !context.method.equals(failure.getMethod())) throw failure;
            throw correct(failure, delegate, source, entryMethod);
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    static void recordInvocation(Object receiver, String method) {
        DispatchContext context = CURRENT.get();
        if (context != null && context.delegate == receiver && !method.equals("methodMissing") && !method.equals("getClass"))
            context.method = method;
    }

    private static final class DispatchContext {
        private final Object delegate;
        private String method;

        private DispatchContext(Object delegate) {
            this.delegate = delegate;
        }
    }

    private static MissingMethodException correct(MissingMethodException failure, Object delegate,
                                                    Class<?> source, String entryMethod) {
        if (delegate == null) return failure;
        Class<?> contract = publicContract(delegate.getClass());
        if (contract == null || !hasDispatchOrigin(failure, delegate, source, entryMethod))
            return failure;
        MissingMethodException corrected = new BuilderMissingMethodException(failure.getMethod(), contract, failure.getArguments());
        corrected.setStackTrace(failure.getStackTrace());
        // Groovy unwraps GroovyRuntimeException causes; retain the dispatch stack without a cause.
        return corrected;
    }

    private static Class<?> publicContract(Class<?> implementation) {
        for (Class<?> contract : implementation.getInterfaces()) {
            if (KlumBuilder.class.isAssignableFrom(contract) && contract != KlumBuilder.class)
                return contract;
            Class<?> enclosing = contract.getEnclosingClass();
            if (enclosing != null && KlumBuilder.class.isAssignableFrom(enclosing))
                return contract;
        }
        return null;
    }

    // Require a Groovy dispatch frame and an immediate configuration entry point. A selected user method,
    // methodMissing handler, or helper frame means the exception belongs to that user code instead.
    private static boolean hasDispatchOrigin(MissingMethodException failure, Object delegate,
                                               Class<?> source, String entryMethod) {
        boolean builderReceiver = failure.getType() == delegate.getClass() && !failure.isStatic();
        boolean schemaReceiver = delegate instanceof InternalKlumBuilder<?> builder
                && failure.getType() == builder.getModelType() && failure.isStatic();
        if (!builderReceiver && !schemaReceiver)
            return false;
        boolean generatedBridge = false;
        boolean groovyDispatch = false;
        for (StackTraceElement frame : failure.getStackTrace()) {
            String name = frame.getClassName();
            if (name.equals(ClosureMetaMethod.class.getName())) return false;
            if (name.equals(OmittedProjectionSupport.class.getName())
                    || name.equals(GeneratedOmittedProjectionSupport.class.getName()))
                continue;
            if (name.startsWith("org.codehaus.groovy.") || name.startsWith("groovy.lang.")
                    || name.equals(DelegatingScript.class.getName())) {
                if ((name.equals(MetaClassImpl.class.getName()) || name.equals(GroovyObject.class.getName()))
                        && frame.getMethodName().startsWith("invoke")
                        && !frame.getMethodName().equals("invokeConstructor")) groovyDispatch = true;
                continue;
            }
            if (name.startsWith("java.") || name.startsWith("jdk.")) continue;
            if (name.equals(delegate.getClass().getName()) && frame.getMethodName().equals("methodMissing")
                    && isSyntheticMissingBridge(delegate.getClass())) {
                generatedBridge = true;
                continue;
            }
            if (frame.getMethodName().equals("doCall") && isNestedClosure(name, source)) {
                if (!groovyDispatch) return false;
                continue;
            }
            return groovyDispatch && name.equals(source.getName()) && frame.getMethodName().equals(entryMethod)
                    && (!schemaReceiver || generatedBridge)
                    && (!generatedBridge || !hasStaticFallback(delegate, failure));
        }
        return false;
    }

    private static boolean isNestedClosure(String name, Class<?> source) {
        try {
            Class<?> nested = Class.forName(name, false, source.getClassLoader());
            while (nested != null && Closure.class.isAssignableFrom(nested)) {
                nested = nested.getEnclosingClass();
                if (nested == source) return true;
            }
        } catch (ClassNotFoundException ignored) {
            // No trustworthy closure ancestry: preserve the original exception.
        }
        return false;
    }

    private static boolean hasStaticFallback(Object delegate, MissingMethodException failure) {
        return delegate instanceof InternalKlumBuilder<?> builder
                && InvokerHelper.getMetaClass(builder.getModelType())
                .getStaticMetaMethod(failure.getMethod(), failure.getArguments()) != null;
    }

    private static boolean isSyntheticMissingBridge(Class<?> type) {
        try {
            Method method = type.getDeclaredMethod("methodMissing", String.class, Object.class);
            return method.isSynthetic();
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    /** Groovy's interface MetaClass omits abstract methods, so rank the actual public contract instead. */
    private static final class BuilderMissingMethodException extends MissingMethodException {
        private static final long serialVersionUID = 1L;

        private BuilderMissingMethodException(String method, Class<?> type, Object[] arguments) {
            super(method, type, arguments);
        }

        @Override
        public String getMessage() {
            String candidates = Arrays.stream(getType().getMethods())
                    .filter(method -> !Modifier.isStatic(method.getModifiers()))
                    .filter(method -> MethodRankHelper.delDistance(getMethod(), method.getName())
                            <= MethodRankHelper.MAX_METHOD_SCORE)
                    .sorted(Comparator.comparingInt(this::score).thenComparing(Method::toGenericString))
                    .map(BuilderMissingMethodException::signature)
                    .distinct()
                    .limit(MethodRankHelper.MAX_RECOMENDATIONS)
                    .collect(Collectors.joining(", "));
            return "No signature of method: " + getType().getName() + "." + getMethod()
                    + "() is applicable for argument types: (" + InvokerHelper.toTypeString(getArguments(), 80)
                    + ") values: " + InvokerHelper.toArrayString(getArguments(), 80, true)
                    + (candidates.isEmpty() ? "" : "\nPossible solutions: " + candidates);
        }

        private int score(Method method) {
            return MethodRankHelper.delDistance(getMethod(), method.getName())
                    + Math.abs(method.getParameterCount() - getArguments().length) * MethodRankHelper.DL_DELETE;
        }

        private static String signature(Method method) {
            return method.getName() + "(" + Arrays.stream(method.getParameterTypes())
                    .map(Class::getTypeName).collect(Collectors.joining(", ")) + ")";
        }
    }

}
