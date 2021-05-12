/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2019 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation;
import com.blackbuild.groovy.configdsl.transform.ast.MutatorsHandler;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.AttributeExpression;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PostfixExpression;
import org.codehaus.groovy.ast.expr.PrefixExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;
import org.codehaus.groovy.transform.stc.StaticTypesMarker;

import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.FIELD_TYPE_METADATA;
import static org.codehaus.groovy.syntax.Types.ASSIGNMENT_OPERATOR;
import static org.codehaus.groovy.syntax.Types.LEFT_SQUARE_BRACKET;
import static org.codehaus.groovy.syntax.Types.ofType;

/**
 * Created by stephan on 12.04.2017.
 */
public class ModelVerificationVisitor extends StaticTypeCheckingVisitor {

    public ModelVerificationVisitor(SourceUnit unit, ClassNode node) {
        super(unit, node);
        extension.addHandler(new MutationDetectingTypeCheckingExtension(this));
    }

    @Override
    public void visitPostfixExpression(PostfixExpression expression) {
        super.visitPostfixExpression(expression);
        Expression inner = expression.getExpression();
        visitPrefixOrPostfixExpression(inner);
    }

    @Override
    public void visitPrefixExpression(PrefixExpression expression) {
        super.visitPrefixExpression(expression);
        Expression inner = expression.getExpression();
        visitPrefixOrPostfixExpression(inner);
    }

    private void visitPrefixOrPostfixExpression(Expression inner) {
        if (inRwClass())
            return;

        assertTargetIsNoModelField(inner);
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        super.visitBinaryExpression(expression);
        checkForIllegalAssignment(expression);
    }

    private void checkForIllegalAssignment(BinaryExpression expression) {
        if (inRwClass())
            return; // don't validate RW class methods

        MethodNode currentMethod = typeCheckingContext.getEnclosingMethod();

        if (currentMethod == null)
            return; // code not inside a method (validation closure?)

        if (currentMethod.getNodeMetaData(DSLASTTransformation.NO_MUTATION_CHECK_METADATA_KEY) != null)
            return;

        if ("<init>".equals(currentMethod.getName()))
            return;

        if (!currentMethod.isPublic())
            return; // check only public methods for now
        if (currentMethod.isStatic())
            return; // ignore factory methods
        if ((currentMethod.getModifiers() & Opcodes.ACC_SYNTHETIC) != 0)
            return;
        if (!currentMethod.getAnnotations(MutatorsHandler.MUTATOR_ANNOTATION).isEmpty())
            return;

        if (ofType(expression.getOperation().getType(), ASSIGNMENT_OPERATOR)) {
            assertTargetIsNoModelField(expression.getLeftExpression());
        }
    }

    private boolean inRwClass() {
        return typeCheckingContext.getEnclosingClassNode().getName().endsWith(DSLASTTransformation.RW_CLASS_SUFFIX);
    }

    private void assertTargetIsNoModelField(Expression target) {
        if (target instanceof VariableExpression) {
            VariableExpression variableExpression = (VariableExpression) target;
            assertVariableIsNoModelField(variableExpression.getAccessedVariable(), variableExpression);
        }
        else if (target instanceof PropertyExpression) {
            PropertyExpression propertyExpression = (PropertyExpression) target;
            if (propertyExpression.getObjectExpression().getText().equals("this")) {
                FieldNode targetedField = typeCheckingContext.getEnclosingClassNode().getField(propertyExpression.getPropertyAsString());
                assertVariableIsNoModelField(targetedField, propertyExpression);
            } else {
                assertTargetIsNoModelField(propertyExpression.getObjectExpression());
            }
        } else if (target instanceof BinaryExpression && ((BinaryExpression) target).getOperation().getType() == LEFT_SQUARE_BRACKET) {
            assertTargetIsNoModelField(((BinaryExpression) target).getLeftExpression());
        } else if (target instanceof TupleExpression) {
            for (Expression value : (TupleExpression) target) {
                assertTargetIsNoModelField(value);
            }
        }
    }

    private void assertVariableIsNoModelField(Variable variable, ASTNode expression) {
        if (!(variable instanceof FieldNode))
            return;
        FieldNode fieldNode = (FieldNode) variable;
        if (fieldNode.isStatic())
            return;
        if (fieldNode.getNodeMetaData(FIELD_TYPE_METADATA) == FieldType.TRANSIENT)
            return;
        addError(String.format("Assigning a value to a field of a model is only allowed in Mutator methods: %s. Maybe you forgot to annotate %s with @Mutator?", variable.getName(), typeCheckingContext.getEnclosingMethod().getText()), expression);
    }

    @Override // enhance visibility, since we need to use this method from Extension
    public List<MethodNode> findMethod(ClassNode receiver, String name, ClassNode... args) {
        return super.findMethod(receiver, name, args);
    }

    @Override
    protected void typeCheckAssignment(BinaryExpression assignmentExpression, Expression leftExpression, ClassNode leftExpressionType, Expression rightExpression, ClassNode inferredRightExpressionType) {

        if (isInMutatorMethod() && leftExpression instanceof PropertyExpression) {
            PropertyExpression leftPropertyExpression = (PropertyExpression) leftExpression;
            if (!"this".equals(leftPropertyExpression.getObjectExpression().getText())) {
                ClassNode targetType = getType(leftPropertyExpression.getObjectExpression());
                if (isDslType(targetType)) {
                    leftPropertyExpression.setObjectExpression(new AttributeExpression(leftPropertyExpression.getObjectExpression(), new ConstantExpression("$rw"), true));
                    leftPropertyExpression.removeNodeMetaData(StaticTypesMarker.READONLY_PROPERTY);
                    visitBinaryExpression(assignmentExpression);
                    return;
                }
            }
        }
        super.typeCheckAssignment(assignmentExpression, leftExpression, leftExpressionType, rightExpression, inferredRightExpressionType);
    }

    boolean isInMutatorMethod() {
        MethodNode currentMethod = typeCheckingContext.getEnclosingMethod();
        if (currentMethod == null)
            return false;
        return currentMethod.getAnnotations(MutatorsHandler.MUTATOR_ANNOTATION).size()
                    + currentMethod.getAnnotations(KlumInstanceProxy.POSTAPPLY_ANNOTATION).size()
                    + currentMethod.getAnnotations(KlumInstanceProxy.POSTCREATE_ANNOTATION).size()
                    > 0;
    }

    private boolean isDslType(ClassNode classNode) {
        return !classNode.getAnnotations(DSLASTTransformation.DSL_CONFIG_ANNOTATION).isEmpty();
    }
}
