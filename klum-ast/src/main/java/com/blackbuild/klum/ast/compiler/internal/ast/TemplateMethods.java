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
package com.blackbuild.klum.ast.compiler.internal.ast;

import com.blackbuild.annodocimal.ast.AstDocumentation;
import com.blackbuild.klum.ast.runtime.generated.GeneratedTemplateSupport;
import com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.runtime.StringGroovyMethods;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.createGeneratedAnnotation;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.copyAnnotationsFromSourceToTarget;
import static com.blackbuild.klum.ast.compiler.internal.ast.ProxyMethodBuilder.*;
import static com.blackbuild.klum.ast.compiler.internal.reflect.AstReflectionBridge.cloneParamsWithAdjustedNames;
import static groovyjarjarasm.asm.Opcodes.*;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.*;

@SuppressWarnings("java:S1192")
class TemplateMethods {
    public static final String TEMPLATE_FIELD_NAME = "Template";
    public static final ClassNode TEMPLATE_SUPPORT_TYPE = make(GeneratedTemplateSupport.class);

    public static final String COPY_FROM = "copyFrom";
    private final ClassNode annotatedClass;
    private ClassNode templateClass;
    private InnerClassNode templateAdapter;
    private final ClassNode dslAncestor;
    private final InnerClassNode rwClass;

    public TemplateMethods(DSLASTTransformation transformation) {
        annotatedClass = transformation.annotatedClass;
        rwClass = transformation.rwClass;
        dslAncestor = DslAstHelper.getHighestAncestorDSLObject(annotatedClass);
    }

    public ClassNode invoke() {
        createImplementationForAbstractClassIfNecessary();
        copyFromMethods();
        createTemplateField();
        return templateClass;
    }

