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
import groovy.lang.Closure;
import org.apache.groovy.util.BeanUtils;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
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
    private static final String CONVERTER_SIGNATURE_MISMATCH = "Converter selection disagrees with field/overload";
    private final BuilderContractReader reader = new BuilderContractReader();
    private final SelectedConverterParameterReader converterReader = new SelectedConverterParameterReader();
    private final Map<Class<?>, Map<String, ConverterConstraints>> converterConstraints = new LinkedHashMap<>();

    CompiledBuilderSchema read(Class<?> model) {
        return readModel(model, false);
    }

    CompiledBuilderSchema readTemplate(Class<?> model) {
        return readModel(model, true);
    }

    private CompiledBuilderSchema readModel(Class<?> model, boolean template) {
        try {
            ClassLoader loader = model.getClassLoader();
            CompiledBuilderSchema schema = read(Class.forName(model.getName() + BUILDER_SUFFIX, false, loader),
                    Class.forName(model.getName() + "_DSL$Factory", false, loader), template);
            if (schema.model() != model) throw invalid("Root Model identity disagrees with Builder self bound");
            return schema;
        } catch (ClassNotFoundException | LinkageError failure) { throw failure(model.getName(), failure); }
    }

    CompiledBuilderSchema read(Class<?> builder, Class<?> factory) {
        return read(builder, factory, false);
    }

    private CompiledBuilderSchema read(Class<?> builder, Class<?> factory, boolean template) {
        try {
            requirePublic(builder);
            requirePublic(factory);
            Class<?> model = modelOf(builder);
            if (model == null || !matchesFactoryModel(factory, model, template))
                throw invalid("Builder self bound and Factory result disagree");
            Profile naming = resolveNaming(builder);
            Facts facts = collect(builder, new LinkedHashMap<>(), naming == Profile.KEBAB_CASE);
            List<Rejection> rejected = new ArrayList<>(facts.rejections());
            reader.validateScope(factory);
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
            validateRequiredFields(builder, entries);
            return new CompiledBuilderSchema(builder, model, entries, rejected.stream().distinct()
                    .sorted(Comparator.comparing(Rejection::source).thenComparing(Rejection::reason)).toList(),
                    key, reader.metadata(builder).key(), keyCollisions(key, entries, rejected), constraintProfiles(builder));
        } catch (ReflectiveOperationException | LinkageError | IOException | RuntimeException failure) {
            throw failure(builder.getName(), failure);
        }
    }

    /** Every true declaration must survive as that exact field, after naming, overrides and operation selection. */
    private void validateRequiredFields(Class<?> builder, Map<String, Entry> entries) throws IOException, ClassNotFoundException {
        Map<Class<?>, Metadata> hierarchy = new LinkedHashMap<>();
        namingHierarchy(builder, hierarchy);
        for (Map.Entry<Class<?>, Metadata> declaration : hierarchy.entrySet()) {
            for (Literals literal : declaration.getValue().methods().values()) {
                if (!literal.rawInputRequired()) continue;
                String source = schemaName(declaration.getKey()) + "#" + literal.field();
                boolean selected = entries.values().stream().anyMatch(entry -> entry.rawInputRequired()
                        && entry.contract() == declaration.getKey() && entry.source().equals(source));
                if (!selected) {
                    throw invalid("rawInputRequired lacks its exact selected field " + source
                            + " on " + namingOrigin(declaration.getKey()));
                }
            }
        }
    }

    private static boolean matchesFactoryModel(Class<?> factory, Class<?> model, boolean template) throws ClassNotFoundException {
        if (template && Modifier.isAbstract(model.getModifiers())) {
            Class<?> templateFactory = Class.forName(factory.getName() + "$Template", false, factory.getClassLoader());
            requirePublic(templateFactory);
            return Arrays.stream(templateFactory.getMethods()).anyMatch(method -> method.getName().equals("With")
                    && method.getParameterCount() == 0 && method.getReturnType() == model);
        }
        return Arrays.stream(factory.getMethods()).anyMatch(method -> method.getName().equals("One") && method.getReturnType() == model);
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
        if (group.size() == 1) return group.get(0);
        rejectCollision(name, group, rejected, strict);
        return null;
    }

    private static void rejectCollision(String name, List<Entry> group, List<Rejection> rejected, boolean strict) {
        String all = group.stream().map(Entry::source).sorted().collect(Collectors.joining(", "));
        if (strict) throw invalid("KEBAB_CASE external-name collision for '" + name + "': " + all);
        group.forEach(entry -> rejected.add(new Rejection(entry.source(), "external-name collision for '" + name + "': " + all, name)));
    }

    private boolean authoritativeString(Entry entry) {
        if (!stringSelectionCandidate(entry)) return false;
        try {
            return Boolean.TRUE.equals(reader.metadata(entry.contract()).methods()
                    .get(signature(entry.methods().get(0))).authoritative());
        } catch (IOException | ClassNotFoundException failure) {
            throw failure(entry.contract().getName(), failure);
        }
    }

    private record Facts(List<Entry> entries, List<Rejection> rejections, Key key) { }
    private record BuilderContext(Class<?> builder, Class<?> model, Metadata metadata, boolean kebab) { }

    private Facts collect(Class<?> builder, Map<Class<?>, Facts> known, boolean nativeCandidates) throws IOException, ClassNotFoundException {
        Facts existing = known.get(builder);
        if (existing != null) return existing;
        List<Entry> entries = new ArrayList<>();
        List<Rejection> rejections = new ArrayList<>();
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
        collectDeclarations(context, accessors, selections, entries, rejections);
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
            rejectRedeclaredVirtualScopes(context, inherited.entries(), entries, rejections);
            inherited.entries().stream().filter(entry -> !overriddenOperation(context, entry)).forEach(entries::add);
            rejections.addAll(inherited.rejections());
        }
        validateKeyMetadata(metadata, key, builder);
        if (!nativeCandidates) retainUnmarkedCollisions(entries, rejections);
        List<Entry> resolved = List.copyOf(entries);
        Facts facts = new Facts(resolved, List.copyOf(rejections), key);
        known.put(builder, facts);
        return facts;
    }

    /** An override owns its own names, exclusion and selection intent; parent annotations cannot select different code. */
    private static boolean overriddenOperation(BuilderContext context, Entry entry) {
        if (entry.role() != Role.COMMAND && entry.role() != Role.VIRTUAL_OBJECT && (entry.role() != Role.OPERATION || entry.payload().kind() != Kind.SCALAR
                || entry.payload().type() != String.class && entry.payload().type() != CharSequence.class)) return false;
        return entry.methods().stream().anyMatch(inherited -> overridden(inherited, declaredMethods(context)));
    }

    private static void rejectRedeclaredVirtualScopes(BuilderContext context, List<Entry> inherited,
                                                      List<Entry> entries, List<Rejection> rejections) {
        for (Entry parent : inherited) {
            if (parent.role() != Role.VIRTUAL_OBJECT || !overridden(parent.methods().get(0), declaredMethods(context))) continue;
            String reason = "redeclared virtual sink has no unique ordinary target; use a non-overridden virtual declaration";
            rejections.add(new Rejection(parent.source(), reason));
            entries.stream().filter(entry -> entry.role() == Role.VIRTUAL_OBJECT && entry.operation().equals(parent.operation()))
                    .forEach(entry -> rejections.add(new Rejection(entry.source(), reason)));
        }
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
            rejectOverriddenField(context, entry, declared, rejections);
        }
    }

    private void rejectOverriddenField(BuilderContext context, Entry entry, Method[] declared, List<Rejection> rejections)
            throws IOException, ClassNotFoundException {
        Metadata origin = reader.metadata(entry.contract());
        for (Method selected : entry.methods()) {
            boolean converter = origin.methods().get(signature(selected)).converter() != null;
            boolean stringField = entry.role() == Role.PROPERTY && entry.payload().kind() == Kind.SCALAR
                    && entry.payload().type() == String.class
                    && Arrays.equals(selected.getParameterTypes(), new Class<?>[]{String.class});
            if ((converter || stringField) && overridden(selected, declared)) {
                String originKind = converter ? "converter overload " : "String field operation ";
                rejections.add(new Rejection(entry.source(), "inherited " + originKind + selected.getName()
                        + "(" + selected.getParameterTypes()[0].getSimpleName() + ") is overridden by " + namingOrigin(context.builder())
                        + "; expose one supported non-overridden scalar route"));
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
        return new Entry(name, entry.role(), entry.source(), entry.contract(), entry.operation(), entry.methods(), entry.dispatch(),
                entry.payload(), entry.keyType(), entry.excludedKey(), entry.fixedChildKey(),
                KEBAB_NAME, entry.documentation(), entry.schemaKey(), entry.sourceDeprecated(), entry.examples(), entry.alternatives(), entry.rawInputRequired());
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

    private void collectDeclarations(BuilderContext context,
                                            Map<String, Method> accessors, Map<String, List<Method>> selections,
                                            List<Entry> entries, List<Rejection> rejections) throws IOException, ClassNotFoundException {
        Metadata metadata = context.metadata();
        for (Method method : Arrays.stream(declaredMethods(context)).sorted(Comparator.comparing(BuilderContractReader::descriptor)).toList()) {
            Literals literal = metadata.methods().get(signature(method));
            if (literal == null) throw invalid("Missing exact public declaration " + method);
            validateDeclaration(method, literal);
            validateRequiredFieldAccessor(context, method, literal);
            if (literal.field() != null && (!fieldAccessor(method, literal.field())
                    || accessors.putIfAbsent(literal.field(), method) != null))
                throw invalid("Duplicate or invalid field accessor association " + literal.field());
            if (literal.selection() != null) selections.computeIfAbsent(literal.selection(), ignored -> new ArrayList<>()).add(method);
            if (literal.scopeCreator() != null) {
                virtualScope(method, literal, context, entries, rejections);
            } else if (literal.operation() && !literal.query()) {
                operation(method, literal, context.model(), entries, rejections);
            }
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

    private static void validateRequiredFieldAccessor(BuilderContext context, Method method, Literals literal) {
        if (!literal.rawInputRequired()) return;
        String suffix = BeanUtils.capitalize(literal.field());
        boolean exact = method.getParameterCount() == 0 ? method.getName().equals("get" + suffix)
                : method.getParameterCount() == 1 && method.getName().equals("set" + suffix);
        if (!exact) {
            throw invalid("rawInputRequired lacks exact field accessor association " + context.model().getName()
                    + "#" + literal.field() + " on " + namingOrigin(context.builder()));
        }
    }

    private void collectField(BuilderContext context, String field, Method accessor,
                                     List<Method> methods, List<Entry> entries, List<Rejection> rejections)
            throws IOException, ClassNotFoundException {
        Metadata metadata = context.metadata();
        Literals literal = metadata.methods().get(signature(accessor));
        String source = context.model().getName() + "#" + field;
        Type fieldType = accessor.getParameterCount() == 0 ? accessor.getGenericReturnType() : accessor.getGenericParameterTypes()[0];
        ConverterConstraints converter = converter(context, field, fieldType, literal, methods);
        if (literal.alternatives().isEmpty() && methods.stream().anyMatch(method ->
                Role.LITERAL_ALTERNATIVE_MAP.name().equals(metadata.methods().get(signature(method)).role())))
            throw invalid("Missing literal alternative association on " + source);
        if (!literal.alternatives().isEmpty()) {
            literalAlternative(context, fieldType, literal, accessor, methods, entries, rejections);
            return;
        }
        Role role = fieldRole(metadata, literal, accessor, methods, fieldType, source);
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
        if (role == null) role = selectedRole(metadata, methods, source);
        if (role == Role.OWNED_OBJECT && literal.schemaKey() == null)
            throw invalid("Missing direct relationship key-authority metadata on " + source);
        if (literal.schemaKey() != null && (role != Role.OWNED_OBJECT || literal.fixed() && !literal.schemaKey()))
            throw invalid("Schema child-key authority requires OWNED_OBJECT and agrees with fixed key: " + source);
        Payload payload = converter == null ? fieldPayload(fieldType, role, source)
                : converterPayload(fieldType, methods.get(0));
        if (converter == null && role == Role.PROPERTY)
            payload = nativePopulationPayload(payload, methods, source);
        String key = childKey(payload, role, literal, source);
        boolean relationship = role == Role.OWNED_OBJECT || role == Role.KEYED_OWNED_MAP;
        List<Method> calls = relationship ? List.of() : methods;
        Method dispatch = resolveDispatch(role, payload, methods, source);
        String name = literal.names().rename().isEmpty() ? field : literal.names().rename();
        String operation = switch (role) {
            case OWNED_SEQUENCE -> literal.sequenceElement();
            case OWNED_OBJECT, KEYED_OWNED_MAP -> field;
            default -> methods.get(0).getName();
        };
        entries.add(new Entry(name, role, source, context.builder(), operation, calls, dispatch, payload,
                role == Role.KEYED_OWNED_MAP ? String.class.getName() : null, key, literal.fixed() ? field : null,
                literal.names().origin(), literal.doc(), Boolean.TRUE.equals(literal.schemaKey()), literal.sourceDeprecated(), literal.examples(), literal.rawInputRequired()));
    }

    /** Resolve only compiler-recorded field/target associations; construction uses the internal owned Map route. */
    private void literalAlternative(BuilderContext context, Type fieldType, Literals literal, Method accessor,
                                    List<Method> methods, List<Entry> entries, List<Rejection> rejections)
            throws IOException, ClassNotFoundException {
        String field = literal.field();
        String source = context.model().getName() + "#" + field;
        Map<String, LiteralAlternative> alternatives = new TreeMap<>();
        for (AlternativeFacts facts : literal.alternatives()) {
            alternatives.put(facts.alias(), resolveAlternative(context, field, fieldType, literal, facts));
        }
        if (literal.names().ignored()) {
            rejections.add(new Rejection(source, ignoreReason(literal.names())));
            return;
        }
        Role role = selectedRole(context.metadata(), methods, source);
        if (role != Role.LITERAL_ALTERNATIVE_MAP)
            throw invalid("Missing literal alternative selection on " + source);
        validateFieldRelationship(role, literal, accessor, methods, fieldType, source);
        LiteralAlternative first = alternatives.values().iterator().next();
        String name = literal.names().rename().isEmpty() ? field : literal.names().rename();
        entries.add(new Entry(name, Role.LITERAL_ALTERNATIVE_MAP, source, context.builder(), field, List.of(), null,
                first.payload(), String.class.getName(), literal.alternatives().get(0).key(), null,
                literal.names().origin(), literal.doc(), false, literal.sourceDeprecated(), literal.examples(), alternatives, literal.rawInputRequired()));
    }

    private LiteralAlternative resolveAlternative(BuilderContext context, String field, Type fieldType, Literals literal,
                                                  AlternativeFacts facts) throws IOException, ClassNotFoundException {
        String source = context.model().getName() + "#" + field;
        ClassLoader loader = context.builder().getClassLoader();
        Class<?> target = Class.forName(facts.target(), false, loader);
        Class<?> childBuilder = Class.forName(facts.target() + BUILDER_SUFFIX, false, loader);
        Class<?> declaredBuilder = alternativeMapValue(fieldType, source);
        Class<?> declaredModel = modelOf(declaredBuilder);
        if (declaredModel == null || !Modifier.isAbstract(declaredModel.getModifiers())
                || !declaredModel.isAssignableFrom(target) || Modifier.isAbstract(target.getModifiers())
                || target.getTypeParameters().length != 0 || declaredModel.getTypeParameters().length != 0
                || !childBuilder.isInterface() || modelOf(childBuilder) != target || !declaredBuilder.isAssignableFrom(childBuilder)
                || !facts.key().equals(reader.metadata(childBuilder).key())
                || !facts.key().equals(reader.metadata(declaredBuilder).key())
                || !literal.exclusion().isEmpty() || literal.schemaKey() != null || literal.fixed())
            throw invalid("Literal alternative field/target/key disagreement on " + source);
        return new LiteralAlternative(facts.alias(),
                new Payload(Kind.NESTED_OBJECT, target, childBuilder, null, null));
    }

    private static Class<?> alternativeMapValue(Type fieldType, String source) {
        if (!(fieldType instanceof ParameterizedType map) || map.getRawType() != Map.class
                || map.getActualTypeArguments()[0] != String.class)
            throw invalid("Literal alternative requires Map<String, abstract keyed DSL Object> on " + source);
        Class<?> value = rawType(map.getActualTypeArguments()[1]);
        if (value == null) throw invalid("Missing literal alternative declared Builder on " + source);
        return value;
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
            if (methods.size() != 1 || !literal.exclusion().isEmpty()
                    || selectedRole(context.metadata(), methods, location) != Role.PROPERTY)
                throw invalid(CONVERTER_SIGNATURE_MISMATCH);
            // Ignored converter fields return before entry dispatch is resolved; admitted fields are checked there.
            if (literal.names().ignored()) validateVisibility(methods, location);
            Class<?> domain = converterDomain(field, fieldType, method, selected);
            if (!selected.parameterFacts() && !validStructuralConverterDescriptor(selected, converterParameter(fieldType, method)))
                throw invalid("Structural converter descriptor disagrees with field/overload");
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

    private static Payload converterPayload(Type fieldType, Method method) {
        Payload scalar = new Payload(Kind.SCALAR, converterParameter(fieldType, method), null, null, null);
        if (isConverterSequence(fieldType)) return new Payload(Kind.LIST, List.class, null, null, scalar);
        if (rawType(fieldType) == Map.class) return new Payload(Kind.MAP, Map.class, null, String.class.getName(), scalar);
        return scalar;
    }

    private static Class<?> converterDomain(String field, Type fieldType, Method method, ConverterSelection selected) {
        boolean container = isConverterSequence(fieldType) || rawType(fieldType) == Map.class;
        Class<?> domain = container ? containerConverterDomain(fieldType) : rawType(fieldType);
        if (domain == null || method.getReturnType() != domain || method.isSynthetic() || method.isBridge()
                || !container && !method.getName().equals(field) || !validConverterParameter(fieldType, method, selected))
            throw invalid(CONVERTER_SIGNATURE_MISMATCH);
        return domain;
    }

    private static boolean validConverterParameter(Type fieldType, Method method, ConverterSelection selected) {
        int valueIndex = rawType(fieldType) == Map.class ? 1 : 0;
        if (method.getParameterCount() != valueIndex + 1) return false;
        if (valueIndex == 1 && (method.getParameterTypes()[0] != String.class || method.getGenericParameterTypes()[0] != String.class)) return false;
        Class<?> parameter = method.getParameterTypes()[valueIndex];
        if (parameter != String.class && parameter != CharSequence.class) return false;
        if (method.getGenericParameterTypes()[valueIndex] != parameter) return false;
        if (rawType(fieldType) == Set.class && parameter != String.class) return false;
        return !selected.parameterFacts() || fieldType instanceof Class<?> && parameter == String.class;
    }

    private static Class<?> converterParameter(Type fieldType, Method method) {
        return method.getParameterTypes()[rawType(fieldType) == Map.class ? 1 : 0];
    }

    private static boolean isConverterSequence(Type fieldType) {
        return rawType(fieldType) == List.class || rawType(fieldType) == Set.class;
    }

    /** Exact List/Set elements and String-keyed Map values share the non-native scalar destination boundary. */
    private static Class<?> containerConverterDomain(Type fieldType) {
        if (rawType(fieldType) == Map.class && (!(fieldType instanceof ParameterizedType map)
                || map.getActualTypeArguments()[0] != String.class)) return null;
        Type element = containerValue(fieldType);
        Class<?> domain = rawType(element);
        if (domain == null || domain == Object.class || domain.isArray() || domain.isPrimitive()
                || Collection.class.isAssignableFrom(domain) || Map.class.isAssignableFrom(domain)
                || Number.class.isAssignableFrom(domain) || leafPayload(domain) != null
                || domain.isInterface() && domain.getName().endsWith(BUILDER_SUFFIX)) return null;
        return domain;
    }

    private static boolean validStructuralConverterDescriptor(ConverterSelection selected, Class<?> parameter) {
        String prefix = "(L" + parameter.getName().replace('.', '/') + ";)";
        if (selected.name().equals("<init>")) return selected.descriptor().equals(prefix + "V");
        // The source may return a subtype; the exact destination type belongs to the public Builder projection.
        // Possessive repetition avoids stack growth; a name segment cannot consume a package separator.
        return selected.descriptor().startsWith(prefix)
                && selected.descriptor().substring(prefix.length()).matches("L[^.;\\[()/]+(?:/[^.;\\[()/]+)*+;");
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
        if (role == Role.OWNED_SEQUENCE) {
            if (payload == null || payload.kind() != Kind.LIST || payload.type() == EnumSet.class
                    || payload.item().kind() != Kind.NESTED_OBJECT)
                throw invalid("Owned sequence getter mismatch: " + source);
            payload = payload.item();
        }
        if (role == Role.KEYED_OWNED_MAP) {
            if (payload == null || payload.kind() != Kind.MAP || payload.item().kind() != Kind.NESTED_OBJECT)
                throw invalid("Keyed relationship getter mismatch: " + source);
            payload = payload.item();
        }
        if (payload == null || ((role == Role.OWNED_OBJECT || role == Role.KEYED_OWNED_MAP || role == Role.OWNED_SEQUENCE) != (payload.kind() == Kind.NESTED_OBJECT)))
            throw invalid("Field role/payload mismatch: " + source);
        return payload;
    }

    /** Relationship execution uses field facts, not public creator overloads. */
    private static Role fieldRole(Metadata metadata, Literals literal, Method accessor, List<Method> methods, Type fieldType, String source) {
        if (literal.sequenceElement() == null) {
            Role role = methods.isEmpty() ? null : selectedRole(metadata, methods, source);
            validateFieldRelationship(role, literal, accessor, methods, fieldType, source);
            return role;
        }
        if (literal.sequenceElement().isBlank() || !methods.isEmpty())
            throw invalid("Owned sequence requires its field relationship association: " + source);
        Payload payload = fieldPayload(fieldType, Role.OWNED_SEQUENCE, source);
        if (payload.type().getTypeParameters().length != 0)
            throw invalid("Owned sequence requires a nongeneric concrete child: " + source);
        return Role.OWNED_SEQUENCE;
    }

    private static void validateFieldRelationship(Role role, Literals literal, Method accessor,
                                                   List<Method> methods, Type fieldType, String source) {
        if (role == Role.OWNED_SEQUENCE) throw invalid("Missing owned sequence field relationship on " + source);
        if (role != Role.OWNED_OBJECT && role != Role.KEYED_OWNED_MAP && role != Role.LITERAL_ALTERNATIVE_MAP) return;
        if (!methods.equals(List.of(accessor)) || !literal.field().equals(literal.selection()))
            throw invalid("Owned input requires its exact field relationship association: " + source);
        if (role == Role.KEYED_OWNED_MAP && (!(fieldType instanceof ParameterizedType map)
                || !MAPS.contains(rawType(fieldType).getName()) || map.getActualTypeArguments()[0] != String.class))
            throw invalid("Keyed relationship getter mismatch: " + source);
    }

    private String childKey(Payload payload, Role role, Literals literal, String source)
            throws IOException, ClassNotFoundException {
        if (literal.fixed() && (role != Role.OWNED_OBJECT || reader.metadata(payload.builder()).key().isEmpty()))
            throw invalid("Fixed direct-child key requires keyed OWNED_OBJECT: " + source);
        String key = role == Role.KEYED_OWNED_MAP ? reader.metadata(payload.builder()).key() : null;
        if (role == Role.KEYED_OWNED_MAP && key.isEmpty()) throw invalid("Keyed relationship has no Key member: " + source);
        return key;
    }

    private static Method resolveDispatch(Role role, Payload payload, List<Method> methods, String source) {
        validateVisibility(methods, source);
        if (role != Role.PROPERTY) return null;
        if (payload.item() != null && containsNested(payload.item())) throw invalid("Owned input requires a relationship role: " + source);
        Class<?>[] parameters = switch (payload.kind()) {
            case LIST -> new Class<?>[]{payload.item().type()};
            case MAP -> new Class<?>[]{String.class, payload.item().type()};
            default -> new Class<?>[]{payload.type()};
        };
        List<Method> selected = methods.stream().filter(method -> Arrays.equals(method.getParameterTypes(), parameters)).toList();
        if (selected.size() != 1)
            throw invalid("Missing exact field payload/population signature: " + source);
        return selected.get(0);
    }

    /** Existing exact field selection carries the value-only native Map route, without a second key policy. */
    private static Payload nativePopulationPayload(Payload payload, List<Method> methods, String source) {
        if (payload.kind() != Kind.MAP || methods.stream().noneMatch(method -> method.getParameterCount() == 1))
            return payload;
        if (methods.size() != 1)
            throw invalid("Native value-only Map population requires one exact selection: " + source);
        Payload item = payload.item();
        Method method = methods.get(0);
        if (item.kind() != Kind.SCALAR && item.kind() != Kind.ENUM
                || method.getGenericParameterTypes()[0] != item.type() || method.getGenericReturnType() != item.type()
                || method.isSynthetic() || method.isBridge())
            throw invalid("Native value-only Map population signature mismatch: " + source);
        return new Payload(Kind.LIST, List.class, null, null, item);
    }

    private static void validateVisibility(List<Method> methods, String source) {
        if (methods.stream().anyMatch(method -> !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())))
            throw invalid("Non-public input selection: " + source);
    }

    private static boolean containsNested(Payload payload) {
        return payload.kind() == Kind.NESTED_OBJECT || payload.item() != null && containsNested(payload.item());
    }

    private void virtualScope(Method sink, Literals literal, BuilderContext context,
                                     List<Entry> entries, List<Rejection> rejections) throws IOException, ClassNotFoundException {
        String source = context.model().getName() + "#" + sourceDescriptor(sink);
        Payload nested = sink.getParameterCount() == 1 ? payload(sink.getGenericParameterTypes()[0], true) : null;
        Method creator = Arrays.stream(declaredMethods(context))
                .filter(method -> descriptor(method).equals(literal.scopeCreator())).findFirst()
                .orElseThrow(() -> invalid("Missing exact virtual scope creator on " + source));
        if (nested == null || nested.kind() != Kind.NESTED_OBJECT || sink.getReturnType() != void.class
                || !Modifier.isPublic(sink.getModifiers()) || Modifier.isStatic(sink.getModifiers())
                || !Modifier.isPublic(creator.getModifiers()) || Modifier.isStatic(creator.getModifiers())
                || !creator.getName().equals(sink.getName()) || creator.getReturnType() != nested.builder()
                || !Arrays.equals(creator.getParameterTypes(), new Class<?>[]{Map.class, Closure.class}))
            throw invalid("Inconsistent virtual scope association on " + source);
        if (!reader.metadata(nested.builder()).key().isEmpty() || nested.type().getTypeParameters().length != 0)
            throw invalid("Virtual scopes require an unkeyed nongeneric concrete target on " + source);
        NameFacts names = literal.operationNames();
        if (names.ignored()) { rejections.add(new Rejection(source, ignoreReason(names))); return; }
        String name = names.rename().isEmpty() ? sink.getName() : names.rename();
        entries.add(new Entry(name, Role.VIRTUAL_OBJECT, source, sink.getDeclaringClass(), sink.getName(), List.of(sink, creator), sink,
                nested, null, null, null, names.origin(), literal.doc(), false, literal.operationDeprecated(), literal.operationExamples(), false));
    }

    private static void operation(Method method, Literals literal, Class<?> model, List<Entry> entries, List<Rejection> rejections) {
        String source = model.getName() + "#" + sourceDescriptor(method);
        NameFacts names = literal.operationNames();
        String reason = null;
        if (names.ignored()) reason = ignoreReason(names);
        else if (literal.operationRole().equals("LIFECYCLE")) reason = "lifecycle methods are not structured commands";
        else if (method.getParameterCount() == 0) {
            String name = names.rename().isEmpty() ? method.getName() : names.rename();
            entries.add(new Entry(name, Role.COMMAND, source, method.getDeclaringClass(), method.getName(), List.of(method), method,
                    new Payload(Kind.EMPTY_OBJECT, Map.class, null, null, null), null, null, null, names.origin(),
                    literal.doc(), false, literal.operationDeprecated(), literal.operationExamples(), false));
            return;
        } else if (method.getParameterCount() != 1) reason = "@Builder.Method must declare zero arguments or exactly one payload";
        Payload payload = reason == null ? payload(method.getGenericParameterTypes()[0], false) : null;
        // Keep CharSequence admission scalar-only; containers retain their existing grammar.
        if (reason == null && method.getGenericParameterTypes()[0] == CharSequence.class) {
            payload = new Payload(Kind.SCALAR, CharSequence.class, null, null, null);
        }
        if (reason == null && payload == null) reason = unsupportedReason(method.getGenericParameterTypes()[0]);
        if (reason != null) { rejections.add(new Rejection(source, reason)); return; }
        String name = names.rename().isEmpty() ? method.getName() : names.rename();
        entries.add(new Entry(name, Role.OPERATION, source, method.getDeclaringClass(), method.getName(), List.of(method), method, payload,
                null, null, null, names.origin(), literal.doc(), false, literal.operationDeprecated(), literal.operationExamples(), false));
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
        // Owned eligibility comes only from exact relationship selection; ordinary data payloads cannot contain Builders.
        if (item == null && field) item = payload(itemType, true);
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
