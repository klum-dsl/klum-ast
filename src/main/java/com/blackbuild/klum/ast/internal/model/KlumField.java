package com.blackbuild.klum.ast.internal.model;

import org.codehaus.groovy.ast.FieldNode;

public abstract class KlumField {
    protected final FieldNode fieldNode;

    public KlumField(FieldNode fieldNode) {
        fieldNode.setNodeMetaData(FieldContainer.class, this);
        this.fieldNode = fieldNode;
    }

    public FieldNode getFieldNode() {
        return fieldNode;
    }

    public KlumClass getOwnerClass() {
        return KlumClass.getOrFail(fieldNode.getOwner());
    }
}
