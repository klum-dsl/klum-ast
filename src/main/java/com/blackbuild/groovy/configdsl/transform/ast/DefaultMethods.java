/**
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
package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.groovy.configdsl.transform.Default;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.stmt.Statement;

import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.expr.CastExpression.asExpression;
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callSuperX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.notX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.transform.AbstractASTTransformation.getMemberStringValue;


/**
 * Helper class for default values.
 */
public class DefaultMethods {
    public static final String CLOSURE_VAR_NAME = "closure";
    private DSLASTTransformation transformation;
    private DslMethodBuilder defaultMethod;

    static final ClassNode DEFAULT_ANNOTATION = make(Default.class);


    public DefaultMethods(DSLASTTransformation dslastTransformation) {
        transformation = dslastTransformation;
    }


    public void execute() {
        defaultMethod = DslMethodBuilder
                .createPrivateMethod("$Default")
                .mod(Opcodes.ACC_SYNTHETIC);

        if (transformation.dslParent != null)
            defaultMethod.statement(callSuperX("$Default"));

        for (FieldNode fNode : transformation.annotatedClass.getFields()) {
            AnnotationNode defaultAnnotation = getAnnotation(fNode, DEFAULT_ANNOTATION);

            if (defaultAnnotation == null)
                continue;

            defaultMethod.statement(
                    ifS(
                            notX(propX(varX("this"), constX(fNode.getName()))),
                            createDefaultValueFor(fNode, defaultAnnotation)
                    )
            );
        }
        defaultMethod.addTo(transformation.rwClass);
    }

    private Statement createDefaultValueFor(FieldNode fNode, AnnotationNode defaultAnnotation) {
        assertExactlyOneMemberOfAnnotationIsSet(defaultAnnotation);

        ClosureExpression code = DslAstHelper.getCodeClosureFor(fNode, defaultAnnotation, "code");
        if (code != null) {
            return createClosureMethod(fNode, code);
        }

        String fieldMember = getMemberStringValue(defaultAnnotation, "value");

        if (fieldMember == null)
            fieldMember = getMemberStringValue(defaultAnnotation, "field");

        if (fieldMember != null) {
            return castToAndAssign(fNode, createFieldMethod(fieldMember));
        }


        String delegateMember = getMemberStringValue(defaultAnnotation, "delegate");
        if (delegateMember != null) {
            return castToAndAssign(fNode, createDelegateMethod(fNode, delegateMember));
        }

        throw new IllegalStateException("Illegal use of Default annotation. This should have been catched earlier");
    }

    private Statement castToAndAssign(FieldNode fNode, Expression value) {
        return assignS(
                propX(varX("this"), constX(fNode.getName())),
                asExpression(
                        fNode.getType(),
                        value
                )
        );
    }

    private Expression createDelegateMethod(FieldNode fNode, String delegate) {
        return new PropertyExpression(propX(varX("this"), delegate), constX(fNode.getName()), true);
    }

    private Statement createClosureMethod(FieldNode fNode, ClosureExpression code) {
        return block(
                declS(varX(CLOSURE_VAR_NAME), code),
                assignS(propX(varX(CLOSURE_VAR_NAME), "delegate"), varX("this")),
                assignS(
                        propX(varX(CLOSURE_VAR_NAME), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_ONLY")
                ),

                castToAndAssign(fNode, callX(varX(CLOSURE_VAR_NAME), "call"))
        );
    }

    private Expression createFieldMethod(String targetField) {
        return propX(varX("this"), targetField);
    }

    private void assertExactlyOneMemberOfAnnotationIsSet(AnnotationNode annotationNode) {
        int numberOfMembers = annotationNode.getMembers().size();

        if (numberOfMembers == 0)
            transformation.addError("You must define either delegate, code or field for @Default annotations", annotationNode);

        if (numberOfMembers > 1)
            transformation.addError("Only one member for @Default annotation is allowed!", annotationNode);
    }


}
