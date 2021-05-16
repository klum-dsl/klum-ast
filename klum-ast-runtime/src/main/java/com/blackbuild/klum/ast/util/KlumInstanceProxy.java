package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.runtime.InvokerHelper;

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
    public static final String POSTAPPLY_ANNOTATION_METHOD_NAME = "$" + POSTAPPLY_ANNOTATION.getNameWithoutPackage();
    public static final ClassNode POSTCREATE_ANNOTATION = make(PostCreate.class);
    public static final String POSTCREATE_ANNOTATION_METHOD_NAME = "$" + POSTCREATE_ANNOTATION.getNameWithoutPackage();

    private GroovyObject instance;

    public KlumInstanceProxy(GroovyObject instance) {
        this.instance = instance;
    }

    public static KlumInstanceProxy getProxyFor(Object target) {
        return (KlumInstanceProxy) InvokerHelper.getAttribute(target, KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS);
    }

    protected Object getRwInstance() {
        return getInstanceAttribute(KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS);
    }

    private Object getInstanceAttribute(String nameOfRwFieldInModelClass) {
        return InvokerHelper.getAttribute(instance, nameOfRwFieldInModelClass);
    }

    public Object apply(Map<String, Object> values, Closure<?> body) {
        Object rw = instance.getProperty(NAME_OF_RW_FIELD_IN_MODEL_CLASS);
        applyNamedParameters(rw, values);

        body.setDelegate(rw);
        body.setResolveStrategy(Closure.DELEGATE_ONLY);
        body.call();
        InvokerHelper.invokeMethod(rw, POSTAPPLY_ANNOTATION_METHOD_NAME, null);

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

    public void validate() {
        new Validator(instance).execute();
    }

    boolean getManualValidation() {
        return (boolean) getInstanceAttribute("$manualValidation");
    }

}
