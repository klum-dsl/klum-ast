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

import com.blackbuild.klum.ast.Builder;
import com.blackbuild.klum.ast.Field;
import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.Owner;
import com.blackbuild.klum.ast.Struct;
import com.blackbuild.klum.ast.Validate;
import com.blackbuild.klum.ast.Struct.Ignore;
import com.blackbuild.klum.ast.Struct.Naming;
import com.blackbuild.klum.ast.Struct.Naming.Profile;
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput;
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessHelper;
import com.blackbuild.klum.ast.compiler.internal.layer3.ClusterFactoryBuilder;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import groovy.transform.Undefined;
import groovy.lang.Closure;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getKeyField;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getFieldType;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getModelClassFor;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getElementTypeForCollection;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getElementTypeForMap;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getKeyTypeForMap;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isCollection;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isMap;

/**
 * Compiler-only classification and minimal public-contract enrichment for structured input.
 *
 * <p>The normalized plan stays on the source AST; runtime consumers reflect the enriched public contracts.</p>
 */
final class StructuredInputAdmissionPlan {

    static final String METADATA_KEY = StructuredInputAdmissionPlan.class.getName() + ".plan";
    private static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String VALUE = "value";
    private static final String FIELD_MEMBER = "field";
    private static final String KEY_MAPPING_MEMBER = "keyMapping";
    private static final String INPUT_IGNORED = "excluded by @Struct.Ignore";
    private static final String IGNORED_INPUT = "excluded by @JsonIgnore(true)";
    private static final String OWNERSHIP_REASON = "requires one concrete owned relationship";
    private static final String KEYED_OWNERSHIP_REASON = "requires Map<String, concrete keyed DSL Object> ownership";
    private static final String POPULATION = "POPULATION";
    private static final String CONVERTER_REASON = "unsupported converter selection; expose one supported public String or CharSequence route and recompile matched domain/Schema artifacts";
    private static final String ABSENT_FIELD_REASON = "field is absent from the final public Builder contract";
    private static final String NUMERIC_REASON_PREFIX = "unsupported numeric type ";
    private static final String CONTAINER_REASON_PREFIX = "unsupported whole-container payload target ";
    private static final String POPULATION_REASON_PREFIX = "has no unambiguous compiler-generated element population method ";
    private static final Set<String> NUMERIC_TYPES = Set.of(
            "byte", "short", "int", "long", "float", "double",
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.math.BigInteger", "java.math.BigDecimal");
    private static final ClassNode BUILDER_METHOD = ClassHelper.make(Builder.Method.class);

    private final String model;
    private final String builder;
    private final List<Entry> entries;
    private final List<Rejection> rejections;

    private StructuredInputAdmissionPlan(String model, String builder, List<Entry> entries, List<Rejection> rejections) {
        this.model = model;
        this.builder = builder;
        this.entries = List.copyOf(entries);
        this.rejections = List.copyOf(rejections);
    }

    static void classify(DSLASTTransformation transformation) {
        ClassNode model = transformation.annotatedClass.redirect();
        List<Entry> entries = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();

        StructuredInputAdmissionPlan inherited = planOf(model.getSuperClass());
        if (inherited != null) {
            inherited.entries.stream().filter(entry -> !overriddenInputCandidate(transformation, entry)).forEach(entries::add);
            rejections.addAll(inherited.rejections);
        }

        transformation.builderFields.keySet().forEach(field -> classifyField(model, field, entries, rejections));
        transformation.builderClass.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(transformation.builderClass.redirect()))
                .filter(MethodNode::isPublic)
                .filter(method -> !method.isStatic())
                .filter(method -> WriteAccessHelper.isBuilderMethod(method))
                .filter(method -> !method.getAnnotations(BUILDER_METHOD).isEmpty())
                .forEach(method -> classifyOperation(method, entries, rejections));

        enrichPublicContract(transformation, entries, rejections);
        rejectCollisions(entries, rejections);
        entries.sort(Comparator.comparing(Entry::externalName).thenComparing(Entry::source));
        rejections.sort(Comparator.comparing(Rejection::source).thenComparing(Rejection::reason));
        model.setNodeMetaData(METADATA_KEY, new StructuredInputAdmissionPlan(
                model.getName(), GeneratedDslSupport.of(model).getBuilderInterface().redirect().getName(), entries, rejections));
    }

