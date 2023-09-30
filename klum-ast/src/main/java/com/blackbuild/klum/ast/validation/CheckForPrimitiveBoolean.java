package com.blackbuild.klum.ast.validation;

import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.klum.cast.checks.impl.KlumCastCheck;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.FieldNode;

public class CheckForPrimitiveBoolean extends KlumCastCheck<Validate> {
    @Override
    protected void doCheck(AnnotationNode annotationToCheck, AnnotatedNode target) {
        if (((FieldNode) target).getType().equals(ClassHelper.boolean_TYPE))
            throw new IllegalStateException("Validation is not valid on 'boolean' fields, use 'Boolean' instead.");
    }

    @Override
    protected boolean isValidFor(AnnotatedNode target) {
        return target instanceof FieldNode;
    }
}
