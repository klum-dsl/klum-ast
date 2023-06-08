/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.Mutator;
import com.blackbuild.groovy.configdsl.transform.WriteAccess;
import com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation;
import com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.SourceUnit;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

import static com.blackbuild.klum.common.CommonAstHelper.assertMethodIsNotPrivate;
import static com.blackbuild.klum.common.CommonAstHelper.assertMethodIsParameterless;
import static groovyjarjarasm.asm.Opcodes.ACC_PROTECTED;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;

/**
 * Helper class to move mutating methods to RW class.
 */
public class WriteAccessMethodsMover {

    public static final ClassNode MUTATOR_ANNOTATION = ClassHelper.make(Mutator.class);
    public static final ClassNode WRITE_ACCESS_ANNOTATION = ClassHelper.make(WriteAccess.class);
    private final ClassNode annotatedClass;

    private final SourceUnit sourceUnit;

    public WriteAccessMethodsMover(ClassNode annotatedClass, SourceUnit sourceUnit) {
        this.annotatedClass = annotatedClass;
        this.sourceUnit = sourceUnit;
    }

    static void downgradeToProtected(MethodNode method) {
        int modifiers = (method.getModifiers() & ~ACC_PUBLIC) | ACC_PROTECTED;
        method.setModifiers(modifiers);
    }

    static void moveMethodFromModelToRWClass(MethodNode method) {
        ClassNode declaringClass = method.getDeclaringClass();
        declaringClass.removeMethod(method);
        ClassNode rwClass = declaringClass.getNodeMetaData(DSLASTTransformation.RWCLASS_METADATA_KEY);
        // if method is public, it will already have been added by delegateTo, replace it again
        CommonAstHelper.replaceMethod(rwClass, method);
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
        Optional<WriteAccess.Type> writeType = getWriteAccessTypeForMethod(method);

        if (!writeType.isPresent()) return;

        assertMethodIsNotPrivate(method, sourceUnit);
        if (writeType.get() == WriteAccess.Type.LIFECYCLE) {
            assertMethodIsParameterless(method, sourceUnit);
            downgradeToProtected(method);
        }
        moveMethodFromModelToRWClass(method);
    }

    static Optional<WriteAccess.Type> getWriteAccessTypeForMethod(MethodNode method) {
        return method.getAnnotations().stream()
                .map(WriteAccessMethodsMover::getWriteAccessTypeForAnnotation)
                .filter(Objects::nonNull)
                .findAny();
    }

    private static WriteAccess.Type getWriteAccessTypeForAnnotation(AnnotationNode annotation) {
        if (!DslAstHelper.hasAnnotation(annotation.getClassNode(), WRITE_ACCESS_ANNOTATION)) return null;

        // We need to use the class explicitly, since we cannot access the members of metaAnnotations directly
        // This is safe, since annotations are in a different module and thus already compiled
        Class<?> annotationClass = annotation.getClassNode().getTypeClass();
        return annotationClass.getAnnotation(WriteAccess.class).value();
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
