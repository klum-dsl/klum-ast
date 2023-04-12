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

import com.blackbuild.groovy.configdsl.transform.Converter;
import com.blackbuild.groovy.configdsl.transform.Converters;
import com.blackbuild.groovy.configdsl.transform.KlumGenerated;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.tools.GenericsUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_FIELD_ANNOTATION;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getClosureMemberList;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.hasAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDslMap;
import static com.blackbuild.groovy.configdsl.transform.ast.ProxyMethodBuilder.createProxyMethod;
import static com.blackbuild.klum.common.CommonAstHelper.addCompileError;
import static com.blackbuild.klum.common.CommonAstHelper.addCompileWarning;
import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static com.blackbuild.klum.common.CommonAstHelper.getElementType;
import static com.blackbuild.klum.common.CommonAstHelper.isCollection;
import static com.blackbuild.klum.common.CommonAstHelper.isMap;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static java.util.Arrays.asList;
import static java.util.Arrays.stream;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GenericsUtils.correctToGenericsSpecRecurse;
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

    private final String methodName;
    private final boolean withKey;
    private final DSLASTTransformation transformation;
    private final FieldNode fieldNode;
    private final ClassNode rwClass;
    private final ClassNode elementType;

    private List<String> includes;
    private List<String> excludes;
    private AnnotationNode convertersAnnotation;

    ConverterBuilder(DSLASTTransformation transformation, FieldNode fieldNode, String methodName, boolean withKey, ClassNode targetClass) {
        this.transformation = transformation;
        this.fieldNode = fieldNode;
        this.methodName = methodName;
        this.withKey = withKey;
        rwClass = targetClass;
        elementType = getElementType(fieldNode);

        convertersAnnotation = getAnnotation(fieldNode, CONVERTERS_ANNOTATION);
        if (convertersAnnotation == null)
            convertersAnnotation = getAnnotation(transformation.annotatedClass, CONVERTERS_ANNOTATION);

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
        convertClosureListToConverterClass(getClosureMemberList(getAnnotation(fieldNode, DSL_FIELD_ANNOTATION), "converters"));

        if (convertersAnnotation != null)
            transformation.getClassList(convertersAnnotation, "value")
                    .forEach(this::createConverterMethodsFromFactoryMethods);

        createConverterMethodsFromOwnFactoryMethods();
        createConstructorConverters();
    }

    private void convertClosureListToConverterClass(List<ClosureExpression> closures) {
        if (closures.isEmpty()) return;

        InnerClassNode converterClass = new InnerClassNode(
                transformation.annotatedClass,
                transformation.annotatedClass.getName() + "$_" + fieldNode.getName() + "_converterClosures",
                ACC_PUBLIC | ACC_STATIC,
                ClassHelper.OBJECT_TYPE);

        closures.forEach(closureExpression -> closureToStaticConverterMethod(converterClass, closureExpression));

        transformation.annotatedClass.getModule().addClass(converterClass);
    }

    private void closureToStaticConverterMethod(ClassNode converterClass, ClosureExpression converter) {
        Parameter[] parameters = rescopeParameters(converter.getParameters());
        String name = stream(parameters)
                .map(Parameter::getOriginType)
                .map(ClassNode::getNameWithoutPackage)
                .collect(Collectors.joining("_"));

        MethodNode method = MethodBuilder.createPublicMethod("from_" + name)
                .mod(ACC_STATIC)
                .returning(elementType)
                .params(parameters)
                .sourceLinkTo(converter)
                .statement(converter.getCode())
                .addTo(converterClass);

        createConverterFactoryCall(method);
    }

    private void createConstructorConverters() {
        if (isDSLObject(elementType))
            return;
        if (convertersAnnotation == null)
            return;
        if (!transformation.memberHasValue(convertersAnnotation, "includeConstructors", true))
            return;

        elementType.getDeclaredConstructors().forEach(this::createConverterConstructorCall);
    }

    private void createConverterMethodsFromOwnFactoryMethods() {
        createConverterMethodsFromFactoryMethods(elementType);
    }

    void createConverterMethodsFromFactoryMethods(ClassNode converterClass) {
        findAllFactoryMethodsFor(converterClass).forEach(this::createConverterFactoryCall);
    }

    private Stream<MethodNode> findAllFactoryMethodsFor(ClassNode converterClass) {
        return converterClass.getMethods()
                .stream()
                .filter(this::isFactoryMethod);
    }

    private boolean isFactoryMethod(MethodNode method) {
        return method.isStatic()
                && method.isPublic()
                && isConverterMethod(method)
                && isAssignable(method.getReturnType(), elementType);
    }

    private boolean isAssignable(ClassNode type, ClassNode classOrInterface) {
        return type.isDerivedFrom(classOrInterface) || type.implementsInterface(classOrInterface);
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean isConverterMethod(MethodNode method) {
        if (hasAnnotation(method.getDeclaringClass(), CONVERTER_ANNOTATION))
            return true;
        if (hasAnnotation(method, CONVERTER_ANNOTATION))
            return true;
        if (isValidName(method.getName()) && !isKlumMethod(method))
            return true;
        return false;
    }

    private boolean isValidName(String name) {
        return isNameIncluded(name) && !isNameExcluded(name);
    }

    private boolean isNameExcluded(String name) {
        return excludes.stream().anyMatch(name::startsWith);
    }

    private boolean isNameIncluded(String name) {
        return includes.isEmpty() || includes.stream().anyMatch(name::startsWith);
    }

    private boolean isKlumMethod(MethodNode method) {
        return hasAnnotation(method, ClassHelper.make(KlumGenerated.class))
                || (isDSLObject(method.getDeclaringClass()) && DSL_METHODS.contains(method.getName()));
    }

    private void createConverterMethod(Parameter[] sourceParameters, ClassNode converterType, String converterMethod, MethodNode sourceMethod) {
        Map<String, ClassNode> genericsSpec = GenericsUtils.createGenericsSpec(elementType);

        checkForUnmatchedGenericPlaceholders(sourceMethod, genericsSpec);

        ProxyMethodBuilder method = createProxyMethod(methodName, getProxyMethodName())
                .mod(ACC_PUBLIC)
                .optional()
                .returning(elementType)
                .sourceLinkTo(sourceMethod)
                .constantParam(fieldNode.getName())
                .constantClassParam(converterType)
                .constantParam(converterMethod);

        if (withKey)
            method.param(STRING_TYPE, "$key");
        else if (isDslMap(fieldNode))
            method.constantParam(null);

        stream(sourceParameters).forEach( parameter -> method.param(
                        correctToGenericsSpecRecurse(genericsSpec, parameter.getOriginType()),
                        parameter.getName(),
                        parameter.getInitialExpression()
                ));
        method.addTo(rwClass);
    }

    private void checkForUnmatchedGenericPlaceholders(MethodNode sourceMethod, Map<String, ClassNode> genericsSpec) {
        if (sourceMethod.getGenericsTypes() == null) return;
        Set<String> unmappedPlaceholder = stream(sourceMethod.getGenericsTypes()).filter(GenericsType::isPlaceholder).map(GenericsType::getName).filter(name -> !genericsSpec.containsKey(name)).collect(Collectors.toSet());
        if (!unmappedPlaceholder.isEmpty())
            addCompileWarning(fieldNode.getOwner().getModule().getContext(), String.format("Placeholder(s) %s of factory method of %s is not used in Class generics of %s, this might lead to unexpected results", unmappedPlaceholder, sourceMethod, sourceMethod.getDeclaringClass()), sourceMethod);
    }

    private String getProxyMethodName() {
        if (isCollection(fieldNode.getType()))
            return "addElementToCollectionViaConverter";
        else if (isMap(fieldNode.getType()))
            return "addElementToMapViaConverter";

        return "setSingleFieldViaConverter";
    }

    private void createConverterFactoryCall(MethodNode converterMethod) {
        createConverterMethod(
                converterMethod.getParameters(),
                converterMethod.getDeclaringClass(),
                converterMethod.getName(),
                converterMethod
        );
    }

    private void createConverterConstructorCall(ConstructorNode constructor) {
        createConverterMethod(
                constructor.getParameters(),
                constructor.getDeclaringClass(),
                null,
                constructor
        );
    }

    private Parameter[] rescopeParameters(Parameter[] source) {
        Parameter[] result = new Parameter[source.length];
        for (int i = 0; i < source.length; i++) {
            Parameter srcParam = source[i];
            if (srcParam.getType() == null)
                addCompileError("All parameters must have an explicit type for the parameter for a converter", elementType, srcParam);
            Parameter dstParam = new Parameter(srcParam.getOriginType(), srcParam.getName());
            result[i] = dstParam;
        }
        return result;
    }

}
