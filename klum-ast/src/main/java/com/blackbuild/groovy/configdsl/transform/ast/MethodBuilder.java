/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.ast.tools.GenericsUtils;

import java.util.ArrayList;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.hasAnnotation;
import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.*;

public final class MethodBuilder extends AbstractMethodBuilder<MethodBuilder> {

    private final List<Parameter> parameters = new ArrayList<>();
    private BlockStatement body = new BlockStatement();

    private MethodBuilder(String name) {
        super(name);
    }

    public static MethodBuilder createMethod(String name) {
        return new MethodBuilder(name);
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

    static MethodBuilder createMethodFromClosure(String name, ClassNode type, ClosureExpression closureExpression, Expression delegate, Expression parameter) {
        String closureVariable = "closure";
        return createPrivateMethod(name)
                .returning(type)
                .optional()
                .declareVariable(closureVariable, closureExpression)
                .assignS(propX(varX(closureVariable), "delegate"), delegate)
                .assignS(
                        propX(varX(closureVariable), "resolveStrategy"),
                        constX(Closure.DELEGATE_ONLY)
                )
                .doReturn(callX(
                        varX(closureVariable),
                        "call",
                        parameter != null ? parameter : MethodCallExpression.NO_ARGUMENTS)
                );
    }

    public MethodBuilder forS(Parameter variable, Expression collection, Statement... code) {
        return statement(new ForStatement(variable, collection, block(code)));
    }

    public MethodBuilder forS(Parameter variable, String collection, Statement... code) {
        return forS(variable, varX(collection), code);
    }

    public MethodBuilder delegateToProxy(String methodName, Expression... args) {
        MethodCallExpression callExpression = callX(
                varX(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS),
                methodName,
                args(args)
        );
        if (returnType != null && !returnType.equals(ClassHelper.VOID_TYPE))
            doReturn(callExpression);
        else
            statement(callExpression);

        return this;
    }

    public MethodBuilder setOwners(String target) {
        return callMethod(
                propX(varX(target), KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS),
                "setOwners",
                varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS)
        );
    }

    public MethodBuilder setOwnersIf(String target, boolean apply) {
        if (apply)
            return setOwners(target);
        return this;
    }

    public MethodBuilder params(Parameter... params) {
        for (Parameter param : params) {
            param(param);
        }
        return this;
    }

    public MethodBuilder params(List<Parameter>params) {
        for (Parameter param : params) {
            param(param);
        }
        return this;
    }

    public MethodBuilder decoratedParam(FieldNode field, ClassNode type, String name) {

        Parameter param = GeneralUtils.param(type, name);

        List<AnnotationNode> annotations = field.getAnnotations();

        for (AnnotationNode annotation : annotations)
            if (hasAnnotation(annotation.getClassNode(), PARAMETER_ANNOTATION_TYPE))
                copyAnnotationsFromMembersToParam(annotation, param);

        return param(param);
    }

    public void copyAnnotationsFromMembersToParam(AnnotationNode source, AnnotatedNode target) {
        for (Expression annotationMember : source.getMembers().values()) {
            if (annotationMember instanceof AnnotationConstantExpression) {
                AnnotationNode annotationNode = (AnnotationNode) ((AnnotationConstantExpression) annotationMember).getValue();
                if (annotationNode.isTargetAllowed(AnnotationNode.PARAMETER_TARGET))
                    target.addAnnotation(annotationNode);
            }
        }
    }

    public MethodBuilder optionalClassLoaderParam() {
        return param(CLASSLOADER_TYPE, "loader", callX(callX(THREAD_TYPE, "currentThread"), "getContextClassLoader"));
    }

    @Override
    protected Parameter[] getMethodParameters() {
        return this.parameters.toArray(EMPTY_PARAMETERS);
    }

    @Override
    protected Statement getMethodBody() {
        return body;
    }

    /**
     * Add a parameter to the method.
     */
    public MethodBuilder param(Parameter param) {
        parameters.add(param);
        return this;
    }

    /**
     * Adds a map entry to the method signature.
     */
    public MethodBuilder namedParams(String name) {
        GenericsType wildcard = new GenericsType(ClassHelper.OBJECT_TYPE);
        wildcard.setWildcard(true);
        return param(makeClassSafeWithGenerics(ClassHelper.MAP_TYPE, new GenericsType(ClassHelper.STRING_TYPE), wildcard), name);
    }

    /**
     * Adds a parameter of type closure.
     */
    public MethodBuilder closureParam(String name) {
        param(GeneralUtils.param(GenericsUtils.nonGeneric(ClassHelper.CLOSURE_TYPE), name));
        return this;
    }

