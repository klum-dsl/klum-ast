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

import com.blackbuild.klum.ast.util.TemplateManager;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.runtime.StringGroovyMethods;

import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.cloneParamsWithDefaultValues;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createPublicMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.ProxyMethodBuilder.createFactoryMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.ProxyMethodBuilder.createProxyMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.ProxyMethodBuilder.createTemplateMethod;
import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_FINAL;
import static groovyjarjarasm.asm.Opcodes.ACC_PRIVATE;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC;
import static org.codehaus.groovy.ast.ClassHelper.LIST_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.MAP_TYPE;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorSuperS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.newClass;

class TemplateMethods {
    public static final String WITH_TEMPLATE = "withTemplate";
    public static final String WITH_MULTIPLE_TEMPLATES = "withTemplates";
    public static final String COPY_FROM_TEMPLATE = "copyFromTemplate";
    public static final String CREATE_AS_TEMPLATE = "createAsTemplate";
    private final ClassNode annotatedClass;
    private final FieldNode keyField;
    private ClassNode templateClass;
    private final ClassNode dslAncestor;
    private final InnerClassNode rwClass;

    public TemplateMethods(DSLASTTransformation transformation) {
        annotatedClass = transformation.annotatedClass;
        rwClass = transformation.rwClass;
        keyField = transformation.keyField;
        dslAncestor = DslAstHelper.getHighestAncestorDSLObject(annotatedClass);
    }

    public void invoke() {
        createImplementationForAbstractClassIfNecessary();
        createAsTemplateMethods();
        copyFromMethod();
        withTemplateMethod();
        withTemplateConvenienceMethod();
        withTemplatesMapMethod();
        withTemplatesListMethod();
    }

    private void withTemplateMethod() {
        createTemplateMethod(WITH_TEMPLATE)
                .constantClassParam(annotatedClass)
                .param(newClass(dslAncestor), "template")
                .closureParam("closure", null)
                .addTo(annotatedClass);
    }

    private void withTemplateConvenienceMethod() {
        createTemplateMethod(WITH_TEMPLATE)
                .constantClassParam(annotatedClass)
                .nonOptionalNamedParams("templateMap")
                .closureParam("closure", null)
                .addTo(annotatedClass);
    }

    @Deprecated
    private void withTemplatesMapMethod() {
        String templatesVarName = "templates";
        String closureParamName = "closure";
        createPublicMethod(WITH_MULTIPLE_TEMPLATES)
                .mod(ACC_STATIC)
                .deprecated()
                .returning(ClassHelper.DYNAMIC_TYPE)
                .param(newClass(MAP_TYPE), templatesVarName)
                .closureParam(closureParamName)
                .doReturn(callX(classX(TemplateManager.class), WITH_MULTIPLE_TEMPLATES, args(templatesVarName, closureParamName)))
                .addTo(annotatedClass);
    }

    private void withTemplatesListMethod() {
        createPublicMethod(WITH_MULTIPLE_TEMPLATES)
                .mod(ACC_STATIC)
                .deprecated()
                .returning(ClassHelper.DYNAMIC_TYPE)
                .param(newClass(LIST_TYPE), "templates")
                .closureParam("closure")
                .doReturn(callX(classX(TemplateManager.class), WITH_MULTIPLE_TEMPLATES, args("templates", "closure")))
                .addTo(annotatedClass);
    }

    private void createImplementationForAbstractClassIfNecessary() {
        if (!DslAstHelper.isInstantiable(annotatedClass))
            createTemplateClass();
        else
            templateClass = annotatedClass;
    }

    private void copyFromMethod() {
        createProxyMethod("copyFrom")
                .mod(ACC_PUBLIC)
                .param(newClass(dslAncestor), "template")
                .addTo(rwClass);
     }

    private void createAsTemplateMethods() {
        createFactoryMethod(CREATE_AS_TEMPLATE, annotatedClass)
                .namedParams("values")
                .delegatingClosureParam(rwClass)
                .addTo(annotatedClass);
    }

    private void createTemplateClass() {

        templateClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + "$Template",
                ACC_STATIC | ACC_SYNTHETIC | ACC_PUBLIC,
                newClass(annotatedClass));

        if (keyField != null) {
            templateClass.addConstructor(
                    ACC_SYNTHETIC | ACC_PUBLIC,
                    Parameter.EMPTY_ARRAY,
                    CommonAstHelper.NO_EXCEPTIONS,
                    block(
                            ctorSuperS(args(constX(null)))
                    )
            );
        }

        templateClass.addField("$rw", ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, ctorX(rwClass, varX("this")));

        List<MethodNode> abstractMethods = annotatedClass.getAbstractMethods();
        if (abstractMethods != null)
            abstractMethods.forEach(this::implementAbstractMethod);

        annotatedClass.getModule().addClass(templateClass);
    }

    private void implementAbstractMethod(MethodNode abstractMethod) {
        if (methodIsAnAlreadyImplementedInterfaceMethod(abstractMethod))
            return;
        templateClass.addMethod(
                abstractMethod.getName(),
                abstractMethod.getModifiers() ^ ACC_ABSTRACT,
                abstractMethod.getReturnType(),
                cloneParamsWithDefaultValues(abstractMethod.getParameters()),
                abstractMethod.getExceptions(),
                block()
        );
    }

    @SuppressWarnings("RedundantIfStatement")
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
        if (ClassHelper.VOID_TYPE==method.getReturnType())
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
