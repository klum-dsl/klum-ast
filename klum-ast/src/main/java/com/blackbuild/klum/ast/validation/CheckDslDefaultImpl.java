package com.blackbuild.klum.ast.validation;

import com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper;
import com.blackbuild.klum.cast.KlumCastValidator;
import com.blackbuild.klum.cast.checks.impl.KlumCastCheck;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.klum.common.CommonAstHelper.getNullSafeClassMember;
import static com.blackbuild.klum.common.CommonAstHelper.isAssignableTo;

public class CheckDslDefaultImpl extends KlumCastCheck<KlumCastValidator> {
    @Override
    protected void doCheck(AnnotationNode annotationToCheck, AnnotatedNode target) {
        ClassNode defaultImpl = getNullSafeClassMember(annotationToCheck, "defaultImpl", null);
        if (defaultImpl == null) return;
        if (!isAssignableTo(defaultImpl, (ClassNode) target))
            throw new IllegalStateException("defaultImpl must be a subtype of the annotated class!");
        if (!isDSLObject(defaultImpl))
            throw new IllegalStateException("defaultImpl must be a DSLObject!");
        if (!DslAstHelper.isInstantiable(defaultImpl))
            throw new IllegalStateException("defaultImpl must be instantiable!");
    }
}
