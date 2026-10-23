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

import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.FieldVisitor;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Opcodes;
import groovyjarjarasm.asm.Type;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/** Disposable SCHEMA-T byte-only projection, deliberately limited to the selected fixture. */
public final class SchemaTracerProjector {
    private final List<JarFile> artifacts;
    private final Map<String, Object> definitions = new TreeMap<>();
    private final List<Object> coverage = new ArrayList<>();
    private final List<Object> rejections = new ArrayList<>();

    private SchemaTracerProjector(List<JarFile> artifacts) { this.artifacts = artifacts; }

    public static void main(String[] args) throws IOException {
        // Artifact JARs are data inputs, never entries on this JVM's classpath.
        try (JarFile api = new JarFile(args[1]); JarFile schema = new JarFile(args[2])) {
            SchemaTracerProjector projector = new SchemaTracerProjector(List.of(api, schema));
            String builder = args[0] + "_DSL$Builder";
            projector.definition(builder);
            projector.requirePublicInterface(args[0] + "_DSL$Factory");
            Map<String, Object> document = new LinkedHashMap<>();
            document.put("$schema", "https://json-schema.org/draft/2020-12/schema");
            document.put("$comment", "SCHEMA-T bounded structural projection only. Does not guarantee binding, operation execution, "
                    + "lifecycle, materialization or completed validation. Defaults and validation code are not evaluated. "
                    + "Float/double number projection cannot guarantee Java conversion or representability. "
                    + "Optional prose is unavailable in this tracer; no Jackson or full runtime equivalence is claimed.");
            document.put("$ref", pointer(builder));
            document.put("$defs", projector.definitions);
            Files.writeString(Path.of(args[3]), json(document) + "\n");
            Files.writeString(Path.of(args[4]), json(Map.of("root", args[0], "builder", builder,
                    "coverage", projector.coverage, "rejections", projector.rejections)) + "\n");
        }
    }

    private void definition(String builder) throws IOException {
        if (definitions.containsKey(builder)) return;
        Map<String, Object> properties = new TreeMap<>();
        definitions.put(builder, Map.of("type", "object", "properties", properties, "additionalProperties", false));
        collect(builder, properties);
    }

    private void collect(String builder, Map<String, Object> properties) throws IOException {
        ClassReader contract = requirePublicInterface(builder);
        for (String parent : contract.getInterfaces()) {
            if (parent.endsWith("_DSL$Builder")) collect(parent.replace('/', '.'), properties);
        }
        String model = builder.substring(0, builder.length() - "_DSL$Builder".length());
        // Also require the exact Model bytes; never load the Model or annotation types.
        if (!new ClassReader(bytes(model)).getClassName().replace('/', '.').equals(model)) {
            throw new IllegalArgumentException("Model identity mismatch: " + model);
        }
        String[] data = new String[1];
        new ClassReader(bytes(model + "_DSL$WirePlan")).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (name.equals("DATA")) data[0] = (String) value;
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        if (data[0] == null) throw new IllegalArgumentException("Missing current carrier: " + model);
        List<String> lines = data[0].lines().toList();
        if (!lines.get(0).equals("WIRE-PLAN/B")
                || !lines.get(1).equals("model|" + token(model) + "|" + token(builder))) {
            throw new IllegalArgumentException("Unknown carrier identity: " + model);
        }
        for (String line : lines.subList(2, lines.size())) {
            String[] fields = line.split("\\|", -1);
            if (fields[0].equals("rejection")) {
                rejections.add(Map.of("source", decode(fields[1]), "reason", decode(fields[2])));
            } else if (fields[0].equals("entry") && fields.length == 12) {
                entry(fields, properties);
            } else {
                throw new IllegalArgumentException("Unknown current carrier record: " + line);
            }
        }
    }

