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

import com.blackbuild.groovy.configdsl.transform.DelegatesToRW;
import com.blackbuild.annodocimal.ast.extractor.ASTExtractor;
import com.blackbuild.annodocimal.ast.formatting.DocText;
import com.blackbuild.annodocimal.ast.formatting.JavaDocUtil;
import com.blackbuild.klum.ast.util.KlumBuilder;
import com.blackbuild.klum.ast.util.KlumFactory;
import com.blackbuild.klum.common.CommonAstHelper;
import groovy.lang.DelegatesTo;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ExpressionTransformer;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ForStatement;
import org.codehaus.groovy.ast.stmt.IfStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.createGeneratedAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.copyAnnotationsFromSourceToTarget;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getKeyField;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.klum.ast.util.reflect.AstReflectionBridge.cloneParamsWithAdjustedNames;
import static com.blackbuild.klum.common.CommonAstHelper.getElementTypeForCollection;
import static com.blackbuild.klum.common.CommonAstHelper.getElementTypeForMap;
import static com.blackbuild.klum.common.CommonAstHelper.isAssignableTo;
import static com.blackbuild.klum.common.CommonAstHelper.isCollection;
import static com.blackbuild.klum.common.CommonAstHelper.isMap;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;

/** Generates and links source-visible Builder-producing method twins for ADR 0004. */
final class BuilderMethodProjection {

    static final String TWIN_METADATA_KEY = BuilderMethodProjection.class.getName() + ".twin";
    static final String ORIGINAL_METADATA_KEY = BuilderMethodProjection.class.getName() + ".original";
    private static final String CONCRETE_MODEL_METADATA_KEY = BuilderMethodProjection.class.getName() + ".concreteModel";
    private static final String OMISSION_REASON_METADATA_KEY = BuilderMethodProjection.class.getName() + ".omissionReason";
    private static final String PROJECTION_STATE_METADATA_KEY = BuilderMethodProjection.class.getName() + ".state";
    private static final String TWIN_PREFIX = "$klum$asBuilder$";

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

            List<org.codehaus.groovy.ast.AnnotationNode> aliases = original.getAnnotations(DELEGATES_TO_RW);
            if (aliases.isEmpty()) continue;
            ClassNode target = CommonAstHelper.getNullSafeClassMember(aliases.get(0), "value", defaultModel);
            projected.getAnnotations().removeIf(annotation -> annotation.getClassNode().equals(DELEGATES_TO));
            org.codehaus.groovy.ast.AnnotationNode delegatesTo = new org.codehaus.groovy.ast.AnnotationNode(DELEGATES_TO);
            delegatesTo.setMember("value", classX(GeneratedDslSupport.builderTypeFor(target)));
            delegatesTo.setMember("strategy", constX(groovy.lang.Closure.DELEGATE_ONLY));
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
        DocText sourceDoc = ASTExtractor.extractDocText(source);
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

