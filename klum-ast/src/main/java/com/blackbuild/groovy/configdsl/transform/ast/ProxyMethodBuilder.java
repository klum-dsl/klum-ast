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

import com.blackbuild.annodocimal.ast.extractor.ASTExtractor;
import com.blackbuild.annodocimal.ast.formatting.AnnoDocUtil;
import com.blackbuild.annodocimal.ast.formatting.DocBuilder;
import com.blackbuild.annodocimal.ast.formatting.DocText;
import com.blackbuild.klum.ast.util.FactoryHelper;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.ast.util.TemplateManager;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GeneralUtils;

import java.util.*;
import java.util.stream.Stream;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.hasAnnotation;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.*;

public final class ProxyMethodBuilder extends AbstractMethodBuilder<ProxyMethodBuilder> {

    private static final ClassNode FACTORY_HELPER_TYPE = make(FactoryHelper.class);
    private static final ClassNode TEMPLATE_MANAGER_TYPE = make(TemplateManager.class);
    private static final ClassNode KLUM_INSTANCE_PROXY_TYPE = make(KlumInstanceProxy.class);

    private final String proxyMethodName;
    private final Expression proxyTarget;
    private ClassNode targetType;

    private final List<ProxyMethodArgument> params = new ArrayList<>();

    private int namedParameterIndex = -1;

    public ProxyMethodBuilder(Expression proxyTarget, String name, String proxyMethodName) {
        super(name);
        this.proxyTarget = proxyTarget;
        this.proxyMethodName = proxyMethodName;
    }

    public static ProxyMethodBuilder createProxyMethod(String name, String proxyMethodName) {
        return new ProxyMethodBuilder(varX(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS), name, proxyMethodName)
                .targetType(KLUM_INSTANCE_PROXY_TYPE);
    }

    public static ProxyMethodBuilder createProxyMethod(String name) {
        return new ProxyMethodBuilder(varX(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS), name, name)
                .targetType(KLUM_INSTANCE_PROXY_TYPE);
    }

    public static ProxyMethodBuilder createFactoryMethod(String name, ClassNode factoryType) {
        return new ProxyMethodBuilder(classX(FACTORY_HELPER_TYPE), name, name)
                .targetType(FACTORY_HELPER_TYPE)
                .mod(ACC_STATIC | ACC_PUBLIC)
                .returning(newClass(factoryType), "The new instance")
                .constantClassParam(factoryType);
    }

