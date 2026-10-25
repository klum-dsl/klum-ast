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
package com.blackbuild.klum.ast.runtime.internal;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

/** Private plugin implementation, loaded beside the facade in one isolated unnamed module. */
final class JsonSchemaProjection implements BiFunction<List<String>, Boolean, Map<String, String>> {
    private static final String BUILDER = "_DSL$Builder";
    private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";
    private static final String STRING_TYPE = "string";
    private final Map<String, Object> definitions = new TreeMap<>();
    private final Map<String, String> diagnostics = new TreeMap<>();
    private boolean integralConstraints;
    private final Map<Class<?>, CompiledBuilderSchema.ConstraintProfile> constraintProfiles = new LinkedHashMap<>();
    private enum KeyPosition { MEMBER, KEYLESS, UNKNOWN_DIRECT }

    @Override
    public Map<String, String> apply(List<String> roots, Boolean enhanced) {
        integralConstraints = Boolean.TRUE.equals(enhanced);
        Map<String, String> result = new LinkedHashMap<>();
        for (String root : roots.stream().sorted().toList()) {
            definitions.clear();
            constraintProfiles.clear();
            diagnostics.clear();
            try {
                Class<?> builder = Class.forName(builderName(root), false, getClass().getClassLoader());
                String rootDefinition = definition(builder, KeyPosition.MEMBER, root, root);
                Map<String, Object> document = new LinkedHashMap<>();
                document.put("$schema", DIALECT);
                document.put("$ref", reference(rootDefinition));
                document.put("$defs", new TreeMap<>(definitions));
                if (integralConstraints) document.put("description", "Opt-in raw-input authoring bounds for supplied direct integral properties; "
                        + "schema validity does not guarantee construction or completed Model validation.");
                document.put("$comment", (integralConstraints ? "Raw-input authoring bounds only; " : "Structural input only; ")
                        + "defaults, lifecycle, conversion and completed validation are not evaluated.");
                document.put("x-klum-unsupported", new TreeMap<>(diagnostics));
                result.put(root + ".schema.json", json(document) + "\n");
            } catch (ClassNotFoundException | LinkageError | RuntimeException failure) {
                throw new IllegalArgumentException("Cannot generate JSON Schema for root '" + root
                        + "'; supply the complete resolved Schema/API/runtime/annotation classpath and recompile with generated input enrichment: "
                        + failure.getMessage(), failure);
            }
        }
        return result;
    }

    private static String builderName(String root) {
        if (root == null || root.isBlank() || Arrays.stream(root.split("\\.", -1)).anyMatch(part -> part.isEmpty()
                || !Character.isJavaIdentifierStart(part.codePointAt(0))
                || part.codePoints().anyMatch(value -> !Character.isJavaIdentifierPart(value))))
            throw new IllegalArgumentException("Expected a fully qualified DSL Model binary name: " + root);
        if (root.endsWith(BUILDER))
            throw new IllegalArgumentException("Generated Builder names are not rootTypes values; use DSL Model name '"
                    + root.substring(0, root.length() - BUILDER.length()) + "' instead of '" + root + "'");
        return root + BUILDER;
    }

    private String definition(Class<?> builder, KeyPosition position, String source, String rootModel) throws ClassNotFoundException {
        Class<?> factory = Class.forName(builder.getName().substring(0, builder.getName().length() - "Builder".length())
                + "Factory", false, builder.getClassLoader());
        CompiledBuilderSchema schema = CompiledBuilderSchema.read(builder, factory);
        validateKeyPosition(schema, position, source);
        boolean groupRedefined = integralConstraints && collectConstraintProfiles(builder, schema, source);
        boolean keyless = position == KeyPosition.KEYLESS;
        String identity = builder.getName() + (keyless && !schema.keyName().isEmpty() ? "::keyless" : "");
        if (definitions.containsKey(identity)) return identity;
        if (rootModel != null && !schema.model().getName().equals(rootModel))
            throw new IllegalArgumentException("Resolved Builder contract '" + builder.getName() + "' belongs to DSL Model '"
                    + schema.model().getName() + "', not requested DSL Model '" + rootModel + "'");
        if (Modifier.isAbstract(schema.model().getModifiers()))
            throw new IllegalArgumentException("Abstract root/owned target requires a separately qualified alias policy: " + builder.getName());
        Map<String, Object> object = new LinkedHashMap<>();
        definitions.put(identity, object); // Reserve each position before following recursive ownership.
        Map<String, Object> properties = new TreeMap<>();
        object.put("type", "object");
        object.put("properties", properties);
        object.put("additionalProperties", false);
        if (!keyless && schema.key() != null) {
            properties.put(schema.key().externalName(), new LinkedHashMap<>(Map.of("type", STRING_TYPE)));
            object.put("required", List.of(schema.key().externalName()));
        }
        for (CompiledBuilderSchema.Entry entry : schema.entries().values()) {
            Map<String, Object> value = payload(entry.payload(), keyPosition(entry), entry.source());
            if (entry.role() == CompiledBuilderSchema.Role.KEYED_OWNED_MAP)
                value = map(value);
            if (entry.documentation() != null && !entry.documentation().isBlank())
                value.put("description", entry.documentation());
            if (integralConstraints) constrain(entry, value, groupRedefined);
            properties.put(entry.externalName(), value);
        }
        for (CompiledBuilderSchema.Rejection rejection : schema.rejections())
            diagnostics.put(rejection.source(), rejection.reason());
        return identity;
    }

