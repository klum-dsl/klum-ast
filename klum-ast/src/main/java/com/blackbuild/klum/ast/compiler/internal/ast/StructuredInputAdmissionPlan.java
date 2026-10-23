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
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput;
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessHelper;
import com.blackbuild.klum.ast.compiler.internal.layer3.ClusterFactoryBuilder;
import groovyjarjarasm.asm.ClassWriter;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.control.SourceUnit;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Base64;
import java.util.StringJoiner;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getKeyField;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getFieldType;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getModelClassFor;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getElementTypeForCollection;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getElementTypeForMap;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getKeyTypeForMap;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isCollection;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isMap;
import static groovyjarjarasm.asm.Opcodes.ACC_PRIVATE;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC;

/**
 * Compiler-only normalization for the deliberately small WIRE-PLAN input grammar.
 *
 * <p>The plan deliberately stays on the source AST. WIRE-PLAN/B owns selecting a binary
 * representation for these facts.</p>
 */
final class StructuredInputAdmissionPlan {

    static final String METADATA_KEY = StructuredInputAdmissionPlan.class.getName() + ".plan";
    private static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String VALUE = "value";
    private static final String IGNORED_INPUT = "excluded by @JsonIgnore(true)";
    private static final String OWNERSHIP_REASON = "requires one concrete owned relationship";
    private static final String KEYED_OWNERSHIP_REASON = "requires Map<String, concrete keyed DSL Object> ownership";
    private static final String POPULATION = "POPULATION";
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
            entries.addAll(inherited.entries);
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

    /** Only irreducible source/group facts; signatures, payloads, names of methods and docs stay on declarations. */
    private static void enrichPublicContract(DSLASTTransformation transformation, List<Entry> entries, List<Rejection> rejections) {
        ClassNode model = transformation.annotatedClass.redirect();
        ClassNode builder = GeneratedDslSupport.of(model).getBuilderInterface().redirect();
        AnnotationNode schema = new AnnotationNode(ClassHelper.make(GeneratedInput.Schema.class));
        FieldNode key = getKeyField(model);
        if (key != null) schema.setMember("key", new ConstantExpression(key.getName()));
        ListExpression invisible = new ListExpression();
        transformation.builderFields.keySet().stream().filter(field -> !excludedField(field))
                .forEach(field -> enrichField(builder, field, entries, rejections, invisible));
        if (!invisible.getExpressions().isEmpty()) schema.setMember("exclusions", invisible);
        builder.addAnnotation(schema);
        restoreAuthoredFacts(transformation);
    }

    private static void enrichField(ClassNode builder, FieldNode field, List<Entry> entries,
                                    List<Rejection> rejections, ListExpression invisible) {
        MethodNode getter = builder.getDeclaredMethod(DslAstHelper.getGetterName(field.getName()), Parameter.EMPTY_ARRAY);
        Rejection rejection = rejections.stream().filter(value -> value.source().equals(source(field))).findFirst().orElse(null);
        if (getter == null) {
            enrichInvisibleField(field, rejection, invisible);
            return;
        }
        AnnotationNode fact = new AnnotationNode(ClassHelper.make(GeneratedInput.Field.class));
        fact.setMember(VALUE, new ConstantExpression(field.getName()));
        Name name = externalName(field);
        if (name.ignored()) fact.setMember("ignored", ConstantExpression.TRUE);
        else if (name.origin() == NameOrigin.JSON_PROPERTY) fact.setMember("rename", new ConstantExpression(name.value()));
        Entry entry = entries.stream().filter(value -> value.source().equals(source(field))).findFirst().orElse(null);
        if (entry != null && entry.fixedChildKey() != null) fact.setMember("fixedKey", ConstantExpression.TRUE);
        if (rejection != null && !name.ignored()) enrichFieldExclusion(field, rejection, fact);
        getter.addAnnotation(fact);
        if (entry != null) enrichSelection(builder, field, entry);
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
        return null;
    }

    private static void enrichSelection(ClassNode builder, FieldNode field, Entry entry) {
        for (MethodNode method : builder.getDeclaredMethods(entry.operation().name())) {
            if (!entry.operation().descriptorGroup().contains(descriptor(method))) continue;
            AnnotationNode selected = new AnnotationNode(ClassHelper.make(GeneratedInput.Selection.class));
            selected.setMember("field", new ConstantExpression(field.getName()));
            selected.setMember("role", new ConstantExpression(entry.role().name()));
            method.addAnnotation(selected);
        }
    }

