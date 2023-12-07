package com.blackbuild.klum.ast.util.copy;

/**
 * Helm like copy strategy. Objects are replaced, Maps are merged and Collections are replaced.
 */
public class HelmCopyStrategy extends CompositeCopyStrategy {

    public HelmCopyStrategy() {
        super(FullOverwriteCopyStrategy.SINGLE_VALUE, FullOverwriteCopyStrategy.COLLECTION, MergeCopyStrategy.MAP);
    }

}
