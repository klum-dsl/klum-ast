package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.klum.ast.validation.AstValidator;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.transform.GroovyASTTransformation;

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class FieldAstValidator extends AstValidator {



    @Override
    protected void extraValidation() {
        baseTypeMustBeSubtypeOfFieldType();
    }

    private void baseTypeMustBeSubtypeOfFieldType() {
        ClassNode baseType = getMemberClassValue(annotation, "baseType");
        if (baseType == null) return;

        ClassNode fieldType = CommonAstHelper.getElementType((FieldNode) target);
        //fieldType.implementsInterface();
    }
}
