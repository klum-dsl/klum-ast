package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.groovy.configdsl.transform.Mutator;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;

import java.util.ArrayList;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.getAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.moveMethodFromModelToRWClass;

/**
 * Helper class to move mutators to RW class.
 */
public class MutatorsHandler {

    public static final ClassNode MUTATOR_ANNOTATION = ClassHelper.make(Mutator.class);
    private final ClassNode annotatedClass;

    MutatorsHandler(ClassNode annotatedClass) {
        this.annotatedClass = annotatedClass;
    }

    public void invoke() {
        List<MethodNode> mutators = findAllMutatorMethods();
        moveAllMethodsToRWClass(mutators);
    }

    private void moveAllMethodsToRWClass(List<MethodNode> mutators) {
        for (MethodNode method : mutators) {
            moveMethodFromModelToRWClass(method);
        }
    }

    private List<MethodNode> findAllMutatorMethods() {
        List<MethodNode> mutators = new ArrayList<MethodNode>();
        for (MethodNode method : annotatedClass.getMethods()) {
            AnnotationNode targetAnnotation = getAnnotation(method, MUTATOR_ANNOTATION);

            if (targetAnnotation != null)
                mutators.add(method);
        }
        return mutators;
    }
}