    private static boolean overriddenInputCandidate(DSLASTTransformation transformation, Entry entry) {
        if (entry.role() != Role.COMMAND && (entry.role() != Role.OPERATION && entry.role() != Role.PROPERTY
                || entry.payload().kind() != PayloadKind.SCALAR
                || !Set.of(String.class.getName(), CharSequence.class.getName()).contains(entry.payload().type()))) return false;
        return transformation.builderClass.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(transformation.builderClass.redirect()))
                .map(GeneratedDslSupport::publicProjectionOf)
                .filter(method -> method != null && method.isPublic() && !method.isStatic())
                .anyMatch(method -> entry.operation().descriptorGroup().stream()
                        .filter(inherited -> entry.role() != Role.PROPERTY
                                || signature(inherited).equals(entry.operation().name() + "(" + entry.payload().type() + ")"))
                        .anyMatch(inherited -> signature(inherited).equals(signature(descriptor(method)))));
    }

    private static String signature(String descriptor) {
        return descriptor.substring(0, descriptor.indexOf(':'));
    }

    /** Only irreducible source/group facts; signatures, payloads, names of methods and docs stay on declarations. */
    private static void enrichPublicContract(DSLASTTransformation transformation, List<Entry> entries, List<Rejection> rejections) {
        ClassNode model = transformation.annotatedClass.redirect();
        ClassNode builder = GeneratedDslSupport.of(model).getBuilderInterface().redirect();
        AnnotationNode schema = new AnnotationNode(ClassHelper.make(GeneratedInput.Schema.class));
        FieldNode key = getKeyField(model);
        if (key != null) {
            schema.setMember("key", new ConstantExpression(key.getName()));
            schema.setMember("keyInput", ConstantExpression.TRUE);
            if (key.getOwner().redirect().equals(model)) enrichKey(builder, key, schema);
        }
        ListExpression invisible = new ListExpression();
        transformation.builderFields.keySet().stream().filter(field -> !excludedField(field))
                .forEach(field -> enrichField(builder, field, entries, rejections, invisible));
        if (!invisible.getExpressions().isEmpty()) schema.setMember("exclusions", invisible);
        schema.setMember("version", new ConstantExpression(1));
        enrichNaming(model, schema);
        StructuredInputConstraintMetadata.enrichSchema(model, schema);
        builder.addAnnotation(schema);
        restoreAuthoredFacts(transformation);
        enrichVirtualScopes(transformation, entries, rejections);
        long operationCount = builder.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(builder))
                .filter(method -> annotation(method, GeneratedInput.Operation.class.getName()) != null).count();
        schema.setMember("operationCount", new ConstantExpression((int) operationCount));
    }

    private static final String LITERAL_ALTERNATIVE = METADATA_KEY + ".literalAlternative";

    /** Freeze only explicit literal facts at the existing CollectionFactory generation site. */
    static void recordLiteralAlternative(FieldNode field, Map<ClassNode, String> alternatives) {
        if (alternatives.isEmpty() || !field.getType().redirect().getName().equals(Map.class.getName())) return;
        ClassNode declared = getElementTypeForMap(field.getType()).redirect();
        AnnotationNode options = annotation(field, Field.class.getName());
        if (!Modifier.isAbstract(declared.getModifiers()) || getFieldType(field) != FieldType.DEFAULT
                || !ClassHelper.STRING_TYPE.equals(getKeyTypeForMap(field.getType()))
                || declared.getGenericsTypes() != null && declared.getGenericsTypes().length != 0
                || options != null && options.getMember(KEY_MAPPING_MEMBER) != null
                || alternatives.values().stream().distinct().count() != alternatives.size()) return;
        List<LiteralAlternative> candidates = new ArrayList<>();
        for (Map.Entry<ClassNode, String> selected : alternatives.entrySet()) {
            ClassNode target = selected.getKey().redirect();
            FieldNode key = getKeyField(target);
            if (!isDSLObject(target) || Modifier.isAbstract(target.getModifiers()) || key == null
                    || !ClassHelper.STRING_TYPE.equals(key.getType()) || !key.equals(getKeyField(declared))
                    || target.getGenericsTypes() != null && target.getGenericsTypes().length != 0) return;
            candidates.add(new LiteralAlternative(selected.getValue(), target, key.getName()));
        }
        candidates.sort(Comparator.comparing(LiteralAlternative::alias));
        field.setNodeMetaData(LITERAL_ALTERNATIVE, List.copyOf(candidates));
    }

    private record LiteralAlternative(String alias, ClassNode target, String key) { }

    private static List<LiteralAlternative> literalAlternatives(FieldNode field) {
        List<LiteralAlternative> candidates = field.getNodeMetaData(LITERAL_ALTERNATIVE);
        return candidates == null ? List.of() : candidates;
    }

    private static void enrichLiteralAlternative(FieldNode field, AnnotationNode fact, MethodNode accessor) {
        List<LiteralAlternative> alternatives = literalAlternatives(field);
        fact.setMember("alternativeCount", new ConstantExpression(alternatives.size()));
        if (alternatives.size() == 1) {
            accessor.addAnnotation(literalAssociation(field, alternatives.get(0)));
        } else if (!alternatives.isEmpty()) {
            AnnotationNode associations = new AnnotationNode(ClassHelper.make(GeneratedInput.LiteralAlternatives.class));
            associations.setMember(VALUE, new ListExpression(alternatives.stream()
                    .map(alternative -> (Expression) new AnnotationConstantExpression(literalAssociation(field, alternative))).toList()));
            accessor.addAnnotation(associations);
        }
    }

    private static AnnotationNode literalAssociation(FieldNode field, LiteralAlternative alternative) {
        AnnotationNode association = new AnnotationNode(ClassHelper.make(GeneratedInput.LiteralAlternative.class));
        association.setMember(FIELD_MEMBER, new ConstantExpression(field.getName()));
        association.setMember("alias", new ConstantExpression(alternative.alias()));
        association.setMember("target", new ConstantExpression(alternative.target().getName()));
        association.setMember("key", new ConstantExpression(alternative.key()));
        association.setMember("keyType", new ConstantExpression(String.class.getName()));
        return association;
    }

    private static final String VIRTUAL_SCOPES = METADATA_KEY + ".virtualScopes";
    private record VirtualScope(MethodNode source, FieldNode field) { }

    static void recordVirtualScope(DSLASTTransformation transformation, MethodNode source, FieldNode field) {
        List<VirtualScope> scopes = transformation.annotatedClass.getNodeMetaData(VIRTUAL_SCOPES);
        if (scopes == null) {
            scopes = new ArrayList<>();
            transformation.annotatedClass.setNodeMetaData(VIRTUAL_SCOPES, scopes);
        }
        scopes.add(new VirtualScope(source, field));
    }

    /** Bind only a bare, unkeyed concrete virtual sink to its existing generated creator. */
    @SuppressWarnings("java:S135") // Each failed proof excludes the scope; separate guards keep admission flat and fail-closed.
    private static void enrichVirtualScopes(DSLASTTransformation transformation, List<Entry> entries, List<Rejection> rejections) {
        List<VirtualScope> scopes = transformation.annotatedClass.getNodeMetaData(VIRTUAL_SCOPES);
        if (scopes == null) return;
        ClassNode builder = GeneratedDslSupport.of(transformation.annotatedClass).getBuilderInterface().redirect();
        for (VirtualScope scope : scopes) {
            if (!eligibleVirtualScope(transformation, scopes, scope)) continue;
            MethodNode creator = virtualCreator(transformation, scope.source().getName());
            if (creator == null) continue;
            MethodNode sink = virtualSink(builder, scope.source().getName(), creator.getReturnType());
            if (sink == null) continue;
            enrichVirtualScope(transformation, scope, sink, creator);
            Name name = externalName(scope.source());
            if (name.ignored()) {
                rejections.add(new Rejection(source(scope.source()), ignoreReason(name)));
            } else {
                entries.add(new Entry(name.value(), Role.VIRTUAL_OBJECT, source(scope.source()),
                        new PublicOperation(builder.getName(), sink.getName(), List.of(descriptor(sink), descriptor(creator))),
                        payloadForModel(scope.field().getType()), name.origin(), null, null, null, false));
            }
        }
    }

    private static boolean eligibleVirtualScope(DSLASTTransformation transformation, List<VirtualScope> scopes, VirtualScope scope) {
        ClassNode target = scope.field().getType().redirect();
        MethodNode source = scope.source();
        AnnotationNode options = annotation(scope.field(), Field.class.getName());
        return isDSLObject(target) && !Modifier.isAbstract(target.getModifiers()) && getKeyField(target) == null
                && (target.getGenericsTypes() == null || target.getGenericsTypes().length == 0)
                && source.isPublic() && !source.isStatic() && source.isVoidMethod()
                && options != null && options.getMembers().isEmpty()
                && annotation(source, Builder.Method.class.getName()) == null
                && annotation(source, Struct.Operation.class.getName()) == null
                && transformation.annotatedClass.getField(source.getName()) == null
                && scopes.stream().filter(other -> other.source().getName().equals(source.getName())).count() == 1;
    }

    private static MethodNode virtualCreator(DSLASTTransformation transformation, String name) {
        return transformation.builderClass.getDeclaredMethods(name).stream()
                .filter(method -> Boolean.TRUE.equals(method.getNodeMetaData(GeneratedDslSupport.RELATIONSHIP_CREATOR_METADATA_KEY)))
                .filter(method -> Arrays.equals(Arrays.stream(method.getParameters()).map(parameter -> parameter.getType().getName()).toArray(String[]::new),
                        new String[]{Map.class.getName(), Closure.class.getName()}))
                .map(GeneratedDslSupport::publicProjectionOf)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    private static MethodNode virtualSink(ClassNode builder, String name, ClassNode targetBuilder) {
        return builder.getDeclaredMethods(name).stream()
                .filter(method -> method.getParameters().length == 1 && method.isVoidMethod())
                .filter(method -> method.getParameters()[0].getType().getName().equals(targetBuilder.getName()))
                .filter(method -> annotation(method, Field.class.getName()) != null)
                .findFirst().orElse(null);
    }

    private static void enrichVirtualScope(DSLASTTransformation transformation, VirtualScope scope, MethodNode sink, MethodNode creator) {
        AnnotationNode association = new AnnotationNode(ClassHelper.make(GeneratedInput.VirtualScope.class));
        association.setMember("creator", new ConstantExpression(descriptor(creator)));
        sink.addAnnotation(association);
        AnnotationNode operation = new AnnotationNode(ClassHelper.make(GeneratedInput.Operation.class));
        operation.setMember("role", new ConstantExpression(Role.VIRTUAL_OBJECT.name()));
        operation.setMember("authoritative", ConstantExpression.FALSE);
        enrichName(externalName(scope.source()), operation);
        enrichDeprecation(scope.source(), operation);
        StructuredInputExamples.enrich(scope.source(), transformation.annotatedClass, operation, source(scope.source()));
        sink.addAnnotation(operation);
    }

    private static void enrichNaming(ClassNode model, AnnotationNode schema) {
        AnnotationNode naming = annotation(model, Naming.class.getName());
        String profile = Profile.IDENTITY.name();
        if (naming != null) {
            Expression value = naming.getMember(VALUE);
            if (!(value instanceof PropertyExpression property)
                    || !Set.of(Profile.IDENTITY.name(), Profile.KEBAB_CASE.name()).contains(property.getPropertyAsString())) {
                throw new IllegalArgumentException("@Struct.Naming requires an explicit supported Profile on " + model.getName());
            }
            profile = property.getPropertyAsString();
        }
        schema.setMember("namingExplicit", new ConstantExpression(naming != null));
        schema.setMember("namingProfile", new ConstantExpression(profile));
    }

    private static void enrichDeprecation(AnnotatedNode source, AnnotationNode fact) {
        fact.setMember("sourceDeprecated", new ConstantExpression(annotation(source, Deprecated.class.getName()) != null));
    }

    private static void enrichKey(ClassNode builder, FieldNode key, AnnotationNode schema) {
        MethodNode getter = builder.getDeclaredMethod(DslAstHelper.getGetterName(key.getName()), Parameter.EMPTY_ARRAY);
        AnnotationNode fact = new AnnotationNode(ClassHelper.make(GeneratedInput.Key.class));
        fact.setMember(VALUE, new ConstantExpression(key.getName()));
        enrichDeprecation(key, fact);
        StructuredInputExamples.enrich(key, key.getOwner(), fact, source(key));
        Name name = externalName(key);
        enrichName(name, fact);
        // Runtime-name overlaps such as `key` have no public getter; no writable key operation exists either.
        if (getter == null) schema.setMember("keyMember", new AnnotationConstantExpression(fact));
        else getter.addAnnotation(fact);
    }

    private static void enrichField(ClassNode builder, FieldNode field, List<Entry> entries,
                                    List<Rejection> rejections, ListExpression invisible) {
        MethodNode accessor = fieldAccessor(builder, field);
        Rejection rejection = rejections.stream().filter(value -> value.source().equals(source(field))).findFirst().orElse(null);
        if (accessor == null) {
            enrichInvisibleField(field, rejection, invisible);
            return;
        }
        AnnotationNode fact = new AnnotationNode(ClassHelper.make(GeneratedInput.Field.class));
        fact.setMember(VALUE, new ConstantExpression(field.getName()));
        enrichDeprecation(field, fact);
        StructuredInputExamples.enrich(field, field.getOwner(), fact, source(field));
        Name name = externalName(field);
        enrichName(name, fact);
        Entry entry = entries.stream().filter(value -> value.source().equals(source(field))).findFirst().orElse(null);
        enrichChildKey(field, entry, fact);
        if (rejection != null && !name.ignored()) {
            enrichFieldExclusion(field, rejection, fact);
        }
        StructuredInputConstraintMetadata.enrichField(field, fact);
        enrichConverter(field, entry, name, fact);
        enrichLiteralAlternative(field, fact, accessor);
        accessor.addAnnotation(fact);
        if (entry != null && entry.role() == Role.OWNED_SEQUENCE) {
            fact.setMember("sequenceElement", new ConstantExpression(entry.operation().name()));
        } else if (entry != null && (entry.role() == Role.OWNED_OBJECT || entry.role() == Role.KEYED_OWNED_MAP
                || entry.role() == Role.LITERAL_ALTERNATIVE_MAP)) {
            enrichSelection(accessor, field, entry.role());
        } else if (entry != null) {
            enrichSelection(builder, field, entry);
        }
    }

    private static void enrichChildKey(FieldNode field, Entry entry, AnnotationNode fact) {
        if (entry == null) return;
        if (entry.fixedChildKey() != null) {
            fact.setMember("fixedKey", ConstantExpression.TRUE);
        }
        if (entry.role() != Role.OWNED_OBJECT) return;

        AnnotationNode options = annotation(field, Field.class.getName());
        Expression provider = options == null ? null : options.getMember("key");
        boolean schemaKey = entry.fixedChildKey() != null || provider != null
                && (!(provider instanceof ClassExpression type) || !type.getType().getName().equals(Undefined.class.getName()));
        fact.setMember("schemaKey", new ConstantExpression(schemaKey));
    }

    private static void enrichConverter(FieldNode field, Entry entry, Name name, AnnotationNode fact) {
        MethodNode converter = StructuredInputConverter.selected(field);
        fact.setMember("converterCount", new ConstantExpression(converter == null ? 0 : 1));
        if (converter == null) return;

        StructuredInputConverter.enrich(field, converter);
        if (entry == null && name.ignored()) {
            // Exclusion cannot hide malformed paired-domain facts from consumers.
            AnnotationNode selected = new AnnotationNode(ClassHelper.make(GeneratedInput.Selection.class));
            selected.setMember(FIELD_MEMBER, new ConstantExpression(field.getName()));
            selected.setMember("role", new ConstantExpression(Role.PROPERTY.name()));
            converter.addAnnotation(selected);
        }
    }

    /**
     * A generated property getter can be suppressed from the public Builder interface when it shadows a
     * package-private runtime method. Its ordinary public setter remains the exact field contract in that case.
     */
    private static MethodNode fieldAccessor(ClassNode builder, FieldNode field) {
        MethodNode getter = builder.getDeclaredMethod(DslAstHelper.getGetterName(field.getName()), Parameter.EMPTY_ARRAY);
        if (getter != null) return getter;
        return builder.getDeclaredMethod(DslAstHelper.getSetterName(field.getName()),
                new Parameter[] { new Parameter(GeneratedDslSupport.publicType(field.getType()), VALUE) });
    }

    private static void enrichInvisibleField(FieldNode field, Rejection rejection, ListExpression invisible) {
        if (rejection == null) return;
        AnnotationNode exclusion = new AnnotationNode(ClassHelper.make(GeneratedInput.Exclusion.class));
        exclusion.setMember("member", new ConstantExpression(field.getName()));
        String[] decision = invisibleExclusion(rejection.reason());
        exclusion.setMember("category", new ConstantExpression(decision[0]));
        if (!decision[1].isEmpty()) exclusion.setMember("detail", new ConstantExpression(decision[1]));
        invisible.addExpression(new AnnotationConstantExpression(exclusion));
    }

    private static void enrichFieldExclusion(FieldNode field, Rejection rejection, AnnotationNode fact) {
        // Type-based exclusions remain derivable. Ownership/mapping/population decisions are not reflected.
        String category = reflectedExclusionCategory(rejection.reason());
        if (category == null) return;
        fact.setMember("exclusion", new ConstantExpression(category));
        if (category.equals(POPULATION))
            fact.setMember("detail", new ConstantExpression(DslAstHelper.getElementNameForCollectionField(field)));
    }

    private static String reflectedExclusionCategory(String reason) {
        if (reason.equals(OWNERSHIP_REASON)) return "OWNERSHIP";
        if (reason.equals(KEYED_OWNERSHIP_REASON)) return "KEYED_OWNERSHIP";
        if (reason.contains("@Field.keyMapping")) return "KEY_MAPPING";
        if (reason.startsWith("has no unambiguous compiler-generated")) return POPULATION;
        if (reason.equals(ABSENT_FIELD_REASON)) return "ABSENT_FIELD";
        if (reason.equals(CONVERTER_REASON)) return "CONVERTER";
        return null;
    }

    private static void enrichSelection(ClassNode builder, FieldNode field, Entry entry) {
        for (MethodNode method : builder.getDeclaredMethods(entry.operation().name())) {
            if (!entry.operation().descriptorGroup().contains(descriptor(method))) continue;
            enrichSelection(method, field, entry.role());
        }
    }

    private static void enrichSelection(MethodNode declaration, FieldNode field, Role role) {
        AnnotationNode selected = new AnnotationNode(ClassHelper.make(GeneratedInput.Selection.class));
        selected.setMember(FIELD_MEMBER, new ConstantExpression(field.getName()));
        selected.setMember("role", new ConstantExpression(role.name()));
        declaration.addAnnotation(selected);
    }

    private static void restoreAuthoredFacts(DSLASTTransformation transformation) {
        // A generated projection can occupy an authored signature before annotation copying occurs.
        // Restore the original admission/naming annotations together; a marker alone can change rejection precedence.
        Set<String> authoredFacts = Set.of(Builder.Method.class.getName(), JSON_IGNORE, JSON_PROPERTY, Struct.Name.class.getName(), Ignore.class.getName(), Struct.Operation.class.getName());
        transformation.builderClass.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(transformation.builderClass.redirect()))
                .filter(method -> !method.getAnnotations(BUILDER_METHOD).isEmpty())
                .forEach(method -> {
                    MethodNode api = GeneratedDslSupport.publicProjectionOf(method);
                    if (api == null) return;
                    // Read the authored implementation, never a generated/compatibility projection's annotations.
                    AnnotationNode operation = new AnnotationNode(ClassHelper.make(GeneratedInput.Operation.class));
                    operation.setMember("role", new ConstantExpression(operationRole(method)));
                    operation.setMember("authoritative", new ConstantExpression(annotation(method, Struct.Operation.class.getName()) != null));
                    enrichDeprecation(method, operation);
                    StructuredInputExamples.enrich(method, transformation.annotatedClass, operation, source(method));
                    enrichName(externalName(method), operation);
                    api.addAnnotation(operation);
                    method.getAnnotations().stream().filter(annotation -> authoredFacts.contains(annotation.getClassNode().getName()))
                            .forEach(annotation -> {
                                api.getAnnotations().removeIf(existing -> existing.getClassNode().getName().equals(annotation.getClassNode().getName()));
                                api.addAnnotation(annotation);
                            });
                });
    }

    /** These sources have no public signature from which even type-based exclusions could be recovered. */
    private static String[] invisibleExclusion(String reason) {
        if (reason.equals(INPUT_IGNORED)) return new String[]{"INPUT_IGNORE", ""};
        if (reason.equals(IGNORED_INPUT)) return new String[]{"IGNORE", ""};
        if (reason.equals("has no admitted scalar, enum, List, or Map payload")) return new String[]{"NO_PAYLOAD", ""};
        if (reason.equals(OWNERSHIP_REASON)) return new String[]{"OWNERSHIP", ""};
        if (reason.equals(KEYED_OWNERSHIP_REASON)) return new String[]{"KEYED_OWNERSHIP", ""};
        if (reason.contains("@Field.keyMapping")) return new String[]{"KEY_MAPPING", ""};
        if (reason.startsWith(NUMERIC_REASON_PREFIX)) return new String[]{"NUMERIC", reason.substring(NUMERIC_REASON_PREFIX.length(), reason.indexOf(';'))};
        if (reason.startsWith(CONTAINER_REASON_PREFIX)) return new String[]{"CONTAINER", reason.substring(CONTAINER_REASON_PREFIX.length(), reason.indexOf(';'))};
        if (reason.startsWith(POPULATION_REASON_PREFIX))
            return new String[]{POPULATION, reason.substring(POPULATION_REASON_PREFIX.length(), reason.indexOf(';'))};
        if (reason.equals(ABSENT_FIELD_REASON)) return new String[]{"ABSENT_FIELD", ""};
        if (reason.equals(CONVERTER_REASON)) return new String[]{"CONVERTER", ""};
        throw new IllegalStateException("Unclassified invisible input exclusion: " + reason);
    }

    static StructuredInputAdmissionPlan planOf(ClassNode model) {
        return model == null ? null : model.redirect().getNodeMetaData(METADATA_KEY);
    }

    String model() {
        return model;
    }

    String builder() {
        return builder;
    }

    List<Entry> entries() {
        return entries;
    }

    List<Rejection> rejections() {
        return rejections;
    }

    private static void classifyField(ClassNode model, FieldNode field, List<Entry> entries, List<Rejection> rejections) {
        if (excludedField(field)) return;
        Name name = externalName(field);
        if (name.ignored()) {
            rejections.add(new Rejection(source(field), ignoreReason(name)));
            return;
        }
        ClassNode type = field.getType();
        if (isDSLObject(type)) {
            classifyOwnedObject(model, field, name, entries, rejections);
        } else if (isMap(type) && isDSLObject(getElementTypeForMap(type))) {
            classifyOwnedMap(field, name, entries, rejections);
        } else if (isCollection(type) && isDSLObject(getElementTypeForCollection(type))) {
            classifyOwnedSequence(field, name, entries, rejections);
        } else {
            classifyProperty(field, name, entries, rejections);
        }
    }

    private static boolean excludedField(FieldNode field) {
        return field.getName().startsWith("$") || (field.getModifiers() & Modifier.TRANSIENT) != 0
                || getFieldType(field) == FieldType.TRANSIENT || field.equals(getKeyField(field.getOwner()))
                || field.getAnnotations().stream().anyMatch(annotation -> annotation.getClassNode().getName()
                .equals(Owner.class.getName()));
    }

    private static void classifyOwnedObject(ClassNode model, FieldNode field, Name name,
                                            List<Entry> entries, List<Rejection> rejections) {
        ClassNode type = field.getType();
        String source = source(field);
        if (getFieldType(field) != FieldType.DEFAULT || Modifier.isAbstract(type.getModifiers())) {
            rejections.add(new Rejection(source, OWNERSHIP_REASON));
            return;
        }
        PublicOperation operation = fieldRelationship(field, field.getName());
        String fixedKey = getKeyField(type) != null && ClusterFactoryBuilder.isFixedKeysClusterMember(model, field)
                ? field.getName() : null;
        entries.add(new Entry(name.value(), Role.OWNED_OBJECT, source, operation,
                payloadForModel(type), name.origin(), null, null, fixedKey, false));
    }

    private static void classifyOwnedSequence(FieldNode field, Name name, List<Entry> entries, List<Rejection> rejections) {
        ClassNode elementType = getElementTypeForCollection(field.getType());
        if (elementType == null) {
            rejections.add(new Rejection(source(field), OWNERSHIP_REASON));
            return;
        }
        ClassNode child = elementType.redirect();
        if (!Set.of("java.util.List", "java.util.Set", "java.util.SortedSet", "java.util.NavigableSet")
                .contains(field.getType().redirect().getName()) || getFieldType(field) != FieldType.DEFAULT
                || Modifier.isAbstract(child.getModifiers())
                || child.getGenericsTypes() != null && child.getGenericsTypes().length != 0) {
            rejections.add(new Rejection(source(field), OWNERSHIP_REASON));
            return;
        }
        String element = DslAstHelper.getElementNameForCollectionField(field);
        entries.add(new Entry(name.value(), Role.OWNED_SEQUENCE, source(field),
                fieldRelationship(field, element),
                payloadForModel(child), name.origin(), null, null, null, false));
    }

    private static void classifyOwnedMap(FieldNode field, Name name, List<Entry> entries, List<Rejection> rejections) {
        ClassNode type = field.getType();
        ClassNode child = getElementTypeForMap(type);
        List<LiteralAlternative> alternatives = literalAlternatives(field);
        if (!alternatives.isEmpty()) {
            LiteralAlternative alternative = alternatives.get(0);
            entries.add(new Entry(name.value(), Role.LITERAL_ALTERNATIVE_MAP, source(field), fieldRelationship(field, field.getName()),
                    payloadForModel(alternative.target()), name.origin(), String.class.getName(), alternative.key(), null, false));
            return;
        }
        String source = source(field);
        if (child == null || getFieldType(field) != FieldType.DEFAULT
                || !ClassHelper.STRING_TYPE.equals(getKeyTypeForMap(type))
                || Modifier.isAbstract(child.getModifiers()) || getKeyField(child) == null) {
            rejections.add(new Rejection(source, KEYED_OWNERSHIP_REASON));
            return;
        }
        AnnotationNode fieldOptions = annotation(field, Field.class.getName());
        if (fieldOptions != null && fieldOptions.getMember(KEY_MAPPING_MEMBER) != null) {
            rejections.add(new Rejection(source,
                    "literal structured instance keys conflict with @Field.keyMapping-derived keys"));
            return;
        }
        entries.add(new Entry(name.value(), Role.KEYED_OWNED_MAP, source, fieldRelationship(field, field.getName()),
                payloadForModel(child), name.origin(), ClassHelper.STRING_TYPE.getName(), getKeyField(child).getName(), null, false));
    }

    private static void classifyProperty(FieldNode field, Name name, List<Entry> entries, List<Rejection> rejections) {
        ClassNode type = field.getType();
        String source = source(field);
        String unsupported = unsupportedNumericType(type);
        if (unsupported != null) {
            rejections.add(new Rejection(source, numericRejection(unsupported)));
            return;
        }
        String unsupportedContainer = unsupportedContainerTarget(fieldValueType(type));
        if (unsupportedContainer != null) {
            rejections.add(new Rejection(source, containerRejection(unsupportedContainer)));
            return;
        }
        Payload payload = payloadForField(type);
        MethodNode converter = payload == null ? StructuredInputConverter.selected(field) : null;
        if (converter != null) {
            Payload converted = new Payload(PayloadKind.SCALAR, converter.getParameters()[0].getOriginType().getName(), null, null, null);
            if (StructuredInputConverter.isListField(field)) {
                converted = new Payload(PayloadKind.LIST, List.class.getName(), null, null, converted);
            } else if (StructuredInputConverter.isMapField(field)) {
                converted = new Payload(PayloadKind.MAP, Map.class.getName(), null, String.class.getName(), converted);
            }
            entries.add(new Entry(name.value(), Role.PROPERTY, source,
                    new PublicOperation(converter.getDeclaringClass().getName(), converter.getName(), List.of(descriptor(converter))),
                    converted, name.origin(), null, null, null, false));
            return;
        }
        if (payload == null) {
            rejections.add(new Rejection(source, StructuredInputConverter.attempted(field) ? CONVERTER_REASON
                    : "has no admitted scalar, enum, List, or Map payload"));
            return;
        }
        PublicOperation operation = isCollection(type) || isMap(type)
                ? populationOperation(field, rejections) : publicOperation(field, rejections);
        if (nativeDerivedMap(field))
            payload = new Payload(PayloadKind.LIST, List.class.getName(), null, null, payload.item());
        if (operation != null)
            entries.add(new Entry(name.value(), Role.PROPERTY, source, operation, payload, name.origin(), null, null, null, false));
    }

    /** Existing scalar exclusions apply before considering an already-generated String projection. */
    static boolean supportsScalarConverterInput(ClassNode type) {
        return type != null && !isDSLObject(type) && !isCollection(type) && !isMap(type) && !type.isArray()
                && !type.equals(ClassHelper.OBJECT_TYPE) && !type.isGenericsPlaceHolder()
                && unsupportedNumericType(type) == null && payloadForField(type) == null;
    }

    private static ClassNode fieldValueType(ClassNode type) {
        if (isCollection(type)) return getElementTypeForCollection(type);
        if (isMap(type)) return getElementTypeForMap(type);
        return type;
    }

    private static void classifyOperation(MethodNode method, List<Entry> entries, List<Rejection> rejections) {
        String source = source(method);
        Name name = externalName(method);
        if (name.ignored()) {
            rejections.add(new Rejection(source, ignoreReason(name)));
            return;
        }
        Parameter[] parameters = method.getParameters();
        if (parameters.length == 0 && lifecycleCommand(method)) {
            rejections.add(new Rejection(source, "lifecycle methods are not structured commands"));
            return;
        }
        if (parameters.length == 0) {
            PublicOperation operation = publicOperation(method, rejections);
            if (operation != null) {
                entries.add(new Entry(name.value(), Role.COMMAND, source, operation,
                        new Payload(PayloadKind.EMPTY_OBJECT, Map.class.getName(), null, null, null),
                        name.origin(), null, null, null, false));
            }
            return;
        }
        if (parameters.length != 1) {
            rejections.add(new Rejection(source, "@Builder.Method must declare zero arguments or exactly one payload"));
            return;
        }
        String unsupported = unsupportedNumericType(parameters[0].getOriginType());
        if (unsupported != null) {
            rejections.add(new Rejection(source, numericRejection(unsupported)));
            return;
        }
        String unsupportedContainer = unsupportedContainerTarget(parameters[0].getOriginType());
        if (unsupportedContainer != null) {
            rejections.add(new Rejection(source, containerRejection(unsupportedContainer)));
            return;
        }
        Payload payload = payloadForValue(parameters[0].getOriginType());
        // A CharSequence signature consumes the same strict String grammar, only for scalar operations.
        if (!parameters[0].getOriginType().isGenericsPlaceHolder()
                && parameters[0].getOriginType().getName().equals(CharSequence.class.getName())) {
            payload = new Payload(PayloadKind.SCALAR, CharSequence.class.getName(), null, null, null);
        }
        if (payload == null) {
            rejections.add(new Rejection(source, "@Builder.Method payload is outside the admitted grammar"));
            return;
        }
        PublicOperation operation = publicOperation(method, rejections);
        if (operation == null) return;
        entries.add(new Entry(name.value(), Role.OPERATION, source, operation, payload, name.origin(), null, null, null,
                StructuredInputConverter.isStringInput(parameters[0].getOriginType()) && annotation(method, Struct.Operation.class.getName()) != null));
    }

    private static String operationRole(MethodNode method) {
        if (method.getParameters().length != 0) return Role.OPERATION.name();
        return lifecycleCommand(method) ? "LIFECYCLE" : Role.COMMAND.name();
    }

    private static boolean lifecycleCommand(MethodNode method) {
        return WriteAccessHelper.isLifecycleMethod(method)
                || annotation(method, Validate.class.getName()) != null;
    }

    private static Payload payloadForModel(ClassNode model) {
        ClassNode target = model.redirect();
        return new Payload(PayloadKind.NESTED_OBJECT, target.getName(),
                GeneratedDslSupport.builderTypeFor(target).redirect().getName(), null, null);
    }

    private static Payload payloadForField(ClassNode type) {
        if (!isCollection(type) && !isMap(type)) return payloadForValue(type);
        if (!DSLASTTransformation.SUPPORTED_COLLECTION_TYPES.contains(type.redirect().getName())) return null;
        if (isCollection(type)) {
            Payload item = payloadForValue(getElementTypeForCollection(type));
            return item == null ? null : new Payload(PayloadKind.LIST, type.redirect().getName(), null, null, item);
        }
        GenericsType[] generics = type.getGenericsTypes();
        if (generics == null || generics.length < 2 || !ClassHelper.STRING_TYPE.equals(generics[0].getType())) return null;
        Payload item = payloadForValue(generics[1].getType());
        return item == null ? null : new Payload(PayloadKind.MAP, type.redirect().getName(), null,
                ClassHelper.STRING_TYPE.getName(), item);
    }

    private static String unsupportedContainerTarget(ClassNode type) {
        if (type == null || type.isGenericsPlaceHolder()) return null;
        if (isCollection(type)) {
            if (!type.redirect().getName().equals(List.class.getName())) return type.redirect().getName();
            return unsupportedContainerTarget(getElementTypeForCollection(type));
        }
        if (isMap(type)) {
            if (!type.redirect().getName().equals(Map.class.getName())) return type.redirect().getName();
            return unsupportedContainerTarget(getElementTypeForMap(type));
        }
        return null;
    }

    private static String containerRejection(String type) {
        return CONTAINER_REASON_PREFIX + type
                + "; use ordinary List/Map payloads or a supported field's generated element population";
    }

    private static Payload payloadForValue(ClassNode type) {
        if (type == null || type.isGenericsPlaceHolder() || type.equals(ClassHelper.OBJECT_TYPE)) return null;
        if (isLeaf(type)) return new Payload(type.isEnum() ? PayloadKind.ENUM : PayloadKind.SCALAR,
                type.redirect().getName(), null, null, null);
        if (isCollection(type) && type.redirect().getName().equals(List.class.getName())) {
            ClassNode element = getElementTypeForCollection(type);
            Payload nested = payloadForValue(element);
            return nested == null || nested.kind() == PayloadKind.NESTED_OBJECT ? null
                    : new Payload(PayloadKind.LIST, type.redirect().getName(), null, null, nested);
        }
        if (isMap(type) && type.redirect().getName().equals(Map.class.getName())) {
            GenericsType[] generics = type.getGenericsTypes();
            if (generics == null || generics.length < 2 || !ClassHelper.STRING_TYPE.equals(generics[0].getType())) return null;
            Payload nested = payloadForValue(generics[1].getType());
            return nested == null || nested.kind() == PayloadKind.NESTED_OBJECT ? null
                    : new Payload(PayloadKind.MAP, type.redirect().getName(), null, ClassHelper.STRING_TYPE.getName(), nested);
        }
        return null;
    }

    private static boolean isLeaf(ClassNode type) {
        return type.isEnum() || ClassHelper.STRING_TYPE.equals(type) || ClassHelper.boolean_TYPE.equals(type)
                || ClassHelper.Boolean_TYPE.equals(type) || NUMERIC_TYPES.contains(type.redirect().getName());
    }

    private static String unsupportedNumericType(ClassNode type) {
        if (type == null || type.isGenericsPlaceHolder()) return null;
        if (type.isDerivedFrom(ClassHelper.make(Number.class)) && !NUMERIC_TYPES.contains(type.redirect().getName()))
            return type.redirect().getName();
        if (isCollection(type)) return unsupportedNumericType(getElementTypeForCollection(type));
        if (isMap(type)) return unsupportedNumericType(getElementTypeForMap(type));
        return null;
    }

    private static String numericRejection(String type) {
        return NUMERIC_REASON_PREFIX + type
                + "; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input";
    }

    private static PublicOperation populationOperation(FieldNode field, List<Rejection> rejections) {
        String name = DslAstHelper.getElementNameForCollectionField(field);
        ClassNode type = field.getType();
        List<String> parameters = isCollection(type) || nativeDerivedMap(field)
                ? List.of(fieldValueType(type).redirect().getName())
                : List.of(ClassHelper.STRING_TYPE.getName(), getElementTypeForMap(type).redirect().getName());
        ClassNode implementation = DslAstHelper.getBuilderClassOf(field.getOwner());
        List<MethodNode> candidates = implementation.getDeclaredMethods(name).stream()
                .filter(method -> method.getNodeMetaData(AbstractMethodBuilder.SOURCE_FIELD_METADATA_KEY) == field)
                .map(GeneratedDslSupport::publicProjectionOf)
                .filter(method -> method != null && method.isPublic() && !method.isStatic())
                .filter(method -> Arrays.stream(method.getParameters())
                        .map(parameter -> parameter.getOriginType().redirect().getName()).toList().equals(parameters))
                .toList();
        if (candidates.size() != 1) {
            rejections.add(new Rejection(source(field), POPULATION_REASON_PREFIX
                    + name + "(" + String.join(", ", parameters) + "); replacement setters and authored overrides are not input paths"));
            return null;
        }
        MethodNode operation = candidates.get(0);
        return new PublicOperation(operation.getDeclaringClass().redirect().getName(), name, List.of(descriptor(operation)));
    }

    /** Presence and exact generated provenance suffice; never inspect or execute the mapping body. */
    private static boolean nativeDerivedMap(FieldNode field) {
        if (!isMap(field.getType())) return false;
        AnnotationNode options = annotation(field, Field.class.getName());
        if (options == null || options.getMember(KEY_MAPPING_MEMBER) == null) return false;
        Payload payload = payloadForField(field.getType());
        return payload != null && (payload.item().kind() == PayloadKind.SCALAR || payload.item().kind() == PayloadKind.ENUM);
    }

    /** Internal preparation/attachment uses the field relationship, never a public creator group. */
    private static PublicOperation fieldRelationship(FieldNode field, String invocation) {
        return new PublicOperation(GeneratedDslSupport.of(field.getOwner()).getBuilderInterface().getName(), invocation, List.of());
    }

    private static PublicOperation publicOperation(FieldNode field, List<Rejection> rejections) {
        return publicOperation(field.getOwner(), field.getName(), source(field), rejections);
    }

    private static PublicOperation publicOperation(MethodNode method, List<Rejection> rejections) {
        MethodNode api = GeneratedDslSupport.publicProjectionOf(method);
        if (api == null) {
            rejections.add(new Rejection(source(method), "@Builder.Method is absent from the final public Builder contract"));
            return null;
        }
        return new PublicOperation(api.getDeclaringClass().redirect().getName(), api.getName(), List.of(descriptor(api)));
    }

    private static PublicOperation publicOperation(ClassNode model, String name, String source, List<Rejection> rejections) {
        ClassNode builder = GeneratedDslSupport.of(model.redirect()).getBuilderInterface().redirect();
        List<String> descriptors = builder.getDeclaredMethods(name).stream()
                .filter(MethodNode::isPublic)
                .filter(method -> !method.isStatic())
                .map(StructuredInputAdmissionPlan::descriptor)
                .sorted()
                .toList();
        if (descriptors.isEmpty()) {
            rejections.add(new Rejection(source, ABSENT_FIELD_REASON));
            return null;
        }
        return new PublicOperation(builder.getName(), name, descriptors);
    }

    private static String descriptor(MethodNode method) {
        String parameters = Arrays.stream(method.getParameters())
                .map(Parameter::getOriginType)
                .map(type -> type.redirect().getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return method.getName() + "(" + parameters + "):" + method.getReturnType().redirect().getName();
    }

    private static void enrichName(Name name, AnnotationNode fact) {
        fact.setMember("nameOrigin", new ConstantExpression(name.origin().name()));
        fact.setMember("rename", new ConstantExpression(name.origin() == NameOrigin.IDENTITY || name.ignored() ? "" : name.value()));
        fact.setMember("ignored", new ConstantExpression(name.ignored()));
    }

    private static String ignoreReason(Name name) {
        return name.origin() == NameOrigin.INPUT_IGNORE ? INPUT_IGNORED : IGNORED_INPUT;
    }

    private static Name externalName(AnnotatedNode node) {
        String explicit = inputName(node);
        AnnotationNode property = annotation(node, JSON_PROPERTY);
        Object jackson = property == null ? null : literal(property.getMember(VALUE));
        requireCompatibleNames(node, explicit, jackson);
        if (annotation(node, Ignore.class.getName()) != null) {
            return new Name(null, NameOrigin.INPUT_IGNORE, true);
        }
        AnnotationNode ignore = annotation(node, JSON_IGNORE);
        if (ignore != null && (ignore.getMember(VALUE) == null || Boolean.TRUE.equals(literal(ignore.getMember(VALUE))))) {
            return new Name(null, NameOrigin.IGNORE, true);
        }
        if (explicit != null) return new Name(explicit, NameOrigin.INPUT_NAME, false);
        if (jackson instanceof String spelling && !spelling.isEmpty()) return new Name(spelling, NameOrigin.JSON_PROPERTY, false);
        return new Name(node instanceof FieldNode field ? field.getName() : ((MethodNode) node).getName(), NameOrigin.IDENTITY, false);
    }

    private static String inputName(AnnotatedNode node) {
        AnnotationNode input = annotation(node, Struct.Name.class.getName());
        if (input == null) return null;
        Object value = literal(input.getMember(VALUE));
        if (!(value instanceof String spelling) || spelling.isEmpty()) {
            throw new IllegalArgumentException("@Struct.Name requires a nonempty exact String on " + source(node));
        }
        return spelling;
    }

    private static void requireCompatibleNames(AnnotatedNode node, String explicit, Object jackson) {
        if (explicit != null && jackson instanceof String spelling && !spelling.isEmpty() && !explicit.equals(jackson)) {
            throw new IllegalArgumentException("Conflicting literal input names on " + source(node)
                    + ": @Struct.Name('" + explicit + "') and @JsonProperty('" + jackson + "')");
        }
    }

    private static AnnotationNode annotation(AnnotatedNode node, String name) {
        return node.getAnnotations().stream().filter(candidate -> candidate.getClassNode().getName().equals(name)).findFirst().orElse(null);
    }

    private static Object literal(Expression expression) {
        return expression instanceof ConstantExpression constant ? constant.getValue() : null;
    }

    private static void rejectCollisions(List<Entry> entries, List<Rejection> rejections) {
        Map<String, List<Entry>> names = new LinkedHashMap<>();
        entries.forEach(entry -> names.computeIfAbsent(entry.externalName(), ignored -> new ArrayList<>()).add(entry));
        Set<Entry> rejected = new LinkedHashSet<>();
        names.forEach((name, candidates) -> selectProvisionalGroup(name, candidates, rejected, rejections));
        entries.removeAll(rejected);
    }

    private static void selectProvisionalGroup(String name, List<Entry> candidates, Set<Entry> rejected,
                                               List<Rejection> rejections) {
        List<Entry> marked = candidates.stream().filter(Entry::authoritative).toList();
        if (marked.size() > 1) {
            // Final hierarchy naming can separate a literal from an identity spelling under KEBAB_CASE.
            rejectGroup(candidates, rejected, rejections, "multiple provisional @Struct.Operation candidates for '" + name + "'");
        } else if (marked.size() == 1) {
            candidates.stream().filter(candidate -> candidate != marked.get(0)).forEach(rejected::add);
        } else if (candidates.size() > 1) {
            String declarations = candidates.stream().map(Entry::source).sorted()
                    .reduce((left, right) -> left + ", " + right).orElseThrow();
            rejectGroup(candidates, rejected, rejections, "external-name collision for '" + name + "': " + declarations);
        }
    }

    private static void rejectGroup(List<Entry> candidates, Set<Entry> rejected, List<Rejection> rejections, String reason) {
        candidates.forEach(candidate -> {
            rejected.add(candidate);
            rejections.add(new Rejection(candidate.source(), reason));
        });
    }

    private static String source(AnnotatedNode node) {
        if (node instanceof FieldNode field) return field.getOwner().getName() + "#" + field.getName();
        MethodNode method = (MethodNode) node;
        ClassNode model = getModelClassFor(method.getDeclaringClass());
        return (model == null ? method.getDeclaringClass() : model).getName() + "#" + descriptor(method);
    }

    enum Role { PROPERTY, OWNED_OBJECT, OWNED_SEQUENCE, KEYED_OWNED_MAP, LITERAL_ALTERNATIVE_MAP, VIRTUAL_OBJECT, OPERATION, COMMAND }
    enum PayloadKind { SCALAR, ENUM, LIST, MAP, NESTED_OBJECT, EMPTY_OBJECT }
    enum NameOrigin { IDENTITY, JSON_PROPERTY, INPUT_NAME, IGNORE, INPUT_IGNORE }
    record Payload(PayloadKind kind, String type, String builder, String keyType, Payload item) { }
    record PublicOperation(String declaringBuilder, String name, List<String> descriptorGroup) {
        PublicOperation {
            descriptorGroup = List.copyOf(descriptorGroup);
        }
    }
    record Entry(String externalName, Role role, String source, PublicOperation operation, Payload payload,
                 NameOrigin nameOrigin, String mapKeyType, String excludedChildKeyMember, String fixedChildKey, boolean authoritative) { }
    record Rejection(String source, String reason) { }
    private record Name(String value, NameOrigin origin, boolean ignored) { }
}
