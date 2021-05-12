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
    public static Closure<?> createClosureInstance(Class<?> closureType) {
        if (!Closure.class.isAssignableFrom(closureType))
            throw new IllegalStateException(format("Expected a closure, but got %s instead.", closureType.getName()));

        return (Closure<?>) InvokerHelper.invokeConstructorOf(closureType, new Object[] {null, null});
    }
}
