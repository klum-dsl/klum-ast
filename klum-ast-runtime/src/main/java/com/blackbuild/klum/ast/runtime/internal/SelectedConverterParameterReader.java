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
import groovyjarjarasm.asm.AnnotationVisitor;
import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.MethodVisitor;
import groovyjarjarasm.asm.Opcodes;
import groovyjarjarasm.asm.Type;
import groovyjarjarasm.asm.TypePath;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static com.blackbuild.klum.ast.runtime.internal.BuilderContractReader.invalid;
import static com.blackbuild.klum.ast.runtime.internal.BuilderContractReader.verifyResourceOrigin;
import static com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema.ConstraintRule;
import static com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema.ConverterConstraints;

/** Resolves only the recorded public signature and decodes a closed, inert parameter vocabulary from its exact bytes. */
final class SelectedConverterParameterReader {
    private static final String PATTERN_NAME = "jakarta.validation.constraints.Pattern";
    private static final String PATTERN = "Ljakarta/validation/constraints/Pattern;";
    private static final String PATTERNS = "Ljakarta/validation/constraints/Pattern$List;";
    private static final String FLAG = "Ljakarta/validation/constraints/Pattern$Flag;";
    private static final String POLICY = "Lcom/blackbuild/klum/ast/ConstraintProjection;";
    private static final String POLICY_VALUE = "Lcom/blackbuild/klum/ast/ConstraintProjection$Policy;";
    private static final Set<String> FLAGS = Set.of("UNIX_LINES", "CASE_INSENSITIVE", "COMMENTS", "MULTILINE",
            "DOTALL", "UNICODE_CASE", "CANON_EQ", "UNICODE_CHARACTER_CLASS");
    private final Map<Executable, ConverterConstraints> cache = new LinkedHashMap<>();