    private boolean collectConstraintProfiles(Class<?> builder, CompiledBuilderSchema schema, String source) {
        CompiledBuilderSchema.ConstraintProfile profile;
        try {
            profile = constraintProfiles.computeIfAbsent(builder, CompiledBuilderSchema::constraintProfile);
        } catch (RuntimeException failure) {
            String affected = schema.entries().values().stream().filter(entry -> entry.contract() == builder)
                    .map(entry -> entry.source() + " (external input '" + entry.externalName() + "')")
                    .collect(Collectors.joining(", "));
            throw new IllegalArgumentException("Enhanced constraint metadata at input use '" + source + "', admitted sources ["
                    + affected + "]: " + failure.getMessage(), failure);
        }
        boolean groupRedefined = profile.groupRedefined();
        for (Class<?> parent : builder.getInterfaces()) {
            if (parent.getName().endsWith(BUILDER)) groupRedefined |= collectConstraintProfiles(parent, schema, source);
        }
        return groupRedefined;
    }

    private void constrain(CompiledBuilderSchema.Entry entry, Map<String, Object> value, boolean groupRedefined) {
        if (entry.role() == CompiledBuilderSchema.Role.OPERATION) return;
        CompiledBuilderSchema.ConstraintProfile profile = constraintProfiles.get(entry.contract());
        String field = entry.source().substring(entry.source().lastIndexOf('#') + 1);
        List<CompiledBuilderSchema.ConstraintRule> rules = profile.fields().get(field);
        if (rules == null) throw new IllegalArgumentException("Missing constraint source facts for '" + entry.source()
                + "', external input '" + entry.externalName() + "', public Builder '" + entry.contract().getName()
                + "'; recompile and package the Schema with matching KlumAST versions");
        for (CompiledBuilderSchema.ConstraintRule rule : rules) {
            if (rule.plain() && !groupRedefined && entry.role() == CompiledBuilderSchema.Role.PROPERTY
                    && entry.payload().kind() == CompiledBuilderSchema.Kind.SCALAR && "integer".equals(value.get("type"))) {
                String keyword = rule.annotation().endsWith(".Min") ? "minimum" : "maximum";
                BigInteger bound = new BigInteger(rule.value());
                // The inert encoding is a Java-long Bean annotation bound, even for BigInteger properties.
                bound.longValueExact();
                BigInteger previous = (BigInteger) value.get(keyword);
                value.put(keyword, previous == null ? bound : keyword.equals("minimum") ? previous.max(bound) : previous.min(bound));
            } else {
                String reason = groupRedefined ? "group redefinition/sequence" : "outside the direct integral, empty groups/payload profile";
                appendDescription(value, "Runtime constraint (not enforced by this schema; " + reason + "): " + rule.declaration());
            }
        }
        if (value.get("minimum") instanceof BigInteger minimum && value.get("maximum") instanceof BigInteger maximum
                && minimum.compareTo(maximum) > 0)
            throw new IllegalArgumentException("Empty integral constraint intersection for '" + entry.source() + "', external input '"
                    + entry.externalName() + "', public Builder '" + entry.contract().getName() + "': " + rules
                    + "; minimum " + minimum + " exceeds maximum " + maximum);
        if (!rules.isEmpty()) value.put("x-klum-constraint-source", entry.source());
    }

    private static void appendDescription(Map<String, Object> value, String description) {
        Object existing = value.get("description");
        value.put("description", existing == null || existing.toString().isBlank() ? description : existing + "\n" + description);
    }

    private static KeyPosition keyPosition(CompiledBuilderSchema.Entry entry) {
        if (entry.role() == CompiledBuilderSchema.Role.KEYED_OWNED_MAP
                || Boolean.TRUE.equals(entry.schemaKey()) || entry.fixedChildKey() != null)
            return KeyPosition.KEYLESS;
        if (entry.role() == CompiledBuilderSchema.Role.OWNED_OBJECT && entry.schemaKey() == null)
            return KeyPosition.UNKNOWN_DIRECT;
        return KeyPosition.MEMBER;
    }

