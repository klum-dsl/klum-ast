package com.blackbuild.klum.ast.internal.model;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;

import java.util.HashMap;
import java.util.Map;

import static com.blackbuild.klum.ast.internal.model.FieldFactory.toKlumField;
import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_FINAL;
import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Access Wrapper for a single DSL annotation
 */
public class KlumClass {

    public static final ClassNode DSL_CONFIG_ANNOTATION = make(DSL.class);
    private final ClassNode classNode;
    private final AnnotationNode annotation;
    private final Map<String, KlumField> fields = new HashMap<>();
    private final KeyField keyField;

    private KlumClass(ClassNode annotatedClass) {
        annotatedClass.setNodeMetaData(KlumClass.class, this);
        classNode = annotatedClass;
        annotation = CommonAstHelper.getAnnotation(annotatedClass, DSL_CONFIG_ANNOTATION);

        initFields();
        keyField = KeyField.fromOld(this);
    }

    private void determineFieldTypes() {
        for (FieldNode fieldNode : annotatedClass.getFields()) {
            storeFieldType(fieldNode);
            warnIfInvalid(fieldNode);
        }
    }


    private void initFields() {
        for (FieldNode fieldNode : classNode.getFields()) {
            fields.put(fieldNode.getName(), toKlumField(fieldNode));
        }
    }

    public KlumClass getSuperClass() {
        return getOrCreate(classNode.getSuperClass());
    }

    public ClassNode getClassNode() {
        return classNode;
    }

    public AnnotationNode getAnnotation() {
        return annotation;
    }

    public Map<String, KlumField> getFields() {
        return fields;
    }

    public KeyField getKeyField() {
        return keyField;
    }

    public static KlumClass getOrCreate(ClassNode type) {
        if (type == null)
            return null;

        KlumClass result = type.getNodeMetaData(KlumClass.class);

        if (result == null && CommonAstHelper.getAnnotation(type, DSL_CONFIG_ANNOTATION) != null) {
            result = new KlumClass(type);
            type.setNodeMetaData(KlumClass.class, result);
        }

        return result;
    }

    public static KlumClass getOrFail(ClassNode type) {
        KlumClass klumClass = type.getNodeMetaData(KlumClass.class);

        if (klumClass == null)
            throw new IllegalStateException("Expected an existing DslClass instance");

        return klumClass;
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
