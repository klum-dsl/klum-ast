package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.StaticTypesTransformation;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;

/**
 * Special version of StaticTypesTransformation. The main only is that this class
 * uses an enhanced subclass of the Visitor.
 */
@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
public class ModelVerifierTransformation extends StaticTypesTransformation {

    @Override
    protected StaticTypeCheckingVisitor newVisitor(SourceUnit unit, ClassNode node) {
        return new ModelVerificationVisitor(unit, node);
    }
}
