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

import com.blackbuild.klum.ast.Struct;
import com.blackbuild.klum.ast.Struct.ConstraintProjection.Policy;
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
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema.*;
import static groovyjarjarasm.asm.Type.getMethodDescriptor;

/**
 * Reads only exact public Builder/Factory declaration bytes and signatures, without initialization.
 * Model types may identify self bounds but their bytes, defaults, lifecycle and validators are never inspected.
 * Required protocol facts are checked even for excluded/suppressed members; failures retain the declaring
 * Schema, public contract and artifact identity. Unrelated annotations are never instantiated as proxies.
 * Each instance caches validated declarations by exact Class identity for one consumer session only.
 * Mutable ASM state stays private; callers receive defensively copied, ordered, inert declaration facts.
 */
final class BuilderContractReader {
    private static final String DIRECT_CONSTRAINT = "DIRECT";
    private static final String DOCUMENTARY_CONSTRAINT = "DOCUMENTARY";
    private static final Set<String> DECLARATION_ORIGINS = Set.of(DIRECT_CONSTRAINT, DOCUMENTARY_CONSTRAINT);
    private static final Set<String> CONSTRAINT_ORIGINS = Set.of(DIRECT_CONSTRAINT, DOCUMENTARY_CONSTRAINT, "COMPOSING_AND", "COMPOSING_UNSUPPORTED");
    private static final String VALUE = "value";
    private static final String EXAMPLES = "examples";
    static final String IDENTITY_NAME = "IDENTITY";
    static final String KEBAB_NAME = "KEBAB_CASE";
    private static final String INVALID_CONSTRAINT_ARRAY_ITEM = "Unexpected constraint array item";
    static final String BUILDER_SUFFIX = "_DSL$Builder";
    private static final String INPUT_IGNORED = "excluded by @Struct.Ignore";
    private static final String JSON_PROPERTY_ORIGIN = "JSON_PROPERTY";
    private static final String IGNORE_ORIGIN = "IGNORE";
    private static final String INPUT_IGNORE_ORIGIN = "INPUT_IGNORE";
    private static final Set<String> NAME_ORIGINS = Set.of(IDENTITY_NAME, JSON_PROPERTY_ORIGIN, "INPUT_NAME", IGNORE_ORIGIN, INPUT_IGNORE_ORIGIN);
    private static final String IGNORED_INPUT = "excluded by @JsonIgnore(true)";
    static final String NUMERIC = "NUMERIC";
    static final String CONTAINER = "CONTAINER";
    private static final String PREFIX = "Lcom/blackbuild/klum/ast/runtime/generated/GeneratedInput$";
    private static final String SCHEMA = PREFIX + "Schema;";
    private static final String KEY_ANNOTATION = PREFIX + "Key;";
    private static final String FIELD = PREFIX + "Field;";
    private static final String OPERATION_FACT = PREFIX + "Operation;";
    private static final String SOURCE_DEPRECATED = "sourceDeprecated";
    private static final String CONSTRAINT = PREFIX + "Constraint;";
    private static final String CONVERTER = PREFIX + "Converter;";
    private static final String SELECTION = PREFIX + "Selection;";
    private static final String EXCLUSION = PREFIX + "Exclusion;";
    private static final String OPERATION = "Lcom/blackbuild/klum/ast/Builder$Method;";
    private static final String AUTHOR_OPERATION = "Lcom/blackbuild/klum/ast/Struct$Operation;";
    private static final String QUERY = "Lcom/blackbuild/klum/ast/Builder$Query;";
    private static final String DOC = "Lcom/blackbuild/annodocimal/annotations/AnnoDoc;";
    private static final Set<String> PROTOCOL = Set.of(SCHEMA, KEY_ANNOTATION, FIELD, SELECTION, CONVERTER, EXCLUSION, OPERATION, OPERATION_FACT, AUTHOR_OPERATION, QUERY, DOC);

    private final Map<Class<?>, Metadata> declarations = new IdentityHashMap<>();

    /** Validated declaration facts; names and source roles are still unresolved. */
    record Metadata(boolean schema, int version, Boolean namingExplicit, String namingProfile,
                    Boolean groupRedefined, boolean keyInput, Literals keyMember, String key,
                    List<Invisible> invisible, Map<String, Literals> methods) {
        Metadata {
            invisible = List.copyOf(invisible);
            methods = Collections.unmodifiableMap(new LinkedHashMap<>(methods));
        }
    }

