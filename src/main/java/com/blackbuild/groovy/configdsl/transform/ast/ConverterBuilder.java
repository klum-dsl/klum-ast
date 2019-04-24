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

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClosureExpression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_FIELD_ANNOTATION;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getClosureMemberList;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getRwClassOf;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createPublicMethod;
import static com.blackbuild.klum.common.CommonAstHelper.addCompileError;
import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static com.blackbuild.klum.common.CommonAstHelper.getElementType;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.cloneParams;
import static org.codehaus.groovy.ast.tools.GeneralUtils.param;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;

/**
 * Created by steph on 29.04.2017.
 */
class ConverterBuilder {
    private final ClassNode annotatedClass;
    private final String methodName;
    private final boolean withKey;
    private final DSLASTTransformation transformation;
    private final FieldNode fieldNode;
    private final ClassNode rwClass;
    private ClassNode elementType;

    private static final List<String> DEFAULT_PREFIXES = Arrays.asList("from", "of", "create");
    private static final List<String> DSL_METHODS = Arrays.asList(DSLASTTransformation.CREATE_FROM, DSLASTTransformation.CREATE_METHOD_NAME, TemplateMethods.CREATE_AS_TEMPLATE);


    ConverterBuilder(DSLASTTransformation transformation, FieldNode fieldNode, String methodName, boolean withKey) {
        this.transformation = transformation;
        this.fieldNode = fieldNode;
        this.annotatedClass = fieldNode.getOwner();
        this.methodName = methodName;
        this.withKey = withKey;
        rwClass = getRwClassOf(annotatedClass);
        elementType = getElementType(fieldNode);
    }

    void execute() {
        for (ClosureExpression converterExpression : getClosureMemberList(getAnnotation(fieldNode, DSL_FIELD_ANNOTATION), "converters"))
            createSingleConverterMethod(converterExpression, withKey);

        for (ClassNode converterClass : transformation.getClassList(transformation.dslAnnotation, "converters"))
            createConverterMethodsFromFactoryMethods(converterClass);

        createConverterMethodsFromOwnFactoryMethods();
    }

    private void createConverterMethodsFromOwnFactoryMethods() {
        createConverterMethodsFromFactoryMethods(elementType);
    }

    private void createConverterMethodsFromFactoryMethods(ClassNode converterClass) {
        for (MethodNode converterMethod : findAllFactoryMethodsFor(converterClass))
            createConverterFactoryCall(converterMethod);
    }

    private List<MethodNode> findAllFactoryMethodsFor(ClassNode converterClass) {
        List<MethodNode> result = new ArrayList<>();

        for (MethodNode method : converterClass.getMethods())
            if (method.isStatic() &&
                    method.isPublic() &&
                    isValidName(method.getName()) &&
                    !isKlumMethod(method) &&
                    method.getReturnType().isDerivedFrom(elementType)
            )
                result.add(method);

        return result;
    }

    private boolean isValidName(String name) {
        return isNameIncluded(name) && !isNameExcluded(name);
    }

    private boolean isNameExcluded(String name) {
        return false;
    }

    private boolean isNameIncluded(String name) {
        for (String prefix : DEFAULT_PREFIXES)
            if (name.startsWith(prefix))
                return true;
        return false;
    }

    private boolean isKlumMethod(MethodNode method) {
        return isDSLObject(method.getDeclaringClass()) && DSL_METHODS.contains(method.getName());
    }

    private void createConverterFactoryCall(MethodNode converterMethod) {
        Parameter[] parameters = converterMethod.getParameters();
        createPublicMethod(methodName)
                .optional()
                .returning(converterMethod.getReturnType())
                .params(cloneParams(parameters))
                .sourceLinkTo(converterMethod)
                .callMethod(
                        "this",
                        methodName,
                        args(callX(converterMethod.getDeclaringClass(), converterMethod.getName(), args(cloneParams(parameters))))
                )
                .addTo(rwClass);
    }

    private void createSingleConverterMethod(ClosureExpression converter, boolean withKey) {
        List<Parameter> parameters = new ArrayList<>(converter.getParameters().length + 1);
        String[] callParameterNames = new String[converter.getParameters().length];

        if (withKey)
            parameters.add(param(STRING_TYPE, "$key"));

        int index = 0;
        for (Parameter parameter : converter.getParameters()) {
            if (parameter.getType() == null) {
                addCompileError("All parameters must have an explicit type for the parameter for a converter", elementType, parameter);
                return;
            }
            String parameterName = "$" + parameter.getName();
            parameters.add(param(parameter.getType(), parameterName));
            callParameterNames[index++] = parameterName;
        }

        DslMethodBuilder method = createPublicMethod(methodName)
                .optional()
                .returning(elementType)
                .params(parameters.toArray(new Parameter[0]))
                .sourceLinkTo(converter);

        if (withKey)
            method.callMethod(
                    "this",
                    methodName,
                    args(varX("$key"), callX(converter, "call", args(callParameterNames)))
            );
        else
            method.callMethod(
                    "this",
                    methodName,
                    args(callX(converter, "call", args(callParameterNames)))
            );

        method.addTo(rwClass);
    }

}
