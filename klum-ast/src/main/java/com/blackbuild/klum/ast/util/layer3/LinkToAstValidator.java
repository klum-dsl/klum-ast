package com.blackbuild.klum.ast.util.layer3;

import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.Set;

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class LinkToAstValidator extends AstValidator {

    private Set<String> members;

    @Override
    protected void extraValidation() {
        members = annotation.getMembers().keySet();
        ownerAndOwnerTypeAreMutuallyExclusive();
        strategyInstanceNameNeedsOwnerOrOwnerType();
    }

    private void strategyInstanceNameNeedsOwnerOrOwnerType() {
        if (!members.contains("strategy")) return;
        if (members.contains("owner") || members.contains("ownerType")) return;
        addError("strategy INSTANCE_NAME needs owner or ownerType", annotation);
    }

    private void ownerAndOwnerTypeAreMutuallyExclusive() {
        if (members.contains("owner") && members.contains("ownerType"))
            addError("Only one of owner and ownerType is allowed", annotation);
    }
}