    private void createTemplateField() {
        createTemplateAdapter();
        FieldNode templateField = new FieldNode(
                TEMPLATE_FIELD_NAME,
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                GeneratedDslSupport.of(annotatedClass).getTemplateInterface(),
                annotatedClass,
                ctorX(templateAdapter)
        );

        AstDocumentation.attachText(templateField, "Assign templates to new objects.");
        templateField.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class));
        annotatedClass.addField(templateField);
    }

    private void createTemplateAdapter() {
        templateAdapter = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + "$_Template",
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL | ACC_SYNTHETIC,
                OBJECT_TYPE,
                new ClassNode[] { GeneratedDslSupport.of(annotatedClass).getTemplateInterface() },
                MixinNode.EMPTY_ARRAY
        );
        FieldNode support = templateAdapter.addField(
                "$support",
                ACC_PRIVATE | ACC_FINAL | ACC_SYNTHETIC,
                makeClassSafeWithGenerics(TEMPLATE_SUPPORT_TYPE, new GenericsType(annotatedClass)),
                ctorX(TEMPLATE_SUPPORT_TYPE, args(classX(annotatedClass)))
        );
        templateAdapter.addConstructor(ACC_PUBLIC, Parameter.EMPTY_ARRAY, CommonAstHelper.NO_EXCEPTIONS, block());
        templateAdapter.addAnnotation(createGeneratedAnnotation(TemplateMethods.class));
        annotatedClass.getModule().addClass(templateAdapter);

        addTemplateMethod("With", params(param(dslAncestor, "template"), param(CLOSURE_TYPE, "body")), support);
        ClassNode mapOfStringsAndObjects = makeClassSafeWithGenerics(MAP_TYPE, new GenericsType(STRING_TYPE), new GenericsType(OBJECT_TYPE));
        addTemplateMethod("With", params(param(mapOfStringsAndObjects, "template"), param(CLOSURE_TYPE, "body")), support);
        ClassNode classOfObject = makeClassSafeWithGenerics(make(Class.class), new GenericsType(OBJECT_TYPE));
        ClassNode mapOfClassToMap = makeClassSafeWithGenerics(MAP_TYPE, new GenericsType(classOfObject), new GenericsType(mapOfStringsAndObjects));
        addTemplateMethod("WithAll", params(param(mapOfClassToMap, "newTemplates"), param(CLOSURE_TYPE, "body")), support);
        ClassNode listOfObjects = makeClassSafeWithGenerics(LIST_TYPE, new GenericsType(OBJECT_TYPE));
        addTemplateMethod("WithAll", params(param(listOfObjects, "newTemplates"), param(CLOSURE_TYPE, "body")), support);
        addTemplateMethod("Create", Parameter.EMPTY_ARRAY, support);
        addTemplateMethod("Create", params(param(mapOfStringsAndObjects, "configMap"), configurationParameter()), support);
        addTemplateMethod("Create", params(configurationParameter()), support);
        addTemplateMethod("Create", params(param(mapOfStringsAndObjects, "configMap")), support);
        addTemplateMethod("CreateFrom", params(param(make(File.class), "scriptFile")), support);
        addTemplateMethod("CreateFrom", params(param(make(File.class), "scriptFile"), param(CLASSLOADER_TYPE, "loader")), support);
        addTemplateMethod("CreateFrom", params(param(make(URL.class), "scriptUrl")), support);
        addTemplateMethod("CreateFrom", params(param(make(URL.class), "scriptUrl"), param(CLASSLOADER_TYPE, "loader")), support);
    }

    private Parameter configurationParameter() {
        Parameter configuration = param(CLOSURE_TYPE, "configuration");
        AnnotationNode delegatesTo = new AnnotationNode(make(groovy.lang.DelegatesTo.class));
        delegatesTo.setMember("value", classX(GeneratedDslSupport.of(annotatedClass).getBuilderInterface()));
        delegatesTo.setMember("strategy", constX(groovy.lang.Closure.DELEGATE_ONLY));
        configuration.addAnnotation(delegatesTo);
        return configuration;
    }

    private void addTemplateMethod(String name, Parameter[] parameters, FieldNode support) {
        boolean mapsFirstArgument = parameters.length > 0 && CommonAstHelper.isMap(parameters[0].getType());
        MethodNode bridgeMethod = TEMPLATE_SUPPORT_TYPE.getMethods(name).stream()
                .filter(candidate -> candidate.getParameters().length == parameters.length)
                .filter(candidate -> !name.startsWith("With")
                        || CommonAstHelper.isMap(candidate.getParameters()[0].getType()) == mapsFirstArgument)
                .filter(candidate -> name.startsWith("With") || hasSameBridgeArguments(candidate, parameters))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No generated Template bridge method for " + name));
        ClassNode bridgeModelType = name.equals("With") && !mapsFirstArgument ? dslAncestor : annotatedClass;
        MethodNode publicMethod = correctToGenericsSpec(Collections.singletonMap("T", bridgeModelType), bridgeMethod);
        publicMethod.setModifiers(ACC_PUBLIC | ACC_ABSTRACT);
        publicMethod.setCode(EmptyStatement.INSTANCE);
        for (int index = 0; index < parameters.length; index++)
            copyAnnotationsFromSourceToTarget(parameters[index], publicMethod.getParameters()[index], Collections.emptyList());
        GeneratedDslSupport.of(annotatedClass).getTemplateInterface().addMethod(publicMethod);
        MethodNode adapterMethod = correctToGenericsSpec(Collections.singletonMap("T", bridgeModelType), bridgeMethod);
        adapterMethod.setModifiers(ACC_PUBLIC);
        Expression[] arguments = Arrays.stream(adapterMethod.getParameters())
                .map(parameter -> (Expression) varX(parameter))
                .toArray(Expression[]::new);
        MethodCallExpression bridgeCall = callX(varX(support), name, args(arguments));
        bridgeCall.setMethodTarget(bridgeMethod);
        adapterMethod.setCode(returnS(bridgeCall));
        templateAdapter.addMethod(adapterMethod);
    }

    private static boolean hasSameBridgeArguments(MethodNode candidate, Parameter[] arguments) {
        for (int index = 0; index < arguments.length; index++) {
            if (!candidate.getParameters()[index].getType().redirect().equals(arguments[index].getType().redirect()))
                return false;
        }
        return true;
    }

    private void createImplementationForAbstractClassIfNecessary() {
        if (!DslAstHelper.isInstantiable(annotatedClass))
            createTemplateClass();
        else
            templateClass = annotatedClass;
    }

    private void copyFromMethods() {
        createProxyMethod(COPY_FROM, "copyFromRecipe")
                .mod(ACC_PUBLIC)
                .documentationTitle("Copies all non-null/non-empty recipe values from the template to this Builder.")
                .param(newClass(dslAncestor), "template", "the recipe to apply")
                .addTo(rwClass);
        ClassNode mapOfStringsAndObjects = makeClassSafeWithGenerics(MAP_TYPE, new GenericsType(STRING_TYPE), new GenericsType(OBJECT_TYPE));
        createProxyMethod(COPY_FROM, "copyFromRecipe")
                .mod(ACC_PUBLIC)
                .documentationTitle("Copies all non-null/non-empty recipe values from the template to this Builder.")
                .param(mapOfStringsAndObjects, "template", "the recipe to apply")
                .addTo(rwClass);
     }

    private void createTemplateClass() {
        templateClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + "$Template",
                ACC_STATIC | ACC_SYNTHETIC | ACC_PUBLIC,
                newClass(annotatedClass));

        templateClass.addConstructor(
                ACC_SYNTHETIC | ACC_PROTECTED,
                params(
                        param(rwClass.getPlainNodeReference(), "builder"),
                        param(DSLASTTransformation.MATERIALIZATION_TOKEN, "materializationToken")
                ),
                CommonAstHelper.NO_EXCEPTIONS,
                block(ctorSuperS(args(varX("builder"), varX("materializationToken"))))
        );

        List<MethodNode> abstractMethods = annotatedClass.getAbstractMethods();
        if (abstractMethods != null)
            abstractMethods.forEach(this::implementAbstractMethod);

        templateClass.addAnnotation(createGeneratedAnnotation(TemplateMethods.class));
        annotatedClass.getModule().addClass(templateClass);
    }

    private void implementAbstractMethod(MethodNode abstractMethod) {
        if (methodIsAnAlreadyImplementedInterfaceMethod(abstractMethod))
            return;
        templateClass.addMethod(
                abstractMethod.getName(),
                abstractMethod.getModifiers() ^ ACC_ABSTRACT,
                abstractMethod.getReturnType(),
                cloneParamsWithAdjustedNames(abstractMethod),
                abstractMethod.getExceptions(),
                block()
        );
    }

    @SuppressWarnings({"RedundantIfStatement", "java:S1126"})
    private boolean methodIsAnAlreadyImplementedInterfaceMethod(MethodNode abstractMethod) {
        if (!abstractMethod.getDeclaringClass().isInterface())
            return false;

        MethodNode existingMethod = annotatedClass.getMethod(abstractMethod.getName(), abstractMethod.getParameters());

        if (existingMethod != null && existingMethod.isAbstract())
            return false;

        if (existingMethod != null)
            return true;

        String fieldName = fieldForGetter(abstractMethod);

        if (fieldName == null)
            return false;

        if (annotatedClass.getField(fieldName) != null)
            return true;

        return false;
    }

    private String fieldForGetter(MethodNode method) {
        if (ClassHelper.VOID_TYPE.equals(method.getReturnType()))
            return null;

        if (method.getParameters().length != 0)
            return null;

        if (method.getName().startsWith("is")) {
            return StringGroovyMethods.uncapitalize(method.getName().substring(2));
        } else if (method.getName().startsWith("get")) {
            return StringGroovyMethods.uncapitalize(method.getName().substring(3));
        } else {
            return null;
        }
    }


}
