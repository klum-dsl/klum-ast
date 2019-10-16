package com.blackbuild.klum.ast.internal.model;

import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;

import java.util.List;
import java.util.stream.Collectors;

import static com.blackbuild.klum.common.CommonAstHelper.addCompileError;


final class ModelHelper {

    private ModelHelper() {
        // helper methods only
    }

    static List<FieldNode> getFieldsAnnotatedBy(ClassNode classNode, ClassNode annotation) {
        return classNode.getFields().stream()
                .filter(it -> hasAnnotation(it, annotation))
                .collect(Collectors.toList());
    }

    static FieldNode getSingleFieldAnnotatedBy(ClassNode classNode, ClassNode annotation) {
        List<FieldNode> fields = getFieldsAnnotatedBy(classNode, annotation);
        if (fields.isEmpty())
            return null;
        if (fields.size() > 1)
            addCompileError(
                    classNode.getModule().getContext(),
                    String.format("Class '%s' has more than one field annotated with '%s', expected at most one",
                            classNode.getName(), annotation.getName()),
                    fields.get(1)
            );

        return fields.get(0);
    }

    static boolean hasAnnotation(FieldNode field, ClassNode annotation) {
        return !field.getAnnotations(annotation).isEmpty();
    }

    static <T> T compilerError(String message, FieldNode node) {
        CommonAstHelper.addCompileError(message, node);
        return null;
    }
}
