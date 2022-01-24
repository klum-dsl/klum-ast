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

import com.blackbuild.groovy.configdsl.transform.ParameterAnnotation;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.common.MethodBuilderException;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.createGeneratedAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.hasAnnotation;
import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.closureX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.notX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.buildWildcardType;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics;
import static org.codehaus.groovy.ast.tools.GenericsUtils.nonGeneric;

public final class MethodBuilder {

    public static final ClassNode CLASSLOADER_TYPE = ClassHelper.make(ClassLoader.class);
    public static final ClassNode THREAD_TYPE = ClassHelper.make(Thread.class);
    public static final ClassNode PARAMETER_ANNOTATION_TYPE = ClassHelper.make(ParameterAnnotation.class);
    public static final ClassNode DEPRECATED_NODE = ClassHelper.make(Deprecated.class);
    private static final ClassNode[] EMPTY_EXCEPTIONS = new ClassNode[0];
    private static final Parameter[] EMPTY_PARAMETERS = new Parameter[0];
    private static final ClassNode DELEGATES_TO_ANNOTATION = make(DelegatesTo.class);
    private static final ClassNode DELEGATES_TO_TARGET_ANNOTATION = make(DelegatesTo.Target.class);
    protected String name;
    protected Map<Object, Object> metadata = new HashMap<>();

    private int modifiers;
    private ClassNode returnType = ClassHelper.VOID_TYPE;
    private List<ClassNode> exceptions = new ArrayList<>();
    private List<Parameter> parameters = new ArrayList<>();
    private boolean deprecated;
    private BlockStatement body = new BlockStatement();
    private boolean optional;
    private ASTNode sourceLinkTo;
    private boolean hasNamedParam;
    private GenericsType[] genericsTypes;
    private String documentation;
    private Set<String> tags = new HashSet<>();

    private MethodBuilder(String name) {
        this.name = name;
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
        return createPrivateMethod(name)
                .returning(type)
                .optional()
                .declareVariable("closure", closureExpression)
                .assignS(propX(varX("closure"), "delegate"), delegate)
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        constX(Closure.DELEGATE_ONLY)
                )
                .doReturn(callX(
                        varX("closure"),
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

    public MethodBuilder withoutMutatorCheck() {
        metadata.put(DSLASTTransformation.NO_MUTATION_CHECK_METADATA_KEY, Boolean.TRUE);
        return this;
    }

    public MethodBuilder callValidationOn(String target) {
        return callValidationMethodOn(varX(target));
    }

    private MethodBuilder callValidationMethodOn(Expression targetX) {
        return statement(ifS(notX(propX(targetX,"$manualValidation")), callX(targetX, DSLASTTransformation.VALIDATE_METHOD)));
    }

    public MethodBuilder returning(ClassNode returnType) {
        this.returnType = returnType;
        return this;
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

    public MethodBuilder linkToField(AnnotatedNode annotatedNode) {
        return inheritDeprecationFrom(annotatedNode).sourceLinkTo(annotatedNode);
    }

    public MethodBuilder inheritDeprecationFrom(AnnotatedNode annotatedNode) {
        if (!annotatedNode.getAnnotations(DEPRECATED_NODE).isEmpty()) {
            return deprecated();
        }
        return this;
    }

    /**
     * Creates the actual method and adds it to the target class. If the target already contains a method with that
     * signature, either an exception is thrown or the method is silently dropped, depending on the presence of
     * the optional parameter.
     * @param target The class node to add to
     * @return The newly created method or the existing method if `optional` is set and a method with that signature
     * already exists
     */
    public void addTo(ClassNode target) {

        Parameter[] parameterArray = this.parameters.toArray(EMPTY_PARAMETERS);
        MethodNode existing = target.getDeclaredMethod(name, parameterArray);

        if (existing != null) {
            if (optional)
                return;
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

        if (genericsTypes != null)
            method.setGenericsTypes(genericsTypes);

        if (deprecated)
            method.addAnnotation(new AnnotationNode(DEPRECATED_NODE));

        if (sourceLinkTo != null)
            method.setSourcePosition(sourceLinkTo);

        method.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class, documentation, tags));

        metadata.forEach(method::putNodeMetaData);
    }

    /**
     * Marks this method as optional. If set, {@link #addTo(ClassNode)} does not throw an error if the method already exists.
     */
    public MethodBuilder optional() {
        this.optional = true;
        return this;
    }

    /**
     * Sets the modifiers as defined by {@link Opcodes}.
     */
    public MethodBuilder mod(int modifier) {
        modifiers |= modifier;
        return this;
    }

    /**
     * Add a parameter to the method.
     */
    public MethodBuilder param(Parameter param) {
        parameters.add(param);
        return this;
    }

    public MethodBuilder deprecated() {
        deprecated = true;
        return this;
    }

    public MethodBuilder documentation(String documentation) {
        this.documentation = documentation;
        return this;
    }

    public MethodBuilder tag(String tag) {
        tags.add(tag);
        return this;
    }

    /**
     * Adds a map entry to the method signature.
     */
    public MethodBuilder namedParams(String name) {
        hasNamedParam = true;
        GenericsType wildcard = new GenericsType(ClassHelper.OBJECT_TYPE);
        wildcard.setWildcard(true);
        return param(makeClassSafeWithGenerics(ClassHelper.MAP_TYPE, new GenericsType(ClassHelper.STRING_TYPE), wildcard), name);
    }

    /**
     * Adds a statement to the method body that converts each entry in the map into a call of a method with the key as
     * methodname and the value as method parameter.
     */
    public MethodBuilder applyNamedParams(String parameterMapName) {
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

        return this;
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
     * @return
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
     * @param addIfNotNull If this parameter is null, the method does nothing
     */
    @Deprecated
    public MethodBuilder optionalStringParam(String name, Object addIfNotNull) {
        return optionalStringParam(name, addIfNotNull != null);
    }

    /**
     * Convenience method to optionally add a string parameter. The parameter is only added, if 'addIfNotNull' is not null.
     * @param name The name of the parameter.
     * @param doAdd If this parameter is null, the method does nothing
     */
    public MethodBuilder optionalStringParam(String name, boolean doAdd) {
        if (doAdd)
            stringParam(name);
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

    /**
     * Add custom metadata to the created AST node of the methods
     * @param key The key of the metadata
     * @param value The name of the metadata
     */
    public MethodBuilder withMetadata(Object key, Object value) {
        metadata.put(key, value);
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

    public MethodBuilder statementIf(boolean condition, Expression expression) {
        return statementIf(condition, stmt(expression));
    }

    public MethodBuilder doReturn(String varName) {
        return doReturn(varX(varName));
    }

    public MethodBuilder doReturn(Expression expression) {
        return statement(returnS(expression));
    }

    public MethodBuilder linkToField(FieldNode fieldNode) {
        return inheritDeprecationFrom(fieldNode).sourceLinkTo(fieldNode);
    }

    public MethodBuilder inheritDeprecationFrom(FieldNode fieldNode) {
        if (!fieldNode.getAnnotations(DEPRECATED_NODE).isEmpty()) {
            deprecated = true;
        }
        return this;
    }

    public MethodBuilder sourceLinkTo(ASTNode sourceLinkTo) {
        this.sourceLinkTo = sourceLinkTo;
        return this;
    }

    public MethodBuilder setGenericsTypes(GenericsType[] genericsTypes) {
        this.genericsTypes = genericsTypes;
        return this;
    }

    public enum ClosureDefaultValue { NONE, EMPTY_CLOSURE }
}
