package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import com.blackbuild.groovy.configdsl.transform.WriteAccess;
import com.blackbuild.klum.cast.checks.impl.KlumCastCheck;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.MethodNode;

public class WriteAccessMethodCheck extends KlumCastCheck<WriteAccess> {
    @Override
    protected void doCheck(AnnotationNode annotationToCheck, AnnotatedNode target) {
        if (!(target instanceof MethodNode)) return;
        MethodNode method = (MethodNode) target;

        if (method.isPrivate())
            throw new IllegalStateException("Lifecycle methods must not be private!");

        if (validatorAnnotation.value() == WriteAccess.Type.LIFECYCLE && method.getParameters().length > 0)
            throw new IllegalStateException(String.format(
                    "Method %s.%s is annotated with @WriteAccess(LIFECYCLE) but has parameters",
                    method.getDeclaringClass().getName(),
                    method.getName()
            ));
    }
}
