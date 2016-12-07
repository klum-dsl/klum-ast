package com.blackbuild.groovy.configdsl.transform.ast;

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.tools.GeneralUtils;

import java.util.List;

import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics;
import static org.codehaus.groovy.ast.tools.GenericsUtils.newClass;

/**
 * Created by snpaux on 07.12.2016.
 */
class TemplateMethods {
    private static final String TEMPLATE_FIELD_NAME = "$TEMPLATE";
    private DSLASTTransformation transformation;
    private ClassNode annotatedClass;
    private FieldNode keyField;
    private ClassNode templateClass;

    public TemplateMethods(DSLASTTransformation transformation) {
        this.transformation = transformation;
    }

    public void invoke() {
        annotatedClass = transformation.annotatedClass;
        keyField = transformation.keyField;

        annotatedClass.addField(TEMPLATE_FIELD_NAME, ACC_STATIC, makeClassSafeWithGenerics(make(ThreadLocal.class), new GenericsType(annotatedClass)), ctorX(make(ThreadLocal.class)));

        if (ASTHelper.isAbstract(annotatedClass))
            templateClass = createTemplateClass();
        else
            templateClass = annotatedClass;

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

        MethodBuilder.createPublicMethod("copyFromTemplate")
                .deprecated()
                .returning(newClass(annotatedClass))
                .doReturn(callThisX("copyFrom", args(callX(GeneralUtils.propX(GeneralUtils.classX(annotatedClass), TEMPLATE_FIELD_NAME), "get"))))
                .addTo(annotatedClass);

        MethodBuilder templateApply = MethodBuilder.createPublicMethod("copyFrom")
                .returning(newClass(annotatedClass))
                // highest ancestor is needed because otherwise wrong methods are called if only parent has a template
                // see DefaultValuesSpec."template for parent class affects child instances"()
                .param(newClass(ASTHelper.getHighestAncestorDSLObject(annotatedClass)), "template");

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
