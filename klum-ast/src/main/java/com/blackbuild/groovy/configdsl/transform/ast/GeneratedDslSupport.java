/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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

import com.blackbuild.annodocimal.ast.formatting.AnnoDocUtil;
import com.blackbuild.groovy.configdsl.transform.KlumGenerated;
import groovy.lang.DelegatesTo;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.MixinNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.stmt.EmptyStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.copyAnnotationsFromSourceToTarget;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.createGeneratedAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getRwClassOf;
import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_INTERFACE;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;

/** Builds and links the public {@code Foo_DSL} contract for one transformed DSL Object. */
public final class GeneratedDslSupport {

    public static final String SUPPORT_METADATA_KEY = GeneratedDslSupport.class.getName() + ".support";
    public static final String PUBLIC_INTERFACE_METADATA_KEY = GeneratedDslSupport.class.getName() + ".publicInterface";
    private static final String BUILDER_PLACEHOLDER_METADATA_KEY = GeneratedDslSupport.class.getName() + ".builderPlaceholder";
    private static final String API_METHOD_METADATA_KEY = GeneratedDslSupport.class.getName() + ".apiMethod";
    public static final String API_TAG = "dsl-support-api";
    public static final String INTERFACE_LINK_TAG = "dsl-support-interface:";

    private static final ClassNode KLUM_GENERATED = ClassHelper.make(KlumGenerated.class);
    private static final ClassNode DELEGATES_TO = ClassHelper.make(DelegatesTo.class);

    private final ClassNode model;
    private final ClassNode namespace;
    private final InnerClassNode factoryInterface;
    private final InnerClassNode builderInterface;
    private final Map<ClassNode, ClassNode> implementations = new LinkedHashMap<>();

    private GeneratedDslSupport(ClassNode model, ClassNode builderImplementation) {
        this.model = model;
        namespace = new ClassNode(
                model.getName() + "_DSL",
                ACC_PUBLIC | ACC_ABSTRACT | ACC_INTERFACE,
                ClassHelper.OBJECT_TYPE
        );
        namespace.setSourcePosition(model);
        AnnoDocUtil.addDocumentation(namespace, "The generated DSL support namespace for " + model.getName() + ".");
        namespace.addAnnotation(createGeneratedAnnotation(GeneratedDslSupport.class, List.of(API_TAG, "dsl-support-role:namespace")));
        model.getModule().addClass(namespace);

        factoryInterface = createNestedInterface(namespace, "Factory", "The public factory contract for " + model.getName() + ".");
        builderInterface = createNestedInterface(namespace, "Builder", "The public Builder contract for " + model.getName() + ".");
        ClassNode builderPlaceholder = model.redirect().getNodeMetaData(BUILDER_PLACEHOLDER_METADATA_KEY);
        if (builderPlaceholder != null)
            builderPlaceholder.setRedirect(builderInterface);
        copyTypeParameters(model, builderInterface);
        addParentBuilderInterface();
        link(builderImplementation, parameterizedForModel(builderInterface, model));
    }

    public static GeneratedDslSupport create(ClassNode model, ClassNode builderImplementation) {
        GeneratedDslSupport support = new GeneratedDslSupport(model, builderImplementation);
        model.setNodeMetaData(SUPPORT_METADATA_KEY, support);
        return support;
    }

    public static GeneratedDslSupport of(ClassNode model) {
        GeneratedDslSupport support = model.getNodeMetaData(SUPPORT_METADATA_KEY);
        if (support == null)
            throw new IllegalStateException("No generated DSL support namespace registered for " + model.getName());
        return support;
    }

    public ClassNode getFactoryInterface() {
        return factoryInterface;
    }

    public ClassNode getBuilderInterface() {
        return parameterizedForModel(builderInterface, model);
    }

    /**
     * Returns the public Builder contract for a model even when that model's local transformation has not run yet.
     * The placeholder is linked to the real generated interface through AST metadata when the target model is visited.
     */
    public static ClassNode builderTypeFor(ClassNode model) {
        ClassNode target = model.redirect();
        GeneratedDslSupport support = target.getNodeMetaData(SUPPORT_METADATA_KEY);
        if (support != null)
            return support.getBuilderInterface();

        ClassNode placeholder = target.getNodeMetaData(BUILDER_PLACEHOLDER_METADATA_KEY);
        if (placeholder == null) {
            placeholder = ClassHelper.makeWithoutCaching(target.getName() + "_DSL$Builder");
            target.setNodeMetaData(BUILDER_PLACEHOLDER_METADATA_KEY, placeholder);
        }
        return parameterizedForModel(placeholder, model);
    }

