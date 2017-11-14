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
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.Verifier;
import org.jetbrains.annotations.Nullable;

import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.expr.CastExpression.asExpression;
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callThisX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
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
        for (FieldNode fNode : transformation.annotatedClass.getFields()) {
            AnnotationNode defaultAnnotation = getAnnotation(fNode, DEFAULT_ANNOTATION);

            if (defaultAnnotation != null) {
                Statement getterCode = createDefaultValueFor(fNode, defaultAnnotation);
                MethodNode getter = transformation.annotatedClass.getGetterMethod("get" + Verifier.capitalize(fNode.getName()));

                getter.setCode(getterCode);

                if (ClassHelper.boolean_TYPE == fNode.getType() || ClassHelper.Boolean_TYPE == fNode.getType()) {
                    MethodNode secondGetter = transformation.annotatedClass.getGetterMethod("is" + Verifier.capitalize(fNode.getName()));
                    secondGetter.setCode(getterCode);
                }
            }
        }
    }

    private Statement createDefaultValueFor(FieldNode fNode, AnnotationNode defaultAnnotation) {
        assertOnlyOneMemberOfAnnotationIsSet(defaultAnnotation);

        String fieldMember = getMemberStringValue(defaultAnnotation, "value");

        if (fieldMember == null)
            fieldMember = getMemberStringValue(defaultAnnotation, "field");

        if (fieldMember != null) {
            return createFieldMethod(fNode, fieldMember);
        }

        ClosureExpression code = getCodeClosureFor(defaultAnnotation);
        if (code != null) {
            return createClosureMethod(fNode, code);
        }

        String delegateMember = getMemberStringValue(defaultAnnotation, "delegate");
        if (delegateMember != null) {
            return createDelegateMethod(fNode, delegateMember);
        }

        throw new IllegalStateException("Illegal use of Default annotation. This should have been catched earlier");
    }

    private Statement createDelegateMethod(FieldNode fNode, String delegate) {
        String delegateGetter = getGetterName(delegate);
        return stmt(
                asExpression(fNode.getType(), new ElvisOperatorExpression(
                        varX(fNode.getName()),
                        new PropertyExpression(callThisX(delegateGetter), new ConstantExpression(fNode.getName()), true))
                )
        );
    }

    private Statement createClosureMethod(FieldNode fNode, ClosureExpression code) {
        return block(
                declS(varX(CLOSURE_VAR_NAME), code),
                assignS(propX(varX(CLOSURE_VAR_NAME), "delegate"), varX("this")),
                assignS(
                        propX(varX(CLOSURE_VAR_NAME), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_ONLY")
                ),
                stmt(asExpression(fNode.getType(), new ElvisOperatorExpression(varX(fNode.getName()), callX(varX(CLOSURE_VAR_NAME), "call"))))
        );
    }

    private Statement createFieldMethod(FieldNode fNode, String targetField) {
        String fieldGetter = getGetterName(targetField);
        return stmt(asExpression(fNode.getType(), new ElvisOperatorExpression(varX(fNode.getName()), callThisX(fieldGetter))));
    }

    private String getGetterName(String property) {
        return "get" + Verifier.capitalize(property);
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
