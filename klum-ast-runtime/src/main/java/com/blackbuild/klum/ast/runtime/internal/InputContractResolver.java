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

import com.blackbuild.klum.ast.Struct.ConstraintProjection.Policy;
import com.blackbuild.klum.ast.Struct.Naming.Profile;
import com.blackbuild.klum.ast.runtime.KlumModelException;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.blackbuild.klum.ast.runtime.internal.BuilderContractReader.*;
import static com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema.*;

/**
 * Resolves validated public declaration facts into one effective structured-input contract.
 * Eligibility and source identity precede literal/profile naming and collision handling; native candidates
 * survive until the effective hierarchy profile is known. Field and operation roles never share source facts.
 * Construction keys retain position-independent authority; consumers select member/keyless use positions.
 * Owned targets remain loader-local type references, so recursive ownership never creates cyclic values here.
 * One resolver belongs to one preparation/projection session. Its reader caches exact declaration identities
 * only for that session; no classloader/global cache, Model inspection or user-code execution is involved.
 * Consumers still visit the admitted owned closure, including omitted children, before execution/publication.
 */
final class InputContractResolver {
    private static final Set<String> NUMBERS = Set.of("byte", "short", "int", "long", "float", "double",
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float",
            "java.lang.Double", "java.math.BigInteger", "java.math.BigDecimal");
    private static final Set<String> COLLECTIONS = Set.of("java.util.List", "java.util.Set", "java.util.SortedSet", "java.util.NavigableSet", "java.util.EnumSet");
    private static final Set<String> MAPS = Set.of("java.util.Map", "java.util.SortedMap", "java.util.NavigableMap");
    private final BuilderContractReader reader = new BuilderContractReader();
    private final SelectedConverterParameterReader converterReader = new SelectedConverterParameterReader();
    private final Map<Class<?>, Map<String, ConverterConstraints>> converterConstraints = new LinkedHashMap<>();

    CompiledBuilderSchema read(Class<?> model) {
        try {
            ClassLoader loader = model.getClassLoader();
            CompiledBuilderSchema schema = read(Class.forName(model.getName() + BUILDER_SUFFIX, false, loader),
                    Class.forName(model.getName() + "_DSL$Factory", false, loader));
            if (schema.model() != model) throw invalid("Root Model identity disagrees with Builder self bound");
            return schema;
        } catch (ClassNotFoundException | LinkageError failure) { throw failure(model.getName(), failure); }
    }

