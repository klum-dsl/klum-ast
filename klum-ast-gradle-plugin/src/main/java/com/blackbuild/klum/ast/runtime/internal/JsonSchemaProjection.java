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
import java.util.function.Function;
import java.util.stream.Collectors;

/** Private plugin implementation, loaded beside the facade in one isolated unnamed module. */
final class JsonSchemaProjection implements Function<List<String>, Map<String, String>> {
    private static final String BUILDER = "_DSL$Builder";
    private static final String DIALECT = "https://json-schema.org/draft/2020-12/schema";
    private final Map<String, Object> definitions = new TreeMap<>();
    private final Map<String, String> diagnostics = new TreeMap<>();

    @Override
    public Map<String, String> apply(List<String> roots) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String root : roots.stream().sorted().toList()) {
            definitions.clear();
            diagnostics.clear();
            try {
                Class<?> builder = Class.forName(builderName(root), false, getClass().getClassLoader());
                definition(builder, root);
                Map<String, Object> document = new LinkedHashMap<>();
                document.put("$schema", DIALECT);
                document.put("$ref", reference(builder));
                document.put("$defs", new TreeMap<>(definitions));
                document.put("$comment", "Structural input only; defaults, lifecycle, conversion and completed validation are not evaluated.");
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

    private void definition(Class<?> builder) throws ClassNotFoundException {
        definition(builder, null);
    }

    private void definition(Class<?> builder, String rootModel) throws ClassNotFoundException {
        if (definitions.containsKey(builder.getName())) return;
        Class<?> factory = Class.forName(builder.getName().substring(0, builder.getName().length() - "Builder".length())
                + "Factory", false, builder.getClassLoader());
        CompiledBuilderSchema schema = CompiledBuilderSchema.read(builder, factory);
        if (rootModel != null && !schema.model().getName().equals(rootModel))
            throw new IllegalArgumentException("Resolved Builder contract '" + builder.getName() + "' belongs to DSL Model '"
                    + schema.model().getName() + "', not requested DSL Model '" + rootModel + "'");
        if (Modifier.isAbstract(schema.model().getModifiers()))
            throw new IllegalArgumentException("Abstract root/owned target requires a separately qualified alias policy: " + builder.getName());
        Map<String, Object> object = new LinkedHashMap<>();
        definitions.put(builder.getName(), object); // Reserve before following recursive ownership.
        Map<String, Object> properties = new TreeMap<>();
        object.put("type", "object");
        object.put("properties", properties);
        object.put("additionalProperties", false);
        for (CompiledBuilderSchema.Entry entry : schema.entries().values()) {
            Map<String, Object> value = payload(entry.payload());
            if (entry.role() == CompiledBuilderSchema.Role.KEYED_OWNED_MAP)
                value = map(value);
            if (entry.documentation() != null && !entry.documentation().isBlank())
                value.put("description", entry.documentation());
            properties.put(entry.externalName(), value);
        }
        for (CompiledBuilderSchema.Rejection rejection : schema.rejections())
            diagnostics.put(rejection.source(), rejection.reason());
    }

    private Map<String, Object> payload(CompiledBuilderSchema.Payload payload) throws ClassNotFoundException {
        return switch (payload.kind()) {
            case SCALAR -> scalar(payload.type());
            case ENUM -> enumeration(payload.type());
            case LIST -> new LinkedHashMap<>(Map.of("type", "array", "items", payload(payload.item())));
            case MAP -> map(payload(payload.item()));
            case NESTED_OBJECT -> {
                definition(payload.builder());
                yield new LinkedHashMap<>(Map.of("$ref", reference(payload.builder())));
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
        return new LinkedHashMap<>(Map.of("type", "string", "enum", names));
    }

    private static Map<String, Object> scalar(Class<?> type) {
        Map<String, Object> result = new LinkedHashMap<>();
        String name = type.getName();
        String jsonType = switch (name) {
            case "java.lang.String" -> "string";
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

    private static String reference(Class<?> builder) {
        return "#/$defs/" + URLEncoder.encode(builder.getName().replace("~", "~0").replace("/", "~1"), StandardCharsets.UTF_8).replace("+", "%20");
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
