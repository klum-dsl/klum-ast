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

import com.blackbuild.klum.ast.runtime.KlumModelException;
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput;
import groovyjarjarasm.asm.AnnotationVisitor;
import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static groovyjarjarasm.asm.Type.getMethodDescriptor;

/** Internal compiled-contract seam. Never inspects Model implementations or reads the retired carrier. */
final class CompiledBuilderSchema {
    static final String DEFAULT_STATUS = "UNAVAILABLE_WITHOUT_EXECUTION";
    private static final String BUILDER_SUFFIX = "_DSL$Builder";
    private static final String IGNORED_INPUT = "excluded by @JsonIgnore(true)";
    private static final String NUMERIC = "NUMERIC";
    private static final String CONTAINER = "CONTAINER";
    private static final String PREFIX = "Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$";
    private static final String SCHEMA = PREFIX + "Schema;";
    private static final String FIELD = PREFIX + "Field;";
    private static final String SELECTION = PREFIX + "Selection;";
    private static final String EXCLUSION = PREFIX + "Exclusion;";
    private static final String OPERATION = "Lcom/blackbuild/klum/ast/Builder$Method;";
    private static final String QUERY = "Lcom/blackbuild/klum/ast/Builder$Query;";
    private static final String DOC = "Lcom/blackbuild/annodocimal/annotations/AnnoDoc;";
    private static final String IGNORE = "Lcom/fasterxml/jackson/annotation/JsonIgnore;";
    private static final String PROPERTY = "Lcom/fasterxml/jackson/annotation/JsonProperty;";
    private static final Set<String> PROTOCOL = Set.of(SCHEMA, FIELD, SELECTION, EXCLUSION, OPERATION, QUERY, DOC, IGNORE, PROPERTY);
    private static final Set<String> NUMBERS = Set.of("byte", "short", "int", "long", "float", "double",
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float",
            "java.lang.Double", "java.math.BigInteger", "java.math.BigDecimal");
    private static final Set<String> COLLECTIONS = Set.of("java.util.List", "java.util.Set", "java.util.SortedSet", "java.util.NavigableSet", "java.util.EnumSet");
    private static final Set<String> MAPS = Set.of("java.util.Map", "java.util.SortedMap", "java.util.NavigableMap");

    private final Class<?> contract;
    private final Class<?> model;
    private final Map<String, Entry> entries;
    private final List<Rejection> rejections;
    private final List<Declaration> declarations;

    enum Role { PROPERTY, OWNED_OBJECT, KEYED_OWNED_MAP, OPERATION }
    enum Kind { SCALAR, ENUM, LIST, MAP, NESTED_OBJECT }
    record Payload(Kind kind, Class<?> type, Class<?> builder, String keyType, Payload item) { }
    record Entry(String externalName, Role role, String source, Class<?> contract, String operation,
                 List<Method> methods, Payload payload, String keyType, String excludedKey, String fixedChildKey,
                 String nameOrigin, String documentation) { }
    record Rejection(String source, String reason) { }
    record Declaration(String owner, String name, List<String> parameters, String returns, String documentation) { }

    private CompiledBuilderSchema(Class<?> contract, Class<?> model, Map<String, Entry> entries,
                                  List<Rejection> rejections, List<Declaration> declarations) {
        this.contract = contract;
        this.model = model;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.rejections = List.copyOf(rejections);
        this.declarations = List.copyOf(declarations);
    }

    static CompiledBuilderSchema read(Class<?> model) {
        try {
            ClassLoader loader = model.getClassLoader();
            CompiledBuilderSchema schema = read(Class.forName(model.getName() + BUILDER_SUFFIX, false, loader),
                    Class.forName(model.getName() + "_DSL$Factory", false, loader));
            if (schema.model != model) throw invalid("Root Model identity disagrees with Builder self bound");
            return schema;
        } catch (ClassNotFoundException | LinkageError failure) { throw failure(model.getName(), failure); }
    }

    static CompiledBuilderSchema read(Class<?> builder, Class<?> factory) {
        try {
            requirePublic(builder);
            requirePublic(factory);
            Class<?> model = modelOf(builder);
            if (model == null || Arrays.stream(factory.getMethods()).noneMatch(method -> method.getName().equals("One") && method.getReturnType() == model))
                throw invalid("Builder self bound and Factory result disagree");
            Facts facts = collect(builder, new LinkedHashMap<>());
            List<Rejection> rejected = new ArrayList<>(facts.rejections());
            List<Declaration> declarations = new ArrayList<>(facts.declarations());
            declarations.addAll(scope(factory));
            Map<String, Entry> entries = new LinkedHashMap<>();
            facts.entries().forEach(entry -> entries.put(entry.externalName(), entry));
            return new CompiledBuilderSchema(builder, model, entries, rejected.stream().distinct()
                    .sorted(Comparator.comparing(Rejection::source).thenComparing(Rejection::reason)).toList(), declarations);
        } catch (ReflectiveOperationException | LinkageError | IOException | RuntimeException failure) {
            throw failure(builder.getName(), failure);
        }
    }

    /** Resolve each declaring hierarchy before its descendants, preserving previously rejected parent sources. */
    private static Map<String, Entry> admitted(List<Entry> candidates, List<Rejection> rejected) {
        Map<String, Entry> sources = new LinkedHashMap<>();
        for (Entry entry : candidates) {
            Entry previous = sources.putIfAbsent(entry.source(), entry);
            if (previous != null && !previous.equals(entry)) throw invalid("Conflicting input source " + entry.source());
        }
        Set<String> exclusions = rejected.stream().map(Rejection::source).collect(Collectors.toSet());
        Map<String, List<Entry>> names = new LinkedHashMap<>();
        sources.values().stream().filter(entry -> !exclusions.contains(entry.source()))
                .sorted(Comparator.comparing(Entry::externalName).thenComparing(Entry::source))
                .forEach(entry -> names.computeIfAbsent(entry.externalName(), ignored -> new ArrayList<>()).add(entry));
        Map<String, Entry> entries = new LinkedHashMap<>();
        names.forEach((name, group) -> {
            if (group.size() == 1) entries.put(name, group.get(0));
            else {
                String all = group.stream().map(Entry::source).sorted().collect(Collectors.joining(", "));
                group.forEach(entry -> rejected.add(new Rejection(entry.source(), "external-name collision for '" + name + "': " + all)));
            }
        });
        return entries;
    }

    private record Facts(List<Entry> entries, List<Rejection> rejections, List<Declaration> declarations) { }
    private record BuilderContext(Class<?> builder, Class<?> model, Metadata metadata) { }

    Class<?> contract() { return contract; }
    Class<?> model() { return model; }
    Map<String, Entry> entries() { return entries; }
    List<Rejection> rejections() { return rejections; }
    List<Declaration> declarations() { return declarations; }

    static List<Declaration> scope(Class<?> contract) throws IOException, ClassNotFoundException {
        requirePublic(contract);
        Map<Class<?>, Metadata> owners = new LinkedHashMap<>();
        List<Declaration> result = new ArrayList<>();
        for (Method method : Arrays.stream(contract.getMethods()).sorted(Comparator.comparing(CompiledBuilderSchema::descriptor)).toList()) {
            Class<?> owner = method.getDeclaringClass();
            if (!owners.containsKey(owner)) owners.put(owner, metadata(owner));
            result.add(declaration(method, owners.get(owner).methods.get(signature(method))));
        }
        return List.copyOf(result);
    }

