package com.blackbuild.groovy.configdsl.transform.ast;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.expr.VariableExpression;

/**
 * Created by stephan on 07.04.2017.
 */
class StronglyTypingClosureParameterVisitor extends CodeVisitorSupport {
    private final String name;
    private final ClassNode type;

    public StronglyTypingClosureParameterVisitor(String name, ClassNode type) {
        this.name = name;
        this.type = type;
    }

    @Override
    public void visitVariableExpression(VariableExpression expression) {
        if (!expression.getName().equals(name))
            return;
        expression.setAccessedVariable(new VariableExpression(name, type));
    }
}
