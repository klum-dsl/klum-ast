package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.Default;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Implementations for generated instance methods.
 */
public class KlumInstanceProxy {

    public static final String NAME_OF_RW_FIELD_IN_MODEL_CLASS = "$rw";
    public static final String NAME_OF_PROXY_FIELD_IN_MODEL_CLASS = "$proxy";
    public static final ClassNode POSTAPPLY_ANNOTATION = make(PostApply.class);
    public static final ClassNode POSTCREATE_ANNOTATION = make(PostCreate.class);

    private final GroovyObject instance;
    private boolean skipPostCreate;
    private boolean skipPostApply;

    public KlumInstanceProxy(GroovyObject instance) {
        this.instance = instance;
    }

    public static KlumInstanceProxy getProxyFor(Object target) {
        return (KlumInstanceProxy) InvokerHelper.getAttribute(target, KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS);
    }

    protected GroovyObject getRwInstance() {
        return (GroovyObject) InvokerHelper.getAttribute(instance, KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS);
    }

    public Object getInstanceAttribute(String attributeName) {
        try {
            return DslHelper.getField(instance.getClass(), attributeName).get(instance);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public void setInstanceAttribute(String name, Object value) {
        try {
            DslHelper.getField(instance.getClass(), name).set(instance, value);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public Object getInstanceProperty(String name){
        Object value = makeReadOnly(getInstanceAttribute(name));

        if (DefaultTypeTransformation.castToBoolean(value))
            return value;

        Default defaultAnnotation = getField(name).getAnnotation(Default.class);
        if (defaultAnnotation == null)
            return value;

        if (!defaultAnnotation.field().isEmpty())
            return getInstanceProperty(defaultAnnotation.field());

        if (!defaultAnnotation.delegate().isEmpty())
            return getProxyFor(getInstanceProperty(defaultAnnotation.delegate())).getInstanceProperty(name);

        return ClosureHelper.invokeClosureWithDelegate(defaultAnnotation.code(), instance, instance);
    }

    private <T> T makeReadOnly(T value) {
        if (value instanceof Collection || value instanceof Map)
            return (T) InvokerHelper.invokeMethod(DefaultGroovyMethods.class, "asImmutable", value);
        return value;
    }

    private Field getField(String name) {
        return DslHelper.getField(instance.getClass(), name);
    }

    public Object apply(Map<String, Object> values, Closure<?> body) {
        Object rw = instance.getProperty(NAME_OF_RW_FIELD_IN_MODEL_CLASS);
        applyNamedParameters(rw, values);

        body.setDelegate(rw);
        body.setResolveStrategy(Closure.DELEGATE_ONLY);
        body.call();
        postApply();

        return instance;
    }

    // TODO: in RW Proxy
    public void applyNamedParameters(Object rw, Map<String, Object> values) {
        values.forEach((key, value) -> InvokerHelper.invokeMethod(rw, key, value));
    }

    public Object getKey() {
        Optional<String> keyField = DslHelper.getKeyField(instance.getClass());

        if (!keyField.isPresent())
            throw new AssertionError();

        return instance.getProperty(keyField.get());
    }

    public void postCreate() {
        if (!skipPostCreate)
            executeLifecycleMethod(PostCreate.class);
    }

    public void postApply() {
        if (!skipPostApply)
            executeLifecycleMethod(PostApply.class);
    }

    private void executeLifecycleMethod(Class<? extends Annotation> annotation) {
        Object rw = getRwInstance();
        DslHelper.getMethodsAnnotatedWith(rw.getClass(), annotation)
                .stream()
                .map(Method::getName)
                .distinct()
                .forEach(method -> InvokerHelper.invokeMethod(rw, method, null));
    }

    public void validate() {
        new Validator(instance).execute();
    }

    boolean getManualValidation() {
        return (boolean) InvokerHelper.getAttribute(instance, "$manualValidation");
    }

    public void setOwners(Object value) {
        DslHelper.getFieldsAnnotatedWith(instance.getClass(), Owner.class)
                .stream()
                .filter(field -> field.getType().isInstance(value))
                .filter(field -> getInstanceAttribute(field.getName()) == null)
                .forEach(field -> setInstanceAttribute(field.getName(), value));

        DslHelper.getMethodsAnnotatedWith(getRwInstance().getClass(), Owner.class)
                .stream()
                .filter(method -> method.getParameterTypes()[0].isInstance(value))
                .map(Method::getName)
                .distinct()
                .forEach(method -> getRwInstance().invokeMethod(method, value));
    }


}