package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import com.blackbuild.groovy.configdsl.transform.WriteAccess;
import com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;

import java.util.Objects;
import java.util.Optional;

public class WriteAccessHelper {

    private WriteAccessHelper() {
        // helper class
    }

    public static Optional<WriteAccess.Type> getWriteAccessTypeForMethodOrField(AnnotatedNode fieldOrMethod) {
        if (fieldOrMethod == null) return Optional.empty();
        return fieldOrMethod.getAnnotations().stream()
                .map(WriteAccessHelper::getWriteAccessTypeForAnnotation)
                .filter(Objects::nonNull)
                .findAny();
    }

    private static WriteAccess.Type getWriteAccessTypeForAnnotation(AnnotationNode annotation) {
        if (!DslAstHelper.hasAnnotation(annotation.getClassNode(), WriteAccessMethodsMover.WRITE_ACCESS_ANNOTATION)) return null;

        // We need to use the class explicitly, since we cannot access the members of metaAnnotations directly
        // This is safe, since annotations are in a different module and thus already compiled
        Class<?> annotationClass = annotation.getClassNode().getTypeClass();
        return annotationClass.getAnnotation(WriteAccess.class).value();
    }
}
