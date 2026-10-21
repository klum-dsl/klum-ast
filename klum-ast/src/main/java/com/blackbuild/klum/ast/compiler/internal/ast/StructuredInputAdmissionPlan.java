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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
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
 * Compiler-only normalization for the deliberately small WIRE-PLAN input grammar.
 *
 * <p>The plan deliberately stays on the source AST. WIRE-PLAN/B owns selecting a binary
 * representation for these facts.</p>
 */
final class StructuredInputAdmissionPlan {

    static final String METADATA_KEY = StructuredInputAdmissionPlan.class.getName() + ".plan";
    private static final String JSON_IGNORE = "com.fasterxml.jackson.annotation.JsonIgnore";
    private static final String JSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";

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

        transformation.builderFields.keySet().forEach(field -> classifyField(transformation, field, entries, rejections));
        transformation.builderClass.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(transformation.builderClass.redirect()))
                .filter(MethodNode::isPublic)
                .filter(method -> !method.isStatic())
                .filter(method -> WriteAccessHelper.isBuilderMethod(method))
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

    private static void classifyField(DSLASTTransformation transformation, FieldNode field, List<Entry> entries,
                                      List<Rejection> rejections) {
        if (field.getName().startsWith("$") || (field.getModifiers() & Modifier.TRANSIENT) != 0
                || getFieldType(field) == FieldType.TRANSIENT || field.equals(getKeyField(field.getOwner()))
                || field.getAnnotations().stream().anyMatch(annotation -> annotation.getClassNode().getName()
                .equals("com.blackbuild.klum.ast.Owner"))) return;

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
            entries.add(new Entry(name.value(), Role.OWNED_OBJECT, source, field.getName(), payloadForModel(type), name.origin()));
            return;
        }

        if (isMap(type) && isDSLObject(getElementTypeForMap(type))) {
            ClassNode child = getElementTypeForMap(type);
            if (getFieldType(field) != FieldType.DEFAULT || !ClassHelper.STRING_TYPE.equals(getKeyTypeForMap(type))
                    || Modifier.isAbstract(child.getModifiers()) || getKeyField(child) == null) {
                rejections.add(new Rejection(source, "requires Map<String, concrete keyed DSL Object> ownership"));
                return;
            }
            entries.add(new Entry(name.value(), Role.KEYED_OWNED_MAP, source, field.getName(), payloadForModel(child), name.origin()));
            return;
        }

        Payload payload = payloadForValue(type);
        if (payload == null) {
            rejections.add(new Rejection(source, "has no admitted scalar, enum, List, or Map payload"));
            return;
        }
        entries.add(new Entry(name.value(), Role.PROPERTY, source, field.getName(), payload, name.origin()));
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
        entries.add(new Entry(name.value(), Role.OPERATION, source, method.getName(), payload, name.origin()));
    }

    private static Payload payloadForModel(ClassNode model) {
        return new Payload(PayloadKind.NESTED_OBJECT, model.redirect().getName());
    }

    private static Payload payloadForValue(ClassNode type) {
        if (type == null || type.isGenericsPlaceHolder() || type.equals(ClassHelper.OBJECT_TYPE)) return null;
        if (isLeaf(type)) return new Payload(type.isEnum() ? PayloadKind.ENUM : PayloadKind.SCALAR, type.redirect().getName());
        if (isCollection(type)) {
            ClassNode element = getElementTypeForCollection(type);
            Payload nested = payloadForValue(element);
            return nested == null || nested.kind() == PayloadKind.NESTED_OBJECT ? null : new Payload(PayloadKind.LIST, nested.type());
        }
        if (isMap(type)) {
            if (!ClassHelper.STRING_TYPE.equals(getKeyTypeForMap(type))) return null;
            Payload nested = payloadForValue(getElementTypeForMap(type));
            return nested == null || nested.kind() == PayloadKind.NESTED_OBJECT ? null : new Payload(PayloadKind.MAP, nested.type());
        }
        return null;
    }

    private static boolean isLeaf(ClassNode type) {
        return type.isEnum() || ClassHelper.STRING_TYPE.equals(type) || ClassHelper.boolean_TYPE.equals(type)
                || ClassHelper.Boolean_TYPE.equals(type) || ClassHelper.isPrimitiveType(type)
                || type.isDerivedFrom(ClassHelper.make(Number.class));
    }

    private static Name externalName(AnnotatedNode node) {
        AnnotationNode ignore = annotation(node, JSON_IGNORE);
        if (ignore != null && Boolean.TRUE.equals(literal(ignore.getMember("value"))))
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
        String parameters = java.util.Arrays.stream(method.getParameters())
                .map(Parameter::getOriginType)
                .map(type -> type.redirect().getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
        return (model == null ? method.getDeclaringClass() : model).getName() + "#" + method.getName() + "(" + parameters + ")";
    }

    enum Role { PROPERTY, OWNED_OBJECT, KEYED_OWNED_MAP, OPERATION }
    enum PayloadKind { SCALAR, ENUM, LIST, MAP, NESTED_OBJECT }
    enum NameOrigin { IDENTITY, JSON_PROPERTY, IGNORE }
    record Payload(PayloadKind kind, String type) { }
    record Entry(String externalName, Role role, String source, String operation, Payload payload, NameOrigin nameOrigin) { }
    record Rejection(String source, String reason) { }
    private record Name(String value, NameOrigin origin, boolean ignored) { }
}
