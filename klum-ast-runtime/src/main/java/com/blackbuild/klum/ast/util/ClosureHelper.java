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
        Closure<T> closure = (Closure<T>) createClosureInstance(closureType);
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