    public static ClassNode registerCollectionFactory(ClassNode model, ClassNode implementation, String fieldName) {
        return of(model).registerNestedFactory(implementation, "CollectionFactory_" + fieldName,
                "The public collection factory contract for " + model.getName() + "." + fieldName + ".");
    }

    public static ClassNode registerClusterFactory(ClassNode model, ClassNode implementation, String fieldName) {
        return of(model).registerNestedFactory(implementation, "ClusterFactory_" + fieldName,
                "The public Cluster factory contract for " + model.getName() + "." + fieldName + ".");
    }

    public static void linkFactory(ClassNode model, ClassNode implementation) {
        GeneratedDslSupport support = of(model);
        support.link(implementation, support.factoryInterface);
    }

    public static void complete(ClassNode model) {
        GeneratedDslSupport support = of(model);
        support.implementations.forEach(support::projectImplementation);
    }

    /** Resolves the public API type through an explicit generated {@code implements} link. */
    public static ClassNode publicType(ClassNode type) {
        if (type == null) return null;
        if (type.isArray()) return publicType(type.getComponentType()).makeArray();
        if (type.isGenericsPlaceHolder()) return type;
        if (hasGeneratedApiTag(type)) return type;

        String linkedInterface = generatedLink(type);
        if (linkedInterface != null) {
            for (ClassNode candidate : type.getInterfaces()) {
                if (candidate.redirect().getName().equals(linkedInterface))
                    return copyGenerics(type, candidate);
            }
        }

        for (ClassNode candidate : type.getInterfaces()) {
            if (hasGeneratedApiTag(candidate))
                return copyGenerics(type, candidate);
        }

        ClassNode direct = type.getNodeMetaData(PUBLIC_INTERFACE_METADATA_KEY);
        if (direct != null)
            return copyGenerics(type, direct);

        direct = type.redirect().getNodeMetaData(PUBLIC_INTERFACE_METADATA_KEY);
        if (direct != null)
            return copyGenerics(type, direct);

        GenericsType[] generics = type.getGenericsTypes();
        if (generics == null || generics.length == 0)
            return type;

        ClassNode result = type.getPlainNodeReference();
        result.setGenericsTypes(Arrays.stream(generics).map(GeneratedDslSupport::projectGenericsType).toArray(GenericsType[]::new));
        return result;
    }

    private ClassNode registerNestedFactory(ClassNode implementation, String name, String documentation) {
        ClassNode api = createNestedInterface(builderInterface, name, documentation);
        link(implementation, api);
        return api;
    }

    private void addParentBuilderInterface() {
        ClassNode parent = model.getUnresolvedSuperClass(false);
        if (!DslAstHelper.isDSLObject(parent)) return;

        ClassNode parentModel = parent.redirect();
        GeneratedDslSupport parentSupport = parentModel.getNodeMetaData(SUPPORT_METADATA_KEY);
        if (parentSupport == null && !parentModel.isResolved()) {
            DslAstHelper.addDelayedAction(parentModel, () -> addParentBuilderInterface(parent));
            return;
        }
        addParentBuilderInterface(parent);
    }

    private void addParentBuilderInterface(ClassNode parent) {
        ClassNode parentBuilder = publicType(getRwClassOf(parent.redirect())).redirect();
        builderInterface.setUsingGenerics(true);
        builderInterface.setInterfaces(new ClassNode[] { parameterizedForModel(parentBuilder, parent) });
    }

    private void link(ClassNode implementation, ClassNode publicInterface) {
        implementation.addInterface(publicInterface);
        implementation.setNodeMetaData(PUBLIC_INTERFACE_METADATA_KEY, publicInterface);
        implementations.put(implementation, publicInterface.redirect());
        appendGeneratedTag(implementation, INTERFACE_LINK_TAG + publicInterface.redirect().getName());
    }

