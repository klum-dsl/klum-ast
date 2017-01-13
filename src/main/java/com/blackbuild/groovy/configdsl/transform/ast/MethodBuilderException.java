package com.blackbuild.groovy.configdsl.transform.ast;

import org.codehaus.groovy.ast.ASTNode;

public class MethodBuilderException extends RuntimeException {
    private final ASTNode node;

    public MethodBuilderException(String message, ASTNode node) {
        super(message);
        this.node = node;
    }

    public ASTNode getNode() {
        return node;
    }
}
