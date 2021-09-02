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
package com.blackbuild.klum.common;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.ast.tools.GenericsUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.closureX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.buildWildcardType;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics;
import static org.codehaus.groovy.ast.tools.GenericsUtils.nonGeneric;

@SuppressWarnings("unchecked")
public abstract class GenericsMethodBuilder<T extends GenericsMethodBuilder> {

    public static final ClassNode DEPRECATED_NODE = ClassHelper.make(Deprecated.class);
    private static final ClassNode[] EMPTY_EXCEPTIONS = new ClassNode[0];
    private static final Parameter[] EMPTY_PARAMETERS = new Parameter[0];
    private static final ClassNode DELEGATES_TO_ANNOTATION = make(DelegatesTo.class);
    private static final ClassNode DELEGATES_TO_TARGET_ANNOTATION = make(DelegatesTo.Target.class);
    protected String name;
    protected Map<Object, Object> metadata = new HashMap<Object, Object>();
    private int modifiers;
    private ClassNode returnType = ClassHelper.VOID_TYPE;
    private List<ClassNode> exceptions = new ArrayList<ClassNode>();
    private List<Parameter> parameters = new ArrayList<Parameter>();
    private boolean deprecated;
    private BlockStatement body = new BlockStatement();
    private boolean optional;
    private ASTNode sourceLinkTo;

    protected GenericsMethodBuilder(String name) {
        this.name = name;
    }

    /**
     * Creates the actual method and adds it to the target class. If the target already contains a method with that
     * signature, either an exception is thrown or the method is silently dropped, depending on the presence of
     * the optional parameter.
     * @param target The class node to add to
     * @return The newly created method or the existing method if `optional` is set and a method with that signature
     * already exists
     */
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

        if (sourceLinkTo != null)
            method.setSourcePosition(sourceLinkTo);

        for (Map.Entry<Object, Object> entry : metadata.entrySet()) {
            method.putNodeMetaData(entry.getKey(), entry.getValue());
        }


