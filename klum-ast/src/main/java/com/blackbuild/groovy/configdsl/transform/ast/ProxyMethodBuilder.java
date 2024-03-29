/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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

import com.blackbuild.klum.ast.util.FactoryHelper;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.ast.util.TemplateManager;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GeneralUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.hasAnnotation;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.buildWildcardType;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics;
import static org.codehaus.groovy.ast.tools.GenericsUtils.newClass;
import static org.codehaus.groovy.ast.tools.GenericsUtils.nonGeneric;

public final class ProxyMethodBuilder extends AbstractMethodBuilder<ProxyMethodBuilder> {

    private final String proxyMethodName;
    private final Expression proxyTarget;

    private final List<ProxyMethodArgument> params = new ArrayList<>();

    private int namedParameterIndex = -1;

    public ProxyMethodBuilder(Expression proxyTarget, String name, String proxyMethodName) {
        super(name);
        this.proxyTarget = proxyTarget;
        this.proxyMethodName = proxyMethodName;
    }

    public static ProxyMethodBuilder createProxyMethod(String name, String proxyMethodName) {
        return new ProxyMethodBuilder(varX(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS), name, proxyMethodName);
    }

    public static ProxyMethodBuilder createProxyMethod(String name) {
        return new ProxyMethodBuilder(varX(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS), name, name);
    }

    public static ProxyMethodBuilder createFactoryMethod(String name, ClassNode factoryType) {
        return new ProxyMethodBuilder(classX(FactoryHelper.class), name, name)
                .mod(ACC_STATIC | ACC_PUBLIC)
                .returning(newClass(factoryType))
                .constantClassParam(factoryType);
    }

    public static ProxyMethodBuilder createTemplateMethod(String name) {
        return new ProxyMethodBuilder(classX(TemplateManager.class), name, name)
                .mod(ACC_STATIC | ACC_PUBLIC)
                .returning(ClassHelper.DYNAMIC_TYPE);
    }

    private Statement delegateToProxy(String methodName, List<Expression> args) {
        MethodCallExpression callExpression = callX(
                proxyTarget,
                methodName,
                args(args)
        );
        if (!returnType.equals(ClassHelper.VOID_TYPE))
            return returnS(callExpression);
        else
            return stmt(callExpression);
    }

    public ProxyMethodBuilder decoratedParam(FieldNode field, String name) {
        List<AnnotationNode> annotations = field.getAnnotations()
                .stream()
                .filter(annotation -> hasAnnotation(annotation.getClassNode(), PARAMETER_ANNOTATION_TYPE))
                .flatMap(this::getAnnotationsFromMembers)
                .collect(toList());

        params.add(new ProxiedArgument(name, field.getType(), annotations));
        return this;
    }

    private Stream<AnnotationNode> getAnnotationsFromMembers(AnnotationNode source) {
        return source.getMembers().values().stream()
                .filter(AnnotationConstantExpression.class::isInstance)
                .map(annotationMember -> (AnnotationNode) ((AnnotationConstantExpression) annotationMember).getValue())
                .filter(annotationNode -> annotationNode.isTargetAllowed(AnnotationNode.PARAMETER_TARGET));
    }

    public ProxyMethodBuilder optionalClassLoaderParam() {
        params.add(new ProxiedArgument(
                "loader",
                CLASSLOADER_TYPE,
                null,
                callX(callX(THREAD_TYPE, "currentThread"), "getContextClassLoader")
        ));
        return this;
    }

    protected Parameter[] getMethodParameters() {
        return params.stream()
                .map(ProxyMethodArgument::asProxyMethodParameter)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toArray(Parameter[]::new);
    }

    private List<Expression> getProxyArguments() {
        return params.stream()
                .map(ProxyMethodArgument::asInstanceProxyArgument)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
    }

    /**
     * Creates the actual method and adds it to the target class. If the target already contains a method with that
     * signature, either an exception is thrown or the method is silently dropped, depending on the presence of
     * the optional parameter.
     * @param target The class node to add to
     * already exists
     * @return The created method node
     */
    @Override
    public MethodNode addTo(ClassNode target) {
        MethodNode result = super.addTo(target);

        if (namedParameterIndex != -1) {
            params.set(namedParameterIndex, new FixedExpressionArgument(new MapExpression()));
            doAddTo(target);
        }
        return result;
    }