    /**
     * Adds a class parameter which is also used as the target for the {@link DelegatesTo} annotation of a delegating closure parameter
     * @param name Name of the parameter
     * @param upperBound The base class for the class parameter
     */
    public MethodBuilder delegationTargetClassParam(String name, ClassNode upperBound) {
        Parameter param = GeneralUtils.param(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(upperBound)), name);
        param.addAnnotation(new AnnotationNode(DELEGATES_TO_TARGET_ANNOTATION));
        return param(param);
    }

    /**
     * Adds a class parameter without delegation.
     * @param name The name of the parameter
     * @param upperBound The base class for the class parameter
     * @return the instance
     */
    public MethodBuilder simpleClassParam(String name, ClassNode upperBound) {
        return param(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(upperBound)), name);
    }

    /**
     * Adds a string paramter with the given name.
     * @param name The name of the string parameter.
     */
    public MethodBuilder stringParam(String name) {
        return param(ClassHelper.STRING_TYPE, name);
    }

    /**
     * Convenience method to optionally add a string parameter. The parameter is only added, if 'addIfNotNull' is not null.
     * @param name The name of the parameter.
     * @param doAdd If this parameter is null, the method does nothing
     */
    public MethodBuilder optionalStringParam(String name, boolean doAdd) {
        if (doAdd)
            return stringParam(name);
        return this;
    }

    /**
     * Add a generic object parameter.
     * @param name The name of the parameter
     */
    public MethodBuilder objectParam(String name) {
        return param(ClassHelper.OBJECT_TYPE, name);
    }

    /**
     * Add a parameter to the method signature.
     * @param type The type of the parameter
     * @param name The name of the parameter
     */
    public MethodBuilder param(ClassNode type, String name) {
        return param(new Parameter(type, name));
    }

    /**
     * Add a parameter to the method signature with an optional default value.
     * @param type The type of the parameter
     * @param name The name of the parameter
     * @param defaultValue An expression to use for the default value for the parameter. Can be null.
     */
    public MethodBuilder param(ClassNode type, String name, Expression defaultValue) {
        return param(new Parameter(type, name, defaultValue));
    }

    /**
     * Adds an array parameter with the given type.
     * @param type The type of the array elements
     * @param name The name of the parameter
     */
    public MethodBuilder arrayParam(ClassNode type, String name) {
        return param(new Parameter(type.makeArray(), name));
    }

    /**
     * Use all parameters of the given source method as parameters to this method.
     * @param sourceMethod The source of the parameter list
     */
    public MethodBuilder cloneParamsFrom(MethodNode sourceMethod) {
        Parameter[] sourceParams = GeneralUtils.cloneParams(sourceMethod.getParameters());
        for (Parameter parameter : sourceParams) {
            param(parameter);
        }
        return this;
    }

    public MethodBuilder delegatingClosureParam(ClassNode delegationTarget, ClosureDefaultValue defaultValue) {
        ClosureExpression emptyClosure = null;
        if (defaultValue == ClosureDefaultValue.EMPTY_CLOSURE) {
            emptyClosure = closureX(block());
        }
        Parameter param = GeneralUtils.param(
                nonGeneric(ClassHelper.CLOSURE_TYPE),
                "closure",
                emptyClosure
        );
        param.addAnnotation(createDelegatesToAnnotation(delegationTarget));
        return param(param);
    }

    /**
     * Creates a delegating closure parameter that delegates to the type parameter of an existing class parameter.
     */
    public MethodBuilder delegatingClosureParam() {
        return delegatingClosureParam(null, ClosureDefaultValue.EMPTY_CLOSURE);
    }

    private AnnotationNode createDelegatesToAnnotation(ClassNode target) {
        AnnotationNode result = new AnnotationNode(DELEGATES_TO_ANNOTATION);
        if (target != null)
            result.setMember("value", classX(target));
        else
            result.setMember("genericTypeIndex", constX(0));
        result.setMember("strategy", constX(Closure.DELEGATE_ONLY));
        return result;
    }

    public MethodBuilder statement(Statement statement) {
        body.addStatement(statement);
        return this;
    }

    public MethodBuilder body(BlockStatement body) {
        this.body = body;
        return this;
    }

    public MethodBuilder statementIf(boolean condition, Statement statement) {
        if (condition)
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

    public MethodBuilder declareVariable(String varName, Expression init) {
        return statement(GeneralUtils.declS(varX(varName), init));
    }

    public MethodBuilder optionalDeclareVariable(String varName, Expression init, boolean doAdd) {
        if (doAdd)
            statement(GeneralUtils.declS(varX(varName), init));
        return this;
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

    /**
     * Insert a println statement into the method body. This is only for debug purposes.
     * @param args The expression to print
     * @return the MethodBuilderInstance
     * @deprecated Debug only
     */
    @Deprecated
    public MethodBuilder println(Expression args) {
        return callThis("println", args);
    }

    /**
     * Insert a println statement into the method body. This is only for debug purposes.
     * @param string The string to print
     * @return the MethodBuilderInstance
     * @deprecated Debug only
     */
    @Deprecated
    public MethodBuilder println(String string) {
        return callThis("println", constX(string));
    }

    public MethodBuilder statement(Expression expression) {
        return statement(stmt(expression));
    }

    public MethodBuilder statementIf(boolean condition, Expression expression) {
        return statementIf(condition, stmt(expression));
    }

    public MethodBuilder doReturn(String varName) {
        return doReturn(varX(varName));
    }

    public MethodBuilder doReturn(Expression expression) {
        return statement(returnS(expression));
    }

    public enum ClosureDefaultValue { NONE, EMPTY_CLOSURE }
}
