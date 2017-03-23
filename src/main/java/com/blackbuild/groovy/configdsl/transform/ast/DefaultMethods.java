/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.Default;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.*;
import org.jetbrains.annotations.Nullable;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.getAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createPublicMethod;
import static java.lang.Character.toUpperCase;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.expr.CastExpression.asExpression;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.transform.AbstractASTTransformation.getMemberStringValue;


/**
 * Helper class for default values.
 */
public class DefaultMethods {
    public static final String CLOSURE_VAR_NAME = "closure";
    private DSLASTTransformation transformation;

    static final ClassNode DEFAULT_ANNOTATION = make(Default.class);


    public DefaultMethods(DSLASTTransformation dslastTransformation) {
        transformation = dslastTransformation;
    }


    public void execute() {

        for (FieldNode fieldNode : transformation.annotatedClass.getFields()) {
            AnnotationNode defaultAnnotation = getAnnotation(fieldNode, DEFAULT_ANNOTATION);

            if (defaultAnnotation != null)
                createDefaultValueFor(fieldNode, defaultAnnotation);
        }
    }

    private void createDefaultValueFor(FieldNode fieldNode, AnnotationNode defaultAnnotation) {
        assertOnlyOneMemberOfAnnotationIsSet(defaultAnnotation);

        String fieldMember = getMemberStringValue(defaultAnnotation, "value");

        if (fieldMember == null)
            fieldMember = getMemberStringValue(defaultAnnotation, "field");

        if (fieldMember != null) {
            createFieldMethod(fieldNode, fieldMember);
            return;
        }

        ClosureExpression code = getCodeClosureFor(defaultAnnotation);
        if (code != null) {
            createClosureMethod(fieldNode, code);
            return;
        }

        String delegateMember = getMemberStringValue(defaultAnnotation, "delegate");
        if (delegateMember != null) {
            createDelegateMethod(fieldNode, delegateMember);
        }
    }

    private void createDelegateMethod(FieldNode fieldNode, String delegate) {
        String ownGetter = getGetterName(fieldNode.getName());
        String delegateGetter = getGetterName(delegate);

        createPublicMethod(ownGetter)
                .returning(fieldNode.getType())
                .statement(asExpression(fieldNode.getType(), new ElvisOperatorExpression(varX(fieldNode.getName()), new PropertyExpression(callThisX(delegateGetter), new ConstantExpression(fieldNode.getName()), true))))
                .addTo(transformation.annotatedClass);
    }

    private void createClosureMethod(FieldNode fieldNode, ClosureExpression code) {
        String ownGetter = getGetterName(fieldNode.getName());

        createPublicMethod(ownGetter)
                .returning(fieldNode.getType())
                .declareVariable(CLOSURE_VAR_NAME, code)
                .assignS(propX(varX(CLOSURE_VAR_NAME), "delegate"), varX("this"))
                .assignS(
                        propX(varX(CLOSURE_VAR_NAME), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .statement(asExpression(fieldNode.getType(), new ElvisOperatorExpression(varX(fieldNode.getName()), callX(varX(CLOSURE_VAR_NAME), "call"))))
                .addTo(transformation.annotatedClass);
    }

    private void createFieldMethod(FieldNode fieldNode, String targetField) {
        String ownGetter = getGetterName(fieldNode.getName());
        String fieldGetter = getGetterName(targetField);

        createPublicMethod(ownGetter)
                .returning(fieldNode.getType())
                .statement(asExpression(fieldNode.getType(), new ElvisOperatorExpression(varX(fieldNode.getName()), callThisX(fieldGetter))))
                .addTo(transformation.annotatedClass);
    }

    private String getGetterName(String property) {
        return "get" + toUpperCase(property.toCharArray()[0]) + property.substring(1);
    }


    private void assertOnlyOneMemberOfAnnotationIsSet(AnnotationNode annotationNode) {
        int numberOfMembers = annotationNode.getMembers().size();

        if (numberOfMembers == 0)
            transformation.addError("You must define either delegate, code or field for @Default annotations", annotationNode);

        if (numberOfMembers > 1)
            transformation.addError("Only one member for @Default annotation is allowed!", annotationNode);
    }

    @Nullable
    private ClosureExpression getCodeClosureFor(AnnotationNode defaultAnnotation) {
        Expression codeExpression = defaultAnnotation.getMember("code");
        if (codeExpression == null)
            return null;
        if (codeExpression instanceof ClosureExpression)
            return (ClosureExpression) codeExpression;

        transformation.addError("Illegal value for code, only None.class or a closure is allowed.", defaultAnnotation);
        return null;
    }


}
