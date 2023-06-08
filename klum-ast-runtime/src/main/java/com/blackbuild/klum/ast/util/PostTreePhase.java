package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.PostTree;
import com.blackbuild.klum.ast.process.KlumPhase;
import com.blackbuild.klum.ast.process.VisitingPhaseAction;

public class PostTreePhase extends VisitingPhaseAction {
    public PostTreePhase() {
        super(KlumPhase.POST_TREE);
    }

    @Override
    public void visit(String path, Object element) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(element);
        proxy.executeLifecycleMethods(PostTree.class);
    }
}
