package com.blackbuild.klum.ast.internal.model;


import com.blackbuild.groovy.configdsl.transform.Key;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;

import static com.blackbuild.klum.common.CommonAstHelper.addCompileError;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Represents the key field of a dsl class.
 */
public class KeyField extends KlumField {

    public static final ClassNode KEY_ANNOTATION = make(Key.class);
    private final FieldNode field;

    public KeyField(FieldNode field) {
        super(field);
        this.field = field;
        validateKeyType();
    }

    private void validateKeyType() {
        if (!field.getType().equals(STRING_TYPE))
            addCompileError("Keyfields must be Strings", field);
    }

    public FieldNode getField() {
        return field;
    }

    public static KeyField from(FieldNode node) {
        if (ModelHelper.hasAnnotation(node, KEY_ANNOTATION))
            return new KeyField(node);
        else
            return null;
    }

    public static KeyField fromOld(KlumClass owner) {
        ClassNode classNode = owner.getClassNode();

        FieldNode determinedKeyField = ModelHelper.getSingleFieldAnnotatedBy(classNode, KEY_ANNOTATION);
        KlumClass superClass = owner.getSuperClass();

        if (superClass == null)
            return determinedKeyField != null ? new KeyField(determinedKeyField) : null;

        KeyField superClassKeyField = superClass.getKeyField();
        if (determinedKeyField == null)
            return superClassKeyField;

        if (superClassKeyField != null) {
            addCompileError(String.format("Class '%s' defines a key field '%s', but its ancestor '%s' already defines the key '%s'",
                    classNode.getName(), determinedKeyField.getName(), superClassKeyField.getOwnerClass().getName(), superClassKeyField.getName()),
                    determinedKeyField);
            return null;
        }

        if (!superClass.isAbstract()) {
            addCompileError(String.format("All non abstract classes must be either keyed or non-keyed. '%s' has a key '%s', while its non-abstract superclass '%s' has none",
                    classNode.getName(), determinedKeyField.getName(), superClass.getClassNode().getName()),
                    determinedKeyField);
            return null;
        }
        return new KeyField(determinedKeyField);
    }

    private String getName() {
        return field.getName();
    }

}