    public static ProxyMethodBuilder createTemplateMethod(String name) {
        return new ProxyMethodBuilder(classX(TEMPLATE_MANAGER_TYPE), name, name)
                .targetType(TEMPLATE_MANAGER_TYPE)
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

    public ProxyMethodBuilder targetType(ClassNode targetType) {
        this.targetType = targetType;
        return this;
    }

    public ProxyMethodBuilder decoratedParam(FieldNode field, String name, String doc) {
        List<AnnotationNode> annotations = field.getAnnotations()
                .stream()
                .filter(annotation -> hasAnnotation(annotation.getClassNode(), PARAMETER_ANNOTATION_TYPE))
                .flatMap(this::getAnnotationsFromMembers)
                .collect(toList());

        params.add(new ProxiedArgument(name, field.getType(), annotations, doc));
        return this;
    }

    private Stream<AnnotationNode> getAnnotationsFromMembers(AnnotationNode source) {
        return source.getMembers().values().stream()
                .filter(AnnotationConstantExpression.class::isInstance)
                .map(annotationMember -> (AnnotationNode) ((AnnotationConstantExpression) annotationMember).getValue())
                .filter(annotationNode -> annotationNode.isTargetAllowed(AnnotationNode.PARAMETER_TARGET));
    }

    public ProxyMethodBuilder optionalClassLoaderParam() {
        return optionalClassLoaderParam("The classloader to use. Defaults to the current thread's context classloader.");
    }

    public ProxyMethodBuilder optionalClassLoaderParam(String doc) {
        params.add(new ProxiedArgument(
                "loader",
                CLASSLOADER_TYPE,
                null,
                callX(callX(THREAD_TYPE, "currentThread"), "getContextClassLoader"),
                doc
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

    private List<ClassNode> getProxyArgumentTypes() {
        return params.stream()
                .map(ProxyMethodArgument::asInstanceProxyArgumentType)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(toList());
    }

    /**
     * @inheritDoc
     */
    @Override
    public ProxyMethodBuilder copyDocFrom(AnnotatedNode source) {
        DocText docTextOfProxyTarget = DocText.fromRawText(ASTExtractor.extractDocumentation(source, null));

        if (source instanceof MethodNode) {
            Map<String, String> mappings = getParameternameMappings(docTextOfProxyTarget);
            if (!mappings.isEmpty()) {
                String rawText = docTextOfProxyTarget.getRawText();
                for (Map.Entry<String, String> stringStringEntry : mappings.entrySet()) {
                    rawText = rawText.replace(
                            "@param " + stringStringEntry.getKey() + " ",
                            "@param " + stringStringEntry.getValue() + " ");
                }
                docTextOfProxyTarget = DocText.fromRawText(rawText);
            }
        }

        documentation.fromDocText(docTextOfProxyTarget);
        return this;
    }

    private Map<String, String> getParameternameMappings(DocText docTextOfProxyTarget) {
        Map<String, String> mappings = new HashMap<>();

        List<ProxyMethodArgument> targetMethodsArguments = params.stream()
                .filter(p -> p.asInstanceProxyArgument().isPresent())
                .collect(toList());

        // since the methodNode does not contain the parameter names, we need to take the names from the docText
        // We could instead determine the method object from the target class
        List<String> docTextParamTags = docTextOfProxyTarget
                .getTags()
                .getOrDefault("param", Collections.emptyList())
                .stream()
                .filter(param -> !param.startsWith("<"))
                .map(param -> param.split(" ")[0].trim())
                .collect(toList());

        if (docTextParamTags.size() != targetMethodsArguments.size()) {
            return mappings;
        }

        for (int i = 0; i < targetMethodsArguments.size(); i++) {
            String targetedMethodParameterName = docTextParamTags.get(i);
            String newMethodParameterName = targetMethodsArguments.get(i).name;
            if (!targetedMethodParameterName.equals(newMethodParameterName)) {
                mappings.put(targetedMethodParameterName, newMethodParameterName);
            }
        }

        return mappings;
    }


    @Override
    protected void postProcessMethod(MethodNode method) {
        List<ClassNode> args = getProxyArgumentTypes();
        MethodNode targetMethod = MethodAstHelper.findMatchingMethod(targetType, proxyMethodName, args);

        if (targetMethod != null) {
            method.setSourcePosition(targetMethod);

            if (deprecationType == null) {
                List<AnnotationNode> deprecatedAnnotations = targetMethod.getAnnotations(DEPRECATED_NODE);
                if (!deprecatedAnnotations.isEmpty())
                    method.addAnnotation(deprecatedAnnotations.get(0));
            }
            addParameterJavaDocs(documentation);
            copyDocFrom(targetMethod);
            AnnoDocUtil.addDocumentation(method, documentation);
        } else {
            AnnoDocUtil.addDocumentation(method, addParameterJavaDocs(documentation.getCopy()));
        }
    }

    private DocBuilder addParameterJavaDocs(DocBuilder doc) {
         params.stream()
                .filter(p -> p.asParameterJavaDoc().isPresent())
                .forEach(p -> doc.param(p.name, p.asParameterJavaDoc().get()));
         return doc;
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
    public ProxyMethodBuilder namedParams(String name, String doc) {
        if (namedParameterIndex == -1)
            namedParameterIndex = params.size();
        return nonOptionalNamedParams(name, doc);
    }

    public ProxyMethodBuilder nonOptionalNamedParams(String name, String doc) {
        params.add(new NamedParamsArgument(name, doc));
        return this;
    }

    /**
     * Adds a parameter of type closure.
     */
    public ProxyMethodBuilder closureParam(String name, String doc) {
        return closureParam(name, ConstantExpression.NULL, doc);
    }

    /**
     * Adds a parameter of type closure.
     */
    public ProxyMethodBuilder closureParam(String name, ConstantExpression defaultValue, String doc) {
        params.add(new ProxiedArgument(name, nonGeneric(ClassHelper.CLOSURE_TYPE), null, defaultValue, doc));
        return this;
    }

    /**
     * Adds a class parameter which is also used as the target for the {@link DelegatesTo} annotation of a delegating closure parameter
     * @param name Name of the parameter
     * @param upperBound The base class for the class parameter
     */
    public ProxyMethodBuilder delegationTargetClassParam(String name, ClassNode upperBound, String doc) {
        params.add(new ProxiedArgument(
                name,
                makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(upperBound)),
                singletonList(new AnnotationNode(DELEGATES_TO_TARGET_ANNOTATION)),
                doc
        ));
        return this;
    }

    /**
     * Adds a class parameter without delegation.
     * @param name The name of the parameter
     * @param upperBound The base class for the class parameter
     */
    public ProxyMethodBuilder simpleClassParam(String name, ClassNode upperBound, String doc) {
        return param(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(upperBound)), name, doc);
    }

    /**
     * Adds a string paramter with the given name.
     * @param name The name of the string parameter.
     */
    public ProxyMethodBuilder stringParam(String name, String doc) {
        return param(ClassHelper.STRING_TYPE, name, doc);
    }

    /**
     * Convenience method to optionally add a string parameter. The parameter is only added, if 'addIfNotNull' is not null.
     * @param name The name of the parameter.
     * @param doAdd If this parameter is null, the method does nothing
     */
    public ProxyMethodBuilder optionalStringParam(String name, boolean doAdd, String doc) {
        params.add(new OptionalArgument(name, ClassHelper.STRING_TYPE, doAdd, doc));
        return this;
    }

    public ProxyMethodBuilder optionalParam(ClassNode type, String name, boolean doAdd, String doc) {
        params.add(new OptionalArgument(name, type, doAdd, doc));
        return this;
    }

    public ProxyMethodBuilder conditionalParam(ClassNode type, String name, boolean doAdd, String doc) {
        if (doAdd)
            return param(type, name, doc);
        return this;
    }

    /**
     * Add a generic object parameter.
     * @param name The name of the parameter
     */
    public ProxyMethodBuilder objectParam(String name, String doc) {
        return param(ClassHelper.OBJECT_TYPE, name, doc);
    }

    /**
     * Add a parameter to the method signature.
     * @param type The type of the parameter
     * @param name The name of the parameter
     */
    public ProxyMethodBuilder param(ClassNode type, String name, String doc) {
        params.add(new ProxiedArgument(name, type, doc));
        return this;
    }

    /**
     * Add a parameter to the method signature with an optional default value.
     * @param type The type of the parameter
     * @param name The name of the parameter
     * @param defaultValue An expression to use for the default value for the parameter. Can be null.
     */
    public ProxyMethodBuilder param(ClassNode type, String name, Expression defaultValue, String doc) {
        params.add(new ProxiedArgument(name, type, null, defaultValue, doc));
        return this;
    }

    public ProxyMethodBuilder param(Parameter parameter) {
        params.add(new ProxiedArgument(parameter.getName(), parameter.getOriginType(), parameter.getAnnotations(), parameter.getInitialExpression(), null));
        return this;
    }

    /**
     * Adds an array parameter with the given type.
     * @param type The type of the array elements
     * @param name The name of the parameter
     */
    public ProxyMethodBuilder arrayParam(ClassNode type, String name, String doc) {
        params.add(new ProxiedArgument(name, type.makeArray(), doc));
        return this;
    }

    public ProxyMethodBuilder delegatingClosureParam(ClassNode delegationTarget, String doc) {
        return delegatingClosureParam(delegationTarget, ConstantExpression.NULL, doc);
    }

    public ProxyMethodBuilder delegatingClosureParam(ClassNode delegationTarget, Expression defaultValue, String doc) {
        params.add(new ProxiedArgument(
                "closure",
                nonGeneric(ClassHelper.CLOSURE_TYPE),
                singletonList(createDelegatesToAnnotation(delegationTarget)),
                defaultValue,
                doc));
        return this;
    }

    /**
     * Creates a delegating closure parameter that delegates to the type parameter of an existing class parameter.
     */
    public ProxyMethodBuilder delegatingClosureParam(String doc) {
        return delegatingClosureParam(null, doc);
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

    public ProxyMethodBuilder paramsFrom(MethodNode targetMethod) {
        Parameter[] source = targetMethod.getParameters();
        for (Parameter parameter : source) param(parameter);
        return this;
    }

    private abstract static class ProxyMethodArgument {
        protected final String name;
        protected final String javaDoc;

        public ProxyMethodArgument(String name, String javaDoc) {
            this.name = name;
            this.javaDoc = javaDoc;
        }

        abstract Optional<Parameter> asProxyMethodParameter();

        Optional<Expression> asInstanceProxyArgument() {
            return Optional.of(varX(name));
        }

        Optional<String> asParameterJavaDoc() {
            return Optional.ofNullable(javaDoc);
        }

        abstract Optional<ClassNode> asInstanceProxyArgumentType();
    }

    private static class ProxiedArgument extends ProxyMethodArgument {
        private final ClassNode type;
        private final List<AnnotationNode> annotations;
        private final Expression defaultValue;

        public ProxiedArgument(String name, ClassNode type, String documentation) {
            this(name, type, null, null, documentation);
        }

        public ProxiedArgument(String name, ClassNode type, List<AnnotationNode> annotations, String documentation) {
            this(name, type, annotations, null, documentation);
        }

        public ProxiedArgument(String name, ClassNode type, List<AnnotationNode> annotations, Expression defaultValue, String documentation) {
            super(name, documentation);
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

        @Override
        Optional<ClassNode> asInstanceProxyArgumentType() {
            return Optional.of(type);
        }
    }

    private static class ConstantArgument extends ProxyMethodArgument {
        Object constant;

        public ConstantArgument(Object constant) {
            super(null, null);
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

        @Override
        Optional<ClassNode> asInstanceProxyArgumentType() {
            if (constant == null) return Optional.of(ClassHelper.VOID_TYPE);
            return Optional.of(ClassHelper.make(constant.getClass()));
        }
    }

    private static class FixedExpressionArgument extends ProxyMethodArgument {
        final Expression expression;

        public FixedExpressionArgument(ClassNode type) {
            this(classX(type));
        }

        public FixedExpressionArgument(Expression expression) {
            super("none", null);
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

        @Override
        Optional<ClassNode> asInstanceProxyArgumentType() {
            if (expression instanceof ClassExpression)
                return Optional.of(CLASS_Type);
            return Optional.of(expression.getType());
        }
    }

    private static class OptionalArgument extends ProxyMethodArgument {
        private final ClassNode type;
        private final boolean doAdd;

        public OptionalArgument(String name, ClassNode type, boolean doAdd, String documentation) {
            super(name, documentation);
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
        Optional<String> asParameterJavaDoc() {
            if (doAdd)
                return super.asParameterJavaDoc();
            else
                return Optional.empty();
        }

        @Override
        Optional<Expression> asInstanceProxyArgument() {
            return Optional.of(doAdd ? varX(name) : ConstantExpression.NULL);
        }

        @Override
        Optional<ClassNode> asInstanceProxyArgumentType() {
            return Optional.of(type);
        }

    }

    private static class NamedParamsArgument extends ProxyMethodArgument {

        public NamedParamsArgument(String name, String documentation) {
            super(name, documentation);
        }

        @Override
        Optional<Parameter> asProxyMethodParameter() {
            GenericsType wildcard = new GenericsType(ClassHelper.OBJECT_TYPE);
            wildcard.setWildcard(true);
            return Optional.of(new Parameter(makeClassSafeWithGenerics(ClassHelper.MAP_TYPE, new GenericsType(ClassHelper.STRING_TYPE), wildcard), name));
        }

        @Override
        Optional<ClassNode> asInstanceProxyArgumentType() {
            return Optional.of(makeClassSafeWithGenerics(ClassHelper.MAP_TYPE, new GenericsType(ClassHelper.STRING_TYPE), new GenericsType(ClassHelper.OBJECT_TYPE)));
        }
    }
}