        return method;
    }

    /**
     * Marks this method as optional. If set, {@link #addTo(ClassNode)} does not throw an error if the method already exists.
     */
    public T optional() {
        this.optional = true;
        return (T)this;
    }

    /**
     * Sets the return type of the generated method.
     * @param returnType The return type.
     */
    public T returning(ClassNode returnType) {
        this.returnType = returnType;
        return (T)this;
    }

    /**
     * Sets the modifiers as defined by {@link groovyjarjarasm.asm.Opcodes}.
     */
    public T mod(int modifier) {
        modifiers |= modifier;
        return (T)this;
    }

    /**
     * Add a parameter to the method.
     */
    public T param(Parameter param) {
        parameters.add(param);
        return (T)this;
    }

    public T deprecated() {
        deprecated = true;
        return (T)this;
    }

    /**
     * Adds a map entry to the method signature.
     */
    public T namedParams(String name) {
        GenericsType wildcard = new GenericsType(ClassHelper.OBJECT_TYPE);
        wildcard.setWildcard(true);
        return param(makeClassSafeWithGenerics(ClassHelper.MAP_TYPE, new GenericsType(ClassHelper.STRING_TYPE), wildcard), name);
    }

    /**
     * Adds a statement to the method body that converts each entry in the map into a call of a method with the key as
     * methodname and the value as method parameter.
     */
    public T applyNamedParams(String parameterMapName) {
        statement(
                new ForStatement(new Parameter(ClassHelper.DYNAMIC_TYPE, "it"), callX(varX(parameterMapName), "entrySet"),
                    new ExpressionStatement(
                            new MethodCallExpression(
                                    varX("$rw"),
                                    "invokeMethod",
                                    args(propX(varX("it"), "key"), propX(varX("it"), "value"))
                            )
                    )
                )
        );

        return (T)this;
    }

    /**
     * Adds a parameter of type closure.
     */
    public T closureParam(String name) {
        param(GeneralUtils.param(GenericsUtils.nonGeneric(ClassHelper.CLOSURE_TYPE), name));
        return (T)this;
    }

    /**
     * Adds a class parameter which is also used as the target for the {@link DelegatesTo} annotation of a delegating closure parameter
     * @param name Name of the parameter
     * @param upperBound The base class for the class parameter
     */
    public T delegationTargetClassParam(String name, ClassNode upperBound) {
        Parameter param = GeneralUtils.param(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(upperBound)), name);
        param.addAnnotation(new AnnotationNode(DELEGATES_TO_TARGET_ANNOTATION));
        return param(param);
    }

    /**
     * Adds a class parameter without delegation.
     * @param name The name of the parameter
     * @param upperBound The base class for the class parameter
     * @return
     */
    public T simpleClassParam(String name, ClassNode upperBound) {
        return param(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(upperBound)), name);
    }

    /**
     * Adds a string paramter with the given name.
     * @param name The name of the string parameter.
     */
    public T stringParam(String name) {
        return param(ClassHelper.STRING_TYPE, name);
    }

    /**
     * Convenience method to optionally add a string parameter. The parameter is only added, if 'addIfNotNull' is not null.
     * @param name The name of the parameter.
     * @param addIfNotNull If this parameter is null, the method does nothing
     */
    @Deprecated
    public T optionalStringParam(String name, Object addIfNotNull) {
        return optionalStringParam(name, addIfNotNull != null);
    }

    /**
     * Convenience method to optionally add a string parameter. The parameter is only added, if 'addIfNotNull' is not null.
     * @param name The name of the parameter.
     * @param doAdd If this parameter is null, the method does nothing
     */
    public T optionalStringParam(String name, boolean doAdd) {
        if (doAdd)
            stringParam(name);
        return (T)this;
    }

    /**
     * Add a generic object parameter.
     * @param name The name of the parameter
     */
    public T objectParam(String name) {
        return param(ClassHelper.OBJECT_TYPE, name);
    }

    /**
     * Add a parameter to the method signature.
     * @param type The type of the parameter
     * @param name The name of the parameter
     */
    public T param(ClassNode type, String name) {
        return param(new Parameter(type, name));
    }

    /**
     * Add a parameter to the method signature with an optional default value.
     * @param type The type of the parameter
     * @param name The name of the parameter
     * @param defaultValue An expression to use for the default value for the parameter. Can be null.
     */
    public T param(ClassNode type, String name, Expression defaultValue) {
        return param(new Parameter(type, name, defaultValue));
    }

    /**
     * Adds an array parameter with the given type.
     * @param type The type of the array elements
     * @param name The name of the parameter
     */
    public T arrayParam(ClassNode type, String name) {
        return param(new Parameter(type.makeArray(), name));
    }

    /**
     * Use all parameters of the given source method as parameters to this method.
     * @param sourceMethod The source of the parameter list
     */
    public T cloneParamsFrom(MethodNode sourceMethod) {
        Parameter[] sourceParams = GeneralUtils.cloneParams(sourceMethod.getParameters());
        for (Parameter parameter : sourceParams) {
            param(parameter);
        }
        return (T)this;
    }

    /**
     * Add custom metadata to the created AST node of the methods
     * @param key The key of the metadata
     * @param value The name of the metadata
     */
    public T withMetadata(Object key, Object value) {
        metadata.put(key, value);
        return (T)this;
    }

    public T delegatingClosureParam(ClassNode delegationTarget, ClosureDefaultValue defaultValue) {
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
    public T delegatingClosureParam() {
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

    public T statement(Statement statement) {
        body.addStatement(statement);
        return (T)this;
    }

    public T statementIf(boolean condition, Statement statement) {
        if (condition)
            body.addStatement(statement);
        return (T)this;
    }

    public T assignToProperty(String propertyName, Expression value) {
        String[] split = propertyName.split("\\.", 2);
        if (split.length == 1)
            return assignS(propX(varX("this"), propertyName), value);

        return assignS(propX(varX(split[0]), split[1]), value);
    }

    public T assignS(Expression target, Expression value) {
        return statement(GeneralUtils.assignS(target, value));
    }

    public T optionalAssignPropertyFromPropertyS(String target, String targetProperty, String value, String valueProperty, Object marker) {
        if (marker != null)
            assignS(propX(varX(target), targetProperty), propX(varX(value), valueProperty));
        return (T)this;
    }

    public T declareVariable(String varName, Expression init) {
        return statement(GeneralUtils.declS(varX(varName), init));
    }

    public T optionalDeclareVariable(String varName, Expression init, boolean doAdd) {
        if (doAdd)
            statement(GeneralUtils.declS(varX(varName), init));
        return (T)this;
    }

    public T callMethod(Expression receiver, String methodName) {
        return callMethod(receiver, methodName, MethodCallExpression.NO_ARGUMENTS);
    }

    public T callMethod(String receiverName, String methodName) {
        return callMethod(varX(receiverName), methodName);
    }

    public T callMethod(Expression receiver, String methodName, Expression args) {
        return statement(callX(receiver, methodName, args));
    }

    public T callMethod(String receiverName, String methodName, Expression args) {
        return callMethod(varX(receiverName), methodName, args);
    }

    public T callThis(String methodName, Expression args) {
        return callMethod("this", methodName, args);
    }

    public T callThis(String methodName) {
        return callMethod("this", methodName);
    }

    @Deprecated
    public T println(Expression args) {
        return callThis("println", args);
    }

    @Deprecated
    public T println(String string) {
        return callThis("println", constX(string));
    }

    public T statement(Expression expression) {
        return statement(stmt(expression));
    }

    public T statementIf(boolean condition, Expression expression) {
        return statementIf(condition, stmt(expression));
    }

    public T doReturn(String varName) {
        return doReturn(varX(varName));
    }

    public T doReturn(Expression expression) {
        return statement(returnS(expression));
    }

    public T linkToField(FieldNode fieldNode) {
        return (T) inheritDeprecationFrom(fieldNode).sourceLinkTo(fieldNode);
    }

    public T inheritDeprecationFrom(FieldNode fieldNode) {
        if (!fieldNode.getAnnotations(DEPRECATED_NODE).isEmpty()) {
            deprecated = true;
        }
        return (T)this;
    }

    public T sourceLinkTo(ASTNode sourceLinkTo) {
        this.sourceLinkTo = sourceLinkTo;
        return (T)this;
    }

    public enum ClosureDefaultValue { NONE, EMPTY_CLOSURE }
}
