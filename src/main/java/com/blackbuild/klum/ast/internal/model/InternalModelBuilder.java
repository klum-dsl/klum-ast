package com.blackbuild.klum.ast.internal.model;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * Prepares the internal model for the other transformation steps.
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class InternalModelBuilder extends AbstractASTTransformation {

    public void visit(ASTNode[] astNodes, SourceUnit sourceUnit) {
        init(astNodes, sourceUnit);
        if (!((AnnotationNode) astNodes[0]).getClassNode().equals(KlumClass.DSL_CONFIG_ANNOTATION))
            addError("Internal Error: Expected DSL annotation", astNodes[0]);
        if (!(astNodes[1] instanceof ClassNode))
            addError("Internal Error: Expected ClassNode", astNodes[1]);

        KlumClass.getOrCreate((ClassNode) astNodes[1]);
    }
}