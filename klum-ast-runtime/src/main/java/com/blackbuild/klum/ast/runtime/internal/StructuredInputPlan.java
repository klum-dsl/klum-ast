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

import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.KlumModelException;
import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.FieldVisitor;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Reads only the compiler's private WIRE-PLAN/B contract; never discovers admission by reflection. */
final class StructuredInputPlan {
    private static final String BUILDER_SUFFIX = "_DSL$Builder";
    private static final Set<Class<?>> SCALARS = Set.of(String.class, boolean.class, Boolean.class,
            byte.class, Byte.class, short.class, Short.class, int.class, Integer.class, long.class, Long.class,
            float.class, Float.class, double.class, Double.class, BigInteger.class, BigDecimal.class);
    private static final Set<String> FIELD_COLLECTIONS = Set.of("java.util.List", "java.util.Set", "java.util.SortedSet",
            "java.util.NavigableSet", "java.util.EnumSet");
    private static final Set<String> FIELD_MAPS = Set.of("java.util.Map", "java.util.SortedMap", "java.util.NavigableMap");
    private final Class<?> contract;
    private final Map<String, Entry> entries;
    private final List<Rejection> rejections;

    private StructuredInputPlan(Class<?> contract, Map<String, Entry> entries, List<Rejection> rejections) {
        this.contract = contract;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.rejections = List.copyOf(rejections);
    }

    static StructuredInputPlan read(Class<?> model) {
        try {
            Class<?> contract = type(model, model.getName() + BUILDER_SUFFIX);
            if (!contract.isInterface() || !KlumBuilder.class.isAssignableFrom(contract))
                throw invalid("Not a generated public Builder contract: " + contract.getName());
            List<Entry> facts = new ArrayList<>();
            List<Rejection> rejections = new ArrayList<>();
            collect(model, contract, new LinkedHashSet<>(), facts, rejections);
            Map<String, Entry> sources = new LinkedHashMap<>();
            for (Entry entry : facts) {
                Entry previous = sources.putIfAbsent(entry.source(), entry);
                if (previous != null && !previous.equals(entry))
                    throw invalid("Conflicting carrier source " + entry.source());
            }
            Set<String> rejected = new LinkedHashSet<>();
            rejections.forEach(rejection -> rejected.add(rejection.source()));
            Map<String, List<Entry>> groups = new LinkedHashMap<>();
            sources.values().stream().filter(entry -> !rejected.contains(entry.source()))
                    .forEach(entry -> groups.computeIfAbsent(entry.externalName(), ignored -> new ArrayList<>()).add(entry));
            Map<String, Entry> entries = new LinkedHashMap<>();
            groups.forEach((name, group) -> {
                if (group.size() == 1) entries.put(name, group.get(0));
                else {
                    String declarations = String.join(", ", group.stream().map(Entry::source).sorted().toList());
                    group.forEach(entry -> rejections.add(new Rejection(entry.source(),
                            "external-name collision for '" + name + "': " + declarations)));
                }
            });
            return new StructuredInputPlan(contract, entries, rejections);
        } catch (ReflectiveOperationException | IOException | RuntimeException exception) {
            throw new KlumModelException("Cannot read structured-input carrier for " + model.getName()
                    + "; recompile and package the Schema with its generated WIRE-PLAN/B carrier: "
                    + exception.getMessage(), exception);
        }
    }

    Class<?> contract() { return contract; }
    Map<String, Entry> entries() { return entries; }
    List<Rejection> rejections() { return rejections; }

    private static void collect(Class<?> model, Class<?> contract, Set<Class<?>> visited,
                                List<Entry> entries, List<Rejection> rejections)
            throws ReflectiveOperationException, IOException {
        if (!visited.add(contract)) return;
        String[] lines = carrierLines(model, contract);
        for (int i = 2; i < lines.length - 1; i++) {
            String[] fields = lines[i].split("\\|", -1);
            if (fields.length == 3 && fields[0].equals("rejection")) {
                rejections.add(new Rejection(required(fields[1]), required(fields[2])));
            } else if (fields.length == 12 && fields[0].equals("entry")) {
                entries.add(readEntry(model, contract, fields));
            } else throw invalid("Malformed carrier record " + i);
        }
        for (Class<?> parent : contract.getInterfaces()) {
            if (!parent.getName().endsWith(BUILDER_SUFFIX)) continue;
            String parentModel = parent.getName().substring(0, parent.getName().length() - BUILDER_SUFFIX.length());
            Class<?> parentType = type(model, parentModel);
            if (!parentType.isAssignableFrom(model)) throw invalid("Unrelated generated Builder parent");
            collect(parentType, parent, visited, entries, rejections);
        }
    }

