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
package com.blackbuild.klum.ast.schema;

import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.blackbuild.klum.ast.schema.WireContract.Entry;
import com.blackbuild.klum.ast.schema.WireContract.Kind;
import com.blackbuild.klum.ast.schema.WireContract.Payload;
import com.blackbuild.klum.ast.schema.WireContract.Rejection;
import com.blackbuild.klum.ast.schema.WireContract.Role;

/** Projects compiler admission without invoking the runtime structured-input reader. */
final class SchemaProjection {
    private final BinaryArtifacts artifacts;
    private final Map<String, ClassFacts> classes = new HashMap<>();
    private final Map<String, Object> definitions = new TreeMap<>();
    private final Set<String> diagnostics = new TreeSet<>();

    SchemaProjection(BinaryArtifacts artifacts) { this.artifacts = artifacts; }

    String generate(String root) throws IOException {
        facts(root).requireConcrete();
        String builder = root + WireContract.BUILDER_SUFFIX;
        definition(builder);
        diagnostics.add("root " + root + "; Builder " + builder + "; definitions " + definitions.size());
        return JsonText.encode(Map.of(
                "$schema", "https://json-schema.org/draft/2020-12/schema",
                "$comment", "Structural projection of compiled KlumAST structured-input admission only. "
                        + "Does not guarantee binding, operation execution, lifecycle, materialization or completed validation. "
                        + "Defaults and validation code are not evaluated; all input properties are optional. "
                        + "Float/double number projection cannot guarantee Java conversion or representability. "
                        + "This is not a Jackson schema or a complete Groovy DSL contract.",
                "$ref", pointer(builder), "$defs", definitions)) + "\n";
    }

    Set<String> diagnostics() { return Set.copyOf(diagnostics); }

    private ClassFacts facts(String name) throws IOException {
        if (!classes.containsKey(name)) classes.put(name, new ClassFacts(artifacts.read(name), false));
        return classes.get(name);
    }

    private void definition(String builder) throws IOException {
        if (definitions.containsKey(builder)) return;
        facts(WireContract.model(builder)).requireConcrete();
        Map<String, Object> properties = new TreeMap<>();
        Map<String, Object> definition = new TreeMap<>(Map.of("type", "object", "properties", properties, "additionalProperties", false));
        definitions.put(builder, definition); // Reserve before following recursive relationships.
        List<Entry> entries = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        collect(builder, new HashSet<>(), entries, rejections);
        Map<String, Entry> sources = new TreeMap<>();
        for (Entry entry : entries) {
            Entry previous = sources.putIfAbsent(entry.source(), entry);
            if (previous != null && !previous.equals(entry)) throw invalid("Conflicting source facts: " + entry.source());
        }
        Set<String> rejected = new HashSet<>();
        rejections.forEach(rejection -> rejected.add(rejection.source()));
        Map<String, List<Entry>> names = new TreeMap<>();
        sources.values().stream().filter(entry -> !rejected.contains(entry.source()))
                .forEach(entry -> names.computeIfAbsent(entry.name(), ignored -> new ArrayList<>()).add(entry));
        for (var group : names.entrySet()) {
            if (group.getValue().size() > 1) {
                String declarations = String.join(", ", group.getValue().stream().map(Entry::source).sorted().toList());
                group.getValue().forEach(entry -> rejections.add(new Rejection(entry.source(),
                        "external-name collision for '" + group.getKey() + "': " + declarations)));
            } else {
                Entry entry = group.getValue().get(0);
                try {
                    Map<String, Object> shape = payload(entry.payload());
                    if (entry.role() == Role.KEYED_OWNED_MAP) shape = new TreeMap<>(Map.of("type", "object", "additionalProperties", shape));
                    String prose = facts(WireContract.model(entry.owner())).documentation.get(entry.source());
                    if (prose == null && entry.role() == Role.OPERATION) {
                        // The exact selected public method is the only safe fallback attribution.
                        prose = facts(entry.owner()).documentation.get(entry.owner() + "#" + entry.descriptors().get(0));
                    }
                    if (prose != null) shape.put("description", prose);
                    else diagnostics.add("prose unavailable: " + entry.source());
                    properties.put(entry.name(), shape);
                    diagnostics.add("admitted " + entry.role() + " " + entry.source() + " as " + JsonText.encode(entry.name())
                            + "; name origin " + entry.nameOrigin() + "; contract " + entry.owner() + "#" + String.join(",", entry.descriptors()));
                } catch (IllegalArgumentException failure) {
                    throw invalid(entry.source() + "; contract " + entry.owner() + ": " + failure.getMessage());
                }
            }
        }
        rejections.forEach(rejection -> diagnostics.add("rejected " + rejection.source() + ": " + rejection.reason()));
        String prose = facts(WireContract.model(builder)).documentation.get(WireContract.model(builder));
        if (prose != null) definition.put("description", prose);
        else diagnostics.add("prose unavailable: " + WireContract.model(builder));
    }