    private static void restoreAuthoredFacts(DSLASTTransformation transformation) {
        // A generated projection can occupy an authored signature before annotation copying occurs.
        // Restore the original admission/naming annotations together; a marker alone can change rejection precedence.
        Set<String> authoredFacts = Set.of(Builder.Method.class.getName(), JSON_IGNORE, JSON_PROPERTY);
        transformation.builderClass.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(transformation.builderClass.redirect()))
                .filter(method -> !method.getAnnotations(BUILDER_METHOD).isEmpty())
                .forEach(method -> {
                    MethodNode api = GeneratedDslSupport.publicProjectionOf(method);
                    if (api == null) return;
                    method.getAnnotations().stream().filter(annotation -> authoredFacts.contains(annotation.getClassNode().getName()))
                            .forEach(annotation -> {
                                api.getAnnotations().removeIf(existing -> existing.getClassNode().getName().equals(annotation.getClassNode().getName()));
                                api.addAnnotation(annotation);
                            });
                });
    }

    /** These sources have no public signature from which even type-based exclusions could be recovered. */
    private static String[] invisibleExclusion(String reason) {
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
        throw new IllegalStateException("Unclassified invisible input exclusion: " + reason);
    }

    static StructuredInputAdmissionPlan planOf(ClassNode model) {
        return model == null ? null : model.redirect().getNodeMetaData(METADATA_KEY);
    }

    /**
     * Emits the selected WIRE-PLAN/B binary form beside the generated public namespace.
     *
     * <p>The existing retained public annotations cannot distinguish generated property,
     * owned-relationship and keyed-map operations, nor can they retain the source declaration
     * identity that makes collision diagnostics deterministic.  This deliberately has no reader
     * API: future consumers inspect the constant from class bytes without loading Schema code.</p>
     */
    static void emitCarrier(ClassNode model) {
        model = model.redirect();
        if (model == null)
            throw new IllegalStateException("No model for WIRE-PLAN carrier");
        StructuredInputAdmissionPlan plan = planOf(model);
        if (plan == null)
            return; // A compiler without this transformation's classification does not publish a carrier.

        SourceUnit source = model.getModule().getContext();
        if (source == null || source.getErrorCollector().hasErrors()
                || source.getConfiguration() == null || source.getConfiguration().getTargetDirectory() == null)
            return; // Failed and AST-only compilation does not publish a generated class-file artifact.
        File outputDirectory = source.getConfiguration().getTargetDirectory();

        String internalName = model.getName().replace('.', '/') + "_DSL$WirePlan";
        ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8,
                Opcodes.ACC_FINAL | Opcodes.ACC_SUPER | ACC_SYNTHETIC,
                internalName, null, "java/lang/Object", null);
        writer.visitField(ACC_PRIVATE | ACC_STATIC | Opcodes.ACC_FINAL,
                "DATA", "Ljava/lang/String;", null, plan.canonicalForm()).visitEnd();
        writer.visitEnd();

        Path carrier = outputDirectory.toPath().resolve(internalName + ".class");
        try {
            Files.createDirectories(carrier.getParent());
            Files.write(carrier, writer.toByteArray());
        } catch (FileSystemException exception) {
            if ("File name too long".equals(exception.getReason()))
                return; // Invalid test/source diagnostics have no class-file artifact to publish.
            throw new IllegalStateException("Could not emit WIRE-PLAN/B carrier " + carrier, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Could not emit WIRE-PLAN/B carrier " + carrier, exception);
        }
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

    String canonicalForm() {
        StringBuilder result = new StringBuilder("WIRE-PLAN/B\n");
        result.append("model|").append(token(model)).append('|').append(token(builder)).append('\n');
        entries.forEach(entry -> result.append("entry|").append(token(entry.externalName())).append('|')
                .append(entry.role()).append('|').append(token(entry.source())).append('|')
                .append(token(entry.operation().declaringBuilder())).append('|').append(token(entry.operation().name())).append('|')
                .append(token(String.join(",", entry.operation().descriptorGroup()))).append('|')
                .append(entry.nameOrigin()).append('|').append(token(entry.mapKeyType())).append('|')
                .append(token(entry.excludedChildKeyMember())).append('|').append(payloadToken(entry.payload())).append('|')
                .append(token(entry.fixedChildKey())).append('\n'));
        rejections.forEach(rejection -> result.append("rejection|").append(token(rejection.source())).append('|')
                .append(token(rejection.reason())).append('\n'));
        return result.toString();
    }

    private static String payloadToken(Payload payload) {
        StringJoiner result = new StringJoiner(",", "[", "]");
        result.add(payload.kind().name()).add(token(payload.type())).add(token(payload.builder())).add(token(payload.keyType()));
        if (payload.item() != null)
            result.add(payloadToken(payload.item()));
        return result.toString();
    }

    private static String token(String value) {
        return value == null ? "-" : Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static void classifyField(ClassNode model, FieldNode field, List<Entry> entries, List<Rejection> rejections) {
        if (excludedField(field)) return;
        Name name = externalName(field);
        if (name.ignored()) {
            rejections.add(new Rejection(source(field), IGNORED_INPUT));
            return;
        }
        ClassNode type = field.getType();
        if (isDSLObject(type)) {
            classifyOwnedObject(model, field, name, entries, rejections);
        } else if (isMap(type) && isDSLObject(getElementTypeForMap(type))) {
            classifyOwnedMap(field, name, entries, rejections);
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
        PublicOperation operation = publicOperation(field, rejections);
        String fixedKey = getKeyField(type) != null && ClusterFactoryBuilder.isFixedKeysClusterMember(model, field)
                ? field.getName() : null;
        if (operation != null)
            entries.add(new Entry(name.value(), Role.OWNED_OBJECT, source, operation,
                    payloadForModel(type), name.origin(), null, null, fixedKey));
    }

    private static void classifyOwnedMap(FieldNode field, Name name, List<Entry> entries, List<Rejection> rejections) {
        ClassNode type = field.getType();
        ClassNode child = getElementTypeForMap(type);
        String source = source(field);
        if (child == null || getFieldType(field) != FieldType.DEFAULT
                || !ClassHelper.STRING_TYPE.equals(getKeyTypeForMap(type))
                || Modifier.isAbstract(child.getModifiers()) || getKeyField(child) == null) {
            rejections.add(new Rejection(source, KEYED_OWNERSHIP_REASON));
            return;
        }
        AnnotationNode fieldOptions = annotation(field, Field.class.getName());
        if (fieldOptions != null && fieldOptions.getMember("keyMapping") != null) {
            rejections.add(new Rejection(source,
                    "literal structured instance keys conflict with @Field.keyMapping-derived keys"));
            return;
        }
        PublicOperation operation = publicOperation(field, rejections);
        if (operation != null)
            entries.add(new Entry(name.value(), Role.KEYED_OWNED_MAP, source, operation,
                    payloadForModel(child), name.origin(), ClassHelper.STRING_TYPE.getName(), getKeyField(child).getName(), null));
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
        if (payload == null) {
            rejections.add(new Rejection(source, "has no admitted scalar, enum, List, or Map payload"));
            return;
        }
        PublicOperation operation = isCollection(type) || isMap(type)
                ? populationOperation(field, rejections) : publicOperation(field, rejections);
        if (operation != null)
            entries.add(new Entry(name.value(), Role.PROPERTY, source, operation, payload, name.origin(), null, null, null));
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
            rejections.add(new Rejection(source, IGNORED_INPUT));
            return;
        }
        Parameter[] parameters = method.getParameters();
        if (parameters.length != 1) {
            rejections.add(new Rejection(source, "@Builder.Method must declare exactly one payload"));
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
        if (payload == null) {
            rejections.add(new Rejection(source, "@Builder.Method payload is outside the admitted grammar"));
            return;
        }
        PublicOperation operation = publicOperation(method, rejections);
        if (operation == null) return;
        entries.add(new Entry(name.value(), Role.OPERATION, source, operation, payload, name.origin(), null, null, null));
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
        List<String> parameters = isCollection(type)
                ? List.of(getElementTypeForCollection(type).redirect().getName())
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

    private static Name externalName(AnnotatedNode node) {
        AnnotationNode ignore = annotation(node, JSON_IGNORE);
        if (ignore != null && (ignore.getMember(VALUE) == null || Boolean.TRUE.equals(literal(ignore.getMember(VALUE)))))
            return new Name(null, NameOrigin.IGNORE, true);
        AnnotationNode property = annotation(node, JSON_PROPERTY);
        Object value = property == null ? null : literal(property.getMember(VALUE));
        if (value instanceof String spelling && !spelling.isEmpty())
            return new Name(spelling, NameOrigin.JSON_PROPERTY, false);
        return new Name(node instanceof FieldNode field ? field.getName() : ((MethodNode) node).getName(), NameOrigin.IDENTITY, false);
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
        names.forEach((name, candidates) -> {
            if (candidates.size() < 2) return;
            String declarations = candidates.stream().map(Entry::source).sorted().reduce((left, right) -> left + ", " + right).orElseThrow();
            candidates.forEach(candidate -> {
                rejected.add(candidate);
                rejections.add(new Rejection(candidate.source(), "external-name collision for '" + name + "': " + declarations));
            });
        });
        entries.removeAll(rejected);
    }

    private static String source(AnnotatedNode node) {
        if (node instanceof FieldNode field) return field.getOwner().getName() + "#" + field.getName();
        MethodNode method = (MethodNode) node;
        ClassNode model = getModelClassFor(method.getDeclaringClass());
        return (model == null ? method.getDeclaringClass() : model).getName() + "#" + descriptor(method);
    }

    enum Role { PROPERTY, OWNED_OBJECT, KEYED_OWNED_MAP, OPERATION }
    enum PayloadKind { SCALAR, ENUM, LIST, MAP, NESTED_OBJECT }
    enum NameOrigin { IDENTITY, JSON_PROPERTY, IGNORE }
    record Payload(PayloadKind kind, String type, String builder, String keyType, Payload item) { }
    record PublicOperation(String declaringBuilder, String name, List<String> descriptorGroup) {
        PublicOperation {
            descriptorGroup = List.copyOf(descriptorGroup);
        }
    }
    record Entry(String externalName, Role role, String source, PublicOperation operation, Payload payload,
                 NameOrigin nameOrigin, String mapKeyType, String excludedChildKeyMember, String fixedChildKey) { }
    record Rejection(String source, String reason) { }
    private record Name(String value, NameOrigin origin, boolean ignored) { }
}
