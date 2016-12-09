package com.blackbuild.groovy.configdsl.transform.ast;

import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.*;
import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics;
import static org.codehaus.groovy.ast.tools.GenericsUtils.newClass;

class TemplateMethods {
    private static final String TEMPLATE_FIELD_NAME = "$TEMPLATE";
    private DSLASTTransformation transformation;
    private ClassNode annotatedClass;
    private FieldNode keyField;
    private ClassNode templateClass;
    private ClassNode dslAncestor;

    public TemplateMethods(DSLASTTransformation transformation) {
        this.transformation = transformation;
        annotatedClass = transformation.annotatedClass;
        keyField = transformation.keyField;
        dslAncestor = ASTHelper.getHighestAncestorDSLObject(annotatedClass);
    }

    public void invoke() {
        addTemplateFieldToAnnotatedClass();
        createImplementationForAbstractClassIfNecessary();
        createTemplateMethod();
        makeTemplateMethod();
        copyFromTemplateMethod();
        copyFromMethod();
        withTemplateMethod();
        withTemplateConvenienceMethod();
        withTemplatesMapMethod();
        withTemplatesListMethod();
    }

    private void withTemplateMethod() {
        MethodBuilder.createPublicMethod("withTemplate")
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
        MethodBuilder.createPublicMethod("withTemplate")
                .mod(ACC_STATIC)
                .returning(ClassHelper.DYNAMIC_TYPE)
                .param(newClass(MAP_TYPE), "templateMap")
                .closureParam("closure")
                .declareVariable("templateInstance", callX(annotatedClass, "makeTemplate", args("templateMap")))
                .callMethod(classX(annotatedClass), "withTemplate", args("templateInstance", "closure"))
                .addTo(annotatedClass);
    }

    private void withTemplatesMapMethod() {
        MethodBuilder.createPublicMethod("withTemplates")
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
                                                    "withTemplates",
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
                .callMethod(callX(varX("keys"), "head"), "withTemplate",
                        args(
                                callX(varX("templates"), "get", callX(varX("keys"), "head")),
                                varX("curried")
                        )
                )
                .addTo(annotatedClass);
    }

    private void withTemplatesListMethod() {
        MethodBuilder.createPublicMethod("withTemplates")
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
                .callMethod(classX(annotatedClass), "withTemplates", args("map", "closure"))
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
                .returning(newClass(annotatedClass))
                // highest ancestor is needed because otherwise wrong methods are called if only parent has a template
                // see DefaultValuesSpec."template for parent class affects child instances"()
                .param(newClass(dslAncestor), "template");

        ClassNode parentClass = annotatedClass.getSuperClass();

        if (isDSLObject(parentClass)) {
            templateApply.statement(callSuperX("copyFrom", args("template")));
        }

        templateApply.statement(ifS(notX(isInstanceOfX(varX("template"), annotatedClass)), returnS(varX("this"))));

        for (FieldNode fieldNode : annotatedClass.getFields()) {
            if (transformation.shouldFieldBeIgnored(fieldNode)) continue;

            if (isListOrMap(fieldNode.getType()))
                templateApply.statement(
                        ifS(
                                propX(varX("template"), fieldNode.getName()),
                                assignS(propX(varX("this"), fieldNode.getName()), callX(propX(varX("template"), fieldNode.getName()), "clone"))
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

        templateApply
                .doReturn("this")
                .addTo(annotatedClass);
    }

    private void copyFromTemplateMethod() {
        MethodBuilder copyFromTemplate = MethodBuilder.createPublicMethod("copyFromTemplate")
                .deprecated()
                .returning(newClass(annotatedClass));

        if (transformation.dslParent != null) {
            copyFromTemplate.statement(callSuperX("copyFromTemplate"));
        }
        copyFromTemplate
                .doReturn(callThisX("copyFrom", args(getTemplateValueExpression())))
                .addTo(annotatedClass);
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
        MethodBuilder.createPublicMethod("createTemplate")
                .deprecated()
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .delegatingClosureParam(annotatedClass)
                .callMethod(propX(varX("this"), TEMPLATE_FIELD_NAME), "remove")
                .declareVariable("result", callX(annotatedClass, "makeTemplate", args("closure")))
                .callMethod(propX(varX("this"), TEMPLATE_FIELD_NAME), "set", varX("result"))
                .doReturn("result")
                .addTo(annotatedClass);
    }

    private void makeTemplateMethod() {
        MethodBuilder.createPublicMethod("makeTemplate")
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .namedParams("values")
                .delegatingClosureParam(annotatedClass)
                .declareVariable("result", keyField != null ? ctorX(templateClass, args(ConstantExpression.NULL)) : ctorX(templateClass))
                .callMethod("result", "copyFromTemplate") // to apply templates of super classes
                .callMethod("result", "manualValidation", constX(true))
                .callMethod("result", "apply", args("values", "closure"))
                .doReturn("result")
                .addTo(annotatedClass);

        MethodBuilder.createPublicMethod("makeTemplate")
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .delegatingClosureParam(annotatedClass)
                .doReturn(callX(annotatedClass, "makeTemplate", args(new MapExpression(), varX("closure"))))
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
