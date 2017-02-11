package com.blackbuild.groovy.configdsl.transform.ast;

import groovy.lang.DelegatesTo;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.ast.tools.GenericsUtils;

import java.util.ArrayList;
import java.util.List;

import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.*;

public class MethodBuilder {

    private static final ClassNode[] EMPTY_EXCEPTIONS = new ClassNode[0];
    private static final Parameter[] EMPTY_PARAMETERS = new Parameter[0];
    private static final ClassNode DELEGATES_TO_ANNOTATION = make(DelegatesTo.class);
    public static final ClassNode DEPRECATED_NODE = ClassHelper.make(Deprecated.class);

    private int modifiers;
    private String name;
    private ClassNode returnType = ClassHelper.VOID_TYPE;
    private List<ClassNode> exceptions = new ArrayList<ClassNode>();
    private List<Parameter> parameters = new ArrayList<Parameter>();
    private boolean deprecated;
    private BlockStatement body = new BlockStatement();
    private boolean optional;

    public MethodBuilder(String name) {
        this.name = name;
    }

    public static MethodBuilder createPublicMethod(String name) {
        return new MethodBuilder(name).mod(Opcodes.ACC_PUBLIC);
    }

    public static MethodBuilder createOptionalPublicMethod(String name) {
        return new MethodBuilder(name).mod(Opcodes.ACC_PUBLIC).optional();
    }

    public static MethodBuilder createProtectedMethod(String name) {
        return new MethodBuilder(name).mod(Opcodes.ACC_PROTECTED);
    }

    public static MethodBuilder createPrivateMethod(String name) {
        return new MethodBuilder(name).mod(Opcodes.ACC_PRIVATE);
    }

    @SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
    public MethodNode addTo(ClassNode target) {

        Parameter[] parameterArray = this.parameters.toArray(EMPTY_PARAMETERS);
        MethodNode existing = target.getDeclaredMethod(name, parameterArray);

        if (existing != null) {
            if (optional)
                return existing;
            else
                throw new MethodBuilderException("Method " + existing + " is already defined.", existing);
        }

        MethodNode method = target.addMethod(
                name,
                modifiers,
                returnType,
                parameterArray,
                exceptions.toArray(EMPTY_EXCEPTIONS),
                body
        );

        if (deprecated)
            method.addAnnotation(new AnnotationNode(DEPRECATED_NODE));

        return method;
    }

    public MethodBuilder optional() {
        this.optional = true;
        return this;
    }

    public MethodBuilder returning(ClassNode returnType) {
        this.returnType = returnType;
        return this;
    }

    public MethodBuilder mod(int modifier) {
        modifiers |= modifier;
        return this;
    }

    public MethodBuilder param(Parameter param) {
        parameters.add(param);
        return this;
    }

    public MethodBuilder deprecated() {
        deprecated = true;
        return this;
    }

    public MethodBuilder namedParams(String name) {
        return param(makeClassSafeWithGenerics(ClassHelper.MAP_TYPE, new GenericsType(ClassHelper.STRING_TYPE), new GenericsType(ClassHelper.OBJECT_TYPE)), name);
    }

    public MethodBuilder applyNamedParams(String name) {
        VariableScope scope = new VariableScope();
        ClosureExpression applyClosure = new ClosureExpression(Parameter.EMPTY_ARRAY,
                block(scope,
                        new ExpressionStatement(
                                new MethodCallExpression(
                                        varX("this"),
                                        "invokeMethod",
                                        args(propX(varX("it"), "key"), propX(varX("it"), "value"))
                                )
                        )
                )
        );
        applyClosure.setVariableScope(scope);

        callMethod(name, "each",
                args(
                        applyClosure
                )
        );

        return this;
    }

    public MethodBuilder closureParam(String name) {
        param(GeneralUtils.param(GenericsUtils.nonGeneric(ClassHelper.CLOSURE_TYPE), name));
        return this;
    }