    private void collect(String builder, Set<String> visited, List<Entry> entries, List<Rejection> rejections) throws IOException {
        if (!visited.add(builder)) return;
        ClassFacts contract = facts(builder);
        contract.requirePublicInterface();
        String model = WireContract.model(builder);
        ClassFacts modelFacts = facts(model);
        String origin = artifacts.read(model).origin();
        for (String sibling : List.of(builder, model + "_DSL$Factory", model + "_DSL$WirePlan")) {
            if (!origin.equals(artifacts.read(sibling).origin())) throw invalid("Generated sibling " + sibling + " is outside Model artifact " + origin);
        }
        facts(model + "_DSL$Factory").requirePublicInterface();
        ClassFacts carrier = new ClassFacts(artifacts.read(model + "_DSL$WirePlan"), true);
        WireContract plan = new WireContract(carrier.carrier, model, builder);
        for (Entry entry : plan.entries) validateEntry(entry, contract);
        entries.addAll(plan.entries);
        rejections.addAll(plan.rejections);
        for (String parent : contract.interfaces) {
            if (!parent.endsWith(WireContract.BUILDER_SUFFIX)) continue;
            String parentModel = WireContract.model(parent);
            if (!modelParent(modelFacts, parentModel, new HashSet<>())) throw invalid("Unrelated generated Builder parent " + parent);
            collect(parent, visited, entries, rejections);
        }
    }

    private void validateEntry(Entry entry, ClassFacts contract) throws IOException {
        for (String descriptor : entry.descriptors()) {
            ClassFacts.Method method = contract.methods.get(descriptor);
            if (method == null || !method.publicInstance() || !method.name().equals(entry.operation()))
                throw invalid("Missing selected public contract " + contract.name + "#" + descriptor + " for " + entry.source());
            if (entry.role() == Role.OPERATION && (entry.descriptors().size() != 1
                    || !method.parameters().equals(List.of(entry.payload().type()))))
                throw invalid("Operation lacks exact single-payload contract: " + entry.source());
        }
        if (entry.role() == Role.PROPERTY || entry.role() == Role.OPERATION) {
            List<String> parameters;
            if (entry.role() == Role.PROPERTY && entry.payload().kind() == Kind.LIST)
                parameters = List.of(entry.payload().item().type());
            else if (entry.role() == Role.PROPERTY && entry.payload().kind() == Kind.MAP)
                parameters = List.of("java.lang.String", entry.payload().item().type());
            else parameters = List.of(entry.payload().type());
            long selected = entry.descriptors().stream().map(contract.methods::get)
                    .filter(method -> method.parameters().equals(parameters)).count();
            if (selected != 1) throw invalid("Missing exact payload/population signature for " + entry.source());
        }
        if (entry.fixedKey() != null && !keys(facts(entry.payload().type())).containsValue("java.lang.String"))
            throw invalid("Fixed key requires keyed OWNED_OBJECT: " + entry.source());
        if (entry.role() == Role.OWNED_OBJECT || entry.role() == Role.KEYED_OWNED_MAP) {
            List<ClassFacts.Method> selected = entry.descriptors().stream().map(contract.methods::get).toList();
            boolean matches = entry.role() == Role.OWNED_OBJECT
                    ? selected.stream().anyMatch(method -> method.result().equals(entry.payload().builder()))
                    : selected.stream().anyMatch(method -> method.parameters().equals(List.of(entry.payload().builder() + "[]")));
            if (!matches) throw invalid("Relationship payload does not match selected public contract: " + entry.source());
            if (entry.role() == Role.KEYED_OWNED_MAP
                    && !"java.lang.String".equals(keys(facts(entry.payload().type())).get(entry.excludedKey())))
                throw invalid("Keyed relationship does not match child key declaration: " + entry.source());
        }
    }

