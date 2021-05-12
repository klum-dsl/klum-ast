package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.Key;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.ast.ClassNode;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static java.util.Arrays.stream;
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

    public GroovyObject apply(Map<String, Object> values, Closure<?> body) {
        GroovyObject rw = (GroovyObject) instance.getProperty(NAME_OF_RW_FIELD_IN_MODEL_CLASS);
        applyNamedParameters(rw, values);

        body.setDelegate(rw);
        body.setResolveStrategy(Closure.DELEGATE_ONLY);
        body.call();
        rw.invokeMethod(POSTAPPLY_ANNOTATION_METHOD_NAME, new Object[0]);

        return instance;
    }

    // TODO: in RW Proxy
    public void applyNamedParameters(GroovyObject rw, Map<String, Object> values) {
        values.forEach(rw::invokeMethod);
    }

    public Object getKey() {
        Optional<Field> keyField = stream(instance.getClass().getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Key.class))
                .findFirst();

        if (!keyField.isPresent())
            throw new AssertionError();

        return instance.getProperty(keyField.get().getName());
    }

    public void validate() {
        new Validator(instance).execute();
    }


}
