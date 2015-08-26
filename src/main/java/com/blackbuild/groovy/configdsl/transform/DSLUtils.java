package com.blackbuild.groovy.configdsl.transform;

import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.transform.AbstractASTTransformation;

import java.util.Deque;
import java.util.List;

import static org.codehaus.groovy.ast.ClassHelper.make;

public class DSLUtils {
    static final ClassNode DSL_CONFIG_ANNOTATION = make(DSLConfig.class);
    static final ClassNode DSL_FIELD_ANNOTATION = make(DSLField.class);

    static boolean isDSLObject(ClassNode classNode) {
        return getAnnotation(classNode, DSL_CONFIG_ANNOTATION) != null;
    }

    static Deque<ClassNode> getHierarchyOfDSLObjectAncestors(Deque<ClassNode> hierarchy, ClassNode target) {
        if (!isDSLObject(target)) return hierarchy;

        hierarchy.addFirst(target);
        return getHierarchyOfDSLObjectAncestors(hierarchy, target.getSuperClass());
    }

    static AnnotationNode getAnnotation(AnnotatedNode field, ClassNode type) {
        List<AnnotationNode> annotation = field.getAnnotations(type);
        return annotation.isEmpty() ? null : annotation.get(0);
    }

    static String getMethodNameForField(FieldNode fieldNode) {
        AnnotationNode fieldAnnotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);

        return getNullSafeMemberStringValue(fieldAnnotation, "value", fieldNode.getName());
    }

    static String getElementNameForCollectionField(FieldNode fieldNode) {
        AnnotationNode fieldAnnotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);

        String result = getNullSafeMemberStringValue(fieldAnnotation, "element", null);

        if (result != null && result.length() > 0) return result;

        String collectionMethodName = getMethodNameForField(fieldNode);

        if (collectionMethodName.endsWith("s"))
            return collectionMethodName.substring(0, collectionMethodName.length() - 1);

        return collectionMethodName;
    }

    static String getNullSafeMemberStringValue(AnnotationNode fieldAnnotation, String value, String name) {
        return fieldAnnotation == null ? name : AbstractASTTransformation.getMemberStringValue(fieldAnnotation, value, name);
    }
}
