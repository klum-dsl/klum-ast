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

import com.blackbuild.groovy.configdsl.transform.Converter;
import com.blackbuild.groovy.configdsl.transform.Converters;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_FIELD_ANNOTATION;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getClosureMemberList;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getRwClassOf;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.hasAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createPublicMethod;
import static com.blackbuild.klum.common.CommonAstHelper.addCompileError;
import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static com.blackbuild.klum.common.CommonAstHelper.getElementType;
import static java.util.Arrays.asList;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.cloneParams;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.param;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.transform.AbstractASTTransformation.getMemberList;

/**
 * Created by steph on 29.04.2017.
 */
class ConverterBuilder {

    private static final List<String> DEFAULT_PREFIXES = asList("from", "of", "create", "parse");
    private static final List<String> DSL_METHODS = asList(
            DSLASTTransformation.CREATE_FROM,
            DSLASTTransformation.CREATE_METHOD_NAME,
            DSLASTTransformation.CREATE_FROM_CLASSPATH,
            TemplateMethods.CREATE_AS_TEMPLATE
    );

    static final ClassNode CONVERTERS_ANNOTATION = make(Converters.class);
    static final ClassNode CONVERTER_ANNOTATION = make(Converter.class);

    private final ClassNode annotatedClass;
    private final String methodName;
    private final boolean withKey;
    private final DSLASTTransformation transformation;
    private final FieldNode fieldNode;
    private final ClassNode rwClass;
    private ClassNode elementType;

    private List<String> includes;
    private List<String> excludes;
    private AnnotationNode convertersAnnotation;

    ConverterBuilder(DSLASTTransformation transformation, FieldNode fieldNode, String methodName, boolean withKey) {
        this.transformation = transformation;
        this.fieldNode = fieldNode;
        this.annotatedClass = fieldNode.getOwner();
        this.methodName = methodName;
        this.withKey = withKey;
        rwClass = getRwClassOf(annotatedClass);
        elementType = getElementType(fieldNode);

        convertersAnnotation = getAnnotation(fieldNode, CONVERTERS_ANNOTATION);
        if (convertersAnnotation == null)
            convertersAnnotation = getAnnotation(annotatedClass, CONVERTERS_ANNOTATION);

        fillIncludesAndExcludes();
    }

    private void fillIncludesAndExcludes() {
        if (convertersAnnotation == null) {
            includes = DEFAULT_PREFIXES;
            excludes = Collections.emptyList();
            return;
        }

        includes = getMemberList(convertersAnnotation, "includeMethods");

        if (transformation.memberHasValue(convertersAnnotation, "excludeDefaultPrefixes", true))
            includes.addAll(DEFAULT_PREFIXES);

        excludes = getMemberList(convertersAnnotation, "excludeMethods");
    }

    void execute() {
        for (ClosureExpression converterExpression : getClosureMemberList(getAnnotation(fieldNode, DSL_FIELD_ANNOTATION), "converters"))
            createSingleConverterMethod(converterExpression);

        if (convertersAnnotation != null)
            for (ClassNode converterClass : transformation.getClassList(convertersAnnotation, "value"))
                createConverterMethodsFromFactoryMethods(converterClass);

        createConverterMethodsFromOwnFactoryMethods();
        createConstructorConverters();
    }

    private void createConstructorConverters() {
        if (isDSLObject(elementType))
            return;
        if (convertersAnnotation == null)
            return;
        if (!transformation.memberHasValue(convertersAnnotation, "includeConstructors", true))
            return;

        for (ConstructorNode constructor : elementType.getDeclaredConstructors())
            createConverterConstructorCall(constructor);
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

        for (MethodNode method : converterClass.getMethods()) {
            if (method.isStatic() && method.isPublic() &&
                    (isConverterMethod(method) ||
                        isValidName(method.getName()) &&
                        !isKlumMethod(method)
                    ) &&
                    method.getReturnType().isDerivedFrom(elementType)
            )
                result.add(method);
        }

        return result;
    }

    private boolean isConverterMethod(MethodNode method) {
        return hasAnnotation(method.getDeclaringClass(), CONVERTER_ANNOTATION) || hasAnnotation(method, CONVERTER_ANNOTATION);
    }

    private boolean isValidName(String name) {
        return isNameIncluded(name) && !isNameExcluded(name);
    }

    private boolean isNameExcluded(String name) {
        for (String prefix : excludes)
            if (name.startsWith(prefix))
                return true;
        return false;
    }

    private boolean isNameIncluded(String name) {
        if (includes.isEmpty())
            return true;
        for (String prefix : includes)
            if (name.startsWith(prefix))
                return true;
        return false;
    }

    private boolean isKlumMethod(MethodNode method) {
        return isDSLObject(method.getDeclaringClass()) && DSL_METHODS.contains(method.getName());
    }

    private void createConverterMethod(Parameter[] sourceParameters, Expression delegationExpression) {
        List<Parameter> parameters = cloneAndPrependParameters(sourceParameters);
        ArgumentListExpression arguments = withKey ? args(varX("$key"), delegationExpression) : args(delegationExpression);

        createPublicMethod(methodName)
                .optional()
                .returning(elementType)
                .params(parameters)
                .sourceLinkTo(delegationExpression)
                .callMethod(
                    "this",
                    methodName,
                    arguments
                ).addTo(rwClass);
    }

    private void createConverterFactoryCall(MethodNode converterMethod) {
        createConverterMethod(
                converterMethod.getParameters(),
                callX(converterMethod.getDeclaringClass(), converterMethod.getName(), args(cloneParams(converterMethod.getParameters())))
        );
    }

    private void createConverterConstructorCall(ConstructorNode constructor) {
        createConverterMethod(
                constructor.getParameters(),
                ctorX(constructor.getDeclaringClass(), args(cloneParams(constructor.getParameters())))
        );
    }

    private void createSingleConverterMethod(ClosureExpression converter) {
        Parameter[] parameters = rescopeParameters(converter.getParameters());
        createConverterMethod(
                parameters,
                callX(converter, "call", args(parameters))
        );
    }

    private List<Parameter> cloneAndPrependParameters(Parameter[] source) {
        List<Parameter> parameters = new ArrayList<>(source.length + 1);

        if (withKey)
            parameters.add(param(STRING_TYPE, "$key"));

        for (Parameter parameter : source)
            parameters.add(param(parameter.getOriginType(), parameter.getName()));

        return parameters;
    }

    private Parameter[] rescopeParameters(Parameter[] source) {
        Parameter[] result = new Parameter[source.length];
        for (int i = 0; i < source.length; i++) {
            Parameter srcParam = source[i];
            if (srcParam.getType() == null)
                addCompileError("All parameters must have an explicit type for the parameter for a converter", elementType, srcParam);
            Parameter dstParam = new Parameter(srcParam.getOriginType(), "_" + srcParam.getName());
            result[i] = dstParam;
        }
        return result;
    }
}