            sourceDoc.getNamedTags("param").forEach(doc::param);
            sourceDoc.getNamedTags("throws").forEach(doc::throwsException);
            sourceDoc.getNamedTags("exception").forEach(doc::throwsException);
            if (mapResult)
                doc.returnType("the producer's original map with its original keys and attached, unsealed Builder values");
            else if (collectionResult)
                doc.returnType("the producer's original container of attached, unsealed Builders");
            else
                doc.returnType("the attached, unsealed Builder");
            doc.seeAlso(JavaDocUtil.toLinkString(source));
        });
    }

    static void ensureProjectedMethods(ClassNode sourceClass, ClassNode expectedModel) {
        if (sourceClass == null || sourceClass.isResolved()) return;

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
                .filter(method -> projectType(method.getReturnType(), expectedModel) != null)
                .forEach(method -> state.addCandidate(method, createTwinShell(method, expectedModel)));

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

    private static MethodNode createTwinShell(MethodNode source, ClassNode expectedModel) {
        normalizeDelegatingParameters(source, expectedModel);
        int modifiers = (source.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_STRICT)) | Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;
        MethodNode twin = new MethodNode(
                TWIN_PREFIX + source.getName(),
                modifiers,
                projectType(source.getReturnType(), expectedModel),
                source.getParameters(),
                source.getExceptions(),
                EmptyStatement.INSTANCE
        );
        twin.setGenericsTypes(source.getGenericsTypes());
        twin.setSourcePosition(source);
        twin.setSynthetic(true);
        twin.addAnnotation(createGeneratedAnnotation(BuilderMethodProjection.class));
        source.setNodeMetaData(TWIN_METADATA_KEY, twin);
        twin.setNodeMetaData(ORIGINAL_METADATA_KEY, source);
        return twin;
    }

    private static void normalizeDelegatingParameters(MethodNode source, ClassNode defaultModel) {
        for (Parameter parameter : source.getParameters()) {
            if (!parameter.getAnnotations(DELEGATES_TO).isEmpty()) continue;
            List<org.codehaus.groovy.ast.AnnotationNode> aliases = parameter.getAnnotations(DELEGATES_TO_RW);
            if (aliases.isEmpty()) continue;
            ClassNode target = CommonAstHelper.getNullSafeClassMember(aliases.get(0), "value", defaultModel);
            DelegatesToRWTransformation.addDelegatesToAnnotation(target, parameter);
        }
    }

    private static Set<Candidate> resolveAdaptableCandidates(Iterable<Candidate> candidates) {
        Set<Candidate> result = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean changed;
        do {
            changed = false;
            for (Candidate candidate : candidates) {
                if (candidate.opaque || result.contains(candidate)) continue;
                if (!candidate.directBuilderCall && candidate.dependencies.stream().noneMatch(result::contains)) continue;
                if (!result.containsAll(candidate.dependencies)) continue;
                changed |= result.add(candidate);
            }
        } while (changed);
        return result;
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
            return GeneratedDslSupport.builderTypeFor(sourceType);

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
        if (isAssignableTo(type, KLUM_BUILDER)) {
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
        GenericsType[] generics = type.getGenericsTypes();
        if (generics == null) return null;
        for (GenericsType generic : generics) {
            if (generic.isWildcard()) {
                ClassNode[] upperBounds = generic.getUpperBounds();
                if ((upperBounds != null && Arrays.stream(upperBounds).anyMatch(bound -> unresolvedBuilderProblem(bound) != null
                        || isAssignableTo(bound, KLUM_BUILDER)))
                        || (generic.getLowerBound() != null && (unresolvedBuilderProblem(generic.getLowerBound()) != null
                        || isAssignableTo(generic.getLowerBound(), KLUM_BUILDER))))
                    return "a container Builder element type is a wildcard";
                continue;
            }
            String nested = unresolvedBuilderProblem(generic.getType());
            if (nested != null) return nested;
        }
        return null;
    }

    private static final class ProjectionState {
        private final ClassNode model;
        private final Map<MethodNode, Candidate> candidates = new LinkedHashMap<>();

        private ProjectionState(ClassNode model) {
            this.model = model;
        }

        private void addCandidate(MethodNode original, MethodNode twin) {
            candidates.put(original, new Candidate(original, twin));
        }

        private Candidate candidateFor(MethodNode method) {
            return candidates.get(method);
        }
    }

    private static final class Candidate {
        private final MethodNode original;
        private final MethodNode twin;
        private final Set<Candidate> dependencies = new LinkedHashSet<>();
        private final Set<ClassNode> concreteModels = new LinkedHashSet<>();
        private boolean directBuilderCall;
        private boolean opaque;

        private Candidate(MethodNode original, MethodNode twin) {
            this.original = original;
            this.twin = twin;
        }
    }

    private static final class ProjectionTransformer implements ExpressionTransformer {
        private final ProjectionState state;
        private final Candidate candidate;

        private ProjectionTransformer(ProjectionState state, Candidate candidate) {
            this.state = state;
            this.candidate = candidate;
        }

        private Statement cloneStatement(Statement source) {
            if (source == null) return null;
            Statement result;
            if (source instanceof BlockStatement) {
                BlockStatement block = (BlockStatement) source;
                List<Statement> statements = new ArrayList<>();
                block.getStatements().forEach(statement -> statements.add(cloneStatement(statement)));
                result = new BlockStatement(statements, block.getVariableScope());
            } else if (source instanceof ReturnStatement) {
                result = new ReturnStatement(transform(((ReturnStatement) source).getExpression()));
            } else if (source instanceof ExpressionStatement) {
                result = new ExpressionStatement(transform(((ExpressionStatement) source).getExpression()));
            } else if (source instanceof IfStatement) {
                IfStatement ifStatement = (IfStatement) source;
                result = new IfStatement(
                        (BooleanExpression) transform(ifStatement.getBooleanExpression()),
                        cloneStatement(ifStatement.getIfBlock()),
                        cloneStatement(ifStatement.getElseBlock())
                );
            } else if (source instanceof ForStatement) {
                ForStatement forStatement = (ForStatement) source;
                ForStatement copy = new ForStatement(
                        forStatement.getVariable(),
                        transform(forStatement.getCollectionExpression()),
                        cloneStatement(forStatement.getLoopBlock())
                );
                copy.setVariableScope(forStatement.getVariableScope());
                result = copy;
            } else if (source instanceof ThrowStatement) {
                result = new ThrowStatement(transform(((ThrowStatement) source).getExpression()));
            } else if (source instanceof EmptyStatement) {
                return source;
            } else {
                candidate.opaque = true;
                return source;
            }
            result.setSourcePosition(source);
            result.copyNodeMetaData(source);
            result.copyStatementLabels(source);
            return result;
        }

        @Override
        public Expression transform(Expression expression) {
            if (expression == null) return null;
            if (expression instanceof ClosureExpression) {
                ClosureExpression source = (ClosureExpression) expression;
                ClosureExpression result = new ClosureExpression(source.getParameters(), cloneStatement(source.getCode()));
                result.setVariableScope(source.getVariableScope());
                result.setSourcePosition(source);
                result.copyNodeMetaData(source);
                return result;
            }
            if (!(expression instanceof MethodCallExpression))
                return expression.transformExpression(this);

            MethodCallExpression source = (MethodCallExpression) expression;
            MethodCallExpression result = (MethodCallExpression) source.transformExpression(this);

            Candidate dependency = findDependency(source);
            if (dependency != null) {
                candidate.dependencies.add(dependency);
                result.setMethod(new org.codehaus.groovy.ast.expr.ConstantExpression(dependency.twin.getName()));
                result.setMethodTarget(dependency.twin);
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
                result.setObjectExpression(propX(factory, "AsBuilder"));
                result.setImplicitThis(false);
                result.setMethodTarget(builderMethod);
                CastExpression cast = new CastExpression(GeneratedDslSupport.builderTypeFor(rootCall.model), result);
                cast.setSourcePosition(source);
                return cast;
            }

            MethodNode target = source.getMethodTarget();
            if (target != null && projectType(target.getReturnType(), state.model) != null)
                candidate.opaque = true;
            return result;
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

        /**
         * Dynamic Groovy source calls do not always have a MethodNode target yet. Resolve only the original
         * source overload here; the hidden twin is then obtained exclusively from that MethodNode's metadata.
         */
        private MethodNode resolveOriginalSourceTarget(MethodCallExpression call) {
            if (!call.isImplicitThis()) return null;
            int argumentCount = argumentCount(call.getArguments());
            List<MethodNode> byArity = state.candidates.values().stream()
                    .map(value -> value.original)
                    .filter(method -> method.getName().equals(call.getMethodAsString()))
                    .filter(method -> acceptsArgumentCount(method, argumentCount))
                    .collect(java.util.stream.Collectors.toList());
            if (byArity.size() == 1) return byArity.get(0);

            List<MethodNode> compatible = byArity.stream()
                    .filter(method -> argumentsMatch(method.getParameters(), call.getArguments()))
                    .collect(java.util.stream.Collectors.toList());
            return compatible.size() == 1 ? compatible.get(0) : null;
        }

        private RootFactoryCall findRootFactoryCall(MethodCallExpression call) {
            Expression receiver = call.getObjectExpression();
            if (receiver instanceof PropertyExpression) {
                PropertyExpression create = (PropertyExpression) receiver;
                if ("Create".equals(create.getPropertyAsString()) && create.getObjectExpression() instanceof ClassExpression) {
                    ClassNode model = ((ClassExpression) create.getObjectExpression()).getType();
                    if (isDSLObject(model)) return new RootFactoryCall(model, true);
                }
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
    }

    private static boolean parameterTypesMatch(Parameter[] left, Parameter[] right) {
        if (left.length != right.length) return false;
        for (int index = 0; index < left.length; index++)
            if (!left[index].getOriginType().redirect().equals(right[index].getOriginType().redirect())) return false;
        return true;
    }

    private static boolean acceptsArgumentCount(MethodNode method, int argumentCount) {
        int required = (int) Arrays.stream(method.getParameters()).filter(parameter -> !parameter.hasInitialExpression()).count();
        return argumentCount >= required && argumentCount <= method.getParameters().length;
    }

    private static int argumentCount(Expression arguments) {
        return arguments instanceof TupleExpression ? ((TupleExpression) arguments).getExpressions().size() : 1;
    }

    private static boolean argumentsMatch(Parameter[] parameters, Expression arguments) {
        if (!(arguments instanceof TupleExpression)) return parameters.length == 1;
        List<Expression> expressions = ((TupleExpression) arguments).getExpressions();
        if (parameters.length != expressions.size()) return false;
        for (int index = 0; index < parameters.length; index++) {
            Expression expression = expressions.get(index);
            if (expression instanceof MapExpression && !isAssignableTo(ClassHelper.MAP_TYPE, parameters[index].getType()))
                return false;
            if (expression instanceof ClosureExpression && !parameters[index].getType().equals(ClassHelper.CLOSURE_TYPE))
                return false;
        }
        return true;
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