    private static Facts collect(Class<?> builder, Map<Class<?>, Facts> known) throws IOException, ClassNotFoundException {
        Facts existing = known.get(builder);
        if (existing != null) return existing;
        List<Entry> entries = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        List<Declaration> declarations = new ArrayList<>();
        requirePublic(builder);
        Class<?> model = modelOf(builder);
        if (model == null) throw invalid("Missing Builder self bound: " + builder.getName());
        Metadata metadata = metadata(builder);
        if (!metadata.schema) throw invalid("Missing generated input enrichment on " + builder.getName());
        for (Invisible exclusion : metadata.invisible)
            rejections.add(new Rejection(model.getName() + "#" + exclusion.member(), exclusion.reason()));
        Map<String, Method> getters = new LinkedHashMap<>();
        Map<String, List<Method>> selections = new LinkedHashMap<>();
        BuilderContext context = new BuilderContext(builder, model, metadata);
        collectDeclarations(context, getters, selections, entries, rejections, declarations);
        for (Map.Entry<String, Method> field : getters.entrySet()) {
            List<Method> methods = selections.remove(field.getKey());
            collectField(context, field.getKey(), field.getValue(),
                    methods == null ? List.of() : methods, entries, rejections);
        }
        if (!selections.isEmpty()) throw invalid("Selections lack field declarations: " + selections.keySet());
        for (Class<?> parent : builder.getInterfaces()) {
            Class<?> parentModel = modelOf(parent);
            if (parentModel == null || parentModel == Object.class || !parent.getName().endsWith(BUILDER_SUFFIX)) continue;
            if (!parentModel.isAssignableFrom(model)) throw invalid("Unrelated Builder parent " + parent.getName());
            Facts inherited = collect(parent, known);
            entries.addAll(inherited.entries());
            rejections.addAll(inherited.rejections());
            declarations.addAll(inherited.declarations());
        }
        Map<String, Entry> resolved = admitted(entries, rejections);
        Facts facts = new Facts(List.copyOf(resolved.values()), List.copyOf(rejections), declarations.stream().distinct().toList());
        known.put(builder, facts);
        return facts;
    }

    private static void collectDeclarations(BuilderContext context,
                                            Map<String, Method> getters, Map<String, List<Method>> selections,
                                            List<Entry> entries, List<Rejection> rejections, List<Declaration> declarations) {
        Class<?> builder = context.builder();
        Metadata metadata = context.metadata();
        for (Method method : Arrays.stream(builder.getDeclaredMethods()).sorted(Comparator.comparing(CompiledBuilderSchema::descriptor)).toList()) {
            Literals literal = metadata.methods.get(signature(method));
            if (literal == null) throw invalid("Missing exact public declaration " + method);
            declarations.add(declaration(method, literal));
            if (literal.field != null && (method.getParameterCount() != 0 || getters.putIfAbsent(literal.field, method) != null))
                throw invalid("Duplicate or non-getter field association " + literal.field);
            if (literal.selection != null) selections.computeIfAbsent(literal.selection, ignored -> new ArrayList<>()).add(method);
            if (literal.operation && !literal.query) operation(method, literal, context.model(), entries, rejections);
        }
    }

    private static void collectField(BuilderContext context, String field, Method getter,
                                     List<Method> methods, List<Entry> entries, List<Rejection> rejections)
            throws IOException, ClassNotFoundException {
        Metadata metadata = context.metadata();
        Literals literal = metadata.methods.get(signature(getter));
        String source = context.model().getName() + "#" + field;
        String reason = fieldReason(getter.getGenericReturnType(), literal);
        if (reason != null) {
            rejections.add(new Rejection(source, reason));
            return;
        }
        Role role = selectedRole(metadata, methods, source);
        Payload payload = fieldPayload(getter, role, source);
        String key = childKey(payload, role, literal, source);
        validateSelection(role, payload, methods, source);
        String name = literal.rename.isEmpty() ? field : literal.rename;
        entries.add(new Entry(name, role, source, context.builder(), methods.get(0).getName(), List.copyOf(methods), payload,
                role == Role.KEYED_OWNED_MAP ? String.class.getName() : null, key, literal.fixed ? field : null,
                literal.rename.isEmpty() ? "IDENTITY" : "JSON_PROPERTY", literal.doc));
    }

