package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import com.blackbuild.groovy.configdsl.transform.Mutator;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Token;

import java.util.*;

/**
 * Validates that methods do not change the class.
 */
public class MutationCheckerVisitor extends ClassCodeVisitorSupport {
    public static final ClassNode MUTATOR_ANNOTATION = ClassHelper.make(Mutator.class);

    // .*=

    // <<
    // --,++

    // a[x] = ...
    // add, put, addAll, clear, putAll, remove, removeAll, removeAt, retainAll, replaceAll, set, sort, removeIf,

    private static List<String> FORBIDDEN_BINARY_OPERATORS =
            Arrays.asList("<<" );
    private final SourceUnit sourceUnit;

    private MethodNode currentMethod;
    private Deque<MethodNode> methodStack = new LinkedList<MethodNode>();
    private Set<MethodNode> visitedMethods = new HashSet<MethodNode>();
    private VariableScope currentScope;

    public MutationCheckerVisitor(SourceUnit sourceUnit) {
        this.sourceUnit = sourceUnit;
    }

    @Override
    public SourceUnit getSourceUnit() {
        return sourceUnit;
    }

    @Override
    public void visitMethod(MethodNode node) {
        if ((node.getModifiers() & Opcodes.ACC_SYNTHETIC) != 0)
            return; // we do not check synthetic methods
        if (!node.getAnnotations(MUTATOR_ANNOTATION).isEmpty())
            return; // we do not check mutator methods

        try {
            methodStack.push(node);
            currentMethod = node;
            super.visitMethod(node);
        } finally {
            currentMethod = methodStack.pop();
        }
    }

    @Override
    public void visitBlockStatement(BlockStatement block) {
        VariableScope oldScope = currentScope;
        try {
            currentScope = block.getVariableScope();
            super.visitBlockStatement(block);
        } finally {
            currentScope = oldScope;
        }
    }

    @Override
    protected void visitConstructorOrMethod(MethodNode node, boolean isConstructor) {
        if (!isConstructor && !node.isSynthetic())
            visitClassCodeContainer(node.getCode());
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        if (!isMutatingBinaryOperation(expression.getOperation()))
            return;

        List<String> list = new ArrayList<String>();
        addLeftMostTargetToList(expression.getLeftExpression(), list);

        if (expression.getRightExpression() instanceof BinaryExpression)
            addLeftMostTargetToList(expression.getRightExpression(), list);

        for (String target : list) {
            if (target.equals("this") || currentScope.isReferencedClassVariable(target))
                addError("Assigning a value to a an element of a model is only allowed in Mutator methods: " + expression.getText()
                        + ". Maybe you forgot to annotate " + currentMethod.toString() + " with @Mutator?", expression);
        }
    }

    private void addLeftMostTargetToList(Expression expression, List<String> list) {
        if (expression instanceof VariableExpression)
            list.add(((VariableExpression) expression).getName());

        else if (expression instanceof MethodCallExpression)
            addLeftMostTargetToList(((MethodCallExpression) expression).getObjectExpression(), list);

        else if (expression instanceof PropertyExpression)
            addLeftMostTargetToList(((PropertyExpression) expression).getObjectExpression(), list);

        else if (expression instanceof BinaryExpression)
            addLeftMostTargetToList(((BinaryExpression) expression).getLeftExpression(), list);

        else if (expression instanceof CastExpression)
            addLeftMostTargetToList(((CastExpression) expression).getExpression(), list);

        else if (expression instanceof TupleExpression) {
            for (Expression value : (TupleExpression) expression) {
                addLeftMostTargetToList(value, list);
            }
        }
        else {
            addError("Unknown expression found as left side of BinaryExpression: " + expression.toString(), expression);
        }
    }

    private boolean isMutatingBinaryOperation(Token operation) {
        String operationText = operation.getText();

        if (operationText.equals("==")) return false;
        if (operationText.equals("!=")) return false;
        if (operationText.endsWith("=")) return true;
        if (operationText.equals("<<")) return true;
        return false;
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        super.visitMethodCallExpression(call);
    }

}