    ConverterConstraints read(Class<?> domain, BuilderContractReader.ConverterSelection selected)
            throws ReflectiveOperationException, IOException {
        Executable executable = resolve(domain, selected);
        ConverterConstraints known = cache.get(executable);
        if (known != null) return known;
        // Resolve the closed API even when the declaration has no Pattern. Missing APIs must never look like absence.
        Class<?> api = Class.forName(PATTERN_NAME, false, domain.getClassLoader());
        if (!api.isAnnotation()) throw invalid("Pattern API is not an annotation");
        Map<String, Object> defaults = defaults(api);
        if (!Class.forName(PATTERN_NAME + "$Flag", false, domain.getClassLoader()).isEnum()
                || !Class.forName("jakarta.validation.Payload", false, domain.getClassLoader()).isInterface())
            throw invalid("Unsupported Pattern member API contract");
        ParameterFacts facts = new ParameterFacts();
        readBytes(domain, new ClassVisitor(Opcodes.ASM9) {
            private int matches;
            @Override public void visit(int version, int access, String name, String signature, String parent, String[] interfaces) {
                if (!name.equals(Type.getInternalName(domain)) || (access & Opcodes.ACC_PUBLIC) == 0)
                    throw invalid("Selected domain resource identity/access mismatch");
            }
            @Override public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                if (descriptor.equals("Ljakarta/validation/GroupSequence;")) facts.groupRedefined = true;
                return null;
            }
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!name.equals(selected.name())) return null;
                if (!descriptor.equals(selected.descriptor())) throw invalid("Overloaded selected executable family");
                if (++matches != 1 || access != executable.getModifiers()) throw invalid("Selected domain byte/reflection signature mismatch");
                return facts.visitor();
            }
            @Override public void visitEnd() {
                if (matches != 1) throw invalid("Missing exact selected domain declaration");
            }
        });
        if (facts.containerSeen) verifyContainerApi(domain);
        ConverterConstraints result = normalize(domain, selected, defaults, facts);
        cache.put(executable, result);
        return result;
    }

    private static Executable resolve(Class<?> domain, BuilderContractReader.ConverterSelection selected)
            throws ReflectiveOperationException {
        if (!Modifier.isPublic(domain.getModifiers()) || Class.forName(domain.getName(), false, domain.getClassLoader()) != domain)
            throw invalid("Expected exact public domain identity");
        if (!domain.getName().equals(selected.owner()) || domain.isInterface() || domain.isEnum()
                || domain.getTypeParameters().length != 0 || selected.parameter() != 0)
            throw invalid("Selected converter domain/index mismatch");
        Executable executable;
        String descriptor;
        if (selected.name().equals("<init>")) {
            Constructor<?> constructor = domain.getDeclaredConstructor(String.class);
            if (Modifier.isAbstract(domain.getModifiers())) throw invalid("Selected constructor domain is abstract");
            executable = constructor;
            descriptor = Type.getConstructorDescriptor(constructor);
        } else {
            Method method = domain.getDeclaredMethod(selected.name(), String.class);
            if (!Modifier.isStatic(method.getModifiers()) || method.getReturnType() != domain || method.isBridge())
                throw invalid("Selected factory must be an unambiguous declared static String-to-exact-domain method");
            executable = method;
            descriptor = Type.getMethodDescriptor(method);
        }
        if (!descriptor.equals(selected.descriptor()) || !Modifier.isPublic(executable.getModifiers())
                || executable.isSynthetic() || executable.isVarArgs() || executable.getTypeParameters().length != 0
                || executable.getGenericParameterTypes()[0] != String.class)
            throw invalid("Selected converter descriptor/access/type mismatch");
        return executable;
    }

    private static void verifyContainerApi(Class<?> domain) throws ReflectiveOperationException, IOException {
        Class<?> container = Class.forName(PATTERN_NAME + "$List", false, domain.getClassLoader());
        if (!container.isAnnotation() || container.getDeclaredMethods().length != 1
                || !Type.getMethodDescriptor(container.getDeclaredMethod("value")).equals("()[Ljakarta/validation/constraints/Pattern;"))
            throw invalid("Unsupported Pattern container API contract");
        verifyResourceOrigin(container, container.getName().replace('.', '/') + ".class");
    }

    private static Map<String, Object> defaults(Class<?> api) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        // Signature resolution detects missing member API types, without constructing annotation proxies.
        Set<String> expected = Set.of("regexp", "flags", "groups", "payload", "message");
        Map<String, String> signatures = Map.of("regexp", "()Ljava/lang/String;", "message", "()Ljava/lang/String;",
                "flags", "()[Ljakarta/validation/constraints/Pattern$Flag;", "groups", "()[Ljava/lang/Class;", "payload", "()[Ljava/lang/Class;");
        Method[] members = api.getDeclaredMethods();
        if (members.length != signatures.size() || Arrays.stream(members).anyMatch(method ->
                !Type.getMethodDescriptor(method).equals(signatures.get(method.getName()))))
            throw invalid("Unsupported Pattern API contract");
        readBytes(api, new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (!expected.contains(name)) return null;
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public AnnotationVisitor visitAnnotationDefault() {
                        return new InertValue(value -> put(result, name, value));
                    }
                };
            }
        });
        if (!result.keySet().equals(Set.of("flags", "groups", "payload", "message")))
            throw invalid("Incomplete Pattern API defaults");
        if (!(result.get("message") instanceof String) || !array(result, "flags").isEmpty()
                || !array(result, "groups").isEmpty() || !array(result, "payload").isEmpty())
            throw invalid("Unsupported Pattern API default contract");
        return result;
    }

    private static void readBytes(Class<?> owner, ClassVisitor visitor) throws IOException {
        String resource = owner.getName().replace('.', '/') + ".class";
        verifyResourceOrigin(owner, resource);
        try (InputStream bytes = owner.getModule().getResourceAsStream(resource)) {
            if (bytes == null) throw new IOException("Missing selected contract resource " + resource);
            new ClassReader(bytes).accept(visitor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    private static ConverterConstraints normalize(Class<?> domain, BuilderContractReader.ConverterSelection selected,
                                                  Map<String, Object> defaults, ParameterFacts facts) throws ClassNotFoundException {
        List<ConstraintRule> rules = new ArrayList<>();
        for (Map<String, Object> authored : facts.patterns) {
            Map<String, Object> values = new LinkedHashMap<>(defaults);
            values.putAll(authored);
            if (!values.keySet().equals(Set.of("regexp", "flags", "groups", "payload", "message"))
                    || !(values.get("regexp") instanceof String regexp) || !(values.get("message") instanceof String))
                throw invalid("Malformed or incomplete selected Pattern members");
            List<?> flags = array(values, "flags");
            int bits = flagBits(flags);
            boolean groupsEmpty = resolveTypes(domain, array(values, "groups"), false);
            boolean payloadEmpty = resolveTypes(domain, array(values, "payload"), true);
            Pattern.compile(regexp, bits); // Syntax checking only; never apply a matcher or validator to user data.
            boolean eligible = groupsEmpty && payloadEmpty && !facts.groupRedefined && !facts.unsupportedPattern
                    && facts.patterns.size() == 1;
            String declaration = "@" + PATTERN_NAME + "(regexp=" + quoted(regexp) + ", flags=" + flags
                    + ", groups=" + values.get("groups") + ", payload=" + values.get("payload") + ")";
            rules.add(new ConstraintRule(PATTERN_NAME, "", false, declaration, null, null, regexp, flags.isEmpty(),
                    eligible ? "PARAMETER" : "PARAMETER_UNSUPPORTED", "", ""));
        }
        for (String annotation : facts.opaque) {
            rules.add(new ConstraintRule(annotation, "", false, "@" + annotation, null, null, null, null,
                    "PARAMETER_UNSUPPORTED", "", ""));
        }
        String source = selected.owner() + "#" + selected.name() + selected.descriptor() + " parameter[0]";
        return new ConverterConstraints(source, domain.getResource("/" + domain.getName().replace('.', '/') + ".class").toExternalForm(),
                facts.policy, rules);
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static List<?> array(Map<String, Object> values, String name) {
        if (!(values.get(name) instanceof List<?> result)) throw invalid("Pattern " + name + " must be an array");
        return result;
    }

    private static int flagBits(List<?> flags) {
        int result = 0;
        for (Object value : flags) {
            if (!(value instanceof EnumValue flag) || !flag.type().equals(FLAG) || !FLAGS.contains(flag.value()))
                throw invalid("Invalid Pattern flag " + value);
            result |= switch (flag.value()) {
                case "UNIX_LINES" -> Pattern.UNIX_LINES;
                case "CASE_INSENSITIVE" -> Pattern.CASE_INSENSITIVE;
                case "COMMENTS" -> Pattern.COMMENTS;
                case "MULTILINE" -> Pattern.MULTILINE;
                case "DOTALL" -> Pattern.DOTALL;
                case "UNICODE_CASE" -> Pattern.UNICODE_CASE;
                case "CANON_EQ" -> Pattern.CANON_EQ;
                case "UNICODE_CHARACTER_CLASS" -> Pattern.UNICODE_CHARACTER_CLASS;
                default -> throw invalid("Unknown Pattern flag");
            };
        }
        return result;
    }

    private static boolean resolveTypes(Class<?> domain, List<?> values, boolean payload) throws ClassNotFoundException {
        Class<?> payloadApi = Class.forName("jakarta.validation.Payload", false, domain.getClassLoader());
        for (Object value : values) {
            if (!(value instanceof Type type) || type.getSort() != Type.OBJECT) throw invalid("Invalid Pattern class member");
            Class<?> resolved = Class.forName(type.getClassName(), false, domain.getClassLoader());
            if (payload && !payloadApi.isAssignableFrom(resolved)) throw invalid("Pattern payload does not implement Payload");
        }
        return values.isEmpty();
    }

    private static final class ParameterFacts {
        private final List<Map<String, Object>> patterns = new ArrayList<>();
        private final List<String> opaque = new ArrayList<>();
        private Policy policy = Policy.PROJECT;
        private boolean policySeen;
        private boolean patternSeen;
        private boolean containerSeen;
        private boolean groupRedefined;
        private boolean unsupportedPattern;

        MethodVisitor visitor() {
            return new MethodVisitor(Opcodes.ASM9) {
                @Override public void visitAnnotableParameterCount(int count, boolean visible) {
                    if (count != 1) throw invalid("Invalid selected parameter annotation count");
                }
                @Override public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                    if (parameter != 0) throw invalid("Wrong selected parameter annotation index");
                    if (descriptor.equals(POLICY)) return policyVisitor(visible);
                    if (descriptor.equals(PATTERN)) {
                        if (!visible || patternSeen) throw invalid("Duplicate or non-runtime Pattern declaration");
                        patternSeen = true;
                        return patternVisitor();
                    }
                    if (descriptor.equals(PATTERNS)) {
                        if (!visible || containerSeen) throw invalid("Duplicate or non-runtime Pattern container");
                        containerSeen = true;
                        unsupportedPattern = true;
                        return new InertMembers(values -> {
                            if (!values.keySet().equals(Set.of("value"))) throw invalid("Malformed Pattern container");
                            for (Object item : array(values, "value")) {
                                if (!(item instanceof Nested nested) || !nested.type().equals(PATTERN))
                                    throw invalid("Malformed Pattern container item");
                                patterns.add(nested.values());
                            }
                        });
                    }
                    if (visible) opaque.add(Type.getType(descriptor).getClassName());
                    return null;
                }
                @Override public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String descriptor, boolean visible) {
                    // Java duplicates declaration-targeted Pattern as type-use. Only declaration facts are authority.
                    return null;
                }
            };
        }

        private AnnotationVisitor patternVisitor() { return new InertMembers(patterns::add); }

        private AnnotationVisitor policyVisitor(boolean visible) {
            if (!visible || policySeen) throw invalid("Duplicate or non-runtime parameter policy");
            policySeen = true;
            return new InertMembers(values -> {
                if (values.isEmpty()) return;
                if (!values.keySet().equals(Set.of("value")) || !(values.get("value") instanceof EnumValue value)
                        || !value.type().equals(POLICY_VALUE)) throw invalid("Malformed parameter projection policy");
                policy = Policy.valueOf(value.value());
            });
        }
    }

    private record EnumValue(String type, String value) {
        @Override public String toString() { return value; }
    }
    private record Nested(String type, Map<String, Object> values) { }

    /** Closed fact values only; arbitrary annotations never enter this decoder. */
    private static final class InertMembers extends AnnotationVisitor {
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final Consumer<Map<String, Object>> sink;
        InertMembers(Consumer<Map<String, Object>> sink) { super(Opcodes.ASM9); this.sink = sink; }
        @Override public void visit(String name, Object value) { put(values, name, value); }
        @Override public void visitEnum(String name, String type, String value) { put(values, name, new EnumValue(type, value)); }
        @Override public AnnotationVisitor visitArray(String name) { return arrayVisitor(value -> put(values, name, value)); }
        @Override public AnnotationVisitor visitAnnotation(String name, String type) {
            throw invalid("Unexpected nested member " + name);
        }
        @Override public void visitEnd() { sink.accept(values); }
    }

    private static final class InertValue extends AnnotationVisitor {
        private final Consumer<Object> sink;
        InertValue(Consumer<Object> sink) { super(Opcodes.ASM9); this.sink = sink; }
        @Override public void visit(String name, Object value) { sink.accept(value); }
        @Override public AnnotationVisitor visitArray(String name) { return arrayVisitor(sink); }
        @Override public void visitEnum(String name, String type, String value) { sink.accept(new EnumValue(type, value)); }
        @Override public AnnotationVisitor visitAnnotation(String name, String type) { throw invalid("Unexpected annotation default"); }
    }

    private static AnnotationVisitor arrayVisitor(Consumer<Object> sink) {
        List<Object> values = new ArrayList<>();
        return new AnnotationVisitor(Opcodes.ASM9) {
            @Override public void visit(String name, Object value) { values.add(value); }
            @Override public void visitEnum(String name, String type, String value) { values.add(new EnumValue(type, value)); }
            @Override public AnnotationVisitor visitAnnotation(String name, String type) {
                if (!type.equals(PATTERN)) throw invalid("Unexpected nested parameter annotation");
                return new InertMembers(value -> values.add(new Nested(type, value)));
            }
            @Override public AnnotationVisitor visitArray(String name) { throw invalid("Unexpected nested parameter array"); }
            @Override public void visitEnd() { sink.accept(List.copyOf(values)); }
        };
    }

    private static void put(Map<String, Object> values, String name, Object value) {
        if (values.putIfAbsent(name, value) != null) throw invalid("Duplicate selected parameter member " + name);
    }
}
