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
package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.transform.stc.AbstractTypeCheckingExtension;

import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS;
import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.NAME_OF_RW_FIELD_IN_MODEL_CLASS;

/**
 * Created by stephan on 12.04.2017.
 */
public class MutationDetectingTypeCheckingExtension extends AbstractTypeCheckingExtension {

    private Deque<MethodNode> methodNodes = new LinkedList<MethodNode>();
    private ModelVerificationVisitor verificationVisitor;

    public MutationDetectingTypeCheckingExtension(ModelVerificationVisitor typeCheckingVisitor) {
        super(typeCheckingVisitor);
        verificationVisitor = typeCheckingVisitor;
    }

    @Override
    public List<MethodNode> handleMissingMethod(ClassNode receiver, String name, ArgumentListExpression argumentList, ClassNode[] argumentTypes, MethodCall call) {

        if (!verificationVisitor.isInMutatorMethod())
            return Collections.emptyList();

        ClassNode rwClass = DslAstHelper.getRwClassOf(receiver);
        if (rwClass != null)
            return delegateCallsToProperty(name, argumentTypes, call, rwClass, NAME_OF_RW_FIELD_IN_MODEL_CLASS);

        ClassNode modelClassOfReceiver = DslAstHelper.getModelClassFor(receiver);
        if (modelClassOfReceiver != null)
            return delegateCallsToProperty(name, argumentTypes, call, modelClassOfReceiver, NAME_OF_MODEL_FIELD_IN_RW_CLASS);

        return Collections.emptyList();
    }

    private List<MethodNode> delegateCallsToProperty(String name, ClassNode[] argumentTypes, MethodCall call, ClassNode rwClass, String property) {
        List<MethodNode> rwMethods = verificationVisitor.findMethod(rwClass, name, argumentTypes);

        if (!rwMethods.isEmpty() && (call instanceof MethodCallExpression)) {
            redirectMethodCallToProperty((MethodCallExpression) call, property);
        }
        return rwMethods;
    }

    private void redirectMethodCallToProperty(MethodCallExpression call, String propertyName) {
        Expression objectExpression = call.getObjectExpression();
        call.setObjectExpression(new PropertyExpression(objectExpression, new ConstantExpression(propertyName), true));
    }

}
