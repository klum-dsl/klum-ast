/*
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

import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.common.CommonAstHelper;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.cloneParamsWithDefaultValues;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createPublicMethod;
import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_FINAL;
import static groovyjarjarasm.asm.Opcodes.ACC_PRIVATE;
import static groovyjarjarasm.asm.Opcodes.ACC_PROTECTED;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC;
import static org.codehaus.groovy.ast.ClassHelper.LIST_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.MAP_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callSuperX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.closureX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorSuperS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.isInstanceOfX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.notX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.param;
import static org.codehaus.groovy.ast.tools.GeneralUtils.params;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ternaryX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics;
import static org.codehaus.groovy.ast.tools.GenericsUtils.newClass;

class TemplateMethods {
    private static final String TEMPLATE_FIELD_NAME = "$TEMPLATE";
    public static final String WITH_TEMPLATE = "withTemplate";
    public static final String WITH_MULTIPLE_TEMPLATES = "withTemplates";
    public static final String COPY_FROM_TEMPLATE = "copyFromTemplate";
    public static final String CREATE_AS_TEMPLATE = "createAsTemplate";
    private DSLASTTransformation transformation;
    private ClassNode annotatedClass;
    private FieldNode keyField;
    private ClassNode templateClass;
    private ClassNode dslAncestor;
    private final InnerClassNode rwClass;

    public TemplateMethods(DSLASTTransformation transformation) {
        this.transformation = transformation;
        annotatedClass = transformation.annotatedClass;
        rwClass = transformation.rwClass;
        keyField = transformation.keyField;
        dslAncestor = DslAstHelper.getHighestAncestorDSLObject(annotatedClass);
    }

    public void invoke() {
        addTemplateFieldToAnnotatedClass();
        createImplementationForAbstractClassIfNecessary();
        createAsTemplateMethods();
        copyFromTemplateMethod();
        copyFromMethod();
        withTemplateMethod();
        withTemplateConvenienceMethod();
        withTemplatesMapMethod();
        withTemplatesListMethod();
    }

    private void withTemplateMethod() {
        createPublicMethod(WITH_TEMPLATE)
                .mod(ACC_STATIC)
                .returning(ClassHelper.DYNAMIC_TYPE)
                .param(newClass(dslAncestor), "template")
                .closureParam("closure")
                .declareVariable("oldTemplate", getTemplateValueExpression())
                .statement(
                        new TryCatchStatement(
                                block(
                                        stmt(setTemplateValueExpression(varX("template"))),
                                        returnS(callX(varX("closure"), "call"))
                                ),
                                stmt(setTemplateValueExpression(varX("oldTemplate")))
                        )
                )
                .addTo(annotatedClass);
    }

    private void withTemplateConvenienceMethod() {
        createPublicMethod(WITH_TEMPLATE)
                .mod(ACC_STATIC)
                .returning(ClassHelper.DYNAMIC_TYPE)
                .namedParams("templateMap")
                .closureParam("closure")
                .declareVariable("templateInstance", callX(annotatedClass, CREATE_AS_TEMPLATE, args("templateMap")))
                .callMethod(classX(annotatedClass), WITH_TEMPLATE, args("templateInstance", "closure"))
                .addTo(annotatedClass);
    }

    private void withTemplatesMapMethod() {
        createPublicMethod(WITH_MULTIPLE_TEMPLATES)
                .mod(ACC_STATIC)
                .returning(ClassHelper.DYNAMIC_TYPE)
                .param(newClass(MAP_TYPE), "templates")
                .closureParam("closure")
                .statement(ifS(notX(varX("templates")), returnS(callX(varX("closure"), "call"))))
                .declareVariable("keys", callX(callX(varX("templates"), "keySet"), "asList"))
                .declareVariable("nextKey", callX(varX("keys"), "first"))
                .callMethod(callX(varX("keys"), "head"), WITH_TEMPLATE,
                        args(
                                callX(varX("templates"), "get", varX("nextKey")),
                                closureX(block(stmt(
                                        callX(
                                                annotatedClass,
                                                WITH_MULTIPLE_TEMPLATES,
                                                args(
                                                        callX(
                                                                varX("templates"), "subMap",
                                                                callX(varX("keys"), "tail")
                                                        ),
                                                        varX("closure")
                                                )
                                        )
                                )))
                        )
                )
                .addTo(annotatedClass);
    }

    private void withTemplatesListMethod() {
        createPublicMethod(WITH_MULTIPLE_TEMPLATES)
                .mod(ACC_STATIC)
                .returning(ClassHelper.DYNAMIC_TYPE)
                .param(newClass(LIST_TYPE), "templates")
                .closureParam("closure")
                .declareVariable("map",
                        callX(varX("templates"), "collectEntries",
                            closureX(block(
                                    declS(varX("clazz"), callX(varX("it"), "getClass")),
                                    declS(varX("className"), propX(varX("clazz"), "name")),
                                    declS(varX("targetClass"), ternaryX(callX(varX("className"), "endsWith", constX("$Template")), propX(varX("clazz"), "superclass"), varX("clazz"))),
                                    stmt(CommonAstHelper.listExpression(varX("clazz"), varX("it")))
                            ))
                        )
                )
                .callMethod(classX(annotatedClass), WITH_MULTIPLE_TEMPLATES, args("map", "closure"))
                .addTo(annotatedClass);
    }


    private void addTemplateFieldToAnnotatedClass() {
        annotatedClass.addField(TEMPLATE_FIELD_NAME, ACC_STATIC | ACC_FINAL | ACC_PROTECTED | ACC_SYNTHETIC, makeClassSafeWithGenerics(make(ThreadLocal.class), new GenericsType(annotatedClass)), ctorX(make(ThreadLocal.class)));
    }

    private void createImplementationForAbstractClassIfNecessary() {
        if (!DslAstHelper.isInstantiable(annotatedClass))
            createTemplateClass();
        else
            templateClass = annotatedClass;
    }

    private void copyFromMethod() {
        DslMethodBuilder templateApply = createPublicMethod("copyFrom")
                // highest ancestor is needed because otherwise wrong methods are called if only parent has a template
                // see DefaultValuesSpec."template for parent class affects child instances"()
                .param(newClass(dslAncestor), "template");

        ClassNode parentClass = annotatedClass.getSuperClass();

        if (isDSLObject(parentClass)) {
            templateApply.statement(callSuperX("copyFrom", args("template")));
        }

        templateApply.statement(ifS(notX(isInstanceOfX(varX("template"), annotatedClass)), returnS(constX(null))));

        for (FieldNode fieldNode : annotatedClass.getFields()) {
            if (transformation.shouldFieldBeIgnored(fieldNode)) continue;

            String fieldName = fieldNode.getName();
            String undefaultedGetter = "get$" + fieldName;

            if (CommonAstHelper.isCollection(fieldNode.getType()))
                templateApply.statement(
                        ifS(
                                callX(varX("template"), undefaultedGetter),
                                block(
                                        // we need an empty collection, since template replaces the field
                                        stmt(callX(propX(varX("this"), fieldName), "clear")),
                                        stmt(callX(propX(varX("this"), fieldName), "addAll", callX(varX("template"), undefaultedGetter)))
                                )
                        )
                );
            else if (CommonAstHelper.isMap(fieldNode.getType()))
                templateApply.statement(
                        ifS(
                                callX(varX("template"), undefaultedGetter),
                                block(
                                        stmt(callX(propX(varX("this"), fieldName), "clear")),
                                        stmt(callX(propX(varX("this"), fieldName), "putAll", callX(varX("template"), undefaultedGetter)))
                                )
                        )
                );
            else
                templateApply.statement(
                        ifS(
                                callX(varX("template"), undefaultedGetter),
                                assignS(propX(varX("this"), fieldName), callX(varX("template"), undefaultedGetter))
                        )
                );
        }

        templateApply.addTo(rwClass);
    }

    private void copyFromTemplateMethod() {
        DslMethodBuilder
                .createProtectedMethod(COPY_FROM_TEMPLATE)
                .mod(ACC_SYNTHETIC)
                .statementIf(transformation.dslParent != null, callSuperX(COPY_FROM_TEMPLATE))
                .callThis("copyFrom", args(getTemplateValueExpression()))
                .addTo(rwClass);
    }

    @NotNull
    private MethodCallExpression getTemplateValueExpression() {
        return callX(propX(classX(annotatedClass), TEMPLATE_FIELD_NAME), "get");
    }

    @NotNull
    private MethodCallExpression setTemplateValueExpression(Expression value) {
        return callX(propX(classX(annotatedClass), TEMPLATE_FIELD_NAME), "set", args(value));
    }

    private void createAsTemplateMethods() {
        createPublicMethod(CREATE_AS_TEMPLATE)
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .namedParams("values")
                .delegatingClosureParam(rwClass, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .declareVariable("result", keyField != null ? ctorX(templateClass, args(ConstantExpression.NULL)) : ctorX(templateClass))
                .callMethod(propX(varX("result"), "$rw"), COPY_FROM_TEMPLATE) // to apply templates of super classes
                .callMethod(propX(varX("result"), "$rw"), "manualValidation", constX(true))
                .assignS(propX(propX(varX("result"), KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS), "skipPostApply"), constX(true))
                .callMethod(propX(varX("result"), "$rw"), "manualValidation", constX(true))
                .callMethod("result", "apply", args("values", "closure"))
                .doReturn("result")
                .addTo(annotatedClass);

        createPublicMethod(CREATE_AS_TEMPLATE)
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .delegatingClosureParam(rwClass, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .doReturn(callX(annotatedClass, CREATE_AS_TEMPLATE, args(new MapExpression(), varX("closure"))))
                .addTo(annotatedClass);
    }

    private void createTemplateClass() {

        templateClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + "$Template",
                ACC_STATIC,
                newClass(annotatedClass));

        // TODO Remove once createTemplate is removed
        // templateClass.addField(TEMPLATE_FIELD_NAME, ACC_STATIC, newClass(templateClass), null);

        if (keyField != null) {
            templateClass.addConstructor(
                    0,
                    params(param(keyField.getType(), "key")),
                    CommonAstHelper.NO_EXCEPTIONS,
                    block(
                            ctorSuperS(args(constX(null)))
                    )
            );
        }

        templateClass.addField("$rw", ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, ctorX(rwClass, varX("this")));

        createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .namedParams("values")
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(rwClass, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .declareVariable("result", keyField != null ? ctorX(templateClass, args("name")) : ctorX(templateClass))
                .callMethod("result", "apply", args("values", "closure"))
                .doReturn("result")
                .addTo(templateClass);

        createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(rwClass, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .doReturn(callX(templateClass, "create",
                        keyField != null ?
                                args(new MapExpression(), varX("name"), varX("closure"))
                                : args(new MapExpression(), varX("closure"))
                ))
                .addTo(templateClass);

        List<MethodNode> abstractMethods = annotatedClass.getAbstractMethods();
        if (abstractMethods != null)
            for (MethodNode abstractMethod : abstractMethods)
                implementAbstractMethod(abstractMethod);

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
