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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static groovyjarjarasm.asm.Type.getMethodDescriptor;

/** Disposable internal facade. UNKNOWN is a fact gap, never admission. No carrier or implementation inspection. */
final class CompiledBuilderSchema {
    private final String model;
    private final String builder;
    private final List<String> parents;
    private final List<Declaration> methods;
    private final List<Declaration> factories;

    record Declaration(String owner, String name, String descriptor, List<String> parameters, String returns,
                       String documentation, String eligibility, String category, String externalName, String nameOrigin,
                       String source, String reason, String payload, List<String> enumNames, String defaultStatus, Shape returnShape, List<Shape> parameterShapes) { }

    record Shape(String kind, String type, String builder, String keyType, Shape item) { }

    private static final String BUILDER_METHOD = "Lcom/blackbuild/klum/ast/Builder$Method;";
    private static final String QUERY = "Lcom/blackbuild/klum/ast/Builder$Query;";
    private static final String DOC = "Lcom/blackbuild/annodocimal/annotations/AnnoDoc;";
    private static final String IGNORE = "Lcom/fasterxml/jackson/annotation/JsonIgnore;";
    private static final String PROPERTY = "Lcom/fasterxml/jackson/annotation/JsonProperty;";
    private static final Set<String> ANNOTATIONS = Set.of(BUILDER_METHOD, QUERY, DOC, IGNORE, PROPERTY);
    private static final Set<String> NUMBERS = Set.of("byte", "short", "int", "long", "float", "double",
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float",
            "java.lang.Double", "java.math.BigInteger", "java.math.BigDecimal");

    private CompiledBuilderSchema(Class<?> builder, Class<?> factory, String model) throws IOException {
        this.model = model;
        this.builder = builder.getName();
        this.parents = Arrays.stream(builder.getInterfaces()).map(Class::getName).sorted().toList();
        this.methods = declarations(builder, false);
        this.factories = declarations(factory, true);
    }

    public String getModel() { return model; }
    public String getBuilder() { return builder; }
    public List<String> getParents() { return parents; }
    public List<Declaration> getMethods() { return methods; }
    public List<Declaration> getFactories() { return factories; }

    static CompiledBuilderSchema read(ClassLoader loader, String builderName, String factoryName) {
        try {
            Class<?> builder = Class.forName(builderName, false, loader);
            Class<?> factory = Class.forName(factoryName, false, loader);
            if (!builder.isInterface() || !Modifier.isPublic(builder.getModifiers()) || !factory.isInterface()
                    || !Modifier.isPublic(factory.getModifiers()))
                throw new IllegalArgumentException("Expected public Builder/Factory interfaces");
            String model = modelOf(builder);
            if (model == null || Arrays.stream(factory.getMethods()).noneMatch(method -> method.getName().equals("One")
                    && method.getReturnType().getName().equals(model)))
                throw new IllegalArgumentException("Builder self bound and Factory result disagree");
            return new CompiledBuilderSchema(builder, factory, model);
        } catch (ClassNotFoundException | LinkageError | IOException failure) {
            throw new IllegalStateException("Compiled contract " + builderName
                    + ": supply the complete API/Schema/runtime classpath and exact contract resources", failure);
        }
    }

    /** Exact explicitly selected Collection/Cluster public scope; no search for nested types. */
    static List<Declaration> readScope(ClassLoader loader, String contractName) {
        try {
            Class<?> contract = Class.forName(contractName, false, loader);
            if (!contract.isInterface() || !Modifier.isPublic(contract.getModifiers()))
                throw new IllegalArgumentException("Expected a public generated scope interface");
            return declarations(contract, true);
        } catch (ClassNotFoundException | LinkageError | IOException failure) {
            throw new IllegalStateException("Compiled scope " + contractName + ": supply the complete classpath and exact contract resources", failure);
        }
    }

    private static String modelOf(Class<?> builder) {
        return Arrays.stream(builder.getTypeParameters()).flatMap(variable -> Arrays.stream(variable.getBounds()))
                .filter(bound -> bound instanceof Class<?> && bound != Object.class)
                .map(Type::getTypeName).findFirst().orElse(null);
    }