    private static String[] carrierLines(Class<?> model, Class<?> contract) throws IOException {
        String[] lines = carrier(model).split("\n", -1);
        if (lines.length < 3 || !lines[0].equals("WIRE-PLAN/B") || !lines[lines.length - 1].isEmpty())
            throw invalid("Malformed WIRE-PLAN/B header or terminator");
        String[] identity = lines[1].split("\\|", -1);
        if (identity.length != 3 || !identity[0].equals("model")
                || !model.getName().equals(token(identity[1])) || !contract.getName().equals(token(identity[2])))
            throw invalid("Carrier Model/Builder identity mismatch");
        return lines;
    }

    private static Entry readEntry(Class<?> model, Class<?> contract, String[] fields)
            throws ReflectiveOperationException {
        Class<?> declaring = type(model, required(fields[4]));
        String source = entrySource(contract, declaring, fields[3]);
        String name = required(fields[5]);
        List<Method> methods = methods(model, declaring, name, required(fields[6]));
        if (!Set.of("IDENTITY", "JSON_PROPERTY").contains(fields[7])) throw invalid("Invalid name origin");
        PayloadParser parser = new PayloadParser(model, fields[10]);
        Payload payload = parser.parse();
        if (parser.position != fields[10].length()) throw invalid("Trailing payload data");
        Role role = Role.valueOf(fields[2]);
        validateRole(role, methods, payload);
        String keyType = token(fields[8]);
        String excludedKey = token(fields[9]);
        if (role == Role.KEYED_OWNED_MAP && (!"java.lang.String".equals(keyType) || excludedKey == null))
            throw invalid("Keyed relationship has no String key declaration");
        String fixedKey = token(fields[11]);
        if (fixedKey != null && (fixedKey.isEmpty() || role != Role.OWNED_OBJECT || !DslHelper.isKeyed(payload.type())))
            throw invalid("Fixed direct-child key requires a keyed OWNED_OBJECT: " + source);
        return new Entry(required(fields[1]), role, source, declaring, name,
                List.copyOf(methods), payload, keyType, excludedKey, fixedKey);
    }

    private static String entrySource(Class<?> contract, Class<?> declaring, String encodedSource) {
        if (!declaring.isAssignableFrom(contract) || !declaring.getName().endsWith(BUILDER_SUFFIX))
            throw invalid("Entry declares an unrelated Builder contract");
        String source = required(encodedSource);
        String sourceOwner = declaring.getName().substring(0, declaring.getName().length() - BUILDER_SUFFIX.length());
        if (!source.startsWith(sourceOwner + "#") || source.length() == sourceOwner.length() + 1)
            throw invalid("Entry source does not belong to its declared Builder contract");
        return source;
    }

    private static void validateRole(Role role, List<Method> methods, Payload payload) {
        validatePayload(payload, role == Role.PROPERTY);
        if ((role == Role.OWNED_OBJECT || role == Role.KEYED_OWNED_MAP) != (payload.kind() == Kind.NESTED_OBJECT))
            throw invalid("Relationship role/payload mismatch");
        if (role == Role.OPERATION && (methods.size() != 1 || methods.get(0).getParameterCount() != 1
                || methods.get(0).getParameterTypes()[0] != payload.type()))
            throw invalid("Operation does not have its exact single-payload signature");
    }

    private static void validatePayload(Payload payload, boolean field) {
        if (payload.kind() == Kind.SCALAR && !SCALARS.contains(payload.type()))
            throw invalid("Unsupported scalar payload " + payload.type().getName());
        if (payload.kind() == Kind.LIST && !(field ? FIELD_COLLECTIONS.contains(payload.type().getName()) : payload.type() == List.class))
            throw invalid("Unsupported collection payload " + payload.type().getName());
        if (payload.kind() == Kind.MAP && !(field ? FIELD_MAPS.contains(payload.type().getName()) : payload.type() == Map.class))
            throw invalid("Unsupported map payload " + payload.type().getName());
        if (payload.item() != null) validatePayload(payload.item(), false);
    }

