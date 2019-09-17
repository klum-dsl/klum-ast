package com.blackbuild.klum.ast.internal.model;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;

import java.util.Map;

import static java.util.stream.Collectors.toMap;
import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Access Wrapper for a single DSL annotation
 */
public class DslClass {

    public static final ClassNode DSL_CONFIG_ANNOTATION = make(DSL.class);
    private final ClassNode classNode;
    private final AnnotationNode annotation;
    private final Map<String, FieldContainer> fields;


    public DslClass(ClassNode annotatedClass) {
        classNode = annotatedClass;
        annotation = CommonAstHelper.getAnnotation(annotatedClass, DSL_CONFIG_ANNOTATION);

        fields = initFields();
    }

    private Map<String, FieldContainer> initFields() {
        return classNode.getFields()
                .stream()
                .collect(toMap(FieldNode::getName, FieldContainer::create));
    }

    public DslClass getSuperClass() {
        return getDslClass(classNode.getSuperClass());
    }

    public FieldNode getKeyField() {
        return null;
    }



    public ClassNode getClassNode() {
        return classNode;
    }

    public AnnotationNode getAnnotation() {
        return annotation;
    }

    public static DslClass getDslClass(ClassNode type) {
        if (type == null)
            return null;

        DslClass existingClass = type.getNodeMetaData(DslClass.class);

        if (existingClass != null)
            return existingClass;

        if (CommonAstHelper.getAnnotation(type, DSL_CONFIG_ANNOTATION) != null)
            return new DslClass(type);

        return null;
    }
}