    private Map<String, String> keys(ClassFacts model) throws IOException {
        Map<String, String> keys = new TreeMap<>(model.keys);
        if (model.parent != null && !model.parent.equals("java.lang.Object")) keys.putAll(keys(facts(model.parent)));
        return keys;
    }

    private boolean modelParent(ClassFacts child, String parent, Set<String> visited) throws IOException {
        if (!visited.add(child.name)) return false;
        if (child.name.equals(parent)) return true;
        List<String> ancestors = new ArrayList<>(child.interfaces);
        if (child.parent != null && !child.parent.equals("java.lang.Object")) ancestors.add(child.parent);
        for (String ancestor : ancestors) {
            if (ancestor.equals(parent)) return true;
            // Framework and Groovy interfaces do not contribute Schema parent facts.
            if (!ancestor.startsWith("java.") && !ancestor.startsWith("groovy.")
                    && !ancestor.startsWith("com.blackbuild.klum.ast.runtime.") && modelParent(facts(ancestor), parent, visited)) return true;
        }
        return false;
    }

    private Map<String, Object> payload(Payload payload) throws IOException {
        return switch (payload.kind()) {
            case SCALAR -> scalar(payload.type());
            case ENUM -> enumeration(payload.type());
            case LIST -> new TreeMap<>(Map.of("type", "array", "items", payload(payload.item())));
            case MAP -> new TreeMap<>(Map.of("type", "object", "additionalProperties", payload(payload.item())));
            case NESTED_OBJECT -> {
                definition(payload.builder());
                yield new TreeMap<>(Map.of("$ref", pointer(payload.builder())));
            }
        };
    }

    private Map<String, Object> enumeration(String type) throws IOException {
        ClassFacts enumeration = facts(type);
        if ((enumeration.access & Opcodes.ACC_ENUM) == 0) throw invalid("Not an enum: " + type);
        if (enumeration.enumConstants.isEmpty()) throw invalid("Unprojectable empty enum " + type);
        return new TreeMap<>(Map.of("type", "string", "enum", enumeration.enumConstants));
    }

    private static Map<String, Object> scalar(String type) {
        return switch (type) {
            case "java.lang.String" -> new TreeMap<>(Map.of("type", "string"));
            case "boolean", "java.lang.Boolean" -> new TreeMap<>(Map.of("type", "boolean"));
            case "byte", "java.lang.Byte" -> integer(Byte.MIN_VALUE, Byte.MAX_VALUE);
            case "short", "java.lang.Short" -> integer(Short.MIN_VALUE, Short.MAX_VALUE);
            case "int", "java.lang.Integer" -> integer(Integer.MIN_VALUE, Integer.MAX_VALUE);
            case "long", "java.lang.Long" -> integer(Long.MIN_VALUE, Long.MAX_VALUE);
            case "java.math.BigInteger" -> new TreeMap<>(Map.of("type", "integer"));
            case "float", "java.lang.Float", "double", "java.lang.Double", "java.math.BigDecimal" -> new TreeMap<>(Map.of("type", "number"));
            default -> throw invalid("Unprojectable admitted scalar " + type);
        };
    }

    private static Map<String, Object> integer(long minimum, long maximum) {
        return new TreeMap<>(Map.of("type", "integer", "minimum", minimum, "maximum", maximum));
    }

    static String pointer(String builder) {
        String token = builder.replace("~", "~0").replace("/", "~1");
        return "#/$defs/" + URLEncoder.encode(token, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
