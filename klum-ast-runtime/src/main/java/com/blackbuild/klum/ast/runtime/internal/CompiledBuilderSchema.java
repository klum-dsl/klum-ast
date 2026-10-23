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
import java.lang.reflect.TypeVariable;
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

/** Internal compiled-contract seam. Never inspects Model implementations or reads the retired carrier. */
final class CompiledBuilderSchema {
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
            CompiledBuilderSchema schema = read(Class.forName(model.getName() + "_DSL$Builder", false, loader),
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
            List<Entry> candidates = new ArrayList<>();
            List<Rejection> rejected = new ArrayList<>();
            List<Declaration> declarations = new ArrayList<>();
            collect(builder, new LinkedHashSet<>(), candidates, rejected, declarations);
            declarations.addAll(scope(factory));
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
            return new CompiledBuilderSchema(builder, model, entries, rejected.stream().distinct()
                    .sorted(Comparator.comparing(Rejection::source).thenComparing(Rejection::reason)).toList(), declarations);
        } catch (ReflectiveOperationException | LinkageError | IOException | RuntimeException failure) {
            throw failure(builder.getName(), failure);
        }
    }

    Class<?> contract() { return contract; }
    Class<?> model() { return model; }
    Map<String, Entry> entries() { return entries; }
    List<Rejection> rejections() { return rejections; }
    List<Declaration> declarations() { return declarations; }
    String defaultStatus() { return "UNAVAILABLE_WITHOUT_EXECUTION"; }

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

    private static void collect(Class<?> builder, Set<Class<?>> visited, List<Entry> entries,
                                List<Rejection> rejections, List<Declaration> declarations) throws IOException, ClassNotFoundException {
        if (!visited.add(builder)) return;
        requirePublic(builder);
        Class<?> model = modelOf(builder);
        if (model == null) throw invalid("Missing Builder self bound: " + builder.getName());
        Metadata metadata = metadata(builder);
        if (!metadata.schema) throw invalid("Missing generated input enrichment on " + builder.getName());
        for (String member : metadata.invisible) rejections.add(new Rejection(model.getName() + "#" + member,
                "field is absent from the final public Builder contract"));
        Map<String, Method> getters = new LinkedHashMap<>();
        Map<String, List<Method>> selections = new LinkedHashMap<>();
        for (Method method : Arrays.stream(builder.getDeclaredMethods()).sorted(Comparator.comparing(CompiledBuilderSchema::descriptor)).toList()) {
            Literals literal = metadata.methods.get(signature(method));
            if (literal == null) throw invalid("Missing exact public declaration " + method);
            declarations.add(declaration(method, literal));
            if (literal.field != null && (method.getParameterCount() != 0 || getters.putIfAbsent(literal.field, method) != null))
                throw invalid("Duplicate or non-getter field association " + literal.field);
            if (literal.selection != null) selections.computeIfAbsent(literal.selection, ignored -> new ArrayList<>()).add(method);
            if (literal.operation && !literal.query) operation(method, literal, model, entries, rejections);
        }
        for (Map.Entry<String, Method> field : getters.entrySet()) {
            Method getter = field.getValue();
            Literals literal = metadata.methods.get(signature(getter));
            String source = model.getName() + "#" + field.getKey();
            List<Method> methods = selections.remove(field.getKey());
            if (methods == null) methods = List.of();
            String reason = fieldReason(getter.getGenericReturnType(), literal);
            if (reason != null) { rejections.add(new Rejection(source, reason)); continue; }
            if (methods.isEmpty()) throw invalid("Missing compiler input/population selection for " + source);
            Set<String> roles = methods.stream().map(method -> metadata.methods.get(signature(method)).role).collect(Collectors.toSet());
            if (roles.size() != 1 || methods.stream().map(Method::getName).distinct().count() != 1)
                throw invalid("Conflicting field selection for " + source);
            Role role = Role.valueOf(roles.iterator().next());
            if (role == Role.OPERATION) throw invalid("Field cannot select authored-operation role");
            Payload payload = payload(getter.getGenericReturnType(), true);
            if (role == Role.KEYED_OWNED_MAP) {
                if (payload == null || payload.kind() != Kind.MAP || payload.item().kind() != Kind.NESTED_OBJECT)
                    throw invalid("Keyed relationship getter mismatch: " + source);
                payload = payload.item();
            }
            if (payload == null || ((role == Role.OWNED_OBJECT || role == Role.KEYED_OWNED_MAP) != (payload.kind() == Kind.NESTED_OBJECT)))
                throw invalid("Field role/payload mismatch: " + source);
            if (!literal.fixed.isEmpty() && (role != Role.OWNED_OBJECT || metadata(payload.builder()).key.isEmpty()))
                throw invalid("Fixed direct-child key requires keyed OWNED_OBJECT: " + source);
            String key = role == Role.KEYED_OWNED_MAP ? metadata(payload.builder()).key : null;
            if (role == Role.KEYED_OWNED_MAP && key.isEmpty()) throw invalid("Keyed relationship has no Key member: " + source);
            validateSelection(role, payload, methods, source);
            String name = literal.rename.isEmpty() ? field.getKey() : literal.rename;
            entries.add(new Entry(name, role, source, builder, methods.get(0).getName(), List.copyOf(methods), payload,
                    role == Role.KEYED_OWNED_MAP ? String.class.getName() : null, key, empty(literal.fixed),
                    literal.rename.isEmpty() ? "IDENTITY" : "JSON_PROPERTY", literal.doc));
        }
        if (!selections.isEmpty()) throw invalid("Selections lack field declarations: " + selections.keySet());
        for (Class<?> parent : builder.getInterfaces()) {
            Class<?> parentModel = modelOf(parent);
            if (parentModel == null || parentModel == Object.class || !parent.getName().endsWith("_DSL$Builder")) continue;
            if (!parentModel.isAssignableFrom(model)) throw invalid("Unrelated Builder parent " + parent.getName());
            collect(parent, visited, entries, rejections, declarations);
        }
    }

    private static void validateSelection(Role role, Payload payload, List<Method> methods, String source) {
        if (methods.stream().anyMatch(method -> !Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())))
            throw invalid("Non-public input selection: " + source);
        if (role != Role.PROPERTY) return;
        if (payload.item() != null && containsNested(payload.item())) throw invalid("Owned input requires a relationship role: " + source);
        Class<?>[] parameters = payload.kind() == Kind.LIST ? new Class<?>[]{payload.item().type()}
                : payload.kind() == Kind.MAP ? new Class<?>[]{String.class, payload.item().type()} : new Class<?>[]{payload.type()};
        if (methods.stream().filter(method -> Arrays.equals(method.getParameterTypes(), parameters)).count() != 1)
            throw invalid("Missing exact field payload/population signature: " + source);
    }

    private static boolean containsNested(Payload payload) {
        return payload.kind() == Kind.NESTED_OBJECT || payload.item() != null && containsNested(payload.item());
    }

    private static void operation(Method method, Literals literal, Class<?> model, List<Entry> entries, List<Rejection> rejections) {
        String source = model.getName() + "#" + descriptor(method);
        String reason = literal.ignored ? "excluded by @JsonIgnore(true)" : method.getParameterCount() != 1
                ? "@Builder.Method must declare exactly one payload" : null;
        Payload payload = reason == null ? payload(method.getGenericParameterTypes()[0], false) : null;
        if (reason == null && payload == null) reason = unsupportedReason(method.getGenericParameterTypes()[0]);
        if (reason != null) { rejections.add(new Rejection(source, reason)); return; }
        String name = literal.rename.isEmpty() ? method.getName() : literal.rename;
        entries.add(new Entry(name, Role.OPERATION, source, method.getDeclaringClass(), method.getName(), List.of(method), payload,
                null, null, null, literal.rename.isEmpty() ? "IDENTITY" : "JSON_PROPERTY", literal.doc));
    }

    private static String fieldReason(Type type, Literals literal) {
        if (literal.ignored) return "excluded by @JsonIgnore(true)";
        if (!literal.exclusion.isEmpty()) return switch (literal.exclusion) {
            case "OWNERSHIP" -> "requires one concrete owned relationship";
            case "KEYED_OWNERSHIP" -> "requires Map<String, concrete keyed DSL Object> ownership";
            case "KEY_MAPPING" -> "literal structured instance keys conflict with @Field.keyMapping-derived keys";
            case "ABSENT_FIELD" -> "field is absent from the final public Builder contract";
            case "POPULATION" -> {
                Class<?> raw = rawType(type);
                Type item = containerValue(type);
                String parameters = (raw != null && Map.class.isAssignableFrom(raw) ? "java.lang.String, " : "") + rawType(item).getName();
                yield "has no unambiguous compiler-generated element population method " + literal.detail + "(" + parameters
                        + "); replacement setters and authored overrides are not input paths";
            }
            default -> throw invalid("Unknown field exclusion " + literal.exclusion);
        };
        String numeric = unsupportedNumericType(type);
        if (numeric != null) return "unsupported numeric type " + numeric
                + "; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input";
        Type item = containerValue(type);
        String container = unsupportedContainerType(item == null ? type : item);
        if (container != null) return "unsupported whole-container payload target " + container
                + "; use ordinary List/Map payloads or a supported field's generated element population";
        return payload(type, true) == null ? "has no admitted scalar, enum, List, or Map payload" : null;
    }

    private static Payload payload(Type type, boolean field) {
        if (type instanceof Class<?> leaf) {
            if (leaf.isEnum()) return new Payload(Kind.ENUM, leaf, null, null, null);
            if (leaf == String.class || leaf == Boolean.class || leaf == boolean.class || NUMBERS.contains(leaf.getName()))
                return new Payload(Kind.SCALAR, leaf, null, null, null);
            return null;
        }
        if (!(type instanceof ParameterizedType parameterized) || !(parameterized.getRawType() instanceof Class<?> raw)) return null;
        Type[] args = parameterized.getActualTypeArguments();
        Class<?> model = modelOf(raw);
        if (field && raw.isInterface() && raw.getName().endsWith("_DSL$Builder") && model != null && !Modifier.isAbstract(model.getModifiers()))
            return new Payload(Kind.NESTED_OBJECT, model, raw, null, null);
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

    private static String descriptor(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", "))
                + "):" + method.getReturnType().getTypeName();
    }
    private static String signature(Method method) { return method.getName() + groovyjarjarasm.asm.Type.getMethodDescriptor(method); }
    private static String empty(String value) { return value.isEmpty() ? null : value; }
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
            return "unsupported numeric type " + numeric + "; use primitive/boxed numeric types, BigInteger or BigDecimal for structured input";
        String container = unsupportedContainerType(type);
        if (container != null)
            return "unsupported whole-container payload target " + container
                    + "; use ordinary List/Map payloads or a supported field's generated element population";
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
        String fixed = "";
        String exclusion = "";
        String detail = "";
        boolean operation;
        boolean query;
        boolean ignored;
    }
    private static final class Metadata {
        boolean schema;
        String key = "";
        final List<String> invisible = new ArrayList<>();
        final Map<String, Literals> methods = new LinkedHashMap<>();
    }

    /** Exact public declaration resources only. Avoids annotation proxies parsing unrelated user enum annotations. */
    private static Metadata metadata(Class<?> owner) throws IOException, ClassNotFoundException {
        for (Class<?> protocol : List.of(GeneratedInput.Schema.class, GeneratedInput.Field.class, GeneratedInput.Selection.class, GeneratedInput.Exclusion.class))
            if (!Class.forName(protocol.getName(), false, owner.getClassLoader()).isAnnotation()) throw invalid("Missing annotation protocol " + protocol.getName());
        String name = owner.getName().replace('.', '/') + ".class";
        URL resource = owner.getResource("/" + name);
        if (resource == null) throw new IOException("Missing exact public contract resource " + name);
        if (!owner.getModule().isNamed()) {
            URL origin = owner.getProtectionDomain().getCodeSource() == null ? null : owner.getProtectionDomain().getCodeSource().getLocation();
            if (origin == null) throw invalid("Missing contract resource origin " + owner.getName());
            boolean matches = resource.openConnection() instanceof JarURLConnection jar ? jar.getJarFileURL().equals(origin)
                    : resource.toExternalForm().equals(new URL(origin, name).toExternalForm());
            if (!matches) throw invalid("Public contract resource origin disagrees with loaded type " + owner.getName());
        }
        try (InputStream bytes = owner.getModule().getResourceAsStream(name)) {
            if (bytes == null) throw new IOException("Missing exact module contract resource " + name);
            Metadata result = new Metadata();
            new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override public void visit(int version, int access, String internal, String signature, String parent, String[] interfaces) {
                    if (!internal.equals(name.substring(0, name.length() - 6)) || (access & Opcodes.ACC_PUBLIC) == 0 || (access & Opcodes.ACC_INTERFACE) == 0)
                        throw invalid("Public contract resource identity/access mismatch " + owner.getName());
                }
                @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                    if (!visible || !annotation.equals(SCHEMA)) return null;
                    if (result.schema) throw invalid("Duplicate input schema marker");
                    result.schema = true;
                    return new AnnotationVisitor(Opcodes.ASM9) {
                        @Override public void visit(String member, Object value) {
                            if (member.equals("key") && value instanceof String text) result.key = text;
                            else throw invalid("Unknown schema member " + member);
                        }
                        @Override public AnnotationVisitor visitArray(String member) {
                            if (!member.equals("exclusions")) throw invalid("Unknown schema array " + member);
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public AnnotationVisitor visitAnnotation(String ignored, String annotation) {
                                    if (!annotation.equals(EXCLUSION)) throw invalid("Invalid invisible exclusion");
                                    return new AnnotationVisitor(Opcodes.ASM9) {
                                        String source;
                                        String category;
                                        @Override public void visit(String member, Object value) {
                                            if (member.equals("member") && value instanceof String text) source = text;
                                            else if (member.equals("category") && value instanceof String text) category = text;
                                            else throw invalid("Unknown exclusion member " + member);
                                        }
                                        @Override public void visitEnd() {
                                            if (source == null || source.isEmpty() || !"ABSENT_FIELD".equals(category)) throw invalid("Invalid absent-field exclusion");
                                            result.invisible.add(source);
                                        }
                                    };
                                }
                            };
                        }
                    };
                }
                @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    Literals literal = new Literals();
                    result.methods.put(name + descriptor, literal);
                    Set<String> seen = new LinkedHashSet<>();
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (!PROTOCOL.contains(annotation)) return null;
                            if (!seen.add(annotation)) throw invalid("Duplicate contract annotation " + annotation);
                            if (annotation.equals(OPERATION)) literal.operation = true;
                            if (annotation.equals(QUERY)) literal.query = true;
                            if (annotation.equals(IGNORE)) literal.ignored = true;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override public void visit(String member, Object value) {
                                    if (annotation.equals(FIELD)) {
                                        switch (member) {
                                            case "value" -> literal.field = (String) value;
                                            case "rename" -> literal.rename = (String) value;
                                            case "ignored" -> literal.ignored = (Boolean) value;
                                            case "fixedKey" -> literal.fixed = (String) value;
                                            case "exclusion" -> literal.exclusion = (String) value;
                                            case "detail" -> literal.detail = (String) value;
                                            default -> throw invalid("Unknown field fact " + member);
                                        }
                                    } else if (annotation.equals(SELECTION)) {
                                        if (member.equals("field")) literal.selection = (String) value;
                                        else if (member.equals("role")) literal.role = (String) value;
                                        else throw invalid("Unknown selection fact " + member);
                                    } else if (member.equals("value")) {
                                        if (annotation.equals(DOC)) literal.doc = (String) value;
                                        if (annotation.equals(PROPERTY)) literal.rename = (String) value;
                                        if (annotation.equals(IGNORE)) literal.ignored = (Boolean) value;
                                    }
                                }
                                @Override public void visitEnd() {
                                    if (annotation.equals(FIELD) && (literal.field == null || literal.field.isEmpty())) throw invalid("Missing field association");
                                    if (annotation.equals(SELECTION) && (literal.selection == null || literal.selection.isEmpty() || literal.role == null))
                                        throw invalid("Missing population selection");
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            if (owner.getName().endsWith("_DSL$Builder") && !result.schema)
                throw invalid("Missing generated input enrichment on " + owner.getName());
            return result;
        }
    }
}
