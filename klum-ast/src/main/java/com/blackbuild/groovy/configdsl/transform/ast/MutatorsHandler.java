/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.groovy.configdsl.transform.Mutator;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;

import java.util.Arrays;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Helper class to move mutators to RW class.
 */
public class MutatorsHandler {

    public static final ClassNode MUTATOR_ANNOTATION = ClassHelper.make(Mutator.class);
    private final ClassNode annotatedClass;
    private static final List<ClassNode> MUTATOR_ANNOTATIONS = Arrays.asList(MUTATOR_ANNOTATION, DSLASTTransformation.DSL_FIELD_ANNOTATION, DSLASTTransformation.OWNER_ANNOTATION);

    MutatorsHandler(ClassNode annotatedClass) {
        this.annotatedClass = annotatedClass;
    }

    private static boolean isMutatorMethod(MethodNode method) {
        return MUTATOR_ANNOTATIONS.stream().anyMatch(classNode -> DslAstHelper.hasAnnotation(method, classNode));
    }

    public void invoke() {
        moveAllDeclaredMutatorMethodsToRWClass();
        //createSyntheticDelegatorsForAllProtectedMethodsOfModel();
    }

    private void moveAllDeclaredMutatorMethodsToRWClass() {
        findAllDeclaredMutatorMethods().forEach(DslAstHelper::moveMethodFromModelToRWClass);
    }

    private List<MethodNode> findAllDeclaredMutatorMethods() {
        //TODO Don't allow private methods
        // Simply Make synthetic?

        // we explicitly create a list instead of passing the stream, since the consumer
        // modifies the method list
        return annotatedClass.getMethods().stream().filter(MutatorsHandler::isMutatorMethod).collect(toList());
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