    private static List<Declaration> declarations(Class<?> contract, boolean factory) throws IOException {
        Map<Class<?>, Map<String, MethodMetadata>> metadata = new HashMap<>();
        List<Declaration> result = new ArrayList<>();
        List<Method> methods = Arrays.stream(contract.getMethods())
                .sorted(Comparator.comparing((Method method) -> method.getDeclaringClass().getName()).thenComparing(CompiledBuilderSchema::descriptor)).toList();
        for (Method method : methods) {
            Class<?> owner = method.getDeclaringClass();
            if (!metadata.containsKey(owner)) metadata.put(owner, annotationsOf(owner));
            MethodMetadata literals = metadata.get(owner).get(getMethodDescriptor(method) + method.getName());
            if (literals == null) throw new IllegalStateException("Missing exact contract declaration " + owner.getName() + " " + descriptor(method));
            String declaringModel = modelOf(owner);
            String source = declaringModel == null ? null : declaringModel + "#" + descriptor(method);
            String eligibility = factory ? "EXCLUDED" : "UNKNOWN";
            String external = null;
            String origin = null;
            String reason = factory ? "Factory invocation is outside WIRE-1 admission" : "Missing compiler field/group or authored provenance";
            String payload = null;
            if (literals.query) {
                eligibility = "EXCLUDED";
                reason = "Builder query is outside input admission";
            } else if (literals.operation && !factory) {
                external = literals.rename == null || literals.rename.isEmpty() ? method.getName() : literals.rename;
                origin = literals.rename == null || literals.rename.isEmpty() ? "IDENTITY" : "JSON_PROPERTY";
                if (literals.ignored) {
                    eligibility = "EXCLUDED";
                    reason = "excluded by @JsonIgnore(true)";
                    external = null;
                    origin = null;
                } else if (method.getParameterCount() != 1) {
                    eligibility = "EXCLUDED";
                    reason = "@Builder.Method must declare exactly one payload";
                } else {
                    payload = payloadOf(method.getGenericParameterTypes()[0]);
                    eligibility = payload == null ? "EXCLUDED" : "ELIGIBLE";
                    reason = payload == null ? unsupportedReason(method.getGenericParameterTypes()[0])
                            : "Global admission needs complete field names and collision provenance";
                }
            }
            List<String> constants = Arrays.stream(method.getParameterTypes()).filter(Class::isEnum)
                    .flatMap(enumeration -> Arrays.stream(enumeration.getDeclaredFields()))
                    .filter(field -> field.isEnumConstant()).map(field -> field.getName()).toList();
            result.add(new Declaration(owner.getName(), method.getName(), descriptor(method),
                    Arrays.stream(method.getGenericParameterTypes()).map(Type::getTypeName).toList(),
                    method.getGenericReturnType().getTypeName(), literals.documentation, eligibility, factory ? "FACTORY" : literals.operation ? "OPERATION" : literals.query ? "QUERY" : "UNCLASSIFIED", external, origin,
                    source, reason, payload, constants, "UNAVAILABLE_WITHOUT_EXECUTION", shapeOf(method.getGenericReturnType()),
                    Arrays.stream(method.getGenericParameterTypes()).map(CompiledBuilderSchema::shapeOf).toList()));
        }
        return List.copyOf(result);
    }

    private static String descriptor(Method method) {
        return method.getName() + "(" + Arrays.stream(method.getParameterTypes()).map(Class::getTypeName)
                .collect(Collectors.joining(", ")) + "):" + method.getReturnType().getTypeName();
    }

    /** Finite operation shapes only: field storage and relationship eligibility remain unknown. */
    private static String payloadOf(Type type) {
        if (type instanceof Class<?> leaf) {
            if (leaf.isEnum()) return "ENUM:" + leaf.getName();
            if (leaf == String.class || leaf == boolean.class || leaf == Boolean.class || NUMBERS.contains(leaf.getName()))
                return "SCALAR:" + leaf.getName();
            return null;
        }
        if (!(type instanceof ParameterizedType parameterized)) return null;
        Type[] arguments = parameterized.getActualTypeArguments();
        if (parameterized.getRawType() == List.class && arguments.length == 1) {
            String item = payloadOf(arguments[0]);
            return item == null ? null : "LIST:java.util.List[" + item + "]";
        }
        if (parameterized.getRawType() == Map.class && arguments.length == 2 && arguments[0] == String.class) {
            String item = payloadOf(arguments[1]);
            return item == null ? null : "MAP:java.util.Map:java.lang.String[" + item + "]";
        }
        return null;
    }

    /** Observed type structure only; a nested Builder reference does not establish owning admission. */
    private static Shape shapeOf(Type type) {
        if (type instanceof Class<?> leaf) {
            String kind = leaf.isEnum() ? "ENUM" : payloadOf(leaf) == null ? "REFERENCE" : "SCALAR";
            return new Shape(kind, leaf.getName(), null, null, null);
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> raw) {
            Type[] arguments = parameterized.getActualTypeArguments();
            if (Map.class.isAssignableFrom(raw) && arguments.length == 2)
                return new Shape("MAP", raw.getName(), null, arguments[0].getTypeName(), shapeOf(arguments[1]));
            if (Collection.class.isAssignableFrom(raw) && arguments.length == 1)
                return new Shape("LIST", raw.getName(), null, null, shapeOf(arguments[0]));
            if (raw.isInterface() && raw.getName().endsWith("_DSL$Builder") && modelOf(raw) != null)
                return new Shape("NESTED_OBJECT", modelOf(raw), raw.getName(), null, null);
        }
        return new Shape("UNRESOLVED", type.getTypeName(), null, null, null);
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

    private static final class MethodMetadata {
        String documentation;
        String rename;
        boolean operation;
        boolean query;
        boolean ignored;
    }

    /** Only allowlisted literals on exact public contract resources; no annotation proxies or enum resolution. */
    private static Map<String, MethodMetadata> annotationsOf(Class<?> owner) throws IOException {
        String resource = "/" + owner.getName().replace('.', '/') + ".class";
        try (InputStream stream = owner.getResourceAsStream(resource)) {
            if (stream == null) throw new IOException("Missing exact public contract resource " + resource);
            Map<String, MethodMetadata> result = new HashMap<>();
            new ClassReader(stream).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodMetadata literals = new MethodMetadata();
                    result.put(descriptor + name, literals);
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public AnnotationVisitor visitAnnotation(String annotation, boolean visible) {
                            if (!visible || !ANNOTATIONS.contains(annotation)) return null;
                            if (annotation.equals(BUILDER_METHOD)) literals.operation = true;
                            if (annotation.equals(QUERY)) literals.query = true;
                            if (annotation.equals(IGNORE)) literals.ignored = true;
                            return new AnnotationVisitor(Opcodes.ASM9) {
                                @Override
                                public void visit(String member, Object value) {
                                    if (!member.equals("value")) return;
                                    if (annotation.equals(DOC) && value instanceof String text) literals.documentation = text;
                                    if (annotation.equals(PROPERTY) && value instanceof String text) literals.rename = text;
                                    if (annotation.equals(IGNORE) && value instanceof Boolean ignored) literals.ignored = ignored;
                                }
                            };
                        }
                    };
                }
            }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