    private static void validateKeyPosition(CompiledBuilderSchema schema, KeyPosition position, String source) {
        if (schema.keyName().isEmpty()) return;
        if (position == KeyPosition.UNKNOWN_DIRECT)
            throw unrepresentable(schema, position, source, "missing direct relationship key-authority metadata; recompile and package the Schema");
        if (position == KeyPosition.KEYLESS) return;
        CompiledBuilderSchema.Key key = schema.key();
        if (key == null)
            throw unrepresentable(schema, position, source, "missing construction key metadata; recompile and package the Schema");
        if (key.ignored())
            throw unrepresentable(schema, position, source, "required construction key " + key.source() + " excluded by @JsonIgnore(true)");
        List<String> collisions = schema.keyCollisions();
        if (!collisions.isEmpty())
            throw unrepresentable(schema, position, source, "required construction key external-name collision for '"
                    + key.externalName() + "': " + key.source() + ", " + String.join(", ", collisions));
    }

    private static IllegalArgumentException unrepresentable(CompiledBuilderSchema schema, KeyPosition position,
                                                           String source, String reason) {
        return new IllegalArgumentException("Cannot represent admitted input at '" + source + "', public Builder '"
                + schema.contract().getName() + "', key position " + position + ": " + reason);
    }

    private Map<String, Object> payload(CompiledBuilderSchema.Payload payload, KeyPosition position, String source) throws ClassNotFoundException {
        return switch (payload.kind()) {
            case SCALAR -> scalar(payload.type());
            case ENUM -> enumeration(payload.type());
            case LIST -> new LinkedHashMap<>(Map.of("type", "array", "items", payload(payload.item(), KeyPosition.MEMBER, source)));
            case MAP -> map(payload(payload.item(), KeyPosition.MEMBER, source));
            case NESTED_OBJECT -> {
                String identity = definition(payload.builder(), position, source, null);
                yield new LinkedHashMap<>(Map.of("$ref", reference(identity)));
            }
        };
    }

    private static Map<String, Object> map(Map<String, Object> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "object");
        result.put("additionalProperties", values);
        return result;
    }

    private static Map<String, Object> enumeration(Class<?> type) {
        List<String> names = Arrays.stream(type.getDeclaredFields()).filter(Field::isEnumConstant)
                .map(Field::getName).sorted().toList();
        if (names.isEmpty()) throw new IllegalArgumentException("Empty enum has no representable input values: " + type.getName());
        return new LinkedHashMap<>(Map.of("type", STRING_TYPE, "enum", names));
    }

    private static Map<String, Object> scalar(Class<?> type) {
        Map<String, Object> result = new LinkedHashMap<>();
        String name = type.getName();
        String jsonType = switch (name) {
            case "java.lang.String" -> STRING_TYPE;
            case "boolean", "java.lang.Boolean" -> "boolean";
            case "float", "double", "java.lang.Float", "java.lang.Double", "java.math.BigDecimal" -> "number";
            default -> "integer";
        };
        result.put("type", jsonType);
        int bits = switch (name) {
            case "byte", "java.lang.Byte" -> 8;
            case "short", "java.lang.Short" -> 16;
            case "int", "java.lang.Integer" -> 32;
            case "long", "java.lang.Long" -> 64;
            default -> 0;
        };
        if (bits != 0) {
            BigInteger limit = BigInteger.ONE.shiftLeft(bits - 1);
            result.put("minimum", limit.negate());
            result.put("maximum", limit.subtract(BigInteger.ONE));
        }
        return result;
    }

    private static String reference(String identity) {
        return "#/$defs/" + URLEncoder.encode(identity.replace("~", "~0").replace("/", "~1"), StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Deterministic JSON with exact integer values and no rendering dependency in runtime or the plugin. */
    private static String json(Object value) {
        if (value instanceof String string) return quote(string);
        if (value instanceof Map<?, ?> map)
            return map.entrySet().stream().sorted((a, b) -> a.getKey().toString().compareTo(b.getKey().toString()))
                    .map(entry -> quote(entry.getKey().toString()) + ":" + json(entry.getValue()))
                    .collect(Collectors.joining(",", "{", "}"));
        if (value instanceof List<?> list) return list.stream().map(JsonSchemaProjection::json).collect(Collectors.joining(",", "[", "]"));
        if (value instanceof Boolean || value instanceof BigInteger) return value.toString();
        throw new IllegalArgumentException("Unexpected private JSON value: " + value);
    }

    private static String quote(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toCharArray()) {
            switch (character) {
                case '"' -> result.append("\\\"");
                case '\\' -> result.append("\\\\");
                default -> {
                    if (character < 32 || Character.isSurrogate(character)) result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }
}