    CompiledBuilderSchema read(Class<?> builder, Class<?> factory) {
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
            declarations.addAll(reader.scope(factory));
            Map<String, Entry> entries = new LinkedHashMap<>();
            Key key = facts.key();
            if (naming == Profile.KEBAB_CASE) {
                List<Entry> candidates = facts.entries().stream().map(InputContractResolver::profileEntry).toList();
                entries.putAll(admitted(candidates, rejected, true));
                if (key != null) {
                    key = profileKey(key);
                }
            } else {
                entries.putAll(admitted(facts.entries(), rejected, false));
            }
            return new CompiledBuilderSchema(builder, model, entries, rejected.stream().distinct()
                    .sorted(Comparator.comparing(Rejection::source).thenComparing(Rejection::reason)).toList(),
                    declarations, key, reader.metadata(builder).key(), keyCollisions(key, entries, rejected), constraintProfiles(builder));
        } catch (ReflectiveOperationException | LinkageError | IOException | RuntimeException failure) {
            throw failure(builder.getName(), failure);
        }
    }

    /** Select only after override, exclusion and effective hierarchy naming have been resolved. */
    private Map<String, Entry> admitted(List<Entry> candidates, List<Rejection> rejected, boolean strict) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        candidateGroups(candidates, rejected).forEach((name, group) -> {
            Entry selected = selectOperation(name, group, rejected, strict);
            if (selected != null) entries.put(name, selected);
        });
        return entries;
    }

    private static Map<String, List<Entry>> candidateGroups(List<Entry> candidates, List<Rejection> rejected) {
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
        return names;
    }

    /** Preserve legacy parent collisions, but resolve String-operation groups only after descendant overrides. */
    private void retainUnmarkedCollisions(List<Entry> entries, List<Rejection> rejected) {
        candidateGroups(entries, rejected).forEach((name, group) -> {
            if (group.size() > 1 && group.stream().noneMatch(InputContractResolver::stringSelectionCandidate)) {
                rejectCollision(name, group, rejected, false);
            }
        });
    }

    private static boolean stringSelectionCandidate(Entry entry) {
        return entry.role() == Role.OPERATION && entry.payload().kind() == Kind.SCALAR
                && (entry.payload().type() == String.class || entry.payload().type() == CharSequence.class)
                && entry.methods().size() == 1;
    }

    private Entry selectOperation(String name, List<Entry> group, List<Rejection> rejected, boolean strict) {
        List<Entry> marked = group.stream().filter(this::authoritativeString).toList();
        if (marked.size() > 1) {
            throw invalid("Multiple @Struct.Operation candidates for '" + name + "': "
                    + marked.stream().map(Entry::source).collect(Collectors.joining(", ")));
        }
        if (marked.size() == 1) return marked.get(0);
        if (group.size() == 1) {
            Entry unique = group.get(0);
            if (unique.payload().type() != CharSequence.class) return unique;
            rejected.add(new Rejection(unique.source(), "CharSequence scalar operations are not admitted; expose an exact String operation"));
            return null;
        }
        rejectCollision(name, group, rejected, strict);
        return null;
    }

    private static void rejectCollision(String name, List<Entry> group, List<Rejection> rejected, boolean strict) {
        String all = group.stream().map(Entry::source).sorted().collect(Collectors.joining(", "));
        if (strict) throw invalid("KEBAB_CASE external-name collision for '" + name + "': " + all);
        group.forEach(entry -> rejected.add(new Rejection(entry.source(), "external-name collision for '" + name + "': " + all, name)));
    }

    private boolean authoritativeString(Entry entry) {
        if (!stringSelectionCandidate(entry) || entry.payload().type() != String.class) return false;
        try {
            return Boolean.TRUE.equals(reader.metadata(entry.contract()).methods()
                    .get(signature(entry.methods().get(0))).authoritative());
        } catch (IOException | ClassNotFoundException failure) {
            throw failure(entry.contract().getName(), failure);
        }
    }

    private record Facts(List<Entry> entries, List<Rejection> rejections, List<Declaration> declarations, Key key) { }
    private record BuilderContext(Class<?> builder, Class<?> model, Metadata metadata, boolean kebab) { }

    private Facts collect(Class<?> builder, Map<Class<?>, Facts> known, boolean nativeCandidates) throws IOException, ClassNotFoundException {
        Facts existing = known.get(builder);
        if (existing != null) return existing;
        List<Entry> entries = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
        List<Declaration> declarations = new ArrayList<>();
        requirePublic(builder);
        Class<?> model = modelOf(builder);
        if (model == null) throw invalid("Missing Builder self bound: " + builder.getName());
        Metadata metadata = reader.metadata(builder);
        if (!metadata.schema()) throw invalid("Missing generated input enrichment on " + builder.getName());
        for (Invisible exclusion : metadata.invisible())
            rejections.add(new Rejection(model.getName() + "#" + exclusion.member(), exclusion.reason()));
        Map<String, Method> accessors = new LinkedHashMap<>();
        Map<String, List<Method>> selections = new LinkedHashMap<>();
        BuilderContext context = new BuilderContext(builder, model, metadata, nativeCandidates);
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
            rejectOverriddenFields(context, inherited.entries(), rejections);
            inherited.entries().stream().filter(entry -> !overriddenOperation(context, entry)).forEach(entries::add);
            rejections.addAll(inherited.rejections());
            declarations.addAll(inherited.declarations());
        }
        validateKeyMetadata(metadata, key, builder);
        if (!nativeCandidates) retainUnmarkedCollisions(entries, rejections);
        List<Entry> resolved = List.copyOf(entries);
        Facts facts = new Facts(resolved, List.copyOf(rejections), declarations.stream().distinct().toList(), key);
        known.put(builder, facts);
        return facts;
    }

    /** An override owns its own names, exclusion and selection intent; parent annotations cannot select different code. */
    private static boolean overriddenOperation(BuilderContext context, Entry entry) {
        if (entry.role() != Role.COMMAND && (entry.role() != Role.OPERATION || entry.payload().kind() != Kind.SCALAR
                || entry.payload().type() != String.class && entry.payload().type() != CharSequence.class)) return false;
        return entry.methods().stream().anyMatch(inherited -> overridden(inherited, declaredMethods(context)));
    }

    private static boolean overridden(Method inherited, Method[] declared) {
        return Arrays.stream(declared).anyMatch(method -> !Modifier.isStatic(method.getModifiers())
                && method.getName().equals(inherited.getName())
                && Arrays.equals(method.getParameterTypes(), inherited.getParameterTypes()));
    }

    private void rejectOverriddenFields(BuilderContext context, List<Entry> inherited, List<Rejection> rejections)
            throws IOException, ClassNotFoundException {
        Method[] declared = declaredMethods(context);
        for (Entry entry : inherited) {
            Metadata origin = reader.metadata(entry.contract());
            for (Method selected : entry.methods()) {
                boolean converter = origin.methods().get(signature(selected)).converter() != null;
                boolean stringField = entry.role() == Role.PROPERTY && entry.payload().kind() == Kind.SCALAR
                        && entry.payload().type() == String.class
                        && Arrays.equals(selected.getParameterTypes(), new Class<?>[]{String.class});
                if ((converter || stringField) && overridden(selected, declared)) {
                    String originKind = converter ? "converter overload " : "String field operation ";
                    rejections.add(new Rejection(entry.source(), "inherited " + originKind + selected.getName()
                            + "(String) is overridden by " + namingOrigin(context.builder())
                            + "; expose one supported non-overridden String route"));
                }
            }
        }
    }

    /** Declaring public contracts are the sole authority, including explicit absence and source/artifact identity. */
    private Profile resolveNaming(Class<?> builder) throws IOException, ClassNotFoundException {
        Map<Class<?>, Metadata> hierarchy = new LinkedHashMap<>();
        namingHierarchy(builder, hierarchy);
        List<Map.Entry<Class<?>, Metadata>> explicit = hierarchy.entrySet().stream()
                .filter(entry -> Boolean.TRUE.equals(entry.getValue().namingExplicit())).toList();
        if (explicit.isEmpty()) return Profile.IDENTITY;
        String selected = explicit.get(0).getValue().namingProfile();
        if (explicit.stream().anyMatch(entry -> !entry.getValue().namingProfile().equals(selected))) {
            String declarations = explicit.stream().map(entry -> namingOrigin(entry.getKey()) + " @Struct.Naming("
                    + entry.getValue().namingProfile() + ")").sorted().collect(Collectors.joining(", "));
            throw invalid("Conflicting explicit input profiles: " + declarations);
        }
        return Profile.valueOf(selected);
    }

    private void namingHierarchy(Class<?> builder, Map<Class<?>, Metadata> hierarchy)
            throws IOException, ClassNotFoundException {
        if (hierarchy.containsKey(builder)) return;
        requirePublic(builder);
        Metadata declared = reader.metadata(builder);
        hierarchy.put(builder, declared);
        for (Class<?> parent : Arrays.stream(builder.getInterfaces()).sorted(Comparator.comparing(Class::getName)).toList()) {
            if (parent.getName().endsWith(BUILDER_SUFFIX)) {
                namingHierarchy(parent, hierarchy);
            }
        }
    }

    private static Entry profileEntry(Entry entry) {
        if (!entry.nameOrigin().equals(IDENTITY_NAME)) return entry;
        String name = InputNamingProfiles.kebab(entry.externalName(), entry.source());
        return new Entry(name, entry.role(), entry.source(), entry.contract(), entry.operation(), entry.methods(),
                entry.payload(), entry.keyType(), entry.excludedKey(), entry.fixedChildKey(),
                KEBAB_NAME, entry.documentation(), entry.schemaKey(), entry.sourceDeprecated(), entry.examples());
    }

    private Key profileKey(Key key) throws IOException, ClassNotFoundException {
        Metadata declaring = reader.metadata(key.contract());
        Literals literal = declaring.keyMember();
        if (literal == null) {
            literal = declaring.methods().values().stream().filter(value -> key.nativeName().equals(value.key())).findFirst().orElseThrow();
        }
        if (!literal.names().rename().isEmpty() || key.ignored()) return key;
        return new Key(key.nativeName(), InputNamingProfiles.kebab(key.nativeName(), key.source()), key.ignored(),
                key.source(), key.contract(), key.sourceDeprecated(), key.ignoreReason(), key.examples());
    }

    private static Key inheritKey(Key key, Key inherited) {
        if (inherited == null) return key;
        if (key != null && !key.equals(inherited)) throw invalid("Conflicting construction key declarations");
        return inherited;
    }

    private static void validateKeyMetadata(Metadata metadata, Key key, Class<?> builder) {
        if (metadata.keyInput() != (key != null) || !metadata.key().equals(key == null ? "" : key.nativeName())) {
            throw invalid("Construction key enrichment disagrees with selected Schema key on " + namingOrigin(builder));
        }
    }

    private static Key declaredKey(BuilderContext context) {
        Literals fallback = context.metadata().keyMember();
        Key key = fallback == null ? null : constructionKey(context, fallback);
        if (fallback != null && Arrays.stream(context.builder().getDeclaredMethods()).anyMatch(method ->
                method.getParameterCount() == 0 && method.getName().equals(keyGetterName(fallback.key()))))
            throw invalid("Type-level construction key fact has a surviving public getter on " + context.builder().getName());
        for (Method method : context.builder().getDeclaredMethods()) {
            Literals literal = context.metadata().methods().get(signature(method));
            if (literal.key() == null) continue;
            if (key != null || method.getParameterCount() != 0 || method.getReturnType() != String.class
                    || !method.getName().equals(keyGetterName(literal.key()))
                    || literal.field() != null || literal.selection() != null || literal.operation())
                throw invalid("Invalid construction key getter " + method);
            key = constructionKey(context, literal);
        }
        return key;
    }

    private static String keyGetterName(String name) {
        return "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static Key constructionKey(BuilderContext context, Literals literal) {
        return new Key(literal.key(), literal.names().rename().isEmpty() ? literal.key() : literal.names().rename(),
                literal.names().ignored(), context.model().getName() + "#" + literal.key(), context.builder(), literal.sourceDeprecated(), ignoreReason(literal.names()), literal.examples());
    }

    private static void collectDeclarations(BuilderContext context,
                                            Map<String, Method> accessors, Map<String, List<Method>> selections,
                                            List<Entry> entries, List<Rejection> rejections, List<Declaration> declarations) {
        Metadata metadata = context.metadata();
        for (Method method : Arrays.stream(declaredMethods(context)).sorted(Comparator.comparing(BuilderContractReader::descriptor)).toList()) {
            Literals literal = metadata.methods().get(signature(method));
            if (literal == null) throw invalid("Missing exact public declaration " + method);
            declarations.add(declaration(method, literal));
            if (literal.field() != null && (!fieldAccessor(method, literal.field())
                    || accessors.putIfAbsent(literal.field(), method) != null))
                throw invalid("Duplicate or invalid field accessor association " + literal.field());
            if (literal.selection() != null) selections.computeIfAbsent(literal.selection(), ignored -> new ArrayList<>()).add(method);
            if (literal.operation() && !literal.query()) operation(method, literal, context.model(), entries, rejections);
        }
    }

    private static Method[] declaredMethods(BuilderContext context) {
        try {
            return context.builder().getDeclaredMethods();
        } catch (LinkageError failure) {
            List<String> candidates = context.metadata().methods().values().stream()
                    .filter(literal -> literal.converter() != null)
                    .map(literal -> converterCandidate(context, literal)).toList();
            throw new KlumModelException("Cannot resolve public signatures on " + namingOrigin(context.builder())
                    + "; compiler-recorded converter candidates: " + candidates
                    + "; supply the complete API classpath and recompile matched domain/Schema artifacts: "
                    + failure.getMessage(), failure);
        }
    }

    private static String converterCandidate(BuilderContext context, Literals selected) {
        Literals field = context.metadata().methods().values().stream()
                .filter(literal -> selected.selection().equals(literal.field())).findFirst().orElse(null);
        ConverterSelection converter = selected.converter();
        String external = field == null ? "<unavailable>" : externalFieldName(context, selected.selection(), field);
        return "field '" + selected.selection() + "', external input '" + external + "', selected "
                + converter.owner() + "#" + converter.name() + converter.descriptor()
                + " parameter[" + converter.parameter() + "]";
    }

    private static String externalFieldName(BuilderContext context, String field, Literals literal) {
        if (!literal.names().rename().isEmpty()) return literal.names().rename();
        return context.kebab() ? InputNamingProfiles.kebab(field, context.model().getName() + "#" + field) : field;
    }

    private static boolean fieldAccessor(Method method, String field) {
        return method.getParameterCount() == 0 || method.getParameterCount() == 1
                && method.getName().equals("set" + Character.toUpperCase(field.charAt(0)) + field.substring(1));
    }

    private void collectField(BuilderContext context, String field, Method accessor,
                                     List<Method> methods, List<Entry> entries, List<Rejection> rejections)
            throws IOException, ClassNotFoundException {
        Metadata metadata = context.metadata();
        Literals literal = metadata.methods().get(signature(accessor));
        String source = context.model().getName() + "#" + field;
        Type fieldType = accessor.getParameterCount() == 0 ? accessor.getGenericReturnType() : accessor.getGenericParameterTypes()[0];
        ConverterConstraints converter = converter(context, field, fieldType, literal, methods);
        String reason;
        if (converter == null) {
            reason = fieldReason(fieldType, literal);
        } else {
            reason = literal.names().ignored() ? ignoreReason(literal.names()) : null;
        }
        if (reason != null) {
            rejections.add(new Rejection(source, reason));
            return;
        }
        Role role = selectedRole(metadata, methods, source);
        if (role == Role.OWNED_OBJECT && literal.schemaKey() == null)
            throw invalid("Missing direct relationship key-authority metadata on " + source);
        if (literal.schemaKey() != null && (role != Role.OWNED_OBJECT || literal.fixed() && !literal.schemaKey()))
            throw invalid("Schema child-key authority requires OWNED_OBJECT and agrees with fixed key: " + source);
        Payload payload = converter == null ? fieldPayload(fieldType, role, source)
                : new Payload(Kind.SCALAR, String.class, null, null, null);
        String key = childKey(payload, role, literal, source);
        validateSelection(role, payload, methods, source);
        String name = literal.names().rename().isEmpty() ? field : literal.names().rename();
        entries.add(new Entry(name, role, source, context.builder(), methods.get(0).getName(), List.copyOf(methods), payload,
                role == Role.KEYED_OWNED_MAP ? String.class.getName() : null, key, literal.fixed() ? field : null,
                literal.names().origin(), literal.doc(), Boolean.TRUE.equals(literal.schemaKey()), literal.sourceDeprecated(), literal.examples()));
    }

    private ConverterConstraints converter(BuilderContext context, String field, Type fieldType, Literals literal,
                                             List<Method> methods) {
        List<Method> converters = methods.stream()
                .filter(method -> context.metadata().methods().get(signature(method)).converter() != null).toList();
        if (converters.size() != literal.converterCount())
            throw invalid("Converter selection completeness mismatch for " + namingOrigin(context.builder()) + "#" + field);
        if (converters.isEmpty()) return null;
        Method method = converters.get(0);
        ConverterSelection selected = context.metadata().methods().get(signature(method)).converter();
        String location = namingOrigin(context.builder()) + "#" + field + ", external input '"
                + externalFieldName(context, field, literal) + "', selected "
                + selected.owner() + "#" + selected.name() + selected.descriptor() + " parameter[" + selected.parameter() + "]";
        try {
            Class<?> domain = rawType(fieldType);
            if (domain == null || selected.parameterFacts() && !(fieldType instanceof Class<?>) || methods.size() != 1
                    || !method.getName().equals(field) || method.getReturnType() != domain
                    || !Arrays.equals(method.getParameterTypes(), new Class<?>[]{String.class})
                    || method.isSynthetic() || method.isBridge() || !literal.exclusion().isEmpty()
                    || selectedRole(context.metadata(), methods, location) != Role.PROPERTY)
                throw invalid("Converter selection disagrees with field/overload");
            if (!selected.parameterFacts() && !validStructuralConverterDescriptor(selected))
                throw invalid("Structural converter descriptor disagrees with field/overload");
            validateSelection(Role.PROPERTY, new Payload(Kind.SCALAR, String.class, null, null, null), methods, location);
            // Structural routes carry provenance, but only C1 owns selected-parameter inspection.
            ConverterConstraints constraints = selected.parameterFacts() ? converterReader.read(domain, selected)
                    : new ConverterConstraints("", "", Policy.OMIT, List.of());
            converterConstraints.computeIfAbsent(context.builder(), ignored -> new LinkedHashMap<>()).put(field, constraints);
            return constraints;
        } catch (ReflectiveOperationException | IOException | LinkageError | RuntimeException failure) {
            throw invalid("Cannot resolve converter for " + location + ": " + failure.getMessage()
                    + "; expose one supported route and recompile matched domain/Schema artifacts with the complete API classpath");
        }
    }

    private static boolean validStructuralConverterDescriptor(ConverterSelection selected) {
        if (selected.name().equals("<init>")) return selected.descriptor().equals("(Ljava/lang/String;)V");
        // The source may return a subtype; the exact destination type belongs to the public Builder projection.
        return selected.descriptor().matches("\\(Ljava/lang/String;\\)L[^.;\\[()/]+(?:/[^.;\\[()/]+)*;");
    }

    private static Role selectedRole(Metadata metadata, List<Method> methods, String source) {
        if (methods.isEmpty()) throw invalid("Missing compiler input/population selection for " + source);
        Set<String> roles = methods.stream().map(method -> metadata.methods().get(signature(method)).role()).collect(Collectors.toSet());
        if (roles.size() != 1 || methods.stream().map(Method::getName).distinct().count() != 1)
            throw invalid("Conflicting field selection for " + source);
        Role role = Role.valueOf(roles.iterator().next());
        if (role == Role.OPERATION || role == Role.COMMAND) throw invalid("Field cannot select authored-operation role");
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

    private String childKey(Payload payload, Role role, Literals literal, String source)
            throws IOException, ClassNotFoundException {
        if (literal.fixed() && (role != Role.OWNED_OBJECT || reader.metadata(payload.builder()).key().isEmpty()))
            throw invalid("Fixed direct-child key requires keyed OWNED_OBJECT: " + source);
        String key = role == Role.KEYED_OWNED_MAP ? reader.metadata(payload.builder()).key() : null;
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
        NameFacts names = literal.operationNames();
        String reason = null;
        if (names.ignored()) reason = ignoreReason(names);
        else if (literal.operationRole().equals("LIFECYCLE")) reason = "lifecycle methods are not structured commands";
        else if (method.getParameterCount() == 0) {
            String name = names.rename().isEmpty() ? method.getName() : names.rename();
            entries.add(new Entry(name, Role.COMMAND, source, method.getDeclaringClass(), method.getName(), List.of(method),
                    new Payload(Kind.EMPTY_OBJECT, Map.class, null, null, null), null, null, null, names.origin(),
                    literal.doc(), false, literal.operationDeprecated(), literal.operationExamples()));
            return;
        } else if (method.getParameterCount() != 1) reason = "@Builder.Method must declare zero arguments or exactly one payload";
        Payload payload = reason == null ? payload(method.getGenericParameterTypes()[0], false) : null;
        // Preserve String/CharSequence competition without admitting CharSequence dispatch before S3b.
        if (reason == null && method.getParameterTypes()[0] == CharSequence.class) {
            payload = new Payload(Kind.SCALAR, CharSequence.class, null, null, null);
        }
        if (reason == null && payload == null) reason = unsupportedReason(method.getGenericParameterTypes()[0]);
        if (reason != null) { rejections.add(new Rejection(source, reason)); return; }
        String name = names.rename().isEmpty() ? method.getName() : names.rename();
        entries.add(new Entry(name, Role.OPERATION, source, method.getDeclaringClass(), method.getName(), List.of(method), payload,
                null, null, null, names.origin(), literal.doc(), false, literal.operationDeprecated(), literal.operationExamples()));
    }

    private static String fieldReason(Type type, Literals literal) {
        if (literal.names().ignored()) return ignoreReason(literal.names());
        if (!literal.exclusion().isEmpty()) {
            String detail = literal.detail();
            if (literal.exclusion().equals("POPULATION")) {
                Class<?> raw = rawType(type);
                Type item = containerValue(type);
                detail += "(" + (raw != null && Map.class.isAssignableFrom(raw) ? "java.lang.String, " : "") + rawType(item).getName() + ")";
            }
            return exclusionReason(literal.exclusion(), detail);
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

    /** Source diagnostics historically use JVM array names; this spelling is available directly from Class. */
    private static String sourceDescriptor(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getName).collect(Collectors.joining(", "))
                + "):" + method.getReturnType().getName();
    }

    private static KlumModelException failure(String root, Throwable cause) {
        return new KlumModelException("Cannot read compiled Builder input contract for " + root
                + "; recompile and package the Schema with generated input enrichment and supply its complete API/runtime classpath: "
                + cause.getMessage(), cause);
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

    private static List<String> keyCollisions(Key key, Map<String, Entry> entries, List<Rejection> rejections) {
        if (key == null || key.ignored()) return List.of();
        List<String> collisions = new ArrayList<>();
        Entry entry = entries.get(key.externalName());
        if (entry != null) collisions.add(entry.source());
        rejections.stream().filter(rejection -> key.externalName().equals(rejection.collisionName()))
                .map(Rejection::source).forEach(collisions::add);
        return collisions.stream().distinct().sorted().toList();
    }

    private Map<Class<?>, ConstraintProfile> constraintProfiles(Class<?> builder)
            throws IOException, ClassNotFoundException {
        Map<Class<?>, ConstraintProfile> profiles = new LinkedHashMap<>();
        collectConstraintProfiles(builder, profiles);
        return profiles;
    }

    private void collectConstraintProfiles(Class<?> builder, Map<Class<?>, ConstraintProfile> profiles)
            throws IOException, ClassNotFoundException {
        if (profiles.containsKey(builder)) return;
        Metadata metadata = reader.metadata(builder);
        Map<String, FieldConstraints> fields = new LinkedHashMap<>();
        for (Literals literal : metadata.methods().values()) {
            if (literal.field() == null) continue;
            FieldConstraints constraints = new FieldConstraints(Policy.valueOf(literal.constraintProjection()), literal.constraints());
            if (fields.putIfAbsent(literal.field(), constraints) != null)
                throw invalid("duplicate constraint source field #" + literal.field());
        }
        profiles.put(builder, new ConstraintProfile(metadata.groupRedefined(), fields, converterConstraints.getOrDefault(builder, Map.of())));
        for (Class<?> parent : builder.getInterfaces()) {
            if (parent.getName().endsWith(BUILDER_SUFFIX)) collectConstraintProfiles(parent, profiles);
        }
    }
}