    private static Role selectedRole(Metadata metadata, List<Method> methods, String source) {
        if (methods.isEmpty()) throw invalid("Missing compiler input/population selection for " + source);
        Set<String> roles = methods.stream().map(method -> metadata.methods.get(signature(method)).role).collect(Collectors.toSet());
        if (roles.size() != 1 || methods.stream().map(Method::getName).distinct().count() != 1)
            throw invalid("Conflicting field selection for " + source);
        Role role = Role.valueOf(roles.iterator().next());
        if (role == Role.OPERATION) throw invalid("Field cannot select authored-operation role");
        return role;
    }

    private static Payload fieldPayload(Method getter, Role role, String source) {
        Payload payload = payload(getter.getGenericReturnType(), true);
        if (role == Role.KEYED_OWNED_MAP) {
            if (payload == null || payload.kind() != Kind.MAP || payload.item().kind() != Kind.NESTED_OBJECT)
                throw invalid("Keyed relationship getter mismatch: " + source);
            payload = payload.item();
        }
        if (payload == null || ((role == Role.OWNED_OBJECT || role == Role.KEYED_OWNED_MAP) != (payload.kind() == Kind.NESTED_OBJECT)))
            throw invalid("Field role/payload mismatch: " + source);
        return payload;
    }

    private static String childKey(Payload payload, Role role, Literals literal, String source)
            throws IOException, ClassNotFoundException {
        if (literal.fixed && (role != Role.OWNED_OBJECT || metadata(payload.builder()).key.isEmpty()))
            throw invalid("Fixed direct-child key requires keyed OWNED_OBJECT: " + source);
        String key = role == Role.KEYED_OWNED_MAP ? metadata(payload.builder()).key : null;
        if (role == Role.KEYED_OWNED_MAP && key.isEmpty()) throw invalid("Keyed relationship has no Key member: " + source);
        return key;
    }

