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