    @Override
    protected Statement getMethodBody() {
        return delegateToProxy(proxyMethodName, getProxyArguments());
    }

    /**
     * Adds a map entry to the method signature.
     */
    public ProxyMethodBuilder namedParams(String name) {
        if (namedParameterIndex == -1)
            namedParameterIndex = params.size();
        return nonOptionalNamedParams(name);
    }

    public ProxyMethodBuilder nonOptionalNamedParams(String name) {
        params.add(new NamedParamsArgument(name));
        return this;
    }

    /**
     * Adds a parameter of type closure.
     */
    public ProxyMethodBuilder closureParam(String name) {
        return closureParam(name, ConstantExpression.NULL);
    }

    /**
     * Adds a parameter of type closure.
     */
    public ProxyMethodBuilder closureParam(String name, ConstantExpression defaultValue) {
        params.add(new ProxiedArgument(name, nonGeneric(ClassHelper.CLOSURE_TYPE), null, defaultValue));
        return this;
    }

    /**
     * Adds a class parameter which is also used as the target for the {@link DelegatesTo} annotation of a delegating closure parameter
     * @param name Name of the parameter
     * @param upperBound The base class for the class parameter
     */
    public ProxyMethodBuilder delegationTargetClassParam(String name, ClassNode upperBound) {
        params.add(new ProxiedArgument(
                name,
                makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(upperBound)),
                singletonList(new AnnotationNode(DELEGATES_TO_TARGET_ANNOTATION))
        ));
        return this;
    }

    /**
     * Adds a class parameter without delegation.
     * @param name The name of the parameter
     * @param upperBound The base class for the class parameter
     */
    public ProxyMethodBuilder simpleClassParam(String name, ClassNode upperBound) {
        return param(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(upperBound)), name);
    }

    /**
     * Adds a string paramter with the given name.
     * @param name The name of the string parameter.
     */
    public ProxyMethodBuilder stringParam(String name) {
        return param(ClassHelper.STRING_TYPE, name);
    }

    /**
     * Convenience method to optionally add a string parameter. The parameter is only added, if 'addIfNotNull' is not null.
     * @param name The name of the parameter.
     * @param doAdd If this parameter is null, the method does nothing
     */
    public ProxyMethodBuilder optionalStringParam(String name, boolean doAdd) {
        params.add(new OptionalArgument(name, ClassHelper.STRING_TYPE, doAdd));
        return this;
    }

    public ProxyMethodBuilder optionalParam(ClassNode type, String name, boolean doAdd) {
        params.add(new OptionalArgument(name, type, doAdd));
        return this;
    }

    public ProxyMethodBuilder conditionalParam(ClassNode type, String name, boolean doAdd) {
        if (doAdd)
            return param(type, name);
        return this;
    }

    /**
     * Add a generic object parameter.
     * @param name The name of the parameter
     */
    public ProxyMethodBuilder objectParam(String name) {
        return param(ClassHelper.OBJECT_TYPE, name);
    }

    /**
     * Add a parameter to the method signature.
     * @param type The type of the parameter
     * @param name The name of the parameter
     */
    public ProxyMethodBuilder param(ClassNode type, String name) {
        params.add(new ProxiedArgument(name, type));
        return this;
    }

    /**
     * Add a parameter to the method signature with an optional default value.
     * @param type The type of the parameter
     * @param name The name of the parameter
     * @param defaultValue An expression to use for the default value for the parameter. Can be null.
     */
    public ProxyMethodBuilder param(ClassNode type, String name, Expression defaultValue) {
        params.add(new ProxiedArgument(name, type, null, defaultValue));
        return this;
    }

    /**
     * Adds an array parameter with the given type.
     * @param type The type of the array elements
     * @param name The name of the parameter
     */
    public ProxyMethodBuilder arrayParam(ClassNode type, String name) {
        params.add(new ProxiedArgument(name, type.makeArray()));
        return this;
    }

    public ProxyMethodBuilder delegatingClosureParam(ClassNode delegationTarget) {
        return delegatingClosureParam(delegationTarget, ConstantExpression.NULL);
    }

    public ProxyMethodBuilder delegatingClosureParam(ClassNode delegationTarget, Expression defaultValue) {
        params.add(new ProxiedArgument("closure", nonGeneric(ClassHelper.CLOSURE_TYPE), singletonList(createDelegatesToAnnotation(delegationTarget)), defaultValue));
        return this;
    }

    /**
     * Creates a delegating closure parameter that delegates to the type parameter of an existing class parameter.
     */
    public ProxyMethodBuilder delegatingClosureParam() {
        return delegatingClosureParam(null);
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

    public ProxyMethodBuilder constantParam(Object constantValue) {
        params.add(new ConstantArgument(constantValue));
        return this;
    }

    public ProxyMethodBuilder constantClassParam(ClassNode targetFieldType) {
        params.add(new FixedExpressionArgument(targetFieldType));
        return this;
    }

    public ProxyMethodBuilder thisParam() {
        params.add(new FixedExpressionArgument(varX("this")));
        return this;
    }

    private abstract static class ProxyMethodArgument {
        protected final String name;

        public ProxyMethodArgument(String name) {
            this.name = name;
        }

        abstract Optional<Parameter> asProxyMethodParameter();

        Optional<Expression> asInstanceProxyArgument() {
            return Optional.of(varX(name));
        }
    }

    private static class ProxiedArgument extends ProxyMethodArgument {
        private final ClassNode type;
        private final List<AnnotationNode> annotations;
        private final Expression defaultValue;

        public ProxiedArgument(String name, ClassNode type) {
            this(name, type, null, null);
        }

        public ProxiedArgument(String name, ClassNode type, List<AnnotationNode> annotations) {
            this(name, type, annotations, null);
        }

        public ProxiedArgument(String name, ClassNode type, List<AnnotationNode> annotations, Expression defaultValue) {
            super(name);
            this.type = type;
            this.annotations = annotations;
            this.defaultValue = defaultValue;
        }

        @Override
        Optional<Parameter> asProxyMethodParameter() {
            Parameter param = GeneralUtils.param(type, name, defaultValue);
            if (annotations != null)
                annotations.forEach(param::addAnnotation);
            return Optional.of(param);
        }
    }

    private static class ConstantArgument extends ProxyMethodArgument {
        Object constant;

        public ConstantArgument(Object constant) {
            super(null);
            this.constant = constant;
        }

        @Override
        Optional<Parameter> asProxyMethodParameter() {
            return Optional.empty();
        }

        @Override
        Optional<Expression> asInstanceProxyArgument() {
            return Optional.of(constX(constant));
        }
    }

    private static class FixedExpressionArgument extends ProxyMethodArgument {
        final Expression expression;

        public FixedExpressionArgument(ClassNode type) {
            this(classX(type));
        }

        public FixedExpressionArgument(Expression expression) {
            super(null);
            this.expression = expression;
        }

        @Override
        Optional<Parameter> asProxyMethodParameter() {
            return Optional.empty();
        }

        @Override
        Optional<Expression> asInstanceProxyArgument() {
            return Optional.of(expression);
        }
    }

    private static class OptionalArgument extends ProxyMethodArgument {
        private final ClassNode type;
        private final boolean doAdd;

        public OptionalArgument(String name, ClassNode type, boolean doAdd) {
            super(name);
            this.type = type;
            this.doAdd = doAdd;
        }

        @Override
        Optional<Parameter> asProxyMethodParameter() {
            if (doAdd)
                return Optional.of(GeneralUtils.param(type, name));
            else
                return Optional.empty();
        }

        @Override
        Optional<Expression> asInstanceProxyArgument() {
            return Optional.of(doAdd ? varX(name) : ConstantExpression.NULL);
        }

    }

    private static class NamedParamsArgument extends ProxyMethodArgument {

        public NamedParamsArgument(String name) {
            super(name);
        }

        @Override
        Optional<Parameter> asProxyMethodParameter() {
            GenericsType wildcard = new GenericsType(ClassHelper.OBJECT_TYPE);
            wildcard.setWildcard(true);
            return Optional.of(new Parameter(makeClassSafeWithGenerics(ClassHelper.MAP_TYPE, new GenericsType(ClassHelper.STRING_TYPE), wildcard), name));
        }
    }
}