    private static List<Method> methods(Class<?> model, Class<?> contract, String name, String descriptors)
            throws ReflectiveOperationException {
        List<Method> result = new ArrayList<>();
        for (String descriptor : descriptors.split(",(?=" + Pattern.quote(name) + "\\()", -1)) {
            int end = descriptor.indexOf("):");
            if (!descriptor.startsWith(name + "(") || end < name.length() + 1) throw invalid("Malformed method descriptor");
            String parameters = descriptor.substring(name.length() + 1, end);
            String[] names = parameters.isEmpty() ? new String[0] : parameters.split(", ", -1);
            Class<?>[] types = new Class<?>[names.length];
            for (int i = 0; i < names.length; i++) types[i] = type(model, names[i]);
            Method method = contract.getMethod(name, types);
            if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                    || method.getReturnType() != type(model, descriptor.substring(end + 2)))
                throw invalid("Descriptor is absent from the public Builder contract: " + descriptor);
            if (result.contains(method)) throw invalid("Duplicate descriptor " + descriptor);
            result.add(method);
        }
        return result;
    }

    private static String carrier(Class<?> model) throws IOException {
        String resource = model.getName().replace('.', '/') + "_DSL$WirePlan.class";
        try (InputStream stream = model.getModule().getResourceAsStream(resource)) {
            if (stream == null) throw invalid("Missing packaged carrier " + resource);
            String[] data = new String[1];
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    if (!name.equals(resource.substring(0, resource.length() - 6)) || (access & Opcodes.ACC_PUBLIC) != 0)
                        throw invalid("Unexpected carrier class identity/access");
                }
                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    if (!name.equals("DATA") || !descriptor.equals("Ljava/lang/String;") || !(value instanceof String)
                            || access != (Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL) || data[0] != null)
                        throw invalid("Invalid carrier DATA constant");
                    data[0] = (String) value;
                    return null;
                }
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    throw invalid("Unexpected executable carrier member");
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (data[0] == null) throw invalid("Missing carrier DATA constant");
            return data[0];
        }
    }

    static Class<?> type(Class<?> model, String name) throws ClassNotFoundException {
        if (name.endsWith("[]")) return type(model, name.substring(0, name.length() - 2)).arrayType();
        return switch (name) {
            case "boolean" -> boolean.class;
            case "byte" -> byte.class;
            case "short" -> short.class;
            case "int" -> int.class;
            case "long" -> long.class;
            case "float" -> float.class;
            case "double" -> double.class;
            case "char" -> char.class;
            case "void" -> void.class;
            default -> Class.forName(name, false, model.getClassLoader());
        };
    }

    private static String required(String encoded) {
        String result = token(encoded);
        if (result == null || result.isEmpty()) throw invalid("Missing required carrier token");
        return result;
    }

    private static String token(String encoded) {
        if (encoded.equals("-")) return null;
        String value = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        if (!Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)).equals(encoded))
            throw invalid("Noncanonical carrier token");
        return value;
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    enum Role { PROPERTY, OWNED_OBJECT, KEYED_OWNED_MAP, OPERATION }
    enum Kind { SCALAR, ENUM, LIST, MAP, NESTED_OBJECT }
    record Payload(Kind kind, Class<?> type, Class<?> builder, String keyType, Payload item) { }
    record Entry(String externalName, Role role, String source, Class<?> contract, String operation,
                 List<Method> methods, Payload payload, String keyType, String excludedKey, String fixedChildKey) { }
    record Rejection(String source, String reason) { }

    private static final class PayloadParser {
        private final Class<?> model;
        private final String text;
        private int position;
        private PayloadParser(Class<?> model, String text) { this.model = model; this.text = text; }
        private Payload parse() throws ReflectiveOperationException {
            take('[');
            Kind kind = Kind.valueOf(atom());
            take(',');
            Class<?> type = type(model, required(atom()));
            take(',');
            String builderName = token(atom());
            Class<?> builder = builderName == null ? null : type(model, builderName);
            take(',');
            String key = token(atom());
            Payload item = null;
            if (peek() == ',') { take(','); item = parse(); }
            take(']');
            if ((kind == Kind.LIST || kind == Kind.MAP) != (item != null)) throw invalid("Invalid payload item");
            if (kind == Kind.LIST && !Collection.class.isAssignableFrom(type)) throw invalid("Invalid collection payload");
            if (kind == Kind.MAP && (!Map.class.isAssignableFrom(type) || !Objects.equals(key, "java.lang.String")))
                throw invalid("Invalid data-map payload");
            if (kind == Kind.ENUM && !type.isEnum()) throw invalid("Invalid enum payload");
            if (kind == Kind.NESTED_OBJECT && (builder == null || builder != type(model, type.getName() + BUILDER_SUFFIX)
                    || !KlumBuilder.class.isAssignableFrom(builder) || Modifier.isAbstract(type.getModifiers())))
                throw invalid("Invalid concrete relationship identity");
            return new Payload(kind, type, builder, key, item);
        }
        private String atom() {
            int start = position;
            while (position < text.length() && text.charAt(position) != ',' && text.charAt(position) != ']') position++;
            if (position == start) throw invalid("Missing payload token");
            return text.substring(start, position);
        }
        private char peek() {
            if (position == text.length()) throw invalid("Truncated payload");
            return text.charAt(position);
        }
        private void take(char expected) {
            if (peek() != expected) throw invalid("Malformed payload; expected " + expected);
            position++;
        }
    }
}