    /** Field/key and authored-operation names remain separate even on a shared accessor. */
    record Literals(String location, String key, String field, String selection, String role, String doc,
                    NameFacts names, NameFacts operationNames, boolean operationFact, boolean fixed,
                    Boolean schemaKey, Boolean sourceDeprecated, Boolean operationDeprecated, Boolean authoritative,
                    String exclusion, String detail, boolean operation, boolean query,
                    List<ConstraintRule> constraints, String constraintProjection,
                    List<String> examples, List<String> operationExamples, Integer converterCount, ConverterSelection converter) {
        Literals {
            constraints = constraints == null ? null : List.copyOf(constraints);
            examples = examples == null ? null : List.copyOf(examples);
            operationExamples = operationExamples == null ? null : List.copyOf(operationExamples);
        }
    }

    record ConverterSelection(String owner, String name, String descriptor, int parameter) { }

    record NameFacts(String rename, String origin, boolean ignored) { }

    private static Metadata freeze(MutableMetadata value) {
        Map<String, Literals> methods = new LinkedHashMap<>();
        value.methods.forEach((signature, literal) -> methods.put(signature, freeze(literal)));
        return new Metadata(value.schema, value.version, value.namingExplicit, value.namingProfile,
                value.groupRedefined, value.keyInput, value.keyMember == null ? null : freeze(value.keyMember),
                value.key, value.invisible, methods);
    }

    private static Literals freeze(MutableLiterals value) {
        return new Literals(value.location, value.key, value.field, value.selection, value.role, value.doc,
                freeze(value.names), freeze(value.operationNames), value.operationFact, value.fixed,
                value.schemaKey, value.sourceDeprecated, value.operationDeprecated, value.authoritative, value.exclusion, value.detail,
                value.operation, value.query, value.constraints, value.constraintProjection, value.examples, value.operationExamples, value.converterCount, value.converter);
    }

    private static NameFacts freeze(MutableNameFacts value) {
        return new NameFacts(value.rename, value.origin, value.ignored);
    }

    private static void validateDecimalFacts(MutableLiterals literal, String source) {
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

    private static void validateNaming(MutableMetadata declared, Class<?> builder) {
        if (declared.namingExplicit == null || declared.namingProfile == null
                || !Set.of(IDENTITY_NAME, KEBAB_NAME).contains(declared.namingProfile)
                || Boolean.FALSE.equals(declared.namingExplicit) && !declared.namingProfile.equals(IDENTITY_NAME)) {
            throw invalid("Invalid or incomplete naming metadata on " + namingOrigin(builder));
        }
    }

    static String schemaName(Class<?> builder) {
        return builder.getName().endsWith(BUILDER_SUFFIX)
                ? builder.getName().substring(0, builder.getName().length() - BUILDER_SUFFIX.length()) : builder.getName();
    }

    static String namingOrigin(Class<?> builder) {
        URL origin = builder.getProtectionDomain().getCodeSource() == null ? null : builder.getProtectionDomain().getCodeSource().getLocation();
        return schemaName(builder) + " [public Builder " + builder.getName() + ", artifact " + origin + "]";
    }

    static Declaration declaration(Method method, Literals literal) {
        if (literal == null) throw invalid("Missing exact contract method " + method);
        return new Declaration(method.getDeclaringClass().getName(), method.getName(),
                Arrays.stream(method.getGenericParameterTypes()).map(Type::getTypeName).toList(),
                method.getGenericReturnType().getTypeName(), literal.doc());
    }

    static String descriptor(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getTypeName).collect(Collectors.joining(", "))
                + "):" + method.getReturnType().getTypeName();
    }

    static String signature(Method method) { return method.getName() + getMethodDescriptor(method); }

