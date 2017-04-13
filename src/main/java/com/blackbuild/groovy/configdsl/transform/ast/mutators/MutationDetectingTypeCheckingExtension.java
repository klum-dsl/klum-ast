package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import org.codehaus.groovy.transform.stc.AbstractTypeCheckingExtension;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;

/**
 * Created by stephan on 12.04.2017.
 */
public class MutationDetectingTypeCheckingExtension extends AbstractTypeCheckingExtension {

    public MutationDetectingTypeCheckingExtension(StaticTypeCheckingVisitor typeCheckingVisitor) {
        super(typeCheckingVisitor);
    }


}