    private static void validateSelection(Role role, Payload payload, List<Method> methods, String source) {
        if (methods.stream().anyMatch(method -> !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())))
            throw invalid("Non-public input selection: " + source);
        if (role != Role.PROPERTY) return;
        if (payload.item() != null && containsNested(payload.item())) throw invalid("Owned input requires a relationship role: " + source);
        Class<?>[] parameters = switch (payload.kind()) {
            case LIST -> new Class<?>[]{payload.item().type()};
            case MAP -> new Class<?>[]{String.class, payload.item().type()};
            default -> new Class<?>[]{payload.type()};
        };
        if (methods.stream().filter(method -> Arrays.equals(method.getParameterTypes(), parameters)).count() != 1)
            throw invalid("Missing exact field payload/population signature: " + source);
    }

    private static boolean containsNested(Payload payload) {
        return payload.kind() == Kind.NESTED_OBJECT || payload.item() != null && containsNested(payload.item());
    }

    private static void operation(Method method, Literals literal, Class<?> model, List<Entry> entries, List<Rejection> rejections) {
        String source = model.getName() + "#" + sourceDescriptor(method);
        String reason = null;
        if (literal.ignored) reason = IGNORED_INPUT;
        else if (method.getParameterCount() != 1) reason = "@Builder.Method must declare exactly one payload";
        Payload payload = reason == null ? payload(method.getGenericParameterTypes()[0], false) : null;
        if (reason == null && payload == null) reason = unsupportedReason(method.getGenericParameterTypes()[0]);
        if (reason != null) { rejections.add(new Rejection(source, reason)); return; }
        String name = literal.rename.isEmpty() ? method.getName() : literal.rename;
        entries.add(new Entry(name, Role.OPERATION, source, method.getDeclaringClass(), method.getName(), List.of(method), payload,
                null, null, null, literal.rename.isEmpty() ? "IDENTITY" : "JSON_PROPERTY", literal.doc));
    }

    private static String fieldReason(Type type, Literals literal) {
        if (literal.ignored) return IGNORED_INPUT;
        if (!literal.exclusion.isEmpty()) {
            String detail = literal.detail;
            if (literal.exclusion.equals("POPULATION")) {
                Class<?> raw = rawType(type);
                Type item = containerValue(type);
                detail += "(" + (raw != null && Map.class.isAssignableFrom(raw) ? "java.lang.String, " : "") + rawType(item).getName() + ")";
            }
            return exclusionReason(literal.exclusion, detail);
        }
        String numeric = unsupportedNumericType(type);
        if (numeric != null) return exclusionReason(NUMERIC, numeric);
        Type item = containerValue(type);
        String container = unsupportedContainerType(item == null ? type : item);
        if (container != null) return exclusionReason(CONTAINER, container);
        return payload(type, true) == null ? "has no admitted scalar, enum, List, or Map payload" : null;
    }

    private static Payload payload(Type type, boolean field) {
        if (type instanceof Class<?> leaf) {
            return leafPayload(leaf);
        }
        if (!(type instanceof ParameterizedType parameterized) || !(parameterized.getRawType() instanceof Class<?> raw)) return null;
        Class<?> model = modelOf(raw);
        if (field && raw.isInterface() && raw.getName().endsWith(BUILDER_SUFFIX) && model != null && !Modifier.isAbstract(model.getModifiers()))
            return new Payload(Kind.NESTED_OBJECT, model, raw, null, null);
        return containerPayload(parameterized, raw, field);
    }

    private static Payload leafPayload(Class<?> leaf) {
        if (leaf.isEnum()) return new Payload(Kind.ENUM, leaf, null, null, null);
        if (leaf == String.class || leaf == Boolean.class || leaf == boolean.class || NUMBERS.contains(leaf.getName()))
            return new Payload(Kind.SCALAR, leaf, null, null, null);
        return null;
    }

    private static Payload containerPayload(ParameterizedType parameterized, Class<?> raw, boolean field) {
        Type[] args = parameterized.getActualTypeArguments();
        boolean list = field ? COLLECTIONS.contains(raw.getName()) : raw == List.class;
        boolean map = field ? MAPS.contains(raw.getName()) : raw == Map.class;
        if ((!list && !map) || args.length != (map ? 2 : 1) || (map && args[0] != String.class)) return null;
        Type itemType = args[map ? 1 : 0];
        Payload item = payload(itemType, false);
        // Owned-map eligibility comes only from compiler selection; no generic Builder collection admission.
        if (item == null && field && map) item = payload(itemType, true);
        if (item == null) return null;
        return new Payload(map ? Kind.MAP : Kind.LIST, raw, null, map ? String.class.getName() : null, item);
    }

    private static Class<?> modelOf(Class<?> builder) {
        Type[] variables = builder.getTypeParameters();
        if (variables.length == 0) return null;
        // The generated self parameter is trailing; source type parameters may have unrelated Class bounds.
        Type variable = variables[variables.length - 1];
        if (!(variable instanceof TypeVariable<?> self)) return null;
        Type bound = self.getBounds()[0];
        return rawType(bound) == Object.class ? null : rawType(bound);
    }

    private static Declaration declaration(Method method, Literals literal) {
        if (literal == null) throw invalid("Missing exact contract method " + method);
        return new Declaration(method.getDeclaringClass().getName(), method.getName(),
                Arrays.stream(method.getGenericParameterTypes()).map(Type::getTypeName).toList(),
                method.getGenericReturnType().getTypeName(), literal.doc);
    }

    /** Source diagnostics historically use JVM array names; this spelling is available directly from Class. */
    private static String sourceDescriptor(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(", "))
                + "):" + method.getReturnType().getName();
    }

    private static String descriptor(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", "))
                + "):" + method.getReturnType().getTypeName();
    }
    private static String signature(Method method) { return method.getName() + getMethodDescriptor(method); }
    private static String exclusionReason(String category, String detail) {
        return switch (category) {
            case "IGNORE" -> IGNORED_INPUT;
            case "NO_PAYLOAD" -> "has no admitted scalar, enum, List, or Map payload";
            case "OWNERSHIP" -> "requires one concrete owned relationship";
            case "KEYED_OWNERSHIP" -> "requires Map<String, concrete keyed DSL Object> ownership";
            case "KEY_MAPPING" -> "literal structured instance keys conflict with @Field.keyMapping-derived keys";
            case NUMERIC -> "unsupported numeric type " + requiredDetail(detail)
                    + "; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input";
            case CONTAINER -> "unsupported whole-container payload target " + requiredDetail(detail)
                    + "; use ordinary List/Map payloads or a supported field's generated element population";
            case "POPULATION" -> "has no unambiguous compiler-generated element population method " + requiredDetail(detail)
                    + "; replacement setters and authored overrides are not input paths";
            case "ABSENT_FIELD" -> "field is absent from the final public Builder contract";
            default -> throw invalid("Unknown input exclusion " + category);
        };
    }

    private static String requiredDetail(String detail) {
        if (detail.isEmpty()) throw invalid("Missing invisible exclusion detail");
        return detail;
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
    private static KlumModelException failure(String root, Throwable cause) {
        return new KlumModelException("Cannot read compiled Builder input contract for " + root
                + "; recompile and package the Schema with generated input enrichment and supply its complete API/runtime classpath: "
                + cause.getMessage(), cause);
    }
    private static void requirePublic(Class<?> contract) throws ClassNotFoundException {
        if (!contract.isInterface() || !Modifier.isPublic(contract.getModifiers())
                || Class.forName(contract.getName(), false, contract.getClassLoader()) != contract)
            throw invalid("Expected exact public contract identity " + contract.getName());
    }

    private static String unsupportedReason(Type type) {
        String numeric = unsupportedNumericType(type);
        if (numeric != null)
            return exclusionReason(NUMERIC, numeric);
        String container = unsupportedContainerType(type);
        if (container != null)
            return exclusionReason(CONTAINER, container);
        return "@Builder.Method payload is outside the admitted grammar";
    }

    private static String unsupportedNumericType(Type type) {
        Class<?> raw = rawType(type);
        if (raw != null && Number.class.isAssignableFrom(raw) && !NUMBERS.contains(raw.getName())) return raw.getName();
        Type nested = containerValue(type);
        return nested == null ? null : unsupportedNumericType(nested);
    }

    private static String unsupportedContainerType(Type type) {
        Class<?> raw = rawType(type);
        if (raw != null && ((Collection.class.isAssignableFrom(raw) && raw != List.class)
                || (Map.class.isAssignableFrom(raw) && raw != Map.class))) return raw.getName();
        Type nested = containerValue(type);
        return nested == null ? null : unsupportedContainerType(nested);
    }

    private static Class<?> rawType(Type type) {
        if (type instanceof Class<?> raw) return raw;
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) return raw;
        return null;
    }

    /** The compiler checks nested numeric/container exclusions before ordinary map-key grammar eligibility. */
    private static Type containerValue(Type type) {
        if (!(type instanceof ParameterizedType parameterized)) return null;
        Class<?> raw = rawType(type);
        Type[] arguments = parameterized.getActualTypeArguments();
        if (raw != null && Collection.class.isAssignableFrom(raw) && arguments.length == 1) return arguments[0];
        if (raw != null && Map.class.isAssignableFrom(raw) && arguments.length == 2) return arguments[1];
        return null;
    }


    private static final class Literals {
        String field;
        String selection;
        String role;
        String doc;
        String rename = "";
        boolean fixed;
        String exclusion = "";
        String detail = "";
        boolean operation;
        boolean query;
        boolean ignored;
    }
    private record Invisible(String member, String reason) { }
    private static final class Metadata {
        boolean schema;
        String key = "";
        final List<Invisible> invisible = new ArrayList<>();
        final Map<String, Literals> methods = new LinkedHashMap<>();
    }

    /** Exact public declaration resources only. Avoids annotation proxies parsing unrelated user enum annotations. */
    private static Metadata metadata(Class<?> owner) throws IOException, ClassNotFoundException {
        verifyAnnotationProtocol(owner);
        String name = owner.getName().replace('.', '/') + ".class";
        verifyResourceOrigin(owner, name);
        try (InputStream bytes = owner.getModule().getResourceAsStream(name)) {
            if (bytes == null) throw new IOException("Missing exact module contract resource " + name);
            Metadata result = new Metadata();
            new ClassReader(bytes).accept(new MetadataVisitor(owner, name, result),
                    ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (owner.getName().endsWith(BUILDER_SUFFIX) && !result.schema)
                throw invalid("Missing generated input enrichment on " + owner.getName());
            return result;
        }
    }

    private static void verifyAnnotationProtocol(Class<?> owner) throws ClassNotFoundException {
        for (Class<?> protocol : List.of(GeneratedInput.Schema.class, GeneratedInput.Field.class, GeneratedInput.Selection.class, GeneratedInput.Exclusion.class))
            if (!Class.forName(protocol.getName(), false, owner.getClassLoader()).isAnnotation())
                throw invalid("Missing annotation protocol " + protocol.getName());
    }

    private static void verifyResourceOrigin(Class<?> owner, String name) throws IOException {
        URL resource = owner.getResource("/" + name);
        if (resource == null) throw new IOException("Missing exact public contract resource " + name);
        if (owner.getModule().isNamed()) return;
        URL origin = owner.getProtectionDomain().getCodeSource() == null ? null : owner.getProtectionDomain().getCodeSource().getLocation();
        if (origin == null) throw invalid("Missing contract resource origin " + owner.getName());
        // Compare resource spellings without URL.equals DNS lookups; loader URLs may contain unescaped spaces.
        boolean matches = resource.openConnection() instanceof JarURLConnection jar
                ? jar.getJarFileURL().toExternalForm().equals(origin.toExternalForm())
                : resource.toExternalForm().equals(new URL(origin, name).toExternalForm());
        if (!matches) throw invalid("Public contract resource origin disagrees with loaded type " + owner.getName());
    }

    private static final class MetadataVisitor extends ClassVisitor {
        private final Class<?> owner;
        private final String name;
        private final Metadata result;

        MetadataVisitor(Class<?> owner, String name, Metadata result) {
            super(Opcodes.ASM9);
            this.owner = owner;
            this.name = name;
            this.result = result;
        }

        @Override public void visit(int version, int access, String internal, String signature, String parent, String[] interfaces) {
            if (!internal.equals(name.substring(0, name.length() - 6)) || (access & Opcodes.ACC_PUBLIC) == 0 || (access & Opcodes.ACC_INTERFACE) == 0)
                throw invalid("Public contract resource identity/access mismatch " + owner.getName());
        }

        @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
            if (!visible || !annotation.equals(SCHEMA)) return null;
            if (result.schema) throw invalid("Duplicate input schema marker");
            result.schema = true;
            return new SchemaAnnotationVisitor(result);
        }

        @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
            Literals literal = new Literals();
            result.methods.put(name + descriptor, literal);
            return new ContractMethodVisitor(literal);
        }
    }

    private static final class SchemaAnnotationVisitor extends AnnotationVisitor {
        private final Metadata result;

        SchemaAnnotationVisitor(Metadata result) {
            super(Opcodes.ASM9);
            this.result = result;
        }

        @Override public void visit(String member, Object value) {
            if (member.equals("key") && value instanceof String text) result.key = text;
            else throw invalid("Unknown schema member " + member);
        }

        @Override public AnnotationVisitor visitArray(String member) {
            if (!member.equals("exclusions")) throw invalid("Unknown schema array " + member);
            return new AnnotationVisitor(Opcodes.ASM9) {
                @Override public AnnotationVisitor visitAnnotation(String ignored, String annotation) {
                    if (!annotation.equals(EXCLUSION)) throw invalid("Invalid invisible exclusion");
                    return new ExclusionAnnotationVisitor(result);
                }
            };
        }
    }

    private static final class ExclusionAnnotationVisitor extends AnnotationVisitor {
        private final Metadata result;
        private String source;
        private String category;
        private String detail = "";

        ExclusionAnnotationVisitor(Metadata result) {
            super(Opcodes.ASM9);
            this.result = result;
        }

        @Override public void visit(String member, Object value) {
            if (!(value instanceof String text)) throw invalid("Unknown exclusion member " + member);
            switch (member) {
                case "member" -> source = text;
                case "category" -> category = text;
                case "detail" -> detail = text;
                default -> throw invalid("Unknown exclusion member " + member);
            }
        }

        @Override public void visitEnd() {
            if (source == null || source.isEmpty() || category == null) throw invalid("Invalid invisible exclusion");
            result.invisible.add(new Invisible(source, exclusionReason(category, detail)));
        }
    }

    private static final class ContractMethodVisitor extends MethodVisitor {
        private final Literals literal;
        private final Set<String> seen = new LinkedHashSet<>();

        ContractMethodVisitor(Literals literal) {
            super(Opcodes.ASM9);
            this.literal = literal;
        }

        @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
            if (!PROTOCOL.contains(annotation)) return null;
            if (annotation.startsWith(PREFIX) && !visible) throw invalid("Generated input annotation requires runtime retention");
            if (!seen.add(annotation)) throw invalid("Duplicate contract annotation " + annotation);
            if (annotation.equals(OPERATION)) literal.operation = true;
            if (annotation.equals(QUERY)) literal.query = true;
            if (annotation.equals(IGNORE)) literal.ignored = true;
            return new MethodAnnotationVisitor(annotation, literal);
        }
    }

    private static final class MethodAnnotationVisitor extends AnnotationVisitor {
        private final String annotation;
        private final Literals literal;

        MethodAnnotationVisitor(String annotation, Literals literal) {
            super(Opcodes.ASM9);
            this.annotation = annotation;
            this.literal = literal;
        }

        @Override public void visit(String member, Object value) {
            switch (annotation) {
                case FIELD -> fieldFact(member, value);
                case SELECTION -> selectionFact(member, value);
                default -> namingFact(member, value);
            }
        }

        private void fieldFact(String member, Object value) {
            switch (member) {
                case "value" -> literal.field = (String) value;
                case "rename" -> literal.rename = (String) value;
                case "ignored" -> literal.ignored = (Boolean) value;
                case "fixedKey" -> literal.fixed = (Boolean) value;
                case "exclusion" -> literal.exclusion = (String) value;
                case "detail" -> literal.detail = (String) value;
                default -> throw invalid("Unknown field fact " + member);
            }
        }

        private void selectionFact(String member, Object value) {
            switch (member) {
                case "field" -> literal.selection = (String) value;
                case "role" -> literal.role = (String) value;
                default -> throw invalid("Unknown selection fact " + member);
            }
        }

        private void namingFact(String member, Object value) {
            if (!member.equals("value")) return;
            switch (annotation) {
                case DOC -> literal.doc = (String) value;
                case PROPERTY -> literal.rename = (String) value;
                case IGNORE -> literal.ignored = (Boolean) value;
                default -> { /* Marker annotations have no naming facts. */ }
            }
        }

        @Override public void visitEnum(String member, String descriptor, String value) {
            if (annotation.startsWith(PREFIX)) throw invalid("Unexpected enum input fact " + member);
        }

        @Override public AnnotationVisitor visitArray(String member) {
            if (annotation.startsWith(PREFIX)) throw invalid("Unexpected array input fact " + member);
            return null;
        }

        @Override public AnnotationVisitor visitAnnotation(String member, String descriptor) {
            if (annotation.startsWith(PREFIX)) throw invalid("Unexpected nested input fact " + member);
            return null;
        }

        @Override public void visitEnd() {
            if (annotation.equals(FIELD) && (literal.field == null || literal.field.isEmpty())) throw invalid("Missing field association");
            if (annotation.equals(SELECTION) && (literal.selection == null || literal.selection.isEmpty() || literal.role == null))
                throw invalid("Missing population selection");
        }
    }
}
