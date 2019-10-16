package com.blackbuild.klum.ast.internal.model;

import com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.FieldNode;

/**
 * Represents a single field of a DslClass
 */
public class FieldContainer extends KlumField {

    private final AnnotationNode fieldAnnotation;

    public FieldContainer(FieldNode fieldNode) {
        super(fieldNode);
        fieldAnnotation = CommonAstHelper.getAnnotation(fieldNode, DSLASTTransformation.DSL_FIELD_ANNOTATION);
    }

    public AnnotationNode getFieldAnnotation() {
        return fieldAnnotation;
    }


}
