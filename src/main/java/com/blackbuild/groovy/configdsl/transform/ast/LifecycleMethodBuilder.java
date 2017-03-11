package com.blackbuild.groovy.configdsl.transform.ast;

import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.SourceUnit;

import java.util.HashSet;
import java.util.Set;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.*;

/**
 * Helper class to create lifecycle methods for a given annotation
 */
class LifecycleMethodBuilder {
    private ClassNode annotationType;
    private MethodBuilder lifecycleMethod;
    private Set<String> alreadyHandled = new HashSet<String>();
    private ClassNode annotatedClass;
    private SourceUnit sourceUnit;

    LifecycleMethodBuilder(ClassNode annotatedClass, ClassNode annotationType, SourceUnit sourceUnit) {
        this.annotationType = annotationType;
        this.annotatedClass = annotatedClass;
        this.sourceUnit = sourceUnit;
    }

    void invoke() {
        lifecycleMethod = MethodBuilder.createPrivateMethod("$" + annotationType.getNameWithoutPackage());
        for (ClassNode level : getHierarchyOfDSLObjectAncestors(annotatedClass)) {
            addLifecycleMethodsForClass(level);
        }
        lifecycleMethod.addTo(annotatedClass);
    }

    private void addLifecycleMethodsForClass(ClassNode level) {
        for (MethodNode method : level.getMethods()) {
            AnnotationNode postApplyAnnotation = getAnnotation(method, annotationType);

            if (postApplyAnnotation == null)
                continue;

            assertMethodIsParameterless(method, sourceUnit);
            assertMethodIsNotPrivate(method, sourceUnit);

            addCallTo(method.getName());
        }
    }

    private void addCallTo(String method) {
        if (!alreadyHandled.contains(method)) {
            lifecycleMethod.callThis(method);
            alreadyHandled.add(method);
        }
    }
}