    private void projectImplementation(ClassNode implementation, ClassNode publicInterface) {
        implementation.getFields().stream()
                .filter(field -> field.getOwner().redirect().equals(implementation.redirect()))
                .forEach(field -> field.setType(publicType(field.getType())));
        List<MethodNode> methods = new ArrayList<>(implementation.getMethods());
        methods.stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(implementation.redirect()))
                .filter(MethodNode::isPublic)
                .filter(method -> !method.isStatic())
                .filter(method -> !method.isSynthetic())
                .filter(method -> !shadowsPackagePrivateSuperMethod(implementation, method))
                .forEach(method -> addProjectedMethod(publicInterface, method));
    }

    private static boolean shadowsPackagePrivateSuperMethod(ClassNode implementation, MethodNode method) {
        ClassNode current = implementation.getSuperClass();
        while (current != null) {
            MethodNode inherited = current.getDeclaredMethod(method.getName(), method.getParameters());
            if (inherited != null) {
                // Groovy resolves shadowing against the nearest superclass declaration.
                int visibility = inherited.getModifiers() & (groovyjarjarasm.asm.Opcodes.ACC_PUBLIC
                        | groovyjarjarasm.asm.Opcodes.ACC_PROTECTED
                        | groovyjarjarasm.asm.Opcodes.ACC_PRIVATE);
                return visibility == 0;
            }
            current = current.getSuperClass();
        }
        return false;
    }

    private void addProjectedMethod(ClassNode publicInterface, MethodNode implementationMethod) {
        MethodNode linked = implementationMethod.getNodeMetaData(API_METHOD_METADATA_KEY);
        if (linked != null) return;

        projectMethodSignature(implementationMethod);
        Parameter[] parameters = cloneParameters(implementationMethod.getParameters());
        MethodNode existing = publicInterface.getDeclaredMethod(implementationMethod.getName(), parameters);
        if (existing != null) {
            implementationMethod.setNodeMetaData(API_METHOD_METADATA_KEY, existing);
            return;
        }

        MethodNode apiMethod = new MethodNode(
                implementationMethod.getName(),
                ACC_PUBLIC | ACC_ABSTRACT,
                publicType(implementationMethod.getReturnType()),
                parameters,
                Arrays.stream(implementationMethod.getExceptions()).map(GeneratedDslSupport::publicType).toArray(ClassNode[]::new),
                EmptyStatement.INSTANCE
        );
        apiMethod.setGenericsTypes(projectGenericsTypes(implementationMethod.getGenericsTypes()));
        copyAnnotationsFromSourceToTarget(implementationMethod, apiMethod, Collections.emptyList());
        publicInterface.addMethod(apiMethod);
        implementationMethod.setNodeMetaData(API_METHOD_METADATA_KEY, apiMethod);
    }

    private static void projectMethodSignature(MethodNode method) {
        method.setReturnType(publicType(method.getReturnType()));
        for (Parameter parameter : method.getParameters()) {
            parameter.setType(publicType(parameter.getType()));
            projectDelegatesTo(parameter);
        }
        method.setGenericsTypes(projectGenericsTypes(method.getGenericsTypes()));
    }

    private static Parameter[] cloneParameters(Parameter[] source) {
        Parameter[] result = new Parameter[source.length];
        for (int index = 0; index < source.length; index++) {
            Parameter parameter = source[index];
            Parameter clone = new Parameter(publicType(parameter.getType()), parameter.getName(), parameter.getInitialExpression());
            copyAnnotationsFromSourceToTarget(parameter, clone, Collections.emptyList());
            projectDelegatesTo(clone);
            result[index] = clone;
        }
        return result;
    }

    private static void projectDelegatesTo(AnnotatedNode target) {
        AnnotationNode delegatesTo = getAnnotation(target, DELEGATES_TO);
        if (delegatesTo == null) return;
        Expression value = delegatesTo.getMember("value");
        if (value instanceof ClassExpression) {
            ClassNode projected = publicType(((ClassExpression) value).getType());
            // Cloned parameters can share this AnnotationNode; replace it instead of mutating shared metadata.
            AnnotationNode replacement = new AnnotationNode(DELEGATES_TO);
            delegatesTo.getMembers().forEach(replacement::setMember);
            replacement.setMember("value", new ClassExpression(projected));
            target.getAnnotations().remove(delegatesTo);
            target.addAnnotation(replacement);
        }
    }

    private static GenericsType[] projectGenericsTypes(GenericsType[] source) {
        if (source == null) return null;
        return Arrays.stream(source).map(GeneratedDslSupport::projectGenericsType).toArray(GenericsType[]::new);
    }

    private static GenericsType projectGenericsType(GenericsType source) {
        GenericsType result = new GenericsType(
                publicType(source.getType()),
                source.getUpperBounds() == null
                        ? null
                        : Arrays.stream(source.getUpperBounds()).map(GeneratedDslSupport::publicType).toArray(ClassNode[]::new),
                publicType(source.getLowerBound())
        );
        result.setName(source.getName());
        result.setPlaceholder(source.isPlaceholder());
        result.setWildcard(source.isWildcard());
        return result;
    }

    private static ClassNode parameterizedForModel(ClassNode api, ClassNode modelType) {
        GenericsType[] modelGenerics = modelType.getGenericsTypes();
        if (modelGenerics == null || modelGenerics.length == 0)
            return api.getPlainNodeReference();
        return withGenerics(api, projectGenericsTypes(modelGenerics));
    }

    private static ClassNode copyGenerics(ClassNode source, ClassNode target) {
        GenericsType[] sourceGenerics = source.getGenericsTypes();
        if (sourceGenerics == null || sourceGenerics.length == 0)
            return target.getPlainNodeReference();
        return withGenerics(target, projectGenericsTypes(sourceGenerics));
    }

    private static ClassNode withGenerics(ClassNode target, GenericsType[] generics) {
        ClassNode result = target.getPlainNodeReference();
        result.setGenericsTypes(generics);
        result.setUsingGenerics(true);
        return result;
    }

    private static void copyTypeParameters(ClassNode source, ClassNode target) {
        GenericsType[] generics = source.getGenericsTypes();
        if (generics != null)
            target.setGenericsTypes(projectGenericsTypes(generics));
    }

    private static InnerClassNode createNestedInterface(ClassNode owner, String name, String documentation) {
        InnerClassNode result = new InnerClassNode(
                owner,
                owner.getName() + "$" + name,
                ACC_PUBLIC | ACC_STATIC | ACC_ABSTRACT | ACC_INTERFACE,
                ClassHelper.OBJECT_TYPE,
                ClassNode.EMPTY_ARRAY,
                MixinNode.EMPTY_ARRAY
        );
        result.setSourcePosition(owner);
        AnnoDocUtil.addDocumentation(result, documentation);
        result.addAnnotation(createGeneratedAnnotation(GeneratedDslSupport.class, List.of(API_TAG, "dsl-support-role:" + name)));
        owner.getModule().addClass(result);
        return result;
    }

    private static void appendGeneratedTag(ClassNode implementation, String tag) {
        AnnotationNode generated = getAnnotation(implementation, KLUM_GENERATED);
        if (generated == null)
            throw new IllegalStateException("Generated implementation has no @KlumGenerated metadata: " + implementation.getName());

        List<Expression> tags = new ArrayList<>();
        Expression existing = generated.getMember("tags");
        if (existing instanceof ListExpression)
            tags.addAll(((ListExpression) existing).getExpressions());
        else if (existing != null)
            tags.add(existing);
        tags.add(new ConstantExpression(tag));
        generated.setMember("tags", new ListExpression(tags));
    }

    private static boolean hasGeneratedApiTag(ClassNode candidate) {
        AnnotationNode generated = getAnnotation(candidate, KLUM_GENERATED);
        if (generated == null) return false;
        Expression tags = generated.getMember("tags");
        if (tags instanceof ListExpression)
            return ((ListExpression) tags).getExpressions().stream()
                    .filter(ConstantExpression.class::isInstance)
                    .map(ConstantExpression.class::cast)
                    .map(ConstantExpression::getText)
                    .anyMatch(API_TAG::equals);
        return tags instanceof ConstantExpression && Objects.equals(((ConstantExpression) tags).getText(), API_TAG);
    }

    private static String generatedLink(ClassNode implementation) {
        AnnotationNode generated = getAnnotation(implementation, KLUM_GENERATED);
        if (generated == null) return null;
        Expression tags = generated.getMember("tags");
        if (tags instanceof ListExpression)
            return ((ListExpression) tags).getExpressions().stream()
                    .filter(ConstantExpression.class::isInstance)
                    .map(ConstantExpression.class::cast)
                    .map(ConstantExpression::getText)
                    .filter(tag -> tag.startsWith(INTERFACE_LINK_TAG))
                    .map(tag -> tag.substring(INTERFACE_LINK_TAG.length()))
                    .findFirst()
                    .orElse(null);
        if (tags instanceof ConstantExpression) {
            String tag = ((ConstantExpression) tags).getText();
            if (tag.startsWith(INTERFACE_LINK_TAG))
                return tag.substring(INTERFACE_LINK_TAG.length());
        }
        return null;
    }
}
