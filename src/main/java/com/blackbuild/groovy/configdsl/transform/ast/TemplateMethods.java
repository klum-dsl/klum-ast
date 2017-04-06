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

import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.*;
import static groovyjarjarasm.asm.Opcodes.*;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics;
import static org.codehaus.groovy.ast.tools.GenericsUtils.newClass;

class TemplateMethods {
    private static final String TEMPLATE_FIELD_NAME = "$TEMPLATE";
    public static final String WITH_TEMPLATE = "withTemplate";
    public static final String WITH_MULTIPLE_TEMPLATES = "withTemplates";
    public static final String COPY_FROM_TEMPLATE = "copyFromTemplate";
    public static final String CREATE_TEMPLATE = "createTemplate";
    public static final String MAKE_TEMPLATE = "makeTemplate";
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
        dslAncestor = ASTHelper.getHighestAncestorDSLObject(annotatedClass);
    }

    public void invoke() {
        addTemplateFieldToAnnotatedClass();
        createImplementationForAbstractClassIfNecessary();
        createTemplateMethod();
        createAsTemplateMethods();
        copyFromTemplateMethod();
        copyFromMethod();
        withTemplateMethod();
        withTemplateConvenienceMethod();
        withTemplatesMapMethod();
        withTemplatesListMethod();
    }

    private void withTemplateMethod() {
        MethodBuilder.createPublicMethod(WITH_TEMPLATE)
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
        MethodBuilder.createPublicMethod(WITH_TEMPLATE)
                .mod(ACC_STATIC)
                .returning(ClassHelper.DYNAMIC_TYPE)
                .param(newClass(MAP_TYPE), "templateMap")
                .closureParam("closure")
                .declareVariable("templateInstance", callX(annotatedClass, CREATE_AS_TEMPLATE, args("templateMap")))
                .callMethod(classX(annotatedClass), WITH_TEMPLATE, args("templateInstance", "closure"))
                .addTo(annotatedClass);
    }

    private void withTemplatesMapMethod() {
        MethodBuilder.createPublicMethod(WITH_MULTIPLE_TEMPLATES)
                .mod(ACC_STATIC)
                .returning(ClassHelper.DYNAMIC_TYPE)
                .param(newClass(MAP_TYPE), "templates")
                .closureParam("closure")
                .statement(ifS(notX(varX("templates")), returnS(callX(varX("closure"), "call"))))
                .declareVariable("keys", callX(callX(varX("templates"), "keySet"), "asList"))
                .declareVariable("recursion",
                        // This is a dirty hack. Since I did not get the Variable Scope to be propagated into the closure
                        // I create a closure using three parameters (templates, keys, closure), which are passed as parameters
                        // and then we curry the closure using the actual values.
                        createClosureExpression(
                            params(
                                    param(newClass(MAP_TYPE), "t"),
                                    param(newClass(LIST_TYPE), "k"),
                                    param(newClass(CLOSURE_TYPE), "c")
                            ),
                            block(
                                    stmt(
                                            callX(
                                                    annotatedClass,
                                                    WITH_MULTIPLE_TEMPLATES,
                                                    args(
                                                            callX(
                                                                    varX("t"), "subMap",
                                                                    callX(varX("k"), "tail")
                                                            ),
                                                            varX("c")
                                                    )
                                            )
                                    )
                            )
                        )
                )
                .declareVariable("curried", callX(varX("recursion"), "curry", args("templates", "keys", "closure")))
                .callMethod(callX(varX("keys"), "head"), WITH_TEMPLATE,
                        args(
                                callX(varX("templates"), "get", callX(varX("keys"), "head")),
                                varX("curried")
                        )
                )
                .addTo(annotatedClass);
    }

    private void withTemplatesListMethod() {
        MethodBuilder.createPublicMethod(WITH_MULTIPLE_TEMPLATES)
                .mod(ACC_STATIC)
                .returning(ClassHelper.DYNAMIC_TYPE)
                .param(newClass(LIST_TYPE), "templates")
                .closureParam("closure")
                .declareVariable("map",
                        callX(varX("templates"), "collectEntries",
                            createClosureExpression(
                                    declS(varX("clazz"), callX(varX("it"), "getClass")),
                                    declS(varX("className"), propX(varX("clazz"), "name")),
                                    declS(varX("targetClass"), ternaryX(callX(varX("className"), "endsWith", constX("$Template")), propX(varX("clazz"), "superclass"), varX("clazz"))),
                                    stmt(listExpression(varX("clazz"), varX("it")))
                            )
                        )
                )
                .callMethod(classX(annotatedClass), WITH_MULTIPLE_TEMPLATES, args("map", "closure"))
                .addTo(annotatedClass);
    }


    private void addTemplateFieldToAnnotatedClass() {
        annotatedClass.addField(TEMPLATE_FIELD_NAME, ACC_STATIC, makeClassSafeWithGenerics(make(ThreadLocal.class), new GenericsType(annotatedClass)), ctorX(make(ThreadLocal.class)));
    }

    private void createImplementationForAbstractClassIfNecessary() {
        if (ASTHelper.isAbstract(annotatedClass))
            createTemplateClass();
        else
            templateClass = annotatedClass;
    }

    private void copyFromMethod() {
        MethodBuilder templateApply = MethodBuilder.createPublicMethod("copyFrom")
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

            if (isCollection(fieldNode.getType()))
                templateApply.statement(
                        ifS(
                                propX(varX("template"), fieldNode.getName()),
                                block(
                                        // we need
                                        stmt(callX(propX(varX("this"), fieldNode.getName()), "clear")),
                                        stmt(callX(propX(varX("this"), fieldNode.getName()), "addAll", propX(varX("template"), fieldNode.getName())))
                                )
                        )
                );
            else if (isMap(fieldNode.getType()))
                templateApply.statement(
                        ifS(
                                propX(varX("template"), fieldNode.getName()),
                                block(
                                        stmt(callX(propX(varX("this"), fieldNode.getName()), "clear")),
                                        stmt(callX(propX(varX("this"), fieldNode.getName()), "putAll", propX(varX("template"), fieldNode.getName())))
                                )
                        )
                );
            else
                templateApply.statement(
                        ifS(
                                propX(varX("template"), fieldNode.getName()),
                                assignS(propX(varX("this"), fieldNode.getName()), propX(varX("template"), fieldNode.getName()))
                        )
                );
        }

        templateApply.addTo(rwClass);
    }

    private void copyFromTemplateMethod() {
        MethodBuilder
                .createProtectedMethod(COPY_FROM_TEMPLATE)
                .deprecated()
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

    private void createTemplateMethod() {
        MethodBuilder.createPublicMethod(CREATE_TEMPLATE)
                .deprecated()
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .delegatingClosureParam(rwClass)
                .callMethod(propX(varX("this"), TEMPLATE_FIELD_NAME), "remove")
                .declareVariable("result", callX(annotatedClass, CREATE_AS_TEMPLATE, args("closure")))
                .callMethod(propX(varX("this"), TEMPLATE_FIELD_NAME), "set", varX("result"))
                .doReturn("result")
                .addTo(annotatedClass);
    }

    private void createAsTemplateMethods() {
        MethodBuilder.createPublicMethod(CREATE_AS_TEMPLATE)
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .namedParams("values")
                .delegatingClosureParam(rwClass)
                .declareVariable("result", keyField != null ? ctorX(templateClass, args(ConstantExpression.NULL)) : ctorX(templateClass))
                .callMethod(propX(varX("result"), "$rw"), COPY_FROM_TEMPLATE) // to apply templates of super classes
                .callMethod("result", "manualValidation", constX(true))
                .callMethod("result", "apply", args("values", "closure"))
                .doReturn("result")
                .addTo(annotatedClass);

        MethodBuilder.createPublicMethod(CREATE_AS_TEMPLATE)
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .delegatingClosureParam(annotatedClass)
                .doReturn(callX(annotatedClass, CREATE_AS_TEMPLATE, args(new MapExpression(), varX("closure"))))
                .addTo(annotatedClass);

        MethodBuilder.createPublicMethod(MAKE_TEMPLATE)
                .returning(newClass(annotatedClass))
                .deprecated()
                .mod(ACC_STATIC)
                .namedParams("values")
                .delegatingClosureParam(annotatedClass)
                .doReturn(callX(annotatedClass, CREATE_AS_TEMPLATE, args("values", "closure")))
                .addTo(annotatedClass);

        MethodBuilder.createPublicMethod(MAKE_TEMPLATE)
                .returning(newClass(annotatedClass))
                .deprecated()
                .mod(ACC_STATIC)
                .delegatingClosureParam(annotatedClass)
                .doReturn(callX(annotatedClass, CREATE_AS_TEMPLATE, args("closure")))
                .addTo(annotatedClass);
    }

    private void createTemplateClass() {

        templateClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + "$Template",
                ACC_STATIC,
                newClass(annotatedClass));

        // TODO Remove once createTemplate is removed
        templateClass.addField(TEMPLATE_FIELD_NAME, ACC_STATIC, newClass(templateClass), null);

        if (keyField != null) {
            templateClass.addConstructor(
                    0,
                    params(param(keyField.getType(), "key")),
                    DSLASTTransformation.NO_EXCEPTIONS,
                    block(
                            ctorSuperS(args(constX(null)))
                    )
            );
        }

        templateClass.addField("$rw", ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, ctorX(rwClass, varX("this")));

        MethodBuilder.createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .namedParams("values")
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(annotatedClass)
                .declareVariable("result", keyField != null ? ctorX(templateClass, args("name")) : ctorX(templateClass))
                .callMethod("result", "apply", args("values", "closure"))
                .doReturn("result")
                .addTo(templateClass);

        MethodBuilder.createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(annotatedClass)
                .doReturn(callX(templateClass, "create",
                        keyField != null ?
                                args(new MapExpression(), varX("name"), varX("closure"))
                                : args(new MapExpression(), varX("closure"))
                ))
                .addTo(templateClass);

        List<MethodNode> abstractMethods = annotatedClass.getAbstractMethods();
        if (abstractMethods != null) {
            for (MethodNode abstractMethod : abstractMethods) {
                implementAbstractMethod(templateClass, abstractMethod);
            }
        }

        annotatedClass.getModule().addClass(templateClass);
    }

    private void implementAbstractMethod(ClassNode target, MethodNode abstractMethod) {
        target.addMethod(
                abstractMethod.getName(),
                abstractMethod.getModifiers() ^ ACC_ABSTRACT,
                abstractMethod.getReturnType(),
                cloneParams(abstractMethod.getParameters()),
                abstractMethod.getExceptions(),
                block()
        );
    }

}
