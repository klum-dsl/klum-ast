package com.blackbuild.klum.ast.internal.model;

import com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;

/**
 * Represents a single field of a DslClass
 */
public class FieldContainer {

    private final FieldNode fieldNode;
    private final ClassNode owner;
    private final AnnotationNode annotation;

    public FieldContainer(FieldNode fieldNode) {
        this.fieldNode = fieldNode;
        this.owner = fieldNode.getOwner();
        annotation = CommonAstHelper.getAnnotation(fieldNode, DSLASTTransformation.DSL_FIELD_ANNOTATION);
    }

    public static FieldContainer create(FieldNode node) {
        return new FieldContainer(node);
    }
}