    static String exclusionReason(String category, String detail) {
        return switch (category) {
            case IGNORE_ORIGIN -> IGNORED_INPUT;
            case INPUT_IGNORE_ORIGIN -> INPUT_IGNORED;
            case "CONVERTER" -> "unsupported converter selection; expose one supported public String route and recompile matched domain/Schema artifacts";
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

    static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }

    static void requirePublic(Class<?> contract) throws ClassNotFoundException {
        if (!contract.isInterface() || !Modifier.isPublic(contract.getModifiers())
                || Class.forName(contract.getName(), false, contract.getClassLoader()) != contract)
            throw invalid("Expected exact public contract identity " + contract.getName());
    }

    List<Declaration> scope(Class<?> contract) throws IOException, ClassNotFoundException {
        requirePublic(contract);
        Map<Class<?>, Metadata> owners = new LinkedHashMap<>();
        List<Declaration> result = new ArrayList<>();
        for (Method method : Arrays.stream(contract.getMethods()).sorted(Comparator.comparing(BuilderContractReader::descriptor)).toList()) {
            Class<?> owner = method.getDeclaringClass();
            if (!owners.containsKey(owner)) owners.put(owner, metadata(owner));
            result.add(declaration(method, owners.get(owner).methods().get(signature(method))));
        }
        return List.copyOf(result);
    }

    /** Names belong to selected source declarations, which can share one public accessor signature. */
    private static final class MutableNameFacts {
        String rename = "";
        String origin;
        boolean ignored;
        boolean renamePresent;
        boolean ignoredPresent;
    }

    private static final class MutableLiterals {
        String location = "construction key";
        String key;
        String field;
        String selection;
        String role;
        String doc;
        final MutableNameFacts names = new MutableNameFacts();
        final MutableNameFacts operationNames = new MutableNameFacts();
        boolean operationFact;
        boolean fixed;
        Boolean schemaKey;
        Boolean sourceDeprecated;
        Boolean operationDeprecated;
        Boolean authoritative;
        boolean authorOperation;
        String exclusion = "";
        String detail = "";
        boolean operation;
        boolean query;
        List<ConstraintRule> constraints;
        Integer constraintCount;
        Integer converterCount;
        ConverterSelection converter;
        String constraintProjection;
        List<String> examples;
        List<String> operationExamples;
    }
    record Invisible(String member, String reason) { }
    private static final class MutableMetadata {
        boolean schema;
        int version;
        Integer operationCount;
        Boolean namingExplicit;
        String namingProfile;
        Boolean groupRedefined;
        boolean keyInput;
        MutableLiterals keyMember;
        String key = "";
        final List<Invisible> invisible = new ArrayList<>();
        final Map<String, MutableLiterals> methods = new LinkedHashMap<>();
    }

    /** Exact public declaration resources only. Avoids annotation proxies parsing unrelated user enum annotations. */
    Metadata metadata(Class<?> owner) throws IOException, ClassNotFoundException {
        Metadata known = declarations.get(owner);
        if (known != null) return known;
        verifyAnnotationProtocol(owner);
        String name = owner.getName().replace('.', '/') + ".class";
        verifyResourceOrigin(owner, name);
        try (InputStream bytes = owner.getModule().getResourceAsStream(name)) {
            if (bytes == null) throw new IOException("Missing exact module contract resource " + name);
            MutableMetadata result = new MutableMetadata();
            try {
                new ClassReader(bytes).accept(new MetadataVisitor(owner, name, result),
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                validateMetadata(owner, result);
            } catch (RuntimeException failure) {
                throw invalid("Invalid public Builder metadata on " + namingOrigin(owner) + ": " + failure.getMessage());
            }
            if (owner.getName().endsWith(BUILDER_SUFFIX) && !result.schema)
                throw invalid("Missing generated input enrichment on " + namingOrigin(owner));
            Metadata frozen = freeze(result);
            declarations.put(owner, frozen);
            return frozen;
        }
    }

    static String ignoreReason(NameFacts names) {
        return INPUT_IGNORE_ORIGIN.equals(names.origin()) ? INPUT_IGNORED : IGNORED_INPUT;
    }

    private static void validateMetadata(Class<?> owner, MutableMetadata metadata) {
        if (!metadata.schema) return;
        if (metadata.version != 1) {
            throw invalid("Unsupported structured-input metadata protocol version " + metadata.version + " on " + namingOrigin(owner)
                    + "; expected version 1; recompile and package the participating Schema artifacts with a matching KlumAST toolchain");
        }
        long operations = metadata.methods.values().stream().filter(literal -> literal.operationFact).count();
        if (metadata.operationCount == null || metadata.operationCount != operations)
            throw invalid("Missing or inconsistent operation selection completeness on " + namingOrigin(owner));
        validateNaming(metadata, owner);
        if (metadata.groupRedefined == null) throw invalid("Missing constraint group facts on " + namingOrigin(owner));
        if (metadata.keyMember != null) validateLiteral(owner, metadata.keyMember, "construction key");
        metadata.methods.forEach((signature, literal) -> validateLiteral(owner, literal, signature));
    }

    private static void validateLiteral(Class<?> owner, MutableLiterals literal, String signature) {
        String source = namingOrigin(owner) + "#" + signature;
        if (literal.field != null || literal.key != null) {
            validateNameFacts(literal.names, source);
            if (literal.examples == null) throw invalid("Missing examples metadata on " + source);
            if (literal.sourceDeprecated == null) throw invalid("Missing source deprecation fact on " + source);
        }
        validateOperationFacts(literal, source);
        validateConverterFacts(literal, source);
        if (literal.field != null) {
            validateFieldFacts(literal, schemaName(owner) + "#" + literal.field);
        }
    }

    private static void validateOperationFacts(MutableLiterals literal, String source) {
        if (literal.operation && !literal.operationFact) throw invalid("Missing literal operation facts on " + source);
        if (!literal.operationFact) return;
        if (!literal.operation || literal.authoritative == null) {
            throw invalid("Missing operation role or authoritative selection fact on " + source);
        }
        if (literal.authoritative.booleanValue() != literal.authorOperation) {
            throw invalid("Author marker disagrees with authoritative selection fact on " + source);
        }
        validateNameFacts(literal.operationNames, source);
        if (literal.operationExamples == null) throw invalid("Missing examples metadata on " + source);
        if (literal.operationDeprecated == null) throw invalid("Missing source deprecation fact on " + source);
    }

    private static void validateConverterFacts(MutableLiterals literal, String source) {
        if (literal.converter != null && (literal.selection == null || !"PROPERTY".equals(literal.role))) {
            throw invalid("Converter association lacks exact PROPERTY selection on " + source);
        }
        if (literal.field != null && (literal.converterCount == null || literal.converterCount < 0 || literal.converterCount > 1)) {
            throw invalid("Missing or invalid converter selection completeness on " + source);
        }
    }

    private static void validateFieldFacts(MutableLiterals literal, String source) {
        if (literal.constraints == null) throw invalidConstraint(literal, "Missing constraint facts on " + source);
        if (literal.constraintProjection == null) throw invalid("Missing constraint projection policy on " + source);
        try {
            Policy.valueOf(literal.constraintProjection);
        } catch (IllegalArgumentException failure) {
            throw invalidConstraint(literal, "Unknown constraint projection policy " + literal.constraintProjection + " on " + source);
        }
        validateDecimalFacts(literal, source);
        validateComposingFacts(literal, source);
        List<ConstraintRule> patterns = literal.constraints.stream()
                .filter(rule -> rule.origin().equals(DIRECT_CONSTRAINT) && rule.annotation().equals("jakarta.validation.constraints.Pattern")).toList();
        boolean repeatedPattern = patterns.size() > 1 || literal.constraints.stream()
                .anyMatch(rule -> rule.origin().equals(DIRECT_CONSTRAINT) && rule.annotation().equals("jakarta.validation.constraints.Pattern$List"));
        if (repeatedPattern && patterns.stream().anyMatch(ConstraintRule::plain)) {
            throw invalidConstraint(literal, "Inconsistent singleton Pattern eligibility on " + source);
        }
        if (literal.constraintCount == null) throw invalidConstraint(literal, "Missing constraint count on " + source);
        if (literal.constraintCount != literal.constraints.size()) {
            throw invalidConstraint(literal, "Constraint count mismatch on " + source);
        }
    }

    private static void validateComposingFacts(MutableLiterals literal, String source) {
        for (ConstraintRule rule : literal.constraints) {
            if (DECLARATION_ORIGINS.contains(rule.origin())) continue;
            if (literal.constraints.stream().noneMatch(outer -> outer.origin().equals(DIRECT_CONSTRAINT)
                    && outer.declaration().equals(rule.owner()) && !outer.plain())) {
                throw invalidConstraint(literal, "Missing composing constraint owner on " + source);
            }
            long occurrences = literal.constraints.stream().filter(other -> other.owner().equals(rule.owner())
                    && other.annotation().equals(rule.annotation())).count();
            if (rule.origin().equals("COMPOSING_AND") && occurrences != 1) {
                throw invalidConstraint(literal, "Inconsistent singleton composing eligibility on " + source);
            }
        }
    }

    private static void validateNameFacts(MutableNameFacts names, String source) {
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
        for (Class<?> protocol : List.of(GeneratedInput.Schema.class, GeneratedInput.Key.class, GeneratedInput.Field.class, GeneratedInput.Selection.class, GeneratedInput.Exclusion.class,
                GeneratedInput.Operation.class, Struct.Operation.class))
            if (!Class.forName(protocol.getName(), false, owner.getClassLoader()).isAnnotation())
                throw invalid("Missing annotation protocol " + protocol.getName());
    }

    static void verifyResourceOrigin(Class<?> owner, String name) throws IOException {
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
        private final MutableMetadata result;

        MetadataVisitor(Class<?> owner, String name, MutableMetadata result) {
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
            MutableLiterals literal = new MutableLiterals();
            literal.location = name + descriptor;
            result.methods.put(name + descriptor, literal);
            return new ContractMethodVisitor(literal);
        }
    }

    private static final class SchemaAnnotationVisitor extends AnnotationVisitor {
        private final MutableMetadata result;
        private final Set<String> seen = new LinkedHashSet<>();

        SchemaAnnotationVisitor(MutableMetadata result) {
            super(Opcodes.ASM9);
            this.result = result;
        }

        @Override public void visit(String member, Object value) {
            if (!seen.add(member)) throw invalid("Duplicate schema member " + member);
            switch (member) {
                case "version" -> result.version = integer(value);
                case "operationCount" -> result.operationCount = integer(value);
                case "namingExplicit" -> result.namingExplicit = flag(member, value);
                case "namingProfile" -> result.namingProfile = text(member, value);
                case "groupRedefined" -> result.groupRedefined = flag(member, value);
                case "key" -> result.key = text(member, value);
                case "keyInput" -> result.keyInput = flag(member, value);
                default -> throw unknownMember(member);
            }
        }

        private static int integer(Object value) {
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
            result.keyMember = new MutableLiterals();
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
        private final MutableMetadata result;
        private String source;
        private String category;
        private String detail = "";

        ExclusionAnnotationVisitor(MutableMetadata result) {
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
        private final MutableLiterals literal;
        private final Set<String> seen = new LinkedHashSet<>();

        ContractMethodVisitor(MutableLiterals literal) {
            super(Opcodes.ASM9);
            this.literal = literal;
        }

        @Override public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
            if (!PROTOCOL.contains(annotation)) return null;
            if (annotation.startsWith(PREFIX) && !visible) throw invalid("Generated input annotation requires runtime retention");
            if (!seen.add(annotation)) throw invalid("Duplicate contract annotation " + annotation);
            if (annotation.equals(OPERATION)) literal.operation = true;
            if (annotation.equals(AUTHOR_OPERATION)) literal.authorOperation = true;
            if (annotation.equals(QUERY)) literal.query = true;
            if (annotation.equals(OPERATION_FACT)) literal.operationFact = true;
            if (annotation.equals(CONVERTER)) return new ConverterAnnotationVisitor(literal);
            return new MethodAnnotationVisitor(annotation, literal);
        }
    }

    private static final class ConverterAnnotationVisitor extends AnnotationVisitor {
        private static final String OWNER_MEMBER = "owner";
        private final MutableLiterals literal;
        private final Map<String, Object> facts = new LinkedHashMap<>();

        ConverterAnnotationVisitor(MutableLiterals literal) {
            super(Opcodes.ASM9);
            this.literal = literal;
        }

        @Override public void visit(String name, Object value) {
            if (!Set.of(OWNER_MEMBER, "name", "descriptor", "parameter").contains(name) || facts.putIfAbsent(name, value) != null) {
                throw invalid("Unknown or duplicate converter fact " + name);
            }
        }
        @Override public AnnotationVisitor visitArray(String name) { throw invalid("Unexpected converter array " + name); }
        @Override public AnnotationVisitor visitAnnotation(String name, String type) { throw invalid("Unexpected converter annotation " + name); }
        @Override public void visitEnum(String name, String type, String value) { throw invalid("Unexpected converter enum " + name); }
        @Override public void visitEnd() {
            if (facts.size() != 4 || !(facts.get(OWNER_MEMBER) instanceof String owner) || owner.isEmpty()
                    || !(facts.get("name") instanceof String name) || name.isEmpty()
                    || !(facts.get("descriptor") instanceof String descriptor) || descriptor.isEmpty()
                    || !(facts.get("parameter") instanceof Integer parameter) || parameter != 0) {
                throw invalid("Incomplete or malformed converter association on " + literal.location);
            }
            literal.converter = new ConverterSelection(owner, name, descriptor, parameter);
        }
    }

    private static IllegalArgumentException invalidConstraint(MutableLiterals field, String reason) {
        return invalid(reason + " for source field #" + field.field + ", external input '"
                + (field.names.rename.isEmpty() ? field.field : field.names.rename) + "'");
    }

    private static boolean decimalRule(String annotation) {
        return Set.of("jakarta.validation.constraints.DecimalMin", "jakarta.validation.constraints.DecimalMax").contains(annotation);
    }

    /** Decodes inert strings/longs/booleans only; no Bean annotation classes/proxies or validator code. */
    private static final class ConstraintAnnotationVisitor extends AnnotationVisitor {
        private final MutableLiterals field;
        private final Set<String> seen = new LinkedHashSet<>();
        private String annotation;
        private String value = "";
        private boolean plain;
        private String declaration;
        private String decimalValue;
        private Boolean inclusive;
        private String regexp;
        private Boolean patternFlagsEmpty;
        private String origin;
        private String owner;
        private String description;

        ConstraintAnnotationVisitor(MutableLiterals field) {
            super(Opcodes.ASM9);
            this.field = field;
        }

        @Override public void visit(String member, Object literal) {
            if (!seen.add(member)) throw invalid("Duplicate constraint member " + member);
            switch (member) {
                case "origin" -> origin = compositionText(literal);
                case "owner" -> owner = compositionText(literal);
                case "description" -> description = compositionText(literal);
                case "annotation" -> annotation = (String) literal;
                case VALUE -> {
                    if (!(literal instanceof Long)) throw invalid("Integral constraint value must be a Java long");
                    value = literal.toString();
                }
                case "regexp" -> {
                    if (!(literal instanceof String)) throw invalidConstraint(field, "Pattern regexp must be a String");
                    regexp = (String) literal;
                }
                case "patternFlagsEmpty" -> {
                    if (!(literal instanceof Boolean)) throw invalidConstraint(field, "Pattern flag emptiness must be a Boolean");
                    patternFlagsEmpty = (Boolean) literal;
                }
                case "decimalValue" -> decimalValue = (String) literal;
                case "inclusive" -> inclusive = (Boolean) literal;
                case "plain" -> plain = (Boolean) literal;
                case "declaration" -> declaration = (String) literal;
                default -> throw invalid("Unknown constraint member " + member);
            }
        }

        private String compositionText(Object literal) {
            if (!(literal instanceof String text)) throw invalidConstraint(field, "Composition/description fact must be a String");
            return text;
        }

        private void validateOrigin() {
            if (origin == null || owner == null || description == null) {
                throw invalidConstraint(field, "Missing composition/description facts");
            }
            if (!CONSTRAINT_ORIGINS.contains(origin)
                    || DECLARATION_ORIGINS.contains(origin) != owner.isEmpty()
                    || !DECLARATION_ORIGINS.contains(origin) && (plain || !description.isEmpty())
                    || origin.equals(DOCUMENTARY_CONSTRAINT) && plain) {
                throw invalidConstraint(field, "Inconsistent composition origin");
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
            boolean pattern = annotation.equals("jakarta.validation.constraints.Pattern");
            if (pattern && (regexp == null || patternFlagsEmpty == null))
                throw invalidConstraint(field, "Missing direct Pattern facts (regexp/patternFlagsEmpty)");
            if (!pattern && (regexp != null || patternFlagsEmpty != null))
                throw invalidConstraint(field, "Pattern facts on a non-Pattern declaration");
            if (plain && !pattern && !decimalRule(annotation) && (!integral
                    || !value.matches("-?\\d+"))) throw invalid("Invalid direct integral constraint fact " + annotation);
            validateOrigin();
            field.constraints.add(new ConstraintRule(annotation, value, plain, declaration, decimalValue, inclusive, regexp, patternFlagsEmpty,
                    origin, owner, description));
        }
    }

    private static final class MethodAnnotationVisitor extends AnnotationVisitor {
        private final String annotation;
        private final MutableLiterals literal;

        MethodAnnotationVisitor(String annotation, MutableLiterals literal) {
            super(Opcodes.ASM9);
            this.annotation = annotation;
            this.literal = literal;
        }

        @Override public void visit(String member, Object value) {
            if (member.equals(EXAMPLES) && annotation.startsWith(PREFIX)) throw invalidExamples(1, "expected String array");
            switch (annotation) {
                case OPERATION_FACT -> {
                    if (member.equals("authoritative")) {
                        if (literal.authoritative != null || !(value instanceof Boolean))
                            throw invalid("Duplicate or invalid authoritative selection fact");
                        literal.authoritative = (Boolean) value;
                    } else if (member.equals(SOURCE_DEPRECATED)) {
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
                case "converterCount" -> {
                    if (!(value instanceof Integer) || literal.converterCount != null)
                        throw invalid("Invalid converter selection count");
                    literal.converterCount = (Integer) value;
                }
                case "constraintCount" -> {
                    if (!(value instanceof Integer count) || count < 0 || literal.constraintCount != null) {
                        throw invalidConstraint(literal, "Invalid constraint count");
                    }
                    literal.constraintCount = count;
                }
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

        private boolean nameFact(MutableNameFacts names, String member, Object value) {
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
            if (member.equals(EXAMPLES) && annotation.startsWith(PREFIX)) throw invalidExamples(1, "unexpected enum");
            if (annotation.equals(FIELD) && member.equals("constraintProjection")
                    && descriptor.equals("Lcom/blackbuild/klum/ast/Struct$ConstraintProjection$Policy;")) {
                if (literal.constraintProjection != null) throw invalid("Duplicate constraint projection policy");
                literal.constraintProjection = value;
                return;
            }
            if (annotation.startsWith(PREFIX)) throw invalid("Unexpected enum input fact " + member);
        }

        private IllegalArgumentException invalidExamples(int index, String reason) {
            return invalid("Invalid examples metadata on #" + literal.location + ", example " + index + ": " + reason);
        }

        private AnnotationVisitor examplesArray() {
            boolean operation = annotation.equals(OPERATION_FACT);
            if ((operation ? literal.operationExamples : literal.examples) != null) throw invalidExamples(1, "duplicate array");
            List<String> examples = new ArrayList<>();
            if (operation) literal.operationExamples = examples;
            else literal.examples = examples;
            return new AnnotationVisitor(Opcodes.ASM9) {
                private int characters;
                @Override public void visit(String ignored, Object value) {
                    if (!(value instanceof String text)) throw invalidExamples(examples.size() + 1, "requires String array elements");
                    // Classfile constants can be reused thousands of times; bound expanded documentary output.
                    characters += text.length();
                    if (characters > 1_048_576) throw invalidExamples(examples.size() + 1, "excessive documentary text");
                    examples.add(text);
                }
                @Override public AnnotationVisitor visitArray(String ignored) { throw invalidExamples(examples.size() + 1, "unexpected nested array"); }
                @Override public AnnotationVisitor visitAnnotation(String ignored, String descriptor) { throw invalidExamples(examples.size() + 1, "unexpected annotation"); }
                @Override public void visitEnum(String ignored, String descriptor, String value) { throw invalidExamples(examples.size() + 1, "unexpected enum"); }
            };
        }

        @Override public AnnotationVisitor visitArray(String member) {
            if (member.equals(EXAMPLES) && Set.of(FIELD, KEY_ANNOTATION, OPERATION_FACT).contains(annotation))
                return examplesArray();
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
            if (member.equals(EXAMPLES) && annotation.startsWith(PREFIX)) throw invalidExamples(1, "unexpected annotation");
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