    public MethodBuilder classParam(String name, ClassNode upperBound) {
        return param(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(upperBound)), name);
    }

    public MethodBuilder stringParam(String name) {
        return param(ClassHelper.STRING_TYPE, name);
    }

    public MethodBuilder optionalStringParam(String name, Object addIfNotNull) {
        if (addIfNotNull != null)
            stringParam(name);
        return this;
    }

    public MethodBuilder objectParam(String name) {
        return param(ClassHelper.OBJECT_TYPE, name);
    }

    public MethodBuilder param(ClassNode type, String name) {
        return param(new Parameter(type, name));
    }

    public MethodBuilder arrayParam(ClassNode type, String name) {
        return param(new Parameter(type.makeArray(), name));
    }

    public MethodBuilder delegatingClosureParam(ClassNode delegationTarget) {
        VariableScope scope = new VariableScope();
        ClosureExpression closureExpression = new ClosureExpression(Parameter.EMPTY_ARRAY, new BlockStatement(new ArrayList<Statement>(), scope));
        closureExpression.setVariableScope(scope);
        Parameter param = GeneralUtils.param(
                nonGeneric(ClassHelper.CLOSURE_TYPE),
                "closure",
                closureExpression
        );
        param.addAnnotation(createDelegatesToAnnotation(delegationTarget));
        return param(param);
    }

    private AnnotationNode createDelegatesToAnnotation(ClassNode target) {
        AnnotationNode result = new AnnotationNode(DELEGATES_TO_ANNOTATION);
        result.setMember("value", classX(target));
        return result;
    }

    public MethodBuilder statement(Statement statement) {
        body.addStatement(statement);
        return this;
    }

    public MethodBuilder assignToProperty(String propertyName, Expression value) {
        String[] split = propertyName.split("\\.", 2);
        if (split.length == 1)
            return assignS(propX(varX("this"), propertyName), value);

        return assignS(propX(varX(split[0]), split[1]), value);
    }

    public MethodBuilder assignS(Expression target, Expression value) {
        return statement(GeneralUtils.assignS(target, value));
    }

    public MethodBuilder optionalAssignPropertyFromPropertyS(String target, String targetProperty, String value, String valueProperty, Object marker) {
        if (marker != null)
            assignS(propX(varX(target), targetProperty), propX(varX(value), valueProperty));
        return this;
    }

    public MethodBuilder optionalAssignThisToPropertyS(String target, String targetProperty) {
        if (targetProperty != null)
            return callMethod(varX(target), "setProperty", args(constX(targetProperty), varX("this")));
        return this;
    }

    public MethodBuilder declareVariable(String target, Expression init) {
        return statement(GeneralUtils.declS(varX(target), init));
    }

    public MethodBuilder callMethod(Expression receiver, String methodName) {
        return callMethod(receiver, methodName, MethodCallExpression.NO_ARGUMENTS);
    }

    public MethodBuilder callMethod(String receiverName, String methodName) {
        return callMethod(varX(receiverName), methodName);
    }

    public MethodBuilder callMethod(Expression receiver, String methodName, Expression args) {
        return statement(callX(receiver, methodName, args));
    }

    public MethodBuilder callMethod(String receiverName, String methodName, Expression args) {
        return callMethod(varX(receiverName), methodName, args);
    }

    public MethodBuilder callThis(String methodName, Expression args) {
        return callMethod("this", methodName, args);
    }

    public MethodBuilder callThis(String methodName) {
        return callMethod("this", methodName);
    }

    @Deprecated
    public MethodBuilder println(Expression args) {
        return callThis("println", args);
    }

    @Deprecated
    public MethodBuilder println(String string) {
        return callThis("println", constX(string));
    }

    public MethodBuilder statement(Expression expression) {
        return statement(stmt(expression));
    }

    public MethodBuilder doReturn(String varName) {
        return doReturn(varX(varName));
    }

    public MethodBuilder doReturn(Expression expression) {
        return statement(returnS(expression));
    }

    public MethodBuilder callValidationOn(String target) {
        return callValidationMethodOn(varX(target));
    }

    private MethodBuilder callValidationMethodOn(Expression targetX) {
        return statement(ifS(notX(propX(targetX,"$manualValidation")), callX(targetX, DSLASTTransformation.VALIDATE_METHOD)));
    }

    VariableScope getVariableScope() {
        if (body == null)
            body = new BlockStatement();

        return body.getVariableScope();
    }
}
