package com.blackbuild.groovy.configdsl.transform.ast;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.runtime.MethodClosure;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.createClosureExpression;
import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
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
        copyFromTemplateMethod();
        copyFromMethod();
        withTemplateMethod();
        withTemplatesMethod();
    }

    private void withTemplateMethod() {
        MethodBuilder.createPublicMethod("withTemplate")
                .mod(ACC_STATIC)
                .param(newClass(dslAncestor), "template")
                .closureParam("closure")
                .declareVariable("oldTemplate", getTemplateValueExpression())
                .statement(
                        new TryCatchStatement(
                                block(
                                        stmt(setTemplateValueExpression(varX("template"))),
                                        stmt(callX(varX("closure"), "call"))
                                ),
                                stmt(setTemplateValueExpression(varX("oldTemplate")))
                        )
                )
                .addTo(annotatedClass);
    }

    private void withTemplatesMethod() {
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
                                    stmt(callThisX("println", varX("t"))),
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

    private void addTemplateFieldToAnnotatedClass() {
        annotatedClass.addField(TEMPLATE_FIELD_NAME, ACC_STATIC, makeClassSafeWithGenerics(make(ThreadLocal.class), new GenericsType(annotatedClass)), ctorX(make(ThreadLocal.class)));
    }

    private void createImplementationForAbstractClassIfNecessary() {
        if (ASTHelper.isAbstract(annotatedClass))
            templateClass = createTemplateClass();
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

        if (ASTHelper.isDSLObject(parentClass)) {
            templateApply.statement(ifS(
                    notX(isInstanceOfX(varX("template"), annotatedClass)),
                    returnS(callSuperX("copyFrom", args(callX(GeneralUtils.propX(classX(parentClass), TEMPLATE_FIELD_NAME), "get"))))
                    )
            );

            templateApply.statement(callSuperX("copyFrom", args("template")));
        } else {
            templateApply.statement(ifS(notX(isInstanceOfX(varX("template"), annotatedClass)), returnS(varX("this"))));
        }

        for (FieldNode fieldNode : annotatedClass.getFields()) {
            if (transformation.shouldFieldBeIgnored(fieldNode)) continue;

            if (ASTHelper.isListOrMap(fieldNode.getType()))
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
        MethodBuilder.createPublicMethod("copyFromTemplate")
                .deprecated()
                .returning(newClass(annotatedClass))
                .doReturn(callThisX("copyFrom", args(getTemplateValueExpression())))
                .addTo(annotatedClass);
    }

    @NotNull
    private MethodCallExpression getTemplateValueExpression() {
        return callX(GeneralUtils.propX(classX(annotatedClass), TEMPLATE_FIELD_NAME), "get");
    }

    @NotNull
    private MethodCallExpression setTemplateValueExpression(Expression value) {
        return callX(GeneralUtils.propX(classX(annotatedClass), TEMPLATE_FIELD_NAME), "set", args(value));
    }

    private void createTemplateMethod() {
        MethodBuilder.createPublicMethod("createTemplate")
                .deprecated()
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .delegatingClosureParam(annotatedClass)
                .callMethod(GeneralUtils.propX(varX("this"), TEMPLATE_FIELD_NAME), "remove")
                .declareVariable("result", keyField != null ? ctorX(templateClass, args(ConstantExpression.NULL)) : ctorX(templateClass))
                .callMethod("result", "copyFromTemplate")
                .callMethod("result", "apply", varX("closure"))
                .callMethod(GeneralUtils.propX(varX("this"), TEMPLATE_FIELD_NAME), "set", args("result"))
                .doReturn("result")
                .addTo(annotatedClass);
    }

    private ClassNode createTemplateClass() {

        InnerClassNode contextClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + "$Template",
                ACC_STATIC,
                newClass(annotatedClass));

        contextClass.addField(TEMPLATE_FIELD_NAME, ACC_STATIC, newClass(contextClass), null);

        if (keyField != null) {
            contextClass.addConstructor(
                    0,
                    params(param(keyField.getType(), "key")),
                    DSLASTTransformation.NO_EXCEPTIONS,
                    block(
                            ctorSuperS(args(constX(null)))
                    )
            );
        }

        List<MethodNode> abstractMethods = annotatedClass.getAbstractMethods();
        if (abstractMethods != null) {
            for (MethodNode abstractMethod : abstractMethods) {
                implementAbstractMethod(contextClass, abstractMethod);
            }
        }

        annotatedClass.getModule().addClass(contextClass);

        return contextClass;
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