    private void entry(String[] fields, Map<String, Object> properties) throws IOException {
        String name = decode(fields[1]);
        String role = fields[2];
        String source = decode(fields[3]);
        String owner = decode(fields[4]);
        String operation = decode(fields[5]);
        String descriptors = decode(fields[6]);
        List<String> publicMethods = publicMethods(owner);
        // Current carrier mixes reflected Class[] binary names and generated Builder[] source names.
        for (String descriptor : descriptors.split(",(?=" + Pattern.quote(operation) + "\\()")) {
            if (!publicMethods.contains(descriptor.replaceAll("\\[L([^;]+);", "$1[]"))) {
                throw new IllegalArgumentException("Missing selected public contract " + owner + "#" + descriptor);
            }
        }
        if (!List.of("PROPERTY", "OPERATION", "OWNED_OBJECT", "KEYED_OWNED_MAP").contains(role)) {
            throw new IllegalArgumentException("Unknown role " + role + " at " + source);
        }
        Map<String, Object> shape = payload(fields[10]);
        if (role.equals("KEYED_OWNED_MAP")) shape = Map.of("type", "object", "additionalProperties", shape);
        if (properties.putIfAbsent(name, shape) != null) {
            throw new IllegalArgumentException("Outside tracer: inherited collision for " + source);
        }
        Map<String, Object> fact = new LinkedHashMap<>();
        fact.put("source", source);
        fact.put("name", name);
        fact.put("role", role);
        fact.put("declaringBuilder", owner);
        fact.put("descriptors", descriptors);
        fact.put("nameOrigin", fields[7]);
        fact.put("excludedChildKey", decode(fields[9]));
        fact.put("fixedChildKey", decode(fields[11]));
        coverage.add(fact);
    }

    private Map<String, Object> payload(String text) throws IOException {
        // The selected fixture uses scalar/enum, nested objects and one ordinary Map payload.
        String[] parts = text.substring(1, text.length() - 1).split(",", 5);
        String kind = parts[0];
        String type = decode(parts[1]);
        switch (kind) {
            case "SCALAR":
                return switch (type) {
                    case "java.lang.String" -> Map.of("type", "string");
                    case "boolean" -> Map.of("type", "boolean");
                    case "int" -> Map.of("type", "integer", "minimum", Integer.MIN_VALUE, "maximum", Integer.MAX_VALUE);
                    case "double" -> Map.of("type", "number");
                    default -> throw new IllegalArgumentException("Unprojectable admitted scalar " + type);
                };
            case "ENUM":
                List<String> constants = new ArrayList<>();
                new ClassReader(bytes(type)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        if ((access & Opcodes.ACC_ENUM) != 0) constants.add(name);
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return Map.of("type", "string", "enum", constants);
            case "MAP":
                if (!type.equals("java.util.Map") || !decode(parts[3]).equals("java.lang.String")) {
                    throw new IllegalArgumentException("Outside tracer map target " + type);
                }
                return Map.of("type", "object", "additionalProperties", payload(parts[4]));
            case "NESTED_OBJECT":
                String child = decode(parts[2]);
                if (!child.equals(type + "_DSL$Builder")) throw new IllegalArgumentException("Nested identity mismatch");
                definition(child);
                return Map.of("$ref", pointer(child));
            default:
                throw new IllegalArgumentException("Unprojectable admitted payload " + text);
        }
    }

    private List<String> publicMethods(String builder) throws IOException {
        List<String> methods = new ArrayList<>();
        requirePublicInterface(builder).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if ((access & Opcodes.ACC_PUBLIC) != 0) {
                    List<String> parameters = Arrays.stream(Type.getArgumentTypes(descriptor)).map(Type::getClassName).toList();
                    methods.add(name + "(" + String.join(", ", parameters) + "):" + Type.getReturnType(descriptor).getClassName());
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return methods;
    }

    private ClassReader requirePublicInterface(String binary) throws IOException {
        ClassReader reader = new ClassReader(bytes(binary));
        if ((reader.getAccess() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE)) != (Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE)) {
            throw new IllegalArgumentException("Missing public generated interface " + binary);
        }
        return reader;
    }

    private byte[] bytes(String binary) throws IOException {
        String path = binary.replace('.', '/') + ".class";
        for (JarFile artifact : artifacts) {
            var entry = artifact.getJarEntry(path);
            if (entry != null) {
                try (var stream = artifact.getInputStream(entry)) { return stream.readAllBytes(); }
            }
        }
        throw new IllegalArgumentException("Missing binary " + binary + "; recompile/package the selected fixture");
    }

    private static String pointer(String builder) {
        return "#/$defs/" + builder.replace("~", "~0").replace("/", "~1").replace("$", "%24");
    }

    private static String decode(String token) {
        return token.equals("-") ? null : new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
    }

    private static String token(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Minimal JSON writer for this disposable test harness, with no additional library dependency. */
    public static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> map) {
            List<String> fields = new ArrayList<>();
            map.forEach((key, item) -> fields.add(json(key.toString()) + ":" + json(item)));
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof Collection<?> items) return "[" + String.join(",", items.stream().map(SchemaTracerProjector::json).toList()) + "]";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        StringBuilder result = new StringBuilder("\"");
        for (char character : value.toString().toCharArray()) {
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                default -> {
                    if (character < 32) result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }
}
