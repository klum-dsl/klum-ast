package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.ast.ClassNode;

import java.util.Map;

import static org.codehaus.groovy.ast.ClassHelper.make;

public class RwProxy {

    public static final String NAME_OF_RW_FIELD_IN_MODEL_CLASS = "$rw";
    public static final ClassNode POSTAPPLY_ANNOTATION = make(PostApply.class);
    public static final String POSTAPPLY_ANNOTATION_METHOD_NAME = "$" + POSTAPPLY_ANNOTATION.getNameWithoutPackage();
    public static final ClassNode POSTCREATE_ANNOTATION = make(PostCreate.class);
    public static final String POSTCREATE_ANNOTATION_METHOD_NAME = "$" + POSTCREATE_ANNOTATION.getNameWithoutPackage();

    public static GroovyObject apply(GroovyObject instance, Map<String, Object> values, Closure<?> body) {
        GroovyObject rw = (GroovyObject) instance.getProperty(NAME_OF_RW_FIELD_IN_MODEL_CLASS);
        applyNamedParameters(rw, values);

        body.setDelegate(rw);
        body.setResolveStrategy(Closure.DELEGATE_ONLY);
        body.call();
        rw.invokeMethod(POSTAPPLY_ANNOTATION_METHOD_NAME, new Object[0]);

        return instance;
    }

    public static void applyNamedParameters(GroovyObject rw, Map<String, Object> values) {
        values.forEach(rw::invokeMethod);
    }

}
