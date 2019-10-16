package com.blackbuild.klum.ast.internal.model;

import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getElementNameForCollectionField;
import static com.blackbuild.klum.common.CommonAstHelper.getGenericsTypes;

public class CollectionField extends KlumField {
    CollectionField(FieldNode node) {
        super(node);
    }

    public static CollectionField from(FieldNode node) {
        if (CommonAstHelper.isCollection(node.getType()))
            return new CollectionField(node);
        return null;
    }

    String getElementName() {
        return getElementNameForCollectionField(fieldNode);
    }

    ClassNode getElementType() {
        return getGenericsTypes(fieldNode)[0].getType();
    }

}
