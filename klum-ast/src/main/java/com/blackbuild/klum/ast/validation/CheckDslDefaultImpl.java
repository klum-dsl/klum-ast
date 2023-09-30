package com.blackbuild.klum.ast.validation;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.Undefined;
import com.blackbuild.klum.ast.util.DslHelper;
import com.blackbuild.klum.cast.checks.impl.KlumCastCheck;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.klum.common.CommonAstHelper.isAssignableTo;

public class CheckDslDefaultImpl extends KlumCastCheck<DSL> {
    @Override
    protected void doCheck(AnnotationNode annotationToCheck, AnnotatedNode target) {
        Class<?> defaultImplClass = validatorAnnotation.defaultImpl();
        if (defaultImplClass == Undefined.class) return;
        ClassNode defaultImpl = ClassHelper.make(defaultImplClass);
        if (!isAssignableTo(defaultImpl, (ClassNode) target))
            throw new IllegalStateException("defaultImpl must be a subtype of the annotated class!");
        if (!isDSLObject(defaultImpl))
            throw new IllegalStateException("defaultImpl must be a DSLObject!");
        if (!DslHelper.isInstantiable(defaultImplClass))
            throw new IllegalStateException("defaultImpl must be instantiable!");
    }
}
