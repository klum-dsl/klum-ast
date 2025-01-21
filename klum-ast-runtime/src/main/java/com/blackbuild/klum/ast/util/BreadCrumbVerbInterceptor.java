package com.blackbuild.klum.ast.util;

import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import groovy.lang.*;

public class BreadCrumbVerbInterceptor implements Interceptor {

    private static final BreadCrumbVerbInterceptor INSTANCE = new BreadCrumbVerbInterceptor();

    @Override
    public Object beforeInvoke(Object object, String methodName, Object[] arguments) {
        BreadcrumbCollector.getInstance().setVerb(methodName);
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
