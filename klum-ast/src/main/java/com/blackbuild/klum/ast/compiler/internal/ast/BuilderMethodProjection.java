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
package com.blackbuild.klum.ast.compiler.internal.ast;

import com.blackbuild.annodocimal.ast.AstDocumentation;
import com.blackbuild.annodocimal.ast.Documentation;
import com.blackbuild.klum.ast.Builder;
import com.blackbuild.klum.ast.DelegatesToRW;
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessHelper;
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.KlumFactory;
import com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ExpressionTransformer;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CaseStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.DoWhileStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.SwitchStatement;
import org.codehaus.groovy.ast.stmt.SynchronizedStatement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.stmt.WhileStatement;
import org.codehaus.groovy.control.SourceUnit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.createGeneratedAnnotation;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.copyAnnotationsFromSourceToTarget;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getKeyField;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.addDelayedAction;
import static com.blackbuild.klum.ast.compiler.internal.reflect.AstReflectionBridge.cloneParamsWithAdjustedNames;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getElementTypeForCollection;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getElementTypeForMap;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isAssignableTo;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isCollection;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isMap;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;

/** Generates and links source-visible Builder-producing method twins for ADR 0004. */
public final class BuilderMethodProjection {

    static final String TWIN_METADATA_KEY = BuilderMethodProjection.class.getName() + ".twin";
    static final String ORIGINAL_METADATA_KEY = BuilderMethodProjection.class.getName() + ".original";
    private static final String CONCRETE_MODEL_METADATA_KEY = BuilderMethodProjection.class.getName() + ".concreteModel";
    private static final String OMISSION_REASON_METADATA_KEY = BuilderMethodProjection.class.getName() + ".omissionReason";
    private static final String PROJECTION_STATE_METADATA_KEY = BuilderMethodProjection.class.getName() + ".state";
    private static final String TWIN_PREFIX = "$klum$asBuilder$";
    private static final String ANNOTATION_VALUE_MEMBER = "value";

    private static final ClassNode BUILDER_INPUT = ClassHelper.make(Builder.Input.class);
    private static final ClassNode BUILDER_RESULT = ClassHelper.make(Builder.Result.class);
    private static final ClassNode KLUM_BUILDER = ClassHelper.make(KlumBuilder.class);
    private static final ClassNode KLUM_FACTORY = ClassHelper.make(KlumFactory.class);
    private static final ClassNode DELEGATES_TO_RW = ClassHelper.make(DelegatesToRW.class);
    private static final ClassNode DELEGATES_TO = ClassHelper.make(DelegatesTo.class);
    private static final List<ClassNode> BUILDER_FACTORY_TYPES = List.of(
            ClassHelper.make(KlumFactory.BuilderFactory.class),
            ClassHelper.make(KlumFactory.KeyedBuilderFactory.class),
            ClassHelper.make(KlumFactory.UnkeyedBuilderFactory.class)
    );

    private BuilderMethodProjection() {
    }

    /** Validates explicit signature facets and links selected static helpers before Builder methods are moved. */
    static void projectExplicitCapabilities(ClassNode model, SourceUnit sourceUnit) {
        List<MethodNode> selected = new ArrayList<>(model.getMethods()).stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(model.redirect()))
                .filter(BuilderMethodProjection::isExplicitCapability)
                .toList();

        selected.forEach(method -> validateExplicitProjection(method, sourceUnit));
        if (selected.stream().anyMatch(MethodNode::isStatic)) ensureProjectedMethods(model, model);

