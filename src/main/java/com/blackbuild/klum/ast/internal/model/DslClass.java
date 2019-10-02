package com.blackbuild.klum.ast.internal.model;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;

import java.util.Map;

import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_FINAL;
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
    private final KeyField keyField;

    private DslClass(ClassNode annotatedClass) {
        classNode = annotatedClass;
        annotation = CommonAstHelper.getAnnotation(annotatedClass, DSL_CONFIG_ANNOTATION);

        fields = initFields();
        keyField = KeyField.from(this);
    }

    private Map<String, FieldContainer> initFields() {
        return classNode.getFields()
                .stream()
                .collect(toMap(FieldNode::getName, FieldContainer::getOrCreate));
    }

    public DslClass getSuperClass() {
        return getOrCreate(classNode.getSuperClass());
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public AnnotationNode getAnnotation() {
        return annotation;
    }

    public Map<String, FieldContainer> getFields() {
        return fields;
    }

    public KeyField getKeyField() {
        return keyField;
    }

    public static DslClass getOrCreate(ClassNode type) {
        if (type == null)
            return null;

        DslClass result = type.getNodeMetaData(DslClass.class);

        if (result == null && CommonAstHelper.getAnnotation(type, DSL_CONFIG_ANNOTATION) != null) {
            result = new DslClass(type);
            type.setNodeMetaData(DslClass.class, result);
        }

        return result;
    }

    public static DslClass getOrFail(ClassNode type) {
        DslClass dslClass = type.getNodeMetaData(DslClass.class);

        if (dslClass == null)
            throw new IllegalStateException("Expected an existing DslClass instance");

        return dslClass;
    }

    public boolean isAbstract() {
        return (classNode.getModifiers() & ACC_ABSTRACT) != 0;
    }

    public boolean isFinal() {
        return (classNode.getModifiers() & ACC_FINAL) != 0;
    }

    public String getName() {
        return classNode.getName();
    }
}
