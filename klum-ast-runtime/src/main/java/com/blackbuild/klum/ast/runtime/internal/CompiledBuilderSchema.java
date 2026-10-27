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

import com.blackbuild.klum.ast.ConstraintProjection.Policy;
import com.blackbuild.klum.ast.InputNaming.Profile;
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
import java.math.BigDecimal;
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
    private static final String VALUE = "value";
    private static final String IDENTITY_NAME = "IDENTITY";
    private static final String KEBAB_NAME = "KEBAB_CASE";
    private static final String INVALID_CONSTRAINT_ARRAY_ITEM = "Unexpected constraint array item";
    private static final String BUILDER_SUFFIX = "_DSL$Builder";
    private static final String INPUT_IGNORED = "excluded by @InputIgnore";
    private static final String JSON_PROPERTY_ORIGIN = "JSON_PROPERTY";
    private static final String IGNORE_ORIGIN = "IGNORE";
    private static final String INPUT_IGNORE_ORIGIN = "INPUT_IGNORE";
    private static final Set<String> NAME_ORIGINS = Set.of(IDENTITY_NAME, JSON_PROPERTY_ORIGIN, "INPUT_NAME", IGNORE_ORIGIN, INPUT_IGNORE_ORIGIN);
    private static final String IGNORED_INPUT = "excluded by @JsonIgnore(true)";
    private static final String NUMERIC = "NUMERIC";
    private static final String CONTAINER = "CONTAINER";
    private static final String PREFIX = "Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$";
    private static final String SCHEMA = PREFIX + "Schema;";
    private static final String KEY_ANNOTATION = PREFIX + "Key;";
    private static final String FIELD = PREFIX + "Field;";
    private static final String OPERATION_FACT = PREFIX + "Operation;";
    private static final String SOURCE_DEPRECATED = "sourceDeprecated";
    private static final String CONSTRAINT = PREFIX + "Constraint;";
    private static final String SELECTION = PREFIX + "Selection;";
    private static final String EXCLUSION = PREFIX + "Exclusion;";
    private static final String OPERATION = "Lcom/blackbuild/klum/ast/Builder$Method;";
    private static final String QUERY = "Lcom/blackbuild/klum/ast/Builder$Query;";
    private static final String DOC = "Lcom/blackbuild/annodocimal/annotations/AnnoDoc;";
    private static final Set<String> PROTOCOL = Set.of(SCHEMA, KEY_ANNOTATION, FIELD, SELECTION, EXCLUSION, OPERATION, OPERATION_FACT, QUERY, DOC);
    private static final Set<String> NUMBERS = Set.of("byte", "short", "int", "long", "float", "double",
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float",
            "java.lang.Double", "java.math.BigInteger", "java.math.BigDecimal");
    private static final Set<String> COLLECTIONS = Set.of("java.util.List", "java.util.Set", "java.util.SortedSet", "java.util.NavigableSet", "java.util.EnumSet");
    private static final Set<String> MAPS = Set.of("java.util.Map", "java.util.SortedMap", "java.util.NavigableMap");

    private final Class<?> contract;
    private final Class<?> model;
    private final Key key;
    private final String keyName;
    private final Map<String, Entry> entries;
    private final List<Rejection> rejections;
    private final List<Declaration> declarations;

    enum Role { PROPERTY, OWNED_OBJECT, KEYED_OWNED_MAP, OPERATION }
    enum Kind { SCALAR, ENUM, LIST, MAP, NESTED_OBJECT }
    record Payload(Kind kind, Class<?> type, Class<?> builder, String keyType, Payload item) { }
    record Entry(String externalName, Role role, String source, Class<?> contract, String operation,
                 List<Method> methods, Payload payload, String keyType, String excludedKey, String fixedChildKey,
                 String nameOrigin, String documentation, boolean schemaKey, boolean sourceDeprecated) { }
    record ConstraintRule(String annotation, String value, boolean plain, String declaration, String decimalValue, Boolean inclusive) { }
    record FieldConstraints(Policy policy, List<ConstraintRule> rules) { }
    record ConstraintProfile(boolean groupRedefined, Map<String, FieldConstraints> fields) { }

    /** Exposes numeric projection facts after the shared protocol/completeness validation. */
    static ConstraintProfile constraintProfile(Class<?> builder) {
        try {
            Metadata metadata = metadata(builder);
            Map<String, FieldConstraints> fields = new LinkedHashMap<>();
            for (Literals literal : metadata.methods.values()) {
                if (literal.field == null) continue;
                FieldConstraints constraints = new FieldConstraints(Policy.valueOf(literal.constraintProjection), List.copyOf(literal.constraints));
                if (fields.putIfAbsent(literal.field, constraints) != null)
                    throw invalid("duplicate constraint source field #" + literal.field);
            }
            return new ConstraintProfile(metadata.groupRedefined, Collections.unmodifiableMap(fields));
        } catch (IOException | ClassNotFoundException failure) {
            throw invalid("JSON Schema constraints require complete public Builder metadata on '" + builder.getName()
                    + "' (artifact " + builder.getProtectionDomain().getCodeSource() + "): " + failure.getMessage()
                    + "; recompile and package this Schema and its inherited/owned Schema dependencies with matching KlumAST compiler/runtime versions");
        }
    }

    private static void validateDecimalFacts(Literals literal, String source) {
        for (ConstraintRule rule : literal.constraints) {
            if (!decimalRule(rule.annotation())) continue;
            if (rule.decimalValue() == null || rule.inclusive() == null) {
                throw invalidConstraint(literal, "missing direct decimal facts");
            }
            try {
                new BigDecimal(rule.decimalValue());
            } catch (NumberFormatException failure) {
                throw invalidConstraint(literal, "Invalid decimal bound '" + rule.decimalValue() + "' on " + source);
            }
        }
    }

    record Key(String nativeName, String externalName, boolean ignored, String source, Class<?> contract,
               boolean sourceDeprecated, String ignoreReason) { }
    record Rejection(String source, String reason, String collisionName) {
        Rejection(String source, String reason) { this(source, reason, null); }
    }
    record Declaration(String owner, String name, List<String> parameters, String returns, String documentation) { }

    private CompiledBuilderSchema(Class<?> contract, Class<?> model, Map<String, Entry> entries,
                                  List<Rejection> rejections, List<Declaration> declarations, Key key, String keyName) {
        this.key = key;
        this.keyName = keyName;
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
            Profile naming = resolveNaming(builder);
            Facts facts = collect(builder, new LinkedHashMap<>(), naming == Profile.KEBAB_CASE);
            List<Rejection> rejected = new ArrayList<>(facts.rejections());
            List<Declaration> declarations = new ArrayList<>(facts.declarations());
            declarations.addAll(scope(factory));
            Map<String, Entry> entries = new LinkedHashMap<>();
            Key key = facts.key();
            if (naming == Profile.KEBAB_CASE) {
                List<Entry> candidates = facts.entries().stream().map(CompiledBuilderSchema::profileEntry).toList();
                entries.putAll(admitted(candidates, rejected, true));
                if (key != null) {
                    key = profileKey(key);
                }
            } else {
                facts.entries().forEach(entry -> entries.put(entry.externalName(), entry));
            }
            return new CompiledBuilderSchema(builder, model, entries, rejected.stream().distinct()
                    .sorted(Comparator.comparing(Rejection::source).thenComparing(Rejection::reason)).toList(), declarations, key, metadata(builder).key);
        } catch (ReflectiveOperationException | LinkageError | IOException | RuntimeException failure) {
            throw failure(builder.getName(), failure);
        }
    }

    /** Resolve each declaring hierarchy before its descendants, preserving previously rejected parent sources. */
    private static Map<String, Entry> admitted(List<Entry> candidates, List<Rejection> rejected, boolean strict) {
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
                if (strict) {
                    throw invalid("KEBAB_CASE external-name collision for '" + name + "': " + all);
                }
                group.forEach(entry -> rejected.add(new Rejection(entry.source(), "external-name collision for '" + name + "': " + all, name)));
            }
        });
        return entries;
    }

    private record Facts(List<Entry> entries, List<Rejection> rejections, List<Declaration> declarations, Key key) { }
    private record BuilderContext(Class<?> builder, Class<?> model, Metadata metadata) { }

    Key key() { return key; }
    List<String> keyCollisions() {
        if (key == null || key.ignored()) return List.of();
        List<String> collisions = new ArrayList<>();
        Entry entry = entries.get(key.externalName());
        if (entry != null) collisions.add(entry.source());
        rejections.stream().filter(rejection -> key.externalName().equals(rejection.collisionName()))
                .map(Rejection::source).forEach(collisions::add);
        return collisions.stream().distinct().sorted().toList();
    }
    String keyName() { return keyName; }
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

    private static Facts collect(Class<?> builder, Map<Class<?>, Facts> known, boolean nativeCandidates) throws IOException, ClassNotFoundException {
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
        Map<String, Method> accessors = new LinkedHashMap<>();
        Map<String, List<Method>> selections = new LinkedHashMap<>();
        BuilderContext context = new BuilderContext(builder, model, metadata);
        collectDeclarations(context, accessors, selections, entries, rejections, declarations);
        Key key = declaredKey(context);
        for (Map.Entry<String, Method> field : accessors.entrySet()) {
            List<Method> methods = selections.remove(field.getKey());
            collectField(context, field.getKey(), field.getValue(),
                    methods == null ? List.of() : methods, entries, rejections);
        }
        if (!selections.isEmpty()) throw invalid("Selections lack field declarations: " + selections.keySet());
        for (Class<?> parent : builder.getInterfaces()) {
            Class<?> parentModel = modelOf(parent);
            if (parentModel == null || parentModel == Object.class || !parent.getName().endsWith(BUILDER_SUFFIX)) continue;
            if (!parentModel.isAssignableFrom(model)) throw invalid("Unrelated Builder parent " + parent.getName());
            Facts inherited = collect(parent, known, nativeCandidates);
            key = inheritKey(key, inherited.key());
            entries.addAll(inherited.entries());
            rejections.addAll(inherited.rejections());
            declarations.addAll(inherited.declarations());
        }
        validateKeyMetadata(metadata, key, builder);
        List<Entry> resolved = List.copyOf(nativeCandidates ? entries : admitted(entries, rejections, false).values());
        Facts facts = new Facts(resolved, List.copyOf(rejections), declarations.stream().distinct().toList(), key);
        known.put(builder, facts);
        return facts;
    }

    /** Declaring public contracts are the sole authority, including explicit absence and source/artifact identity. */
    private static Profile resolveNaming(Class<?> builder) throws IOException, ClassNotFoundException {
        Map<Class<?>, Metadata> hierarchy = new LinkedHashMap<>();
        namingHierarchy(builder, hierarchy);
        List<Map.Entry<Class<?>, Metadata>> explicit = hierarchy.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue().namingExplicit)).toList();
        if (explicit.isEmpty()) return Profile.IDENTITY;
        String selected = explicit.get(0).getValue().namingProfile;
        if (explicit.stream().anyMatch(entry -> !entry.getValue().namingProfile.equals(selected))) {
            String declarations = explicit.stream().map(entry -> namingOrigin(entry.getKey()) + " @InputNaming("
                    + entry.getValue().namingProfile + ")").sorted().collect(Collectors.joining(", "));
            throw invalid("Conflicting explicit input profiles: " + declarations);
        }
        return Profile.valueOf(selected);
    }

    private static void namingHierarchy(Class<?> builder, Map<Class<?>, Metadata> hierarchy)
            throws IOException, ClassNotFoundException {
        if (hierarchy.containsKey(builder)) return;
        requirePublic(builder);
        Metadata declared = metadata(builder);
        hierarchy.put(builder, declared);
        for (Class<?> parent : Arrays.stream(builder.getInterfaces()).sorted(Comparator.comparing(Class::getName)).toList()) {
            if (parent.getName().endsWith(BUILDER_SUFFIX)) {
                namingHierarchy(parent, hierarchy);
            }
        }
    }

    private static void validateNaming(Metadata declared, Class<?> builder) {
        if (declared.namingExplicit == null || declared.namingProfile == null
                || !Set.of(IDENTITY_NAME, KEBAB_NAME).contains(declared.namingProfile)
                || Boolean.FALSE.equals(declared.namingExplicit) && !declared.namingProfile.equals(IDENTITY_NAME)) {
            throw invalid("Invalid or incomplete naming metadata on " + namingOrigin(builder));
        }
    }

    private static String schemaName(Class<?> builder) {
        return builder.getName().endsWith(BUILDER_SUFFIX)
                ? builder.getName().substring(0, builder.getName().length() - BUILDER_SUFFIX.length()) : builder.getName();
    }

    private static String namingOrigin(Class<?> builder) {
        URL origin = builder.getProtectionDomain().getCodeSource() == null ? null : builder.getProtectionDomain().getCodeSource().getLocation();
        return schemaName(builder) + " [public Builder " + builder.getName() + ", artifact " + origin + "]";
    }

    private static Entry profileEntry(Entry entry) {
        if (!entry.nameOrigin().equals(IDENTITY_NAME)) return entry;
        String name = InputNamingProfiles.kebab(entry.externalName(), entry.source());
        return new Entry(name, entry.role(), entry.source(), entry.contract(), entry.operation(), entry.methods(),
                entry.payload(), entry.keyType(), entry.excludedKey(), entry.fixedChildKey(),
                KEBAB_NAME, entry.documentation(), entry.schemaKey(), entry.sourceDeprecated());
    }

    private static Key profileKey(Key key) throws IOException, ClassNotFoundException {
        Metadata declaring = metadata(key.contract());
        Literals literal = declaring.keyMember;
        if (literal == null) {
            literal = declaring.methods.values().stream().filter(value -> key.nativeName().equals(value.key)).findFirst().orElseThrow();
        }
        if (!literal.names.rename.isEmpty() || key.ignored()) return key;
        return new Key(key.nativeName(), InputNamingProfiles.kebab(key.nativeName(), key.source()), key.ignored(),
                key.source(), key.contract(), key.sourceDeprecated(), key.ignoreReason());
    }

    private static Key inheritKey(Key key, Key inherited) {
        if (inherited == null) return key;
        if (key != null && !key.equals(inherited)) throw invalid("Conflicting construction key declarations");
        return inherited;
    }

    private static void validateKeyMetadata(Metadata metadata, Key key, Class<?> builder) {
        if (metadata.keyInput != (key != null) || !metadata.key.equals(key == null ? "" : key.nativeName())) {
            throw invalid("Construction key enrichment disagrees with selected Schema key on " + namingOrigin(builder));
        }
    }

    private static Key declaredKey(BuilderContext context) {
        Literals fallback = context.metadata().keyMember;
        Key key = fallback == null ? null : constructionKey(context, fallback);
        if (fallback != null && Arrays.stream(context.builder().getDeclaredMethods()).anyMatch(method ->
                method.getParameterCount() == 0 && method.getName().equals(keyGetterName(fallback.key))))
            throw invalid("Type-level construction key fact has a surviving public getter on " + context.builder().getName());
        for (Method method : context.builder().getDeclaredMethods()) {
            Literals literal = context.metadata().methods.get(signature(method));
            if (literal.key == null) continue;
            if (key != null || method.getParameterCount() != 0 || method.getReturnType() != String.class
                    || !method.getName().equals(keyGetterName(literal.key))
                    || literal.field != null || literal.selection != null || literal.operation)
                throw invalid("Invalid construction key getter " + method);
            key = constructionKey(context, literal);
        }
        return key;
    }

    private static String keyGetterName(String name) {
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Key constructionKey(BuilderContext context, Literals literal) {
        return new Key(literal.key, literal.names.rename.isEmpty() ? literal.key : literal.names.rename,
                literal.names.ignored, context.model().getName() + "#" + literal.key, context.builder(), literal.sourceDeprecated, ignoreReason(literal.names));
    }

    private static void collectDeclarations(BuilderContext context,
                                            Map<String, Method> accessors, Map<String, List<Method>> selections,
                                            List<Entry> entries, List<Rejection> rejections, List<Declaration> declarations) {
        Class<?> builder = context.builder();
        Metadata metadata = context.metadata();
        for (Method method : Arrays.stream(builder.getDeclaredMethods()).sorted(Comparator.comparing(CompiledBuilderSchema::descriptor)).toList()) {
            Literals literal = metadata.methods.get(signature(method));
            if (literal == null) throw invalid("Missing exact public declaration " + method);
            declarations.add(declaration(method, literal));
            if (literal.field != null && (!fieldAccessor(method, literal.field)
                    || accessors.putIfAbsent(literal.field, method) != null))
                throw invalid("Duplicate or invalid field accessor association " + literal.field);
            if (literal.selection != null) selections.computeIfAbsent(literal.selection, ignored -> new ArrayList<>()).add(method);
            if (literal.operation && !literal.query) operation(method, literal, context.model(), entries, rejections);
        }
    }

    private static boolean fieldAccessor(Method method, String field) {
        return method.getParameterCount() == 0 || method.getParameterCount() == 1
                && method.getName().equals("set" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
    }

    private static void collectField(BuilderContext context, String field, Method accessor,
                                     List<Method> methods, List<Entry> entries, List<Rejection> rejections)
            throws IOException, ClassNotFoundException {
        Metadata metadata = context.metadata();
        Literals literal = metadata.methods.get(signature(accessor));
        String source = context.model().getName() + "#" + field;
        Type fieldType = accessor.getParameterCount() == 0 ? accessor.getGenericReturnType() : accessor.getGenericParameterTypes()[0];
        String reason = fieldReason(fieldType, literal);
        if (reason != null) {
            rejections.add(new Rejection(source, reason));
            return;
        }
        Role role = selectedRole(metadata, methods, source);
        if (role == Role.OWNED_OBJECT && literal.schemaKey == null)
            throw invalid("Missing direct relationship key-authority metadata on " + source);
        if (literal.schemaKey != null && (role != Role.OWNED_OBJECT || literal.fixed && !literal.schemaKey))
            throw invalid("Schema child-key authority requires OWNED_OBJECT and agrees with fixed key: " + source);
        Payload payload = fieldPayload(fieldType, role, source);
        String key = childKey(payload, role, literal, source);
        validateSelection(role, payload, methods, source);
        String name = literal.names.rename.isEmpty() ? field : literal.names.rename;
        entries.add(new Entry(name, role, source, context.builder(), methods.get(0).getName(), List.copyOf(methods), payload,
                role == Role.KEYED_OWNED_MAP ? String.class.getName() : null, key, literal.fixed ? field : null,
                literal.names.origin, literal.doc, Boolean.TRUE.equals(literal.schemaKey), literal.sourceDeprecated));
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

    private static Payload fieldPayload(Type fieldType, Role role, String source) {
        Payload payload = payload(fieldType, true);
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
        NameFacts names = literal.operationNames;
        String reason = null;
        if (names.ignored) reason = ignoreReason(names);
        else if (method.getParameterCount() != 1) reason = "@Builder.Method must declare exactly one payload";
        Payload payload = reason == null ? payload(method.getGenericParameterTypes()[0], false) : null;
        if (reason == null && payload == null) reason = unsupportedReason(method.getGenericParameterTypes()[0]);
        if (reason != null) { rejections.add(new Rejection(source, reason)); return; }
        String name = names.rename.isEmpty() ? method.getName() : names.rename;
        entries.add(new Entry(name, Role.OPERATION, source, method.getDeclaringClass(), method.getName(), List.of(method), payload,
                null, null, null, names.origin, literal.doc, false, literal.operationDeprecated));
    }

    private static String fieldReason(Type type, Literals literal) {
        if (literal.names.ignored) return ignoreReason(literal.names);
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
            // Binary relationships may erase only the generated SELF argument. Do not infer user type arguments.
            if (field && leaf.getTypeParameters().length == 1) {
                Payload nested = ownedPayload(leaf);
                if (nested != null) return nested;
            }
            return leafPayload(leaf);
        }
        if (!(type instanceof ParameterizedType parameterized) || !(parameterized.getRawType() instanceof Class<?> raw)) return null;
        if (field) {
            Payload nested = ownedPayload(raw);
            if (nested != null) return nested;
        }
        return containerPayload(parameterized, raw, field);
    }

    private static Payload ownedPayload(Class<?> builder) {
        Class<?> model = modelOf(builder);
        return builder.isInterface() && builder.getName().endsWith(BUILDER_SUFFIX)
                && model != null && !Modifier.isAbstract(model.getModifiers())
                ? new Payload(Kind.NESTED_OBJECT, model, builder, null, null) : null;
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
            case IGNORE_ORIGIN -> IGNORED_INPUT;
            case INPUT_IGNORE_ORIGIN -> INPUT_IGNORED;
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


    /** Names belong to selected source declarations, which can share one public accessor signature. */
    private static final class NameFacts {
        String rename = "";
        String origin;
        boolean ignored;
        boolean renamePresent;
        boolean ignoredPresent;
    }

    private static final class Literals {
        String key;
        String field;
        String selection;
        String role;
        String doc;
        final NameFacts names = new NameFacts();
        final NameFacts operationNames = new NameFacts();
        boolean operationFact;
        boolean fixed;
        Boolean schemaKey;
        Boolean sourceDeprecated;
        Boolean operationDeprecated;
        String exclusion = "";
        String detail = "";
        boolean operation;
        boolean query;
        List<ConstraintRule> constraints;
        String constraintProjection;
    }
    private record Invisible(String member, String reason) { }
    private static final class Metadata {
        boolean schema;
        int version;
        Boolean namingExplicit;
        String namingProfile;
        Boolean groupRedefined;
        boolean keyInput;
        Literals keyMember;
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
            try {
                new ClassReader(bytes).accept(new MetadataVisitor(owner, name, result),
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                validateMetadata(owner, result);
            } catch (RuntimeException failure) {
                throw invalid("Invalid public Builder metadata on " + namingOrigin(owner) + ": " + failure.getMessage());
            }
            if (owner.getName().endsWith(BUILDER_SUFFIX) && !result.schema)
                throw invalid("Missing generated input enrichment on " + namingOrigin(owner));
            return result;
        }
    }

    private static String ignoreReason(NameFacts names) {
        return INPUT_IGNORE_ORIGIN.equals(names.origin) ? INPUT_IGNORED : IGNORED_INPUT;
    }

    private static void validateMetadata(Class<?> owner, Metadata metadata) {
        if (!metadata.schema) return;
        if (metadata.version != 1) {
            throw invalid("Unsupported structured-input metadata protocol version " + metadata.version + " on " + namingOrigin(owner)
                    + "; expected version 1; recompile and package the participating Schema artifacts with a matching KlumAST toolchain");
        }
        validateNaming(metadata, owner);
        if (metadata.groupRedefined == null) throw invalid("Missing constraint group facts on " + namingOrigin(owner));
        if (metadata.keyMember != null) validateLiteral(owner, metadata.keyMember, "construction key");
        metadata.methods.forEach((signature, literal) -> validateLiteral(owner, literal, signature));
    }

    private static void validateLiteral(Class<?> owner, Literals literal, String signature) {
        String source = namingOrigin(owner) + "#" + signature;
        if (literal.field != null || literal.key != null) {
            validateNameFacts(literal.names, source);
            if (literal.sourceDeprecated == null) throw invalid("Missing source deprecation fact on " + source);
        }
        if (literal.operation && !literal.operationFact) throw invalid("Missing literal operation facts on " + source);
        if (literal.operationFact) {
            validateNameFacts(literal.operationNames, source);
            if (literal.operationDeprecated == null) throw invalid("Missing source deprecation fact on " + source);
        }
        if (literal.field != null) validateFieldFacts(literal, schemaName(owner) + "#" + literal.field);
    }

    private static void validateFieldFacts(Literals literal, String source) {
        if (literal.constraints == null) throw invalidConstraint(literal, "Missing constraint facts on " + source);
        if (literal.constraintProjection == null) throw invalid("Missing constraint projection policy on " + source);
        try {
            Policy.valueOf(literal.constraintProjection);
        } catch (IllegalArgumentException failure) {
            throw invalidConstraint(literal, "Unknown constraint projection policy " + literal.constraintProjection + " on " + source);
        }
        validateDecimalFacts(literal, source);
    }

    private static void validateNameFacts(NameFacts names, String source) {
        if (!NAME_ORIGINS.contains(names.origin == null ? "" : names.origin)
                || !names.renamePresent || !names.ignoredPresent) {
            throw invalid("Invalid or incomplete literal metadata on " + source);
        }
        boolean excluded = Set.of(IGNORE_ORIGIN, INPUT_IGNORE_ORIGIN).contains(names.origin);
        boolean renamed = Set.of(JSON_PROPERTY_ORIGIN, "INPUT_NAME").contains(names.origin);
        if (names.ignored != excluded || renamed == names.rename.isEmpty()) {
            throw invalid("Inconsistent literal metadata on " + source);
        }
    }

    private static void verifyAnnotationProtocol(Class<?> owner) throws ClassNotFoundException {
        for (Class<?> protocol : List.of(GeneratedInput.Schema.class, GeneratedInput.Key.class, GeneratedInput.Field.class, GeneratedInput.Selection.class, GeneratedInput.Exclusion.class))
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
        private final Set<String> seen = new LinkedHashSet<>();

        SchemaAnnotationVisitor(Metadata result) {
            super(Opcodes.ASM9);
            this.result = result;
        }

        @Override public void visit(String member, Object value) {
            if (!seen.add(member)) throw invalid("Duplicate schema member " + member);
            switch (member) {
                case "version" -> result.version = integer(member, value);
                case "namingExplicit" -> result.namingExplicit = flag(member, value);
                case "namingProfile" -> result.namingProfile = text(member, value);
                case "groupRedefined" -> result.groupRedefined = flag(member, value);
                case "key" -> result.key = text(member, value);
                case "keyInput" -> result.keyInput = flag(member, value);
                default -> throw unknownMember(member);
            }
        }

        private static int integer(String member, Object value) {
            if (value instanceof Integer number) {
                return number;
            }
            throw invalid("Malformed structured-input metadata protocol version: expected an integer");
        }

        private static boolean flag(String member, Object value) {
            if (value instanceof Boolean flag) {
                return flag;
            }
            throw unknownMember(member);
        }

        private static String text(String member, Object value) {
            if (value instanceof String text) {
                return text;
            }
            throw unknownMember(member);
        }

        private static IllegalArgumentException unknownMember(String member) {
            return invalid("Unknown schema member " + member);
        }

        @Override public AnnotationVisitor visitAnnotation(String member, String descriptor) {
            if (!member.equals("keyMember") || !descriptor.equals(KEY_ANNOTATION) || result.keyMember != null)
                throw invalid("Unknown or duplicate schema key member " + member);
            result.keyMember = new Literals();
            return new MethodAnnotationVisitor(KEY_ANNOTATION, result.keyMember);
        }

        @Override public void visitEnum(String member, String descriptor, String value) {
            throw invalid("Unexpected schema enum " + member);
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
            if (annotation.equals(OPERATION_FACT)) literal.operationFact = true;
            return new MethodAnnotationVisitor(annotation, literal);
        }
    }

    private static IllegalArgumentException invalidConstraint(Literals field, String reason) {
        return invalid(reason + " for source field #" + field.field + ", external input '"
                + (field.names.rename.isEmpty() ? field.field : field.names.rename) + "'");
    }

    private static boolean decimalRule(String annotation) {
        return Set.of("jakarta.validation.constraints.DecimalMin", "jakarta.validation.constraints.DecimalMax").contains(annotation);
    }

    /** Decodes inert strings/longs/booleans only; no Bean annotation classes/proxies or validator code. */
    private static final class ConstraintAnnotationVisitor extends AnnotationVisitor {
        private final Literals field;
        private final Set<String> seen = new LinkedHashSet<>();
        private String annotation;
        private String value = "";
        private boolean plain;
        private String declaration;
        private String decimalValue;
        private Boolean inclusive;

        ConstraintAnnotationVisitor(Literals field) {
            super(Opcodes.ASM9);
            this.field = field;
        }

        @Override public void visit(String member, Object literal) {
            if (!seen.add(member)) throw invalid("Duplicate constraint member " + member);
            switch (member) {
                case "annotation" -> annotation = (String) literal;
                case VALUE -> {
                    if (!(literal instanceof Long)) throw invalid("Integral constraint value must be a Java long");
                    value = literal.toString();
                }
                case "decimalValue" -> decimalValue = (String) literal;
                case "inclusive" -> inclusive = (Boolean) literal;
                case "plain" -> plain = (Boolean) literal;
                case "declaration" -> declaration = (String) literal;
                default -> throw invalid("Unknown constraint member " + member);
            }
        }

        @Override public AnnotationVisitor visitArray(String member) { throw invalid("Unexpected constraint array " + member); }
        @Override public AnnotationVisitor visitAnnotation(String member, String descriptor) { throw invalid("Unexpected nested constraint " + member); }
        @Override public void visitEnum(String member, String descriptor, String literal) { throw invalid("Unexpected constraint enum " + member); }

        @Override public void visitEnd() {
            if (annotation == null || annotation.isBlank() || declaration == null || declaration.isBlank())
                throw invalidConstraint(field, "Missing constraint declaration identity");
            if (!seen.contains("plain")) throw invalidConstraint(field, "Missing constraint eligibility");
            boolean integral = Set.of("jakarta.validation.constraints.Min", "jakarta.validation.constraints.Max").contains(annotation);
            if (integral && !seen.contains(VALUE)) throw invalidConstraint(field, "Missing direct integral bound");
            if (plain && !decimalRule(annotation) && (!integral
                    || !value.matches("-?\\d+"))) throw invalid("Invalid direct integral constraint fact " + annotation);
            field.constraints.add(new ConstraintRule(annotation, value, plain, declaration, decimalValue, inclusive));
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
                case OPERATION_FACT -> {
                    if (member.equals(SOURCE_DEPRECATED)) {
                        literal.operationDeprecated = (Boolean) value;
                    } else if (!nameFact(literal.operationNames, member, value)) {
                        throw invalid("Unknown operation fact " + member);
                    }
                }
                case KEY_ANNOTATION -> keyFact(member, value);
                case FIELD -> fieldFact(member, value);
                case SELECTION -> selectionFact(member, value);
                default -> { if (annotation.equals(DOC) && member.equals(VALUE)) literal.doc = (String) value; }
            }
        }

        private void keyFact(String member, Object value) {
            switch (member) {
                case VALUE -> literal.key = (String) value;
                case SOURCE_DEPRECATED -> literal.sourceDeprecated = (Boolean) value;
                default -> { if (!nameFact(literal.names, member, value)) throw invalid("Unknown construction key fact " + member); }
            }
        }

        private void fieldFact(String member, Object value) {
            switch (member) {
                case VALUE -> literal.field = (String) value;
                case SOURCE_DEPRECATED -> literal.sourceDeprecated = (Boolean) value;
                case "constraints" -> throw invalidConstraint(literal, INVALID_CONSTRAINT_ARRAY_ITEM);
                case "fixedKey" -> literal.fixed = (Boolean) value;
                case "schemaKey" -> literal.schemaKey = (Boolean) value;
                case "exclusion" -> literal.exclusion = (String) value;
                case "detail" -> literal.detail = (String) value;
                default -> { if (!nameFact(literal.names, member, value)) throw invalid("Unknown field fact " + member); }
            }
        }

        private void selectionFact(String member, Object value) {
            switch (member) {
                case "field" -> literal.selection = (String) value;
                case "role" -> literal.role = (String) value;
                default -> throw invalid("Unknown selection fact " + member);
            }
        }

        private boolean nameFact(NameFacts names, String member, Object value) {
            if (member.equals("nameOrigin") && names.origin != null
                    || member.equals("rename") && names.renamePresent
                    || member.equals("ignored") && names.ignoredPresent) {
                throw invalid("Duplicate literal metadata member " + member);
            }
            switch (member) {
                case "nameOrigin" -> names.origin = (String) value;
                case "rename" -> { names.rename = (String) value; names.renamePresent = true; }
                case "ignored" -> { names.ignored = (Boolean) value; names.ignoredPresent = true; }
                default -> { return false; }
            }
            return true;
        }

        @Override public void visitEnum(String member, String descriptor, String value) {
            if (annotation.equals(FIELD) && member.equals("constraintProjection")
                    && descriptor.equals("Lcom/blackbuild/klum/ast/ConstraintProjection$Policy;")) {
                if (literal.constraintProjection != null) throw invalid("Duplicate constraint projection policy");
                literal.constraintProjection = value;
                return;
            }
            if (annotation.startsWith(PREFIX)) throw invalid("Unexpected enum input fact " + member);
        }

        @Override public AnnotationVisitor visitArray(String member) {
            if (annotation.equals(FIELD) && member.equals("constraints")) {
                if (literal.constraints != null) throw invalid("Duplicate constraint facts");
                literal.constraints = new ArrayList<>();
                return new AnnotationVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotation(String ignored, String descriptor) {
                        if (!descriptor.equals(CONSTRAINT)) throw invalid("Unexpected constraint fact " + descriptor);
                        return new ConstraintAnnotationVisitor(literal);
                    }
                    @Override public void visit(String ignored, Object value) {
                        throw invalidConstraint(literal, INVALID_CONSTRAINT_ARRAY_ITEM);
                    }
                    @Override public void visitEnum(String ignored, String descriptor, String value) {
                        throw invalidConstraint(literal, INVALID_CONSTRAINT_ARRAY_ITEM);
                    }
                    @Override public AnnotationVisitor visitArray(String ignored) {
                        throw invalidConstraint(literal, INVALID_CONSTRAINT_ARRAY_ITEM);
                    }
                };
            }
            if (annotation.startsWith(PREFIX)) throw invalid("Unexpected array input fact " + member);
            return null;
        }

        @Override public AnnotationVisitor visitAnnotation(String member, String descriptor) {
            if (annotation.startsWith(PREFIX)) throw invalid("Unexpected nested input fact " + member);
            return null;
        }

        @Override public void visitEnd() {
            if (annotation.equals(KEY_ANNOTATION) && (literal.key == null || literal.key.isEmpty())) throw invalid("Missing construction key association");
            if (annotation.equals(FIELD) && (literal.field == null || literal.field.isEmpty())) throw invalid("Missing field association");
            if (annotation.equals(SELECTION) && (literal.selection == null || literal.selection.isEmpty() || literal.role == null))
                throw invalid("Missing population selection");
        }
    }
}
