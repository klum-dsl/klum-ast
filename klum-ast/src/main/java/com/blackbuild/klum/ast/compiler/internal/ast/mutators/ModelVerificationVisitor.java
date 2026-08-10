/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast.compiler.internal.ast.mutators;

import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.compiler.internal.ast.DSLASTTransformation;
import com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper;
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;
import org.codehaus.groovy.transform.stc.StaticTypesMarker;

import java.util.List;
import java.util.Set;

import static org.codehaus.groovy.syntax.Types.*;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isAssignableTo;

/**
 * Created by stephan on 12.04.2017.
 */
public class ModelVerificationVisitor extends StaticTypeCheckingVisitor {

    private static final ClassNode KLUM_BUILDER = make(KlumBuilder.class);
    private static final Set<String> ROOT_FACTORY_METHODS = Set.of("With", "One", "From");
    private int builderAnnotationClosureDepth;
    private int modelResultAnnotationClosureDepth;

    public ModelVerificationVisitor(SourceUnit unit, ClassNode node) {
        super(unit, node);
        extension.addHandler(new MutationDetectingTypeCheckingExtension(this));
    }

    @Override
    public void visitClosureExpression(ClosureExpression expression) {
        boolean builderAnnotationClosure = Boolean.TRUE.equals(expression.getNodeMetaData(
                DSLASTTransformation.BUILDER_ANNOTATION_CLOSURE_METADATA_KEY));
        boolean modelResultAnnotationClosure = Boolean.TRUE.equals(expression.getNodeMetaData(
                DSLASTTransformation.MODEL_RESULT_ANNOTATION_CLOSURE_METADATA_KEY));
        if (builderAnnotationClosure)
            builderAnnotationClosureDepth++;
        if (modelResultAnnotationClosure)
            modelResultAnnotationClosureDepth++;

        try {
            super.visitClosureExpression(expression);
        } finally {
            if (modelResultAnnotationClosure)
                modelResultAnnotationClosureDepth--;
            if (builderAnnotationClosure)
                builderAnnotationClosureDepth--;
        }

        if (!builderAnnotationClosure)
            return;

        ClassNode inferredReturnType = expression.getNodeMetaData(StaticTypesMarker.INFERRED_RETURN_TYPE);
        if (DslAstHelper.isDSLObject(inferredReturnType))
            expression.putNodeMetaData(
                    StaticTypesMarker.INFERRED_RETURN_TYPE,
                    DslAstHelper.getBuilderClassOf(inferredReturnType).getPlainNodeReference()
            );
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
        if (inBuilderClass())
            return;

        assertTargetIsNoModelField(inner);
    }

    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        super.visitBinaryExpression(expression);
        checkForIllegalAssignment(expression);
        checkForCompletedModelInstanceofOnBuilder(expression);
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression expression) {
        super.visitMethodCallExpression(expression);
        checkForNestedRootFactory(expression);
    }

    private void checkForNestedRootFactory(MethodCallExpression expression) {
        String factoryMethod = expression.getMethodAsString();
        if (!isBuilderPhaseCode() || isInModelResultAnnotationClosure() || isInStaticMethod()
                || factoryMethod == null || !ROOT_FACTORY_METHODS.contains(factoryMethod))
            return;

        if (!(expression.getObjectExpression() instanceof PropertyExpression factoryExpression)
                || !DSLASTTransformation.FACTORY_FIELD_NAME.equals(factoryExpression.getPropertyAsString())
                || !(factoryExpression.getObjectExpression() instanceof ClassExpression modelExpression)
                || !DslAstHelper.isDSLObject(modelExpression.getType()))
            return;

        String modelName = modelExpression.getType().getNameWithoutPackage();
        addError(String.format(
                "%1$s.Create.%2$s starts a completed-model root factory. In Builder-phase code use %1$s.Create.AsBuilder.%2$s and attach the returned Builder to an owned relationship.",
                modelName,
                factoryMethod), expression);
    }

    private void checkForCompletedModelInstanceofOnBuilder(BinaryExpression expression) {
        if (!isBuilderPhaseCode() || expression.getOperation().getType() != KEYWORD_INSTANCEOF)
            return;
        if (!(expression.getRightExpression() instanceof ClassExpression modelTypeExpression)
                || !DslAstHelper.isDSLObject(modelTypeExpression.getType()))
            return;

        ClassNode inferredType = getType(expression.getLeftExpression());
        if (!isAssignableTo(inferredType, KLUM_BUILDER))
            return;

        addError(String.format(
                "Cannot use 'instanceof %s' on '%s' in Builder-phase code: the relationship value is a Builder before materialization (inferred Builder type: %s), not a completed DSL Object. See the Builder-first migration guidance: docs/user/Builder-First-Migration.md.",
                modelTypeExpression.getType().getNameWithoutPackage(),
                expression.getLeftExpression().getText(),
                inferredType.getName()), expression);
    }

    private boolean isBuilderPhaseCode() {
        return inBuilderClass() || builderAnnotationClosureDepth > 0;
    }

    private boolean isInStaticMethod() {
        MethodNode currentMethod = typeCheckingContext.getEnclosingMethod();
        return currentMethod != null && currentMethod.isStatic();
    }

    private boolean isInModelResultAnnotationClosure() {
        return modelResultAnnotationClosureDepth > 0;
    }

    private void checkForIllegalAssignment(BinaryExpression expression) {
        if (inBuilderClass())
            return; // don't validate Builder class methods

        MethodNode currentMethod = typeCheckingContext.getEnclosingMethod();

        if (currentMethod == null)
            return; // code not inside a method (validation closure?)

        if ("<init>".equals(currentMethod.getName()))
            return;

        if (currentMethod.isStatic())
            return; // ignore factory methods
        if ((currentMethod.getModifiers() & Opcodes.ACC_SYNTHETIC) != 0)
            return;

        if (WriteAccessHelper.getWriteAccessTypeForMethodOrField(currentMethod).isPresent())
            return; // ignore methods already marked as write access methods

        if (ofType(expression.getOperation().getType(), ASSIGNMENT_OPERATOR)) {
            assertTargetIsNoModelField(expression.getLeftExpression());
        }
    }

    private boolean inBuilderClass() {
        return typeCheckingContext.getEnclosingClassNode().getName().endsWith(DSLASTTransformation.BUILDER_CLASS_SUFFIX);
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
        if (DslAstHelper.getFieldType(fieldNode) == FieldType.TRANSIENT)
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

        return WriteAccessHelper.getWriteAccessTypeForMethodOrField(currentMethod).isPresent();
    }

    private boolean isDslType(ClassNode classNode) {
        return !classNode.getAnnotations(DSLASTTransformation.DSL_CONFIG_ANNOTATION).isEmpty();
    }
}
