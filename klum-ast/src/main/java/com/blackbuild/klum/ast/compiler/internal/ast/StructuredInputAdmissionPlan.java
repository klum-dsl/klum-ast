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
import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.Owner;
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessHelper;
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

import java.lang.reflect.Modifier;
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
import static groovyjarjarasm.asm.Opcodes.ACC_FINAL;
import static groovyjarjarasm.asm.Opcodes.ACC_PRIVATE;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC;
import static org.codehaus.groovy.ast.stmt.EmptyStatement.INSTANCE;

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

        transformation.builderFields.keySet().forEach(field -> classifyField(field, entries, rejections));
        transformation.builderClass.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(transformation.builderClass.redirect()))
                .filter(MethodNode::isPublic)
                .filter(method -> !method.isStatic())
                .filter(method -> WriteAccessHelper.isBuilderMethod(method))
                .filter(method -> !method.getAnnotations(BUILDER_METHOD).isEmpty())
                .forEach(method -> classifyOperation(method, entries, rejections));

        rejectCollisions(entries, rejections);
        entries.sort(Comparator.comparing(Entry::externalName).thenComparing(Entry::source));
        rejections.sort(Comparator.comparing(Rejection::source).thenComparing(Rejection::reason));
        model.setNodeMetaData(METADATA_KEY, new StructuredInputAdmissionPlan(
                model.getName(), GeneratedDslSupport.of(model).getBuilderInterface().redirect().getName(), entries, rejections));
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
    static void emitCarrier(DSLASTTransformation transformation) {
        ClassNode model = transformation.annotatedClass.redirect();
        StructuredInputAdmissionPlan plan = planOf(model);
        if (plan == null)
            throw new IllegalStateException("No WIRE-PLAN classification for " + model.getName());

        ClassNode carrier = new ClassNode(model.getName() + "_DSL$WirePlan",
                ACC_FINAL | ACC_SYNTHETIC, ClassHelper.OBJECT_TYPE);
        carrier.setSourcePosition(model);
        carrier.addField("DATA", ACC_PRIVATE | ACC_STATIC | ACC_FINAL,
                ClassHelper.STRING_TYPE, new ConstantExpression(plan.canonicalForm()));
        carrier.addConstructor(ACC_PRIVATE, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, INSTANCE);
        model.getModule().addClass(carrier);
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
                .append(token(entry.excludedChildKeyMember())).append('|').append(payloadToken(entry.payload())).append('\n'));
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
        return value == null ? "-" : Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static void classifyField(FieldNode field, List<Entry> entries, List<Rejection> rejections) {
        if (field.getName().startsWith("$") || (field.getModifiers() & Modifier.TRANSIENT) != 0
                || getFieldType(field) == FieldType.TRANSIENT || field.equals(getKeyField(field.getOwner()))
                || field.getAnnotations().stream().anyMatch(annotation -> annotation.getClassNode().getName()
                .equals(Owner.class.getName()))) return;

        Name name = externalName(field);
        String source = source(field);
        if (name.ignored()) {
            rejections.add(new Rejection(source, "excluded by @JsonIgnore(true)"));
            return;
        }

        ClassNode type = field.getType();
        if (isDSLObject(type)) {
            if (getFieldType(field) != FieldType.DEFAULT || Modifier.isAbstract(type.getModifiers())) {
                rejections.add(new Rejection(source, "requires one concrete owned relationship"));
                return;
            }
            PublicOperation operation = publicOperation(field, rejections);
            if (operation != null)
                entries.add(new Entry(name.value(), Role.OWNED_OBJECT, source, operation,
                        payloadForModel(type), name.origin(), null, null));
            return;
        }

        if (isMap(type) && isDSLObject(getElementTypeForMap(type))) {
            ClassNode child = getElementTypeForMap(type);
            if (getFieldType(field) != FieldType.DEFAULT || !ClassHelper.STRING_TYPE.equals(getKeyTypeForMap(type))
                    || Modifier.isAbstract(child.getModifiers()) || getKeyField(child) == null) {
                rejections.add(new Rejection(source, "requires Map<String, concrete keyed DSL Object> ownership"));
                return;
            }
            PublicOperation operation = publicOperation(field, rejections);
            if (operation != null)
                entries.add(new Entry(name.value(), Role.KEYED_OWNED_MAP, source, operation,
                        payloadForModel(child), name.origin(), ClassHelper.STRING_TYPE.getName(), getKeyField(child).getName()));
            return;
        }

        Payload payload = payloadForValue(type);
        if (payload == null) {
            rejections.add(new Rejection(source, "has no admitted scalar, enum, List, or Map payload"));
            return;
        }
        PublicOperation operation = publicOperation(field, rejections);
        if (operation != null)
            entries.add(new Entry(name.value(), Role.PROPERTY, source, operation, payload, name.origin(), null, null));
    }

    private static void classifyOperation(MethodNode method, List<Entry> entries, List<Rejection> rejections) {
        String source = source(method);
        Name name = externalName(method);
        if (name.ignored()) {
            rejections.add(new Rejection(source, "excluded by @JsonIgnore(true)"));
            return;
        }
        Parameter[] parameters = method.getParameters();
        if (parameters.length != 1) {
            rejections.add(new Rejection(source, "@Builder.Method must declare exactly one payload"));
            return;
        }
        Payload payload = payloadForValue(parameters[0].getOriginType());
        if (payload == null) {
            rejections.add(new Rejection(source, "@Builder.Method payload is outside the admitted grammar"));
            return;
        }
        PublicOperation operation = publicOperation(method, rejections);
        if (operation == null) return;
        entries.add(new Entry(name.value(), Role.OPERATION, source, operation, payload, name.origin(), null, null));
    }

    private static Payload payloadForModel(ClassNode model) {
        ClassNode target = model.redirect();
        return new Payload(PayloadKind.NESTED_OBJECT, target.getName(),
                GeneratedDslSupport.builderTypeFor(target).redirect().getName(), null, null);
    }

    private static Payload payloadForValue(ClassNode type) {
        if (type == null || type.isGenericsPlaceHolder() || type.equals(ClassHelper.OBJECT_TYPE)) return null;
        if (isLeaf(type)) return new Payload(type.isEnum() ? PayloadKind.ENUM : PayloadKind.SCALAR,
                type.redirect().getName(), null, null, null);
        if (isCollection(type)) {
            ClassNode element = getElementTypeForCollection(type);
            Payload nested = payloadForValue(element);
            return nested == null || nested.kind() == PayloadKind.NESTED_OBJECT ? null
                    : new Payload(PayloadKind.LIST, type.redirect().getName(), null, null, nested);
        }
        if (isMap(type)) {
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
                || ClassHelper.Boolean_TYPE.equals(type) || ClassHelper.byte_TYPE.equals(type)
                || ClassHelper.short_TYPE.equals(type) || ClassHelper.int_TYPE.equals(type)
                || ClassHelper.long_TYPE.equals(type) || ClassHelper.float_TYPE.equals(type)
                || ClassHelper.double_TYPE.equals(type)
                || type.isDerivedFrom(ClassHelper.make(Number.class));
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
            rejections.add(new Rejection(source, "field is absent from the final public Builder contract"));
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
        if (ignore != null && (ignore.getMember("value") == null || Boolean.TRUE.equals(literal(ignore.getMember("value")))))
            return new Name(null, NameOrigin.IGNORE, true);
        AnnotationNode property = annotation(node, JSON_PROPERTY);
        Object value = property == null ? null : literal(property.getMember("value"));
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
                 NameOrigin nameOrigin, String mapKeyType, String excludedChildKeyMember) { }
    record Rejection(String source, String reason) { }
    private record Name(String value, NameOrigin origin, boolean ignored) { }
}
