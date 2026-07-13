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
package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.WriteAccess;
import com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation;
import com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.VariableExpression;

import java.util.ArrayList;
import java.util.Optional;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_CONFIG_ANNOTATION;
import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_FIELD_ANNOTATION;
import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static com.blackbuild.klum.common.CommonAstHelper.getNullSafeClassMember;
import static groovyjarjarasm.asm.Opcodes.ACC_PROTECTED;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Helper class to move mutating methods to RW class.
 */
public class WriteAccessMethodsMover {

    private static final String NO_MUTATOR_KEY = WriteAccessMethodsMover.class.getName() + ".noMutator";
    private final ClassNode annotatedClass;

    public WriteAccessMethodsMover(ClassNode annotatedClass) {
        this.annotatedClass = annotatedClass;
    }

    static void downgradeToProtected(MethodNode method) {
        int modifiers = (method.getModifiers() & ~ACC_PUBLIC) | ACC_PROTECTED;
        method.setModifiers(modifiers);
    }

    static void moveMethodFromModelToRWClass(MethodNode method) {
        ClassNode declaringClass = method.getDeclaringClass();
        ClassNode rwClass = declaringClass.getNodeMetaData(DSLASTTransformation.RWCLASS_METADATA_KEY);
        retargetOwnerParameters(method);
        retargetVirtualFieldParameter(method);
        retargetFieldVariables(method, rwClass);
        declaringClass.removeMethod(method);
        // if method is public, it will already have been added by delegateTo, replace it again
        CommonAstHelper.replaceMethod(rwClass, method);
    }

    private static void retargetOwnerParameters(MethodNode method) {
        if (method.getAnnotations(make(Owner.class)).isEmpty())
            return;
        for (Parameter parameter : method.getParameters()) {
            if (DslAstHelper.isDSLObject(parameter.getType()))
                parameter.setType(DslAstHelper.getRwClassOf(parameter.getType()).getPlainNodeReference());
        }
    }

    private static void retargetVirtualFieldParameter(MethodNode method) {
        AnnotationNode fieldAnnotation = getAnnotation(method, DSL_FIELD_ANNOTATION);
        if (fieldAnnotation == null || method.getParameters().length != 1)
            return;

        Parameter parameter = method.getParameters()[0];
        ClassNode effectiveType = getNullSafeClassMember(fieldAnnotation, "defaultImpl", null);
        if (effectiveType == null)
            effectiveType = getNullSafeClassMember(
                    getAnnotation(parameter.getType(), DSL_CONFIG_ANNOTATION),
                    "defaultImpl",
                    parameter.getType()
            );
        if (DslAstHelper.isDSLObject(effectiveType))
            parameter.setType(DslAstHelper.getRwClassOf(effectiveType).getPlainNodeReference());
    }

    private static void retargetFieldVariables(MethodNode method, ClassNode rwClass) {
        method.getCode().visit(new CodeVisitorSupport() {
            @Override
            public void visitVariableExpression(VariableExpression expression) {
                Variable accessedVariable = expression.getAccessedVariable();
                FieldNode builderField = rwClass.getField(expression.getName());
                if (builderField != null && (accessedVariable instanceof FieldNode || accessedVariable instanceof DynamicVariable)) {
                    expression.setAccessedVariable(builderField);
                    expression.setType(builderField.getType());
                }
                super.visitVariableExpression(expression);
            }
        });
    }

    public void invoke() {
        moveAllDeclaredMutatorMethodsToRWClass();
        //createSyntheticDelegatorsForAllProtectedMethodsOfModel();
    }

    private void moveAllDeclaredMutatorMethodsToRWClass() {
        // create copy of the list since we modify it on the go
        new ArrayList<>(annotatedClass.getMethods())
                .forEach(this::ifMutatorMoveToRWClass);
    }

    private void ifMutatorMoveToRWClass(MethodNode method) {
        if (isExcluded(method)) return;

        Optional<WriteAccess.Type> writeType = WriteAccessHelper.getWriteAccessTypeForMethodOrField(method);

        if (writeType.isEmpty()) return;

        if (writeType.get() == WriteAccess.Type.LIFECYCLE)
            downgradeToProtected(method);

        moveMethodFromModelToRWClass(method);
    }

    private boolean isExcluded(MethodNode method) {
        return method.getNodeMetaData(NO_MUTATOR_KEY) == Boolean.TRUE;
    }

    public static void markAsNoMutator(MethodNode method) {
        method.putNodeMetaData(NO_MUTATOR_KEY, Boolean.TRUE);
    }

    //    static class ReplaceDirectAccessWithSettersVisitor extends CodeVisitorSupport {
//
//        private MethodNode method;
//
//        ReplaceDirectAccessWithSettersVisitor(MethodNode method) {
//            this.method = method;
//        }
//
//        @Override
//        public void visitExpressionStatement(ExpressionStatement statement) {
//            Expression expression = statement.getExpression();
//            if (!(expression instanceof BinaryExpression))
//                return;
//
//            BinaryExpression binaryExpression = (BinaryExpression) expression;
//
//            if (!"=".equals(binaryExpression.getOperation().getText()))
//                return;
//
//            Expression target = binaryExpression.getLeftExpression();
//
//            String targetFieldName = null;
//            if (target instanceof VariableExpression) {
//                String expressionTarget = ((VariableExpression) target).getName();
//                if (owningClass.getField(expressionTarget) != null)
//                    targetFieldName = expressionTarget;
//            }
//
//
//
//
//        }
//
//    }
}