        // Static helpers use the linked-twin path below. Builder.Query owns query projection, while Builder.Method
        // declarations are retargeted in place when write-access methods move to the Builder.
    }

    /** Retargets the explicitly annotated positions of a mutator that is about to move to its Builder. */
    public static void projectExplicitMovedMethod(MethodNode method, ClassNode builder) {
        if (!isExplicitCapability(method) || !isValidExplicitProjection(method)) return;

        ClassNode model = method.getDeclaringClass();
        ProjectionState state = new ProjectionState(projectionModel(method, model));
        for (Parameter parameter : method.getParameters()) {
            if (isBuilderInput(parameter)) {
                ClassNode projected = projectExplicitType(parameter.getType());
                parameter.setType(projected);
                parameter.setOriginType(projected);
            }
        }
        if (hasBuilderResult(method)) method.setReturnType(projectExplicitType(method.getReturnType()));

        Candidate candidate = new Candidate(method, method, true, builder);
        state.candidates.put(method, candidate);
        method.setCode(new ProjectionTransformer(state, candidate).cloneStatement(method.getCode()));

        if (candidate.opaque)
            CommonAstHelper.addCompileError(
                    model.getModule().getContext(),
                    "Cannot project " + explicitAnnotationName(method) + " method " + method.getTypeDescriptor()
                            + ": its Builder-side body contains an opaque or precompiled Model-producing call. "
                            + "Recompile the producer with its explicit Builder contract or call a generated relationship/Builder method.",
                    method
            );
    }

    private static void validateExplicitProjection(MethodNode method, SourceUnit sourceUnit) {
        if (!method.isPublic())
            CommonAstHelper.addCompileError(sourceUnit, explicitAnnotationName(method) + " methods must be public", method);
        if (method.isAbstract())
            CommonAstHelper.addCompileError(sourceUnit, explicitAnnotationName(method) + " methods must declare an implementation", method);
        if (!method.isStatic()
                && !BuilderQuerySupport.isBuilderQuery(method)
                && !WriteAccessHelper.isBuilderMethod(method))
            CommonAstHelper.addCompileError(
                    sourceUnit,
                    explicitAnnotationName(method)
                            + " on an instance method requires @Builder.Query or @Builder.Method",
                    method
            );

        for (Parameter parameter : method.getParameters()) {
            if (!isBuilderInput(parameter)) continue;
            String problem = explicitProjectionProblem(parameter.getType(), false);
            if (problem != null)
                CommonAstHelper.addCompileError(
                        sourceUnit,
                        "Cannot project @Builder.Input parameter '" + parameter.getName() + "' of "
                                + method.getTypeDescriptor() + ": " + problem,
                        parameter
                );
        }

        if (hasBuilderResult(method)) {
            String problem = explicitProjectionProblem(method.getReturnType(), false);
            if (problem != null)
                CommonAstHelper.addCompileError(
                        sourceUnit,
                        "Cannot project @Builder.Result of " + method.getTypeDescriptor() + ": " + problem,
                        method
                );
        }
    }

    private static boolean isValidExplicitProjection(MethodNode method) {
        if (!method.isPublic() || method.isAbstract()) return false;
        if (hasBuilderResult(method) && explicitProjectionProblem(method.getReturnType(), false) != null) return false;
        return Arrays.stream(method.getParameters())
                .filter(BuilderMethodProjection::isBuilderInput)
                .allMatch(parameter -> explicitProjectionProblem(parameter.getType(), false) == null);
    }

    private static boolean isExplicitCapability(MethodNode method) {
        return hasBuilderResult(method) || Arrays.stream(method.getParameters()).anyMatch(BuilderMethodProjection::isBuilderInput);
    }

    static boolean hasExplicitProjection(MethodNode method) {
        return isExplicitCapability(method);
    }

    static boolean hasBuilderResult(MethodNode method) {
        return !method.getAnnotations(BUILDER_RESULT).isEmpty();
    }

    static boolean isBuilderInput(Parameter parameter) {
        return !parameter.getAnnotations(BUILDER_INPUT).isEmpty();
    }

    private static String explicitAnnotationName(MethodNode method) {
        if (hasBuilderResult(method) && Arrays.stream(method.getParameters()).anyMatch(BuilderMethodProjection::isBuilderInput))
            return "@Builder.Input/@Builder.Result";
        return hasBuilderResult(method) ? "@Builder.Result" : "@Builder.Input";
    }

    private static String projectedSignature(MethodNode source, Parameter[] parameters) {
        return source.getName() + "(" + Arrays.stream(parameters)
                .map(parameter -> parameter.getType().getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("") + ")";
    }

    private static ClassNode projectionModel(MethodNode method, ClassNode fallback) {
        if (!hasBuilderResult(method)) return fallback;
        ClassNode type = method.getReturnType();
        if (isDSLObject(type)) return type;
        if (isCollection(type)) return getElementTypeForCollection(type);
        if (isMap(type)) return getElementTypeForMap(type);
        return fallback;
    }

    private static String explicitProjectionProblem(ClassNode type, boolean nested) {
        if (type == null) return "the type is unresolved";
        if (type.isGenericsPlaceHolder()) return "the DSL Object type is an unresolved generic placeholder";
        if (isAssignableTo(type, KLUM_BUILDER)) {
            GenericsType[] generics = type.getGenericsTypes();
            if (generics == null || generics.length != 1) return "the KlumBuilder type is raw";
            GenericsType model = generics[0];
            if (model.isWildcard()) return "the KlumBuilder model type is a wildcard";
            if (model.isPlaceholder()) return "the KlumBuilder model type is an unresolved generic placeholder";
            if (model.getType() == null || !isDSLObject(model.getType()))
                return "the KlumBuilder model type does not resolve to a DSL Object";
            return null;
        }
        if (isDSLObject(type)) return null;
        if (isCollection(type)) {
            if (nested) return "nested Collection/Map positions are not supported";
            GenericsType[] generics = type.getGenericsTypes();
            if (generics == null || generics.length != 1) return "the Collection type is raw";
            return explicitGenericProjectionProblem(generics[0], true);
        }
        if (isMap(type)) {
            if (nested) return "nested Collection/Map positions are not supported";
            GenericsType[] generics = type.getGenericsTypes();
            if (generics == null || generics.length != 2) return "the Map type is raw";
            return explicitGenericProjectionProblem(generics[1], true);
        }
        return "the type does not resolve to a DSL Object or a supported Collection/Map of DSL Objects";
    }

    private static String explicitGenericProjectionProblem(GenericsType generic, boolean nested) {
        if (generic == null) return "the generic DSL Object type is unresolved";
        if (generic.isWildcard()) return "the DSL Object element type is a wildcard";
        if (generic.isPlaceholder()) return "the DSL Object element type is an unresolved generic placeholder";
        return explicitProjectionProblem(generic.getType(), nested);
    }

    private static ClassNode projectExplicitType(ClassNode sourceType) {
        if (isAssignableTo(sourceType, KLUM_BUILDER))
            return GeneratedDslSupport.builderTypeFor(sourceType.getGenericsTypes()[0].getType());
        if (isDSLObject(sourceType))
            return (sourceType.getModifiers() & Opcodes.ACC_ABSTRACT) != 0
                    ? GeneratedDslSupport.builderTypeForSubtype(sourceType)
                    : GeneratedDslSupport.builderTypeFor(sourceType);
        if (isCollection(sourceType)) {
            ClassNode result = sourceType.getPlainNodeReference();
            result.setUsingGenerics(true);
            result.setGenericsTypes(new GenericsType[] {
                    new GenericsType(projectExplicitType(getElementTypeForCollection(sourceType)))
            });
            return result;
        }
        if (isMap(sourceType)) {
            GenericsType[] generics = sourceType.getGenericsTypes();
            ClassNode result = sourceType.getPlainNodeReference();
            result.setUsingGenerics(true);
            result.setGenericsTypes(new GenericsType[] {
                    generics[0],
                    new GenericsType(projectExplicitType(getElementTypeForMap(sourceType)))
            });
            return result;
        }
        return sourceType;
    }

    static MethodNode builderProducerFor(MethodNode source, ClassNode expectedModel) {
        if (isExplicitBuilderProducer(source.getReturnType(), expectedModel))
            return source;

        ensureProjectedMethods(source.getDeclaringClass(), expectedModel);
        return source.getNodeMetaData(TWIN_METADATA_KEY);
    }

    static ClassNode projectedBuilderType(ClassNode sourceType, ClassNode expectedModel) {
        ClassNode projected = projectType(sourceType, expectedModel);
        return projected == null ? sourceType : projected;
    }

    static boolean isBuilderProducerType(ClassNode sourceType, ClassNode expectedModel) {
        return isExplicitBuilderProducer(sourceType, expectedModel);
    }

    static boolean isSingleBuilderProducerType(ClassNode sourceType, ClassNode expectedModel) {
        if (!isAssignableTo(sourceType, KLUM_BUILDER)) return false;
        GenericsType[] generics = sourceType.getGenericsTypes();
        return generics != null && generics.length == 1 && isConcreteModelType(generics[0], expectedModel);
    }

    static Parameter[] projectedParameters(MethodNode source, ClassNode defaultModel) {
        Parameter[] result = cloneParamsWithAdjustedNames(source);
        for (int index = 0; index < result.length; index++) {
            Parameter original = source.getParameters()[index];
            Parameter projected = result[index];
            copyAnnotationsFromSourceToTarget(original, projected, Collections.emptyList());

            if (isBuilderInput(original)
                    && explicitProjectionProblem(original.getType(), false) == null) {
                ClassNode projectedType = projectExplicitType(original.getType());
                projected.setType(projectedType);
                projected.setOriginType(projectedType);
            }

            List<AnnotationNode> aliases = original.getAnnotations(DELEGATES_TO_RW);
            if (aliases.isEmpty()) continue;
            ClassNode target = CommonAstHelper.getNullSafeClassMember(aliases.get(0), ANNOTATION_VALUE_MEMBER, defaultModel);
            projected.getAnnotations().removeIf(annotation -> annotation.getClassNode().equals(DELEGATES_TO));
            AnnotationNode delegatesTo = new AnnotationNode(DELEGATES_TO);
            delegatesTo.setMember(ANNOTATION_VALUE_MEMBER, classX(GeneratedDslSupport.builderTypeFor(target)));
            delegatesTo.setMember("strategy", constX(Closure.DELEGATE_ONLY));
            projected.addAnnotation(delegatesTo);
        }
        return result;
    }

    static ClassNode concreteModelFor(MethodNode twin, ClassNode fallback) {
        ClassNode concrete = twin.getNodeMetaData(CONCRETE_MODEL_METADATA_KEY);
        return concrete != null ? concrete : fallback;
    }

    static String omissionReasonFor(MethodNode source) {
        String reason = source.getNodeMetaData(OMISSION_REASON_METADATA_KEY);
        if (reason != null) return reason;
        if (source.getDeclaringClass().isResolved())
            return "the producer is opaque or precompiled and has no AST-linked Builder-producing twin";
        return "the producer body cannot be resolved exclusively to active-session Builder-producing calls";
    }

    static void documentComposition(AbstractMethodBuilder<?> method, MethodNode source, ClassNode producerReturnType) {
        Documentation sourceDoc = AstDocumentation.extractExact(source).orElse(Documentation.empty());
        boolean mapResult = isMap(producerReturnType);
        boolean collectionResult = isCollection(producerReturnType);

        method.withDocumentation(doc -> {
            if (mapResult || collectionResult) {
                doc.title("Creates active-session Builders, attaches them to this relationship, and returns the producer's original container.")
                        .p("Every returned Builder remains unsealed in the active construction session and attached to this relationship; "
                                + "it cannot be independently materialized or validated.")
                        .p("The returned container preserves its concrete subtype, iteration order, comparator, duplicate behavior, "
                                + (mapResult ? "and original map keys." : "and element order."));
            } else {
                doc.title("Creates an unsealed Builder in the active construction session and attaches it to this relationship.")
                        .p("The returned Builder remains attached to the current construction session; "
                                + "it cannot be independently materialized or validated.");
            }

            sourceDoc.getParameters().forEach(doc::param);
            sourceDoc.getExceptions().forEach(doc::throwsException);
            if (mapResult)
                doc.returnType("the producer's original map with its original keys and attached, unsealed Builder values");
            else if (collectionResult)
                doc.returnType("the producer's original container of attached, unsealed Builders");
            else
                doc.returnType("the attached, unsealed Builder");
            doc.see(AstDocumentation.referenceTo(source));
        });
    }

    static void ensureProjectedMethods(ClassNode sourceClass, ClassNode expectedModel) {
        if (sourceClass == null || (sourceClass.isResolved() && sourceClass.getModule() == null)) return;

        ProjectionState existing = sourceClass.redirect().getNodeMetaData(PROJECTION_STATE_METADATA_KEY);
        if (existing != null) return;

        ProjectionState state = new ProjectionState(expectedModel.redirect());
        sourceClass.redirect().setNodeMetaData(PROJECTION_STATE_METADATA_KEY, state);

        List<MethodNode> declaredMethods = new ArrayList<>();
        sourceClass.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(sourceClass.redirect()))
                .filter(MethodNode::isPublic)
                .filter(method -> !method.isAbstract())
                .filter(method -> !method.getName().startsWith("$klum$"))
                .forEach(declaredMethods::add);

        declaredMethods.forEach(method -> {
            String problem = unresolvedBuilderProblem(method.getReturnType());
            if (problem != null)
                CommonAstHelper.addCompileError(
                        sourceClass.getModule().getContext(),
                        "Cannot project Builder-producing method " + method.getTypeDescriptor() + ": " + problem
                                + ". Declare a concrete KlumBuilder<Foo> element type.",
                        method
                );
        });

        declaredMethods.stream()
                .filter(method -> !(isDSLObject(sourceClass) && isExplicitCapability(method) && !method.isStatic()))
                .filter(method -> projectType(method.getReturnType(), expectedModel) != null
                        || method.isStatic() && isExplicitCapability(method))
                .forEach(method -> state.addCandidate(
                        method,
                        createTwinShell(method, expectedModel),
                        isExplicitCapability(method),
                        null
                ));

        Map<String, List<Candidate>> projectedSignatures = new LinkedHashMap<>();
        state.candidates.values().forEach(candidate -> projectedSignatures
                .computeIfAbsent(projectedSignature(candidate.twin, candidate.twin.getParameters()), ignored -> new ArrayList<>())
                .add(candidate));
        projectedSignatures.values().stream()
                .filter(candidates -> candidates.size() > 1)
                .forEach(candidates -> {
                    String originals = candidates.stream()
                            .map(candidate -> candidate.original.getTypeDescriptor())
                            .reduce((left, right) -> left + " and " + right)
                            .orElse("projected methods");
                    candidates.forEach(candidate -> {
                        candidate.opaque = true;
                        CommonAstHelper.addCompileError(
                                sourceClass.getModule().getContext(),
                                "Projected Builder overloads collapse to "
                                        + projectedSignature(candidate.twin, candidate.twin.getParameters())
                                        + ": " + originals,
                                candidate.original
                        );
                    });
                });

        state.candidates.values().forEach(candidate -> {
            ProjectionTransformer transformer = new ProjectionTransformer(state, candidate);
            candidate.twin.setCode(transformer.cloneStatement(candidate.original.getCode()));
        });

        Set<Candidate> adaptable = resolveAdaptableCandidates(state.candidates.values());
        adaptable.forEach(candidate -> {
            if (candidate.concreteModels.size() == 1)
                candidate.twin.setNodeMetaData(CONCRETE_MODEL_METADATA_KEY, candidate.concreteModels.iterator().next());
            candidate.original.getDeclaringClass().addMethod(candidate.twin);
        });
        state.candidates.values().stream()
                .filter(candidate -> !adaptable.contains(candidate))
                .forEach(candidate -> {
                    candidate.original.setNodeMetaData(
                            OMISSION_REASON_METADATA_KEY,
                            candidate.opaque
                                    ? "the producer body contains an opaque materializing call"
                                    : "the producer body has no active-session Builder-producing path");
                    candidate.original.removeNodeMetaData(TWIN_METADATA_KEY);
                    candidate.twin.removeNodeMetaData(ORIGINAL_METADATA_KEY);
                });
    }

    /**
     * Rewrites qualified calls to source-visible static model converters after a method has moved to Builder code.
     * The original source method is no longer callable in that phase because its completed-model result cannot be
     * attached as owned composition.
     */
    public static void projectQualifiedStaticCallsInBuilderMethod(MethodNode method) {
        if (method.getCode() == null) return;
        ensureQualifiedStaticCallOwners(method);
        scheduleProjectionAfterSourceTypeTransformation(method);
        method.setCode(new BuilderPhaseProjectionTransformer(method.getDeclaringClass()).cloneStatement(method.getCode()));
    }

    private static void ensureQualifiedStaticCallOwners(MethodNode method) {
        method.getCode().visit(new CodeVisitorSupport() {
            @Override
            public void visitMethodCallExpression(MethodCallExpression call) {
                super.visitMethodCallExpression(call);
                ClassNode owner = sourceClassFor(call.getObjectExpression(), method.getDeclaringClass());
                if (owner != null)
                    ensureProjectedMethods(owner, owner);
            }

            @Override
            public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
                super.visitStaticMethodCallExpression(call);
                ensureProjectedMethods(sourceClassFor(call.getOwnerType(), method.getDeclaringClass()), call.getOwnerType());
            }
        });
    }

    private static void scheduleProjectionAfterSourceTypeTransformation(MethodNode method) {
        Set<ClassNode> pendingOwners = Collections.newSetFromMap(new IdentityHashMap<>());
        method.getCode().visit(new CodeVisitorSupport() {
            @Override
            public void visitMethodCallExpression(MethodCallExpression call) {
                super.visitMethodCallExpression(call);
                ClassNode owner = sourceClassFor(call.getObjectExpression(), method.getDeclaringClass());
                if (owner != null && builderTwinFor(call.getMethodTarget(), owner,
                        call.getMethodAsString(), call.getArguments()) == null)
                    pendingOwners.add(owner.redirect());
            }

            @Override
            public void visitStaticMethodCallExpression(StaticMethodCallExpression call) {
                super.visitStaticMethodCallExpression(call);
                ClassNode owner = sourceClassFor(call.getOwnerType(), method.getDeclaringClass());
                if (builderTwinFor(null, owner, call.getMethod(), call.getArguments()) == null)
                    pendingOwners.add(owner.redirect());
            }
        });
        pendingOwners.forEach(owner -> addDelayedAction(owner, () ->
                method.setCode(new BuilderPhaseProjectionTransformer(method.getDeclaringClass()).cloneStatement(method.getCode()))));
    }

    private static ClassNode sourceClassFor(ClassNode owner, ClassNode context) {
        if (owner == null || context == null || context.getModule() == null) return owner;
        return context.getModule().getClasses().stream()
                .filter(candidate -> candidate.getName().equals(owner.getName()))
                .findFirst()
                .orElse(owner);
    }

    private static ClassNode sourceClassFor(Expression receiver, ClassNode context) {
        if (receiver instanceof ClassExpression owner)
            return sourceClassFor(owner.getType(), context);
        if (!(receiver instanceof VariableExpression variable)
                || !(variable.getAccessedVariable() instanceof DynamicVariable)
                || context == null
                || context.getModule() == null)
            return null;
        return context.getModule().getClasses().stream()
                .filter(candidate -> candidate.getNameWithoutPackage().equals(variable.getName()))
                .findFirst()
                .orElse(null);
    }

    private static MethodNode builderTwinFor(MethodNode target, ClassNode owner, String name, Expression arguments) {
        if (target != null) {
            MethodNode twin = target.getNodeMetaData(TWIN_METADATA_KEY);
            if (twin != null) return twin;
        }
        if (owner == null) return null;

        List<MethodNode> emittedTwins = owner.getMethods(TWIN_PREFIX + name).stream()
                .filter(MethodNode::isStatic)
                .filter(method -> method.getDeclaringClass().redirect().equals(owner.redirect()))
                .filter(BuilderMethodProjection::isExplicitCapability)
                .filter(method -> acceptsArgumentCount(method, argumentCount(arguments)))
                .toList();
        if (emittedTwins.size() == 1) return emittedTwins.get(0);
        MethodNode matchingEmittedTwin = emittedTwins.stream()
                .filter(method -> argumentsMatch(method.getParameters(), arguments))
                .findFirst()
                .orElse(null);
        if (matchingEmittedTwin != null) return matchingEmittedTwin;

        List<MethodNode> candidates = owner.getMethods(name).stream()
                .filter(MethodNode::isStatic)
                .filter(method -> method.getDeclaringClass().redirect().equals(owner.redirect()))
                .filter(method -> method.getNodeMetaData(TWIN_METADATA_KEY) != null)
                .filter(method -> acceptsArgumentCount(method, argumentCount(arguments)))
                .toList();
        if (candidates.size() == 1) return candidates.get(0).getNodeMetaData(TWIN_METADATA_KEY);

        return candidates.stream()
                .filter(method -> argumentsMatch(method.getParameters(), arguments))
                .map(method -> (MethodNode) method.getNodeMetaData(TWIN_METADATA_KEY))
                .findFirst()
                .orElse(null);
    }

    private static boolean acceptsArgumentCount(MethodNode method, int argumentCount) {
        int required = (int) Arrays.stream(method.getParameters())
                .filter(parameter -> !parameter.hasInitialExpression())
                .count();
        return argumentCount >= required && argumentCount <= method.getParameters().length;
    }

    private static int argumentCount(Expression arguments) {
        return arguments instanceof TupleExpression tupleExpression ? tupleExpression.getExpressions().size() : 1;
    }

    private static boolean argumentsMatch(Parameter[] parameters, Expression arguments) {
        if (!(arguments instanceof TupleExpression tupleExpression)) return parameters.length == 1;
        List<Expression> expressions = tupleExpression.getExpressions();
        if (parameters.length != expressions.size()) return false;
        for (int index = 0; index < parameters.length; index++) {
            Expression expression = expressions.get(index);
            if (expression instanceof MapExpression
                    && !isAssignableTo(ClassHelper.MAP_TYPE, parameters[index].getType()))
                return false;
            if (expression instanceof ClosureExpression
                    && !parameters[index].getType().equals(ClassHelper.CLOSURE_TYPE))
                return false;
            ClassNode expressionType = expression.getType();
            if (expressionType != null
                    && !expressionType.equals(ClassHelper.OBJECT_TYPE)
                    && !isAssignableTo(expressionType, parameters[index].getOriginType()))
                return false;
        }
        return true;
    }

    abstract static class StatementCloner implements ExpressionTransformer {

        protected final Statement cloneStatement(Statement source) {
            if (source == null) return null;
            Statement result;
            if (source instanceof BlockStatement block) {
                List<Statement> statements = new ArrayList<>();
                block.getStatements().forEach(statement -> statements.add(cloneStatement(statement)));
                result = new BlockStatement(statements, block.getVariableScope());
            } else if (source instanceof ReturnStatement returnStatement) {
                result = new ReturnStatement(transform(returnStatement.getExpression()));
            } else if (source instanceof ExpressionStatement expressionStatement) {
                result = new ExpressionStatement(transform(expressionStatement.getExpression()));
            } else if (source instanceof IfStatement ifStatement) {
                result = new IfStatement(
                        (BooleanExpression) transform(ifStatement.getBooleanExpression()),
                        cloneStatement(ifStatement.getIfBlock()),
                        cloneStatement(ifStatement.getElseBlock())
                );
            } else if (source instanceof ForStatement forStatement) {
                ForStatement copy = new ForStatement(
                        forStatement.getVariable(),
                        transform(forStatement.getCollectionExpression()),
                        cloneStatement(forStatement.getLoopBlock())
                );
                copy.setVariableScope(forStatement.getVariableScope());
                result = copy;
            } else if (source instanceof WhileStatement whileStatement) {
                result = new WhileStatement(
                        (BooleanExpression) transform(whileStatement.getBooleanExpression()),
                        cloneStatement(whileStatement.getLoopBlock())
                );
            } else if (source instanceof DoWhileStatement doWhileStatement) {
                result = new DoWhileStatement(
                        (BooleanExpression) transform(doWhileStatement.getBooleanExpression()),
                        cloneStatement(doWhileStatement.getLoopBlock())
                );
            } else if (source instanceof TryCatchStatement tryCatchStatement) {
                TryCatchStatement copy = new TryCatchStatement(
                        cloneStatement(tryCatchStatement.getTryStatement()),
                        cloneStatement(tryCatchStatement.getFinallyStatement())
                );
                tryCatchStatement.getResourceStatements().forEach(resource ->
                        copy.addResource((ExpressionStatement) cloneStatement(resource)));
                tryCatchStatement.getCatchStatements().forEach(catchStatement ->
                        copy.addCatch(cloneCatchStatement(catchStatement)));
                result = copy;
            } else if (source instanceof SwitchStatement switchStatement) {
                List<CaseStatement> cases = switchStatement.getCaseStatements().stream()
                        .map(this::cloneCaseStatement)
                        .toList();
                result = new SwitchStatement(
                        transform(switchStatement.getExpression()),
                        cases,
                        cloneStatement(switchStatement.getDefaultStatement())
                );
            } else if (source instanceof SynchronizedStatement synchronizedStatement) {
                result = new SynchronizedStatement(
                        transform(synchronizedStatement.getExpression()),
                        cloneStatement(synchronizedStatement.getCode())
                );
            } else if (source instanceof AssertStatement assertStatement) {
                result = new AssertStatement(
                        (BooleanExpression) transform(assertStatement.getBooleanExpression()),
                        transform(assertStatement.getMessageExpression())
                );
            } else if (source instanceof ThrowStatement throwStatement) {
                result = new ThrowStatement(transform(throwStatement.getExpression()));
            } else if (source instanceof EmptyStatement) {
                return source;
            } else {
                return handleUnsupportedStatement(source);
            }
            result.setSourcePosition(source);
            result.copyNodeMetaData(source);
            result.copyStatementLabels(source);
            return result;
        }

        protected abstract Statement handleUnsupportedStatement(Statement source);

        protected final ClosureExpression cloneClosure(ClosureExpression source) {
            ClosureExpression result = new ClosureExpression(source.getParameters(), cloneStatement(source.getCode()));
            result.setVariableScope(source.getVariableScope());
            result.setSourcePosition(source);
            result.copyNodeMetaData(source);
            return result;
        }

        private CatchStatement cloneCatchStatement(CatchStatement source) {
            CatchStatement result = new CatchStatement(source.getVariable(), cloneStatement(source.getCode()));
            result.setSourcePosition(source);
            result.copyNodeMetaData(source);
            result.copyStatementLabels(source);
            return result;
        }

        private CaseStatement cloneCaseStatement(CaseStatement source) {
            CaseStatement result = new CaseStatement(transform(source.getExpression()), cloneStatement(source.getCode()));
            result.setSourcePosition(source);
            result.copyNodeMetaData(source);
            result.copyStatementLabels(source);
            return result;
        }
    }

    private static final class BuilderPhaseProjectionTransformer extends StatementCloner {
        private final ClassNode context;

        private BuilderPhaseProjectionTransformer(ClassNode context) {
            this.context = context;
        }

        @Override
        protected Statement handleUnsupportedStatement(Statement source) {
            return source;
        }

        @Override
        public Expression transform(Expression expression) {
            if (expression == null) return null;
            if (expression instanceof ClosureExpression source) {
                return cloneClosure(source);
            }
            if (expression instanceof StaticMethodCallExpression source) {
                Expression arguments = transform(source.getArguments());
                MethodNode twin = builderTwinFor(null, sourceClassFor(source.getOwnerType(), context), source.getMethod(), source.getArguments());
                if (twin == null) {
                    diagnoseMissingPrecompiledTwin(sourceClassFor(source.getOwnerType(), context), source.getMethod(),
                            source.getArguments(), source);
                    StaticMethodCallExpression result = new StaticMethodCallExpression(
                            source.getOwnerType(), source.getMethod(), arguments);
                    result.setSourcePosition(source);
                    result.copyNodeMetaData(source);
                    return result;
                }
                MethodCallExpression result = new MethodCallExpression(
                        classX(source.getOwnerType()), new ConstantExpression(twin.getName()), arguments);
                result.setImplicitThis(false);
                result.setType(source.getType());
                result.setSourcePosition(source);
                result.copyNodeMetaData(source);
                return result;
            }
            if (expression instanceof MethodCallExpression source) {
                MethodCallExpression result = (MethodCallExpression) source.transformExpression(this);
                ClassNode owner = sourceClassFor(source.getObjectExpression(), context);
                MethodNode twin = owner == null ? null
                        : builderTwinFor(source.getMethodTarget(), owner, source.getMethodAsString(), source.getArguments());
                if (twin == null) {
                    diagnoseMissingPrecompiledTwin(owner, source.getMethodAsString(), source.getArguments(), source);
                    return result;
                }
                result.setMethod(new ConstantExpression(twin.getName()));
                result.setType(source.getType());
                return result;
            }
            return expression.transformExpression(this);
        }

        private void diagnoseMissingPrecompiledTwin(ClassNode owner, String name, Expression arguments, Expression source) {
            if (owner == null || !owner.isResolved() || owner.getModule() != null) return;
            boolean explicitlySelected = owner.getMethods(name).stream()
                    .filter(MethodNode::isStatic)
                    .filter(method -> acceptsArgumentCount(method, argumentCount(arguments)))
                    .anyMatch(BuilderMethodProjection::isExplicitCapability);
            if (!explicitlySelected) return;
            CommonAstHelper.addCompileError(
                    context.getModule().getContext(),
                    "Cannot project explicitly selected precompiled helper " + owner.getName() + "." + name
                            + "(): its emitted Builder twin is unavailable. Recompile the declaring Schema with the current "
                            + "KlumAST version or call an explicit generated relationship/Builder method.",
                    source
            );
        }
    }

    private static MethodNode createTwinShell(MethodNode source, ClassNode expectedModel) {
        normalizeDelegatingParameters(source, expectedModel);
        ClassNode projectedReturn = hasBuilderResult(source)
                && explicitProjectionProblem(source.getReturnType(), false) == null
                ? projectExplicitType(source.getReturnType())
                : projectType(source.getReturnType(), expectedModel);
        if (projectedReturn == null) projectedReturn = source.getReturnType();
        int modifiers = (source.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_STRICT)) | Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;
        MethodNode twin = new MethodNode(
                TWIN_PREFIX + source.getName(),
                modifiers,
                projectedReturn,
                isExplicitCapability(source) ? projectedParameters(source, expectedModel) : source.getParameters(),
                source.getExceptions(),
                EmptyStatement.INSTANCE
        );
        twin.setGenericsTypes(source.getGenericsTypes());
        twin.setSourcePosition(source);
        twin.setSynthetic(true);
        source.getAnnotations(BUILDER_RESULT).forEach(twin::addAnnotation);
        twin.addAnnotation(createGeneratedAnnotation(BuilderMethodProjection.class));
        source.setNodeMetaData(TWIN_METADATA_KEY, twin);
        twin.setNodeMetaData(ORIGINAL_METADATA_KEY, source);
        return twin;
    }

    private static void normalizeDelegatingParameters(MethodNode source, ClassNode defaultModel) {
        for (Parameter parameter : source.getParameters()) {
            if (parameter.getAnnotations(DELEGATES_TO).isEmpty()) {
                List<AnnotationNode> aliases = parameter.getAnnotations(DELEGATES_TO_RW);
                if (!aliases.isEmpty()) {
                    ClassNode target = CommonAstHelper.getNullSafeClassMember(
                            aliases.get(0),
                            ANNOTATION_VALUE_MEMBER,
                            defaultModel
                    );
                    DelegatesToRWTransformation.addDelegatesToAnnotation(target, parameter);
                }
            }
        }
    }

    private static Set<Candidate> resolveAdaptableCandidates(Iterable<Candidate> candidates) {
        Set<Candidate> result = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean changed;
        do {
            changed = false;
            for (Candidate candidate : candidates) {
                if (isNewlyAdaptable(candidate, result)) changed |= result.add(candidate);
            }
        } while (changed);
        return result;
    }

    private static boolean isNewlyAdaptable(Candidate candidate, Set<Candidate> adaptable) {
        if (candidate.opaque || adaptable.contains(candidate)) return false;
        if (candidate.explicit) return adaptable.containsAll(candidate.dependencies);
        boolean hasBuilderPath = candidate.directBuilderCall
                || candidate.dependencies.stream().anyMatch(adaptable::contains);
        return hasBuilderPath && adaptable.containsAll(candidate.dependencies);
    }

    private static boolean isExplicitBuilderProducer(ClassNode returnType, ClassNode expectedModel) {
        if (isSingleBuilderProducerType(returnType, expectedModel)) return true;
        if (isCollection(returnType))
            return isExplicitBuilderProducer(getElementTypeForCollection(returnType), expectedModel);
        if (isMap(returnType))
            return isExplicitBuilderProducer(getElementTypeForMap(returnType), expectedModel);
        return false;
    }

    private static ClassNode projectType(ClassNode sourceType, ClassNode expectedModel) {
        if (sourceType == null) return null;
        if (isDSLObject(sourceType) && isAssignableTo(sourceType, expectedModel))
            return (sourceType.getModifiers() & Opcodes.ACC_ABSTRACT) != 0
                    ? GeneratedDslSupport.builderTypeForSubtype(sourceType)
                    : GeneratedDslSupport.builderTypeFor(sourceType);

        if (isAssignableTo(sourceType, KLUM_BUILDER)) {
            GenericsType[] generics = sourceType.getGenericsTypes();
            if (generics == null || generics.length != 1 || !isConcreteModelType(generics[0], expectedModel))
                return null;
            return GeneratedDslSupport.builderTypeFor(generics[0].getType());
        }

        if (isCollection(sourceType)) {
            ClassNode elementType = getElementTypeForCollection(sourceType);
            ClassNode projectedElement = projectType(elementType, expectedModel);
            if (projectedElement == null) return null;
            ClassNode result = sourceType.getPlainNodeReference();
            result.setUsingGenerics(true);
            result.setGenericsTypes(new GenericsType[] { new GenericsType(projectedElement) });
            return result;
        }

        if (isMap(sourceType)) {
            GenericsType[] generics = sourceType.getGenericsTypes();
            ClassNode valueType = getElementTypeForMap(sourceType);
            ClassNode projectedValue = projectType(valueType, expectedModel);
            if (generics == null || generics.length != 2 || projectedValue == null) return null;
            ClassNode result = sourceType.getPlainNodeReference();
            result.setUsingGenerics(true);
            result.setGenericsTypes(new GenericsType[] { generics[0], new GenericsType(projectedValue) });
            return result;
        }
        return null;
    }

    private static boolean isConcreteModelType(GenericsType generic, ClassNode expectedModel) {
        return generic != null
                && !generic.isWildcard()
                && !generic.isPlaceholder()
                && generic.getType() != null
                && isDSLObject(generic.getType())
                && isAssignableTo(generic.getType(), expectedModel);
    }

    private static String unresolvedBuilderProblem(ClassNode type) {
        if (type == null) return null;
        if (isAssignableTo(type, KLUM_BUILDER)) return unresolvedKlumBuilderProblem(type);
        GenericsType[] generics = type.getGenericsTypes();
        if (generics == null) return null;
        return Arrays.stream(generics)
                .map(BuilderMethodProjection::unresolvedGenericBuilderProblem)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private static String unresolvedKlumBuilderProblem(ClassNode type) {
        GenericsType[] generics = type.getGenericsTypes();
        if (generics == null || generics.length != 1)
            return "the KlumBuilder return type is raw";
        GenericsType element = generics[0];
        if (element.isWildcard())
            return "the KlumBuilder element type is a wildcard";
        if (element.isPlaceholder())
            return "the KlumBuilder element type is an unresolved generic placeholder";
        if (element.getType() == null || !isDSLObject(element.getType()))
            return "the KlumBuilder element type does not resolve to a DSL Object";
        return null;
    }

    private static String unresolvedGenericBuilderProblem(GenericsType generic) {
        if (generic.isWildcard())
            return wildcardContainsBuilderType(generic)
                    ? "a container Builder element type is a wildcard"
                    : null;
        return unresolvedBuilderProblem(generic.getType());
    }

    private static boolean wildcardContainsBuilderType(GenericsType generic) {
        ClassNode[] upperBounds = generic.getUpperBounds();
        boolean matchingUpperBound = upperBounds != null
                && Arrays.stream(upperBounds).anyMatch(BuilderMethodProjection::containsBuilderType);
        return matchingUpperBound || containsBuilderType(generic.getLowerBound());
    }

    private static boolean containsBuilderType(ClassNode type) {
        return type != null
                && (isAssignableTo(type, KLUM_BUILDER) || unresolvedBuilderProblem(type) != null);
    }

    private static final class ProjectionState {
        private final ClassNode model;
        private final Map<MethodNode, Candidate> candidates = new LinkedHashMap<>();

        private ProjectionState(ClassNode model) {
            this.model = model;
        }

        private void addCandidate(MethodNode original, MethodNode twin, boolean explicit, ClassNode builder) {
            candidates.put(original, new Candidate(original, twin, explicit, builder));
        }

        private Candidate candidateFor(MethodNode method) {
            return candidates.get(method);
        }
    }

    private static final class Candidate {
        private final MethodNode original;
        private final MethodNode twin;
        private final boolean explicit;
        private final ClassNode builder;
        private final Map<Variable, Parameter> parameters = new IdentityHashMap<>();
        private final Set<Candidate> dependencies = new LinkedHashSet<>();
        private final Set<ClassNode> concreteModels = new LinkedHashSet<>();
        private boolean directBuilderCall;
        private boolean opaque;

        private Candidate(MethodNode original, MethodNode twin, boolean explicit, ClassNode builder) {
            this.original = original;
            this.twin = twin;
            this.explicit = explicit;
            this.builder = builder;
            Parameter[] originalParameters = original.getParameters();
            Parameter[] twinParameters = twin.getParameters();
            for (int index = 0; index < Math.min(originalParameters.length, twinParameters.length); index++)
                parameters.put(originalParameters[index], twinParameters[index]);
        }
    }

    private static final class ProjectionTransformer extends StatementCloner {
        private final ProjectionState state;
        private final Candidate candidate;

        private ProjectionTransformer(ProjectionState state, Candidate candidate) {
            this.state = state;
            this.candidate = candidate;
        }

        @Override
        protected Statement handleUnsupportedStatement(Statement source) {
            candidate.opaque = true;
            return source;
        }

        @Override
        public Expression transform(Expression expression) {
            if (expression == null) return null;
            if (expression instanceof ClosureExpression source) {
                return cloneClosure(source);
            }
            if (expression instanceof VariableExpression source) {
                VariableExpression result = (VariableExpression) source.transformExpression(this);
                Parameter parameter = candidate.parameters.get(source.getAccessedVariable());
                if (parameter != null) {
                    result.setAccessedVariable(parameter);
                    result.setType(parameter.getType());
                    return result;
                }
                if (candidate.builder != null && source.getAccessedVariable() instanceof FieldNode field) {
                    FieldNode builderField = candidate.builder.getField(field.getName());
                    if (builderField != null) {
                        result.setAccessedVariable(builderField);
                        result.setType(builderField.getType());
                    }
                }
                return result;
            }
            if (expression instanceof StaticMethodCallExpression source)
                return transformStaticMethodCall(source);
            if (!(expression instanceof MethodCallExpression source))
                return expression.transformExpression(this);

            MethodCallExpression result = (MethodCallExpression) source.transformExpression(this);

            Candidate dependency = findDependency(source);
            if (dependency != null) {
                candidate.dependencies.add(dependency);
                result.setMethod(new ConstantExpression(dependency.twin.getName()));
                result.setMethodTarget(dependency.twin);
                return result;
            }

            MethodNode target = source.getMethodTarget();
            MethodNode linkedTwin = target == null ? null : target.getNodeMetaData(TWIN_METADATA_KEY);
            if (linkedTwin != null) {
                candidate.directBuilderCall = true;
                result.setMethod(new ConstantExpression(linkedTwin.getName()));
                result.setMethodTarget(linkedTwin);
                return result;
            }

            MethodNode externalTwin = findQualifiedBuilderTwin(source);
            if (externalTwin != null) {
                candidate.directBuilderCall = true;
                result.setMethod(new ConstantExpression(externalTwin.getName()));
                result.setMethodTarget(externalTwin);
                return result;
            }

            RootFactoryCall rootCall = findRootFactoryCall(source);
            if (rootCall != null) {
                MethodNode builderMethod = findBuilderFactoryMethod(source, rootCall.model);
                if (builderMethod == null) {
                    candidate.opaque = true;
                    return result;
                }
                candidate.directBuilderCall = true;
                candidate.concreteModels.add(rootCall.model.redirect());
                Expression factory = rootCall.explicitFactory
                        ? transform(source.getObjectExpression())
                        : varX("this");
                result.setObjectExpression(asBuilderCall(factory, rootCall.model));
                result.setImplicitThis(false);
                result.setMethodTarget(builderMethod);
                CastExpression cast = new CastExpression(GeneratedDslSupport.builderTypeFor(rootCall.model), result);
                cast.setSourcePosition(source);
                return cast;
            }

            if (target != null && projectType(target.getReturnType(), state.model) != null)
                candidate.opaque = true;
            return result;
        }

        private static MethodCallExpression asBuilderCall(Expression factory, ClassNode model) {
            boolean keyedModel = getKeyField(model) != null;
            ClassNode runtimeFactory = !keyedModel
                    ? DSLASTTransformation.UNKEYED_FACTORY : DSLASTTransformation.KEYED_FACTORY;
            ClassNode builderFactory = !keyedModel
                    ? DSLASTTransformation.UNKEYED_BUILDER_FACTORY : DSLASTTransformation.KEYED_BUILDER_FACTORY;
            MethodCallExpression result = callX(factory, "AsBuilder");
            result.setMethodTarget(runtimeFactory.getDeclaredMethod("AsBuilder", Parameter.EMPTY_ARRAY));
            result.setType(builderFactory);
            return result;
        }

        private Expression transformStaticMethodCall(StaticMethodCallExpression source) {
            StaticMethodCallExpression result = (StaticMethodCallExpression) source.transformExpression(this);

            Candidate dependency = findDependency(source);
            if (dependency == null) {
                MethodNode externalTwin = findQualifiedBuilderTwin(source);
                if (externalTwin == null) return result;
                candidate.directBuilderCall = true;
                StaticMethodCallExpression projected = new StaticMethodCallExpression(
                        source.getOwnerType(), externalTwin.getName(), result.getArguments());
                projected.setSourcePosition(source);
                projected.copyNodeMetaData(source);
                return projected;
            }

            candidate.dependencies.add(dependency);
            StaticMethodCallExpression projected = new StaticMethodCallExpression(
                    source.getOwnerType(),
                    dependency.twin.getName(),
                    result.getArguments()
            );
            projected.setSourcePosition(source);
            projected.copyNodeMetaData(source);
            return projected;
        }

        private Candidate findDependency(MethodCallExpression call) {
            MethodNode target = call.getMethodTarget();
            if (target != null) {
                Candidate direct = state.candidateFor(target);
                if (direct != null) return direct;
                MethodNode twin = target.getNodeMetaData(TWIN_METADATA_KEY);
                if (twin != null) return state.candidateFor(twin.getNodeMetaData(ORIGINAL_METADATA_KEY));
            }

            MethodNode resolvedOriginal = resolveOriginalSourceTarget(call);
            if (resolvedOriginal == null) return null;
            call.setMethodTarget(resolvedOriginal);
            return state.candidateFor(resolvedOriginal);
        }

        private Candidate findDependency(StaticMethodCallExpression call) {
            if (!call.getOwnerType().redirect().equals(candidate.original.getDeclaringClass().redirect())) return null;
            MethodNode resolvedOriginal = resolveOriginalSourceTarget(call.getMethod(), call.getArguments());
            return resolvedOriginal == null ? null : state.candidateFor(resolvedOriginal);
        }

        private MethodNode findQualifiedBuilderTwin(MethodCallExpression call) {
            ClassNode sourceOwner = sourceClassFor(call.getObjectExpression(), candidate.original.getDeclaringClass());
            if (sourceOwner == null) return null;
            ensureProjectedMethods(sourceOwner, sourceOwner);
            return builderTwinFor(call.getMethodTarget(), sourceOwner, call.getMethodAsString(), call.getArguments());
        }

        private MethodNode findQualifiedBuilderTwin(StaticMethodCallExpression call) {
            ClassNode sourceOwner = sourceClassFor(call.getOwnerType(), candidate.original.getDeclaringClass());
            ensureProjectedMethods(sourceOwner, sourceOwner);
            return builderTwinFor(null, sourceOwner, call.getMethod(), call.getArguments());
        }

        /**
         * Dynamic Groovy source calls do not always have a MethodNode target yet. Resolve only the original
         * source overload here; the hidden twin is then obtained exclusively from that MethodNode's metadata.
         */
        private MethodNode resolveOriginalSourceTarget(MethodCallExpression call) {
            if (!call.isImplicitThis()
                    && (!(call.getObjectExpression() instanceof ClassExpression owner)
                    || !owner.getType().redirect().equals(candidate.original.getDeclaringClass().redirect())))
                return null;
            return resolveOriginalSourceTarget(call.getMethodAsString(), call.getArguments());
        }

        private MethodNode resolveOriginalSourceTarget(String methodName, Expression arguments) {
            int argumentCount = argumentCount(arguments);
            List<MethodNode> byArity = state.candidates.values().stream()
                    .map(value -> value.original)
                    .filter(method -> method.getName().equals(methodName))
                    .filter(method -> acceptsArgumentCount(method, argumentCount))
                    .toList();
            if (byArity.size() == 1) return byArity.get(0);

            List<MethodNode> compatible = byArity.stream()
                    .filter(method -> argumentsMatch(method.getParameters(), arguments))
                    .toList();
            return compatible.size() == 1 ? compatible.get(0) : null;
        }

        private RootFactoryCall findRootFactoryCall(MethodCallExpression call) {
            Expression receiver = call.getObjectExpression();
            if (receiver instanceof PropertyExpression create
                    && "Create".equals(create.getPropertyAsString())
                    && sourceClassFor(create.getObjectExpression(), candidate.original.getDeclaringClass()) != null) {
                ClassNode model = sourceClassFor(create.getObjectExpression(), candidate.original.getDeclaringClass());
                if (isDSLObject(model)) return new RootFactoryCall(model, true);
            }

            MethodNode target = call.getMethodTarget();
            if (call.isImplicitThis() && target != null
                    && (target.getDeclaringClass().equals(KLUM_FACTORY)
                    || target.getDeclaringClass().isDerivedFrom(KLUM_FACTORY)))
                return new RootFactoryCall(state.model, false);
            if (call.isImplicitThis() && findBuilderFactoryMethod(call, state.model) != null)
                return new RootFactoryCall(state.model, false);
            return null;
        }

        private MethodNode findBuilderFactoryMethod(MethodCallExpression source, ClassNode model) {
            MethodNode rootTarget = source.getMethodTarget();
            List<MethodNode> candidates = new ArrayList<>();
            for (ClassNode factoryType : BUILDER_FACTORY_TYPES)
                factoryType.getMethods(source.getMethodAsString()).forEach(method -> {
                    if (method.getDeclaringClass().redirect().equals(factoryType.redirect())
                            || method.getDeclaringClass().isDerivedFrom(BUILDER_FACTORY_TYPES.get(0)))
                        candidates.add(method);
                });

            if (getKeyField(model) == null)
                candidates.removeIf(method -> method.getDeclaringClass().redirect().equals(BUILDER_FACTORY_TYPES.get(1).redirect()));
            else
                candidates.removeIf(method -> method.getDeclaringClass().redirect().equals(BUILDER_FACTORY_TYPES.get(2).redirect()));

            if (rootTarget != null) {
                MethodNode exact = candidates.stream()
                        .filter(method -> parameterTypesMatch(method.getParameters(), rootTarget.getParameters()))
                        .findFirst()
                        .orElse(null);
                if (exact != null) return exact;
            }

            int arguments = argumentCount(source.getArguments());
            List<MethodNode> byArity = new ArrayList<>();
            candidates.stream().filter(method -> acceptsArgumentCount(method, arguments)).forEach(byArity::add);
            if (byArity.size() == 1) return byArity.get(0);
            return byArity.stream()
                    .filter(method -> argumentsMatch(method.getParameters(), source.getArguments()))
                    .findFirst()
                    .orElse(null);
        }

        private static boolean parameterTypesMatch(Parameter[] left, Parameter[] right) {
            if (left.length != right.length) return false;
            for (int index = 0; index < left.length; index++)
                if (!left[index].getOriginType().redirect().equals(right[index].getOriginType().redirect())) return false;
            return true;
        }

    }

    private static final class RootFactoryCall {
        private final ClassNode model;
        private final boolean explicitFactory;

        private RootFactoryCall(ClassNode model, boolean explicitFactory) {
            this.model = model;
            this.explicitFactory = explicitFactory;
        }
    }
}
