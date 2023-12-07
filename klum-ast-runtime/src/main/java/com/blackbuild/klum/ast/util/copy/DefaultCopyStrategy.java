package com.blackbuild.klum.ast.util.copy;

public class DefaultCopyStrategy extends CompositeCopyStrategy {

    public static final CopyStrategy INSTANCE = new DefaultCopyStrategy();

    public DefaultCopyStrategy() {
        super(FullOverwriteCopyStrategy.SINGLE_VALUE, FullOverwriteCopyStrategy.COLLECTION, FullOverwriteCopyStrategy.MAP);
    }
}
