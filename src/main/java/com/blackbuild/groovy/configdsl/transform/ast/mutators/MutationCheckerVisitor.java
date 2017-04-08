package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import com.blackbuild.groovy.configdsl.transform.Mutator;
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

        List<String> leftMostTargets = getLeftMostTargetName(expression.getLeftExpression());

        for (String target : leftMostTargets) {
            if (target.equals("this") || currentScope.isReferencedClassVariable(target))
                addError("Assigning a value to a an element of a model is only allowed in Mutator methods: " + expression.getText(), expression);
        }
    }

    private List<String> getLeftMostTargetName(Expression expression) {
        if (expression instanceof VariableExpression)
            return Collections.singletonList(((VariableExpression) expression).getName());

        if (expression instanceof MethodCallExpression)
            return getLeftMostTargetName(((MethodCallExpression) expression).getObjectExpression());

        if (expression instanceof PropertyExpression)
            return getLeftMostTargetName(((PropertyExpression) expression).getObjectExpression());

        if (expression instanceof BinaryExpression)
            return getLeftMostTargetName(((BinaryExpression) expression).getLeftExpression());

        if (expression instanceof CastExpression)
            return getLeftMostTargetName(((CastExpression) expression).getExpression());

        if (expression instanceof TupleExpression) {
            List<String> result = new ArrayList<String>();
            for (Iterator<Expression> it = ((TupleExpression) expression).iterator(); it.hasNext(); ) {
                result.addAll(getLeftMostTargetName(it.next()));
            }
            return result;
        }

        addError("Unknown expression found as left side of BinaryExpression: " + expression.toString(), expression);
        return Collections.emptyList();
    }

    private boolean isMutatingBinaryOperation(Token operation) {
        if (operation.getText().endsWith("=")) return true;
        if (operation.getText().equals("<<")) return true;
        return false;
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression call) {
        super.visitMethodCallExpression(call);
    }

}
