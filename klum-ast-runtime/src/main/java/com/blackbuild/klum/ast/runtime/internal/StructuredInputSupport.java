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
import com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema.Entry;
import com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema.Key;
import com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema.Payload;
import com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema.Kind;
import com.blackbuild.klum.ast.runtime.internal.CompiledBuilderSchema.Role;
import com.blackbuild.klum.ast.runtime.internal.process.BreadcrumbCollector;
import com.blackbuild.klum.ast.runtime.internal.process.PhaseDriver;
import groovy.lang.MetaMethod;
import groovyjarjarasm.asm.ClassReader;
import groovyjarjarasm.asm.ClassVisitor;
import groovyjarjarasm.asm.FieldVisitor;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.InvokerInvocationException;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Implementation bridge in the existing non-exported internal package. No metadata leaves this class. */
public final class StructuredInputSupport {
    private static final String DOMAIN_NAME = "<domain>";

    private StructuredInputSupport() { }

    public static <T> T readRoot(Class<T> type, Object parsed, String sourceName) {
        requireSource(sourceName);
        requireConcrete(type, sourceName);
        Domain domain = new Preparation(sourceName).domain(type, parsed, "", Position.ROOT);
        return FactoryHelper.createWithConfiguration(type, domain.key(), domain::execute);
    }

    private static void requireConcrete(Class<?> type, String sourceName) {
        if (type == null || type.isInterface() || Modifier.isAbstract(type.getModifiers()))
            throw new KlumModelException(sourceName + ": structured input requires an exact concrete Schema type");
    }

    public static <T> T readRoot(Class<T> type, String rootKey, Object parsed, String sourceName) {
        requireSource(sourceName);
        requireConcrete(type, sourceName);
        Preparation preparation = new Preparation(sourceName);
        Domain domain = preparation.domain(type, parsed, "", Position.EXPLICIT_ROOT);
        if (DslHelper.isKeyed(type) != (rootKey != null))
            throw new KlumModelException(sourceName + ": supply an explicit rootKey for a keyed Schema and null for an unkeyed Schema");
        return FactoryHelper.createWithConfiguration(type, rootKey, domain::execute);
    }

    public static <B extends KlumBuilder<?>> B applyToBuilder(B target, Object parsed, String sourceName) {
        requireSource(sourceName);
        if (!(target instanceof InternalKlumBuilder<?> builder))
            throw new KlumModelException(sourceName + ": structured input requires an active generated Builder");
        PhaseDriver.requireCurrentConstructionSession(builder);
        if (builder.isSealed() || builder.isTemplate()
                || builder.getClass() != GeneratedBuilderSupport.builderTypeFor(builder.getModelType()))
            throw new KlumModelException(sourceName + ": structured input requires an unsealed ordinary generated Builder");
        Domain domain = new Preparation(sourceName).domain(builder.getModelType(), parsed, "", Position.ACTIVE_BUILDER);
        apply(builder, domain);
        return target;
    }

    private static <T> void apply(InternalKlumBuilder<T> builder, Domain domain) {
        builder.applyConfiguration(domain::execute);
    }

    private static void requireSource(String source) {
        if (source == null || source.isEmpty())
            throw new KlumModelException("Structured input requires a nonempty opaque sourceName");
    }

    private static String pointer(String parent, String token) {
        return parent + "/" + token.replace("~", "~0").replace("/", "~1");
    }

    private static KlumModelException problem(String source, String path, Class<?> contract, String name,
                                               String expected, Object actual, Throwable cause) {
        String message = source + " at '" + (path.isEmpty() ? "/" : path) + "', external name '" + name
                + "', public Builder " + contract.getName() + ": expected " + expected + "; actual "
                + (actual == null ? "null" : actual.getClass().getName());
        return cause == null ? new KlumModelException(message) : new KlumModelException(message, cause);
    }

    private enum Position {
        ROOT, DIRECT, EXPLICIT_ROOT, ACTIVE_BUILDER, SCHEMA_CHILD, MAP_VALUE;
        boolean memberKey() { return this == ROOT || this == DIRECT; }
        String authority() {
            return switch (this) {
                case EXPLICIT_ROOT -> "explicit rootKey argument";
                case ACTIVE_BUILDER -> "already allocated active Builder";
                case SCHEMA_CHILD -> "Schema key provider";
                case MAP_VALUE -> "outer Map entry key";
                default -> throw new IllegalStateException("Position has no external key authority: " + this);
            };
        }
    }

    private interface Value { Object materialize(); }
    private record Scalar(Object value) implements Value {
        @Override public Object materialize() { return value; }
    }
    private record EnumValue(Class<?> type, String name) implements Value {
        @Override @SuppressWarnings({"unchecked", "rawtypes"})
        public Object materialize() { return Enum.valueOf((Class) type, name); }
    }
    private record Sequence(List<Value> values) implements Value {
        @Override public Object materialize() {
            List<Object> result = new ArrayList<>();
            values.forEach(value -> result.add(value.materialize()));
            return result;
        }
    }
    private record MapItem(String key, Value value) { }
    private record DataMap(List<MapItem> items) implements Value {
        @Override public Object materialize() {
            Map<String, Object> result = new LinkedHashMap<>();
            items.forEach(item -> result.put(item.key(), item.value().materialize()));
            return result;
        }
    }
    private record KeyedChild(String key, Domain domain) { }
    private record Domain(String key, List<Step> steps) {
        void execute(InternalKlumBuilder<?> builder) { steps.forEach(step -> step.execute(builder)); }
    }

    private record Step(String source, String path, Entry entry, Method method,
                        Value value, Domain child, List<KeyedChild> children) {
        void execute(InternalKlumBuilder<?> builder) {
            switch (entry.role()) {
                case OPERATION -> invoke(builder, new Object[]{value.materialize()}, path);
                case PROPERTY -> {
                    if (value instanceof Sequence sequence) {
                        for (int i = 0; i < sequence.values().size(); i++)
                            invoke(builder, new Object[]{sequence.values().get(i).materialize()}, pointer(path, Integer.toString(i)));
                    } else if (value instanceof DataMap map) {
                        for (MapItem item : map.items())
                            invoke(builder, new Object[]{item.key(), item.value().materialize()}, pointer(path, item.key()));
                    } else invoke(builder, new Object[]{value.materialize()}, path);
                }
                case OWNED_OBJECT -> {
                    recordInvocation(builder);
                    builder.createSingleChildWithConfiguration(fieldName(), entry.payload().type(), false, child.key() == null ? entry.fixedChildKey() : child.key(), child::execute);
                }
                case KEYED_OWNED_MAP -> {
                    for (KeyedChild item : children) {
                        recordInvocation(builder);
                        builder.addNewDslElementToMapWithConfiguration(fieldName(), entry.payload().type(), false,
                                item.key(), item.domain()::execute);
                    }
                }
            }
        }

        private String fieldName() { return entry.source().substring(entry.source().indexOf('#') + 1); }

        private void recordInvocation(InternalKlumBuilder<?> builder) {
            BuilderDispatchSupport.recordInvocation(builder, entry.operation());
            BreadcrumbCollector.getInstance().setVerb(entry.operation());
        }

        private void invoke(InternalKlumBuilder<?> builder, Object[] arguments, String argumentPath) {
            try {
                MetaMethod selected = InvokerHelper.getMetaClass(builder).pickMethod(method.getName(), method.getParameterTypes());
                if (!entry.contract().isInstance(builder) || selected == null || !selected.isPublic()
                        || !selected.getName().equals(method.getName())
                        || !Arrays.equals(selected.getNativeParameterTypes(), method.getParameterTypes())
                        || !method.getReturnType().isAssignableFrom(selected.getReturnType()))
                    throw new IllegalArgumentException("Groovy selection does not match the recorded public contract signature " + method);
                recordInvocation(builder);
                selected.invoke(builder, arguments);
            } catch (RuntimeException failure) {
                Throwable cause = failure;
                while (cause instanceof InvokerInvocationException && cause.getCause() != null) cause = cause.getCause();
                if (cause instanceof Error error) throw error;
                throw problem(source, argumentPath, entry.contract(), entry.externalName(),
                        "successful invocation of " + method, arguments.length == 1 ? arguments[0] : arguments, cause);
            }
        }
    }

    private static final class Preparation {
        private static final Set<Class<?>> NUMBERS = Set.of(Byte.class, Short.class, Integer.class, Long.class,
                Float.class, Double.class, BigInteger.class, BigDecimal.class);
        private final String source;
        private final Map<Class<?>, CompiledBuilderSchema> plans = new HashMap<>();
        private final Map<Object, Boolean> visiting = new IdentityHashMap<>();
        private Preparation(String source) { this.source = source; }

        private Domain domain(Class<?> model, Object input, String path, Position position) {
            CompiledBuilderSchema plan = plan(model, path);
            if (!(input instanceof Map<?, ?> map))
                throw problem(source, path, plan.contract(), DOMAIN_NAME, "domain Map with String keys", input, null);
            enter(input, path, plan.contract(), DOMAIN_NAME);
            try {
                Key key = plan.key();
                validatePosition(plan, key, position, path, input);
                String inputKey = null;
                List<Step> result = new ArrayList<>();
                for (Map.Entry<?, ?> supplied : map.entrySet()) {
                    String name = suppliedName(plan, supplied.getKey(), path);
                    String location = pointer(path, name);
                    Entry entry = plan.entries().get(name);
                    if (isKeyInput(key, position, name, entry)) {
                        inputKey = constructionKeyValue(plan, key, position, location, supplied.getValue());
                    } else {
                        requireEntry(plan, key, entry, name, location, supplied.getValue());
                        result.add(step(entry, supplied.getValue(), location));
                    }
                }
                requireInputKey(plan, key, position, path, inputKey);
                return new Domain(inputKey, List.copyOf(result));
            } finally { visiting.remove(input); }
        }

        private CompiledBuilderSchema plan(Class<?> model, String path) {
            try { return plans.computeIfAbsent(model, CompiledBuilderSchema::read); }
            catch (KlumModelException failure) {
                throw new KlumModelException(source + " at '" + (path.isEmpty() ? "/" : path) + "': " + failure.getMessage(), failure);
            }
        }

        private void validatePosition(CompiledBuilderSchema plan, Key key, Position position, String path, Object input) {
            if (position.memberKey() && key != null) validateMemberKey(plan, key, path, input);
        }

        private String suppliedName(CompiledBuilderSchema plan, Object supplied, String path) {
            if (!(supplied instanceof String name))
                throw problem(source, path, plan.contract(), "<key>", "String domain key", supplied, null);
            return name;
        }

        private static boolean isKeyInput(Key key, Position position, String name, Entry entry) {
            return key != null && !key.ignored() && name.equals(key.externalName()) && (position.memberKey() || entry == null);
        }

        private String constructionKeyValue(CompiledBuilderSchema plan, Key key, Position position, String path, Object input) {
            if (!position.memberKey())
                throw problem(source, path, plan.contract(), key.externalName(),
                        "no in-document construction key declared by " + key.source() + "; identity belongs to "
                                + position.authority() + "; inner identity is forbidden even if equal", input, null);
            if (!(input instanceof String text))
                throw problem(source, path, plan.contract(), key.externalName(),
                        "String required construction key declared by " + key.source(), input, null);
            return text;
        }

        private void requireEntry(CompiledBuilderSchema plan, Key key, Entry entry, String name, String path, Object input) {
            if (entry != null) return;
            String spelling = key != null && !key.ignored() && !key.nativeName().equals(key.externalName())
                    ? "; accepted construction key spelling is '" + key.externalName() + "' declared by " + key.source() : "";
            throw problem(source, path, plan.contract(), name,
                    "an admitted entry; unavailable declarations: " + plan.rejections() + spelling, input, null);
        }

        private void requireInputKey(CompiledBuilderSchema plan, Key key, Position position, String path, String inputKey) {
            if (position.memberKey() && key != null && inputKey == null)
                throw problem(source, pointer(path, key.externalName()), plan.contract(), key.externalName(),
                        "required construction key declared by " + key.source() + " (member is absent)", null, null);
        }

        private void validateMemberKey(CompiledBuilderSchema plan, Key key, String path, Object input) {
            if (key.ignored())
                throw problem(source, pointer(path, key.externalName()), plan.contract(), key.externalName(),
                        "available required construction key declared by " + key.source()
                                + "; " + key.ignoreReason() + "; use an explicit rootKey for a root, or Schema derivation/keyed Map for a child", input, null);
            List<String> collisions = plan.keyCollisions();
            if (!collisions.isEmpty())
                throw problem(source, pointer(path, key.externalName()), plan.contract(), key.externalName(),
                        "unambiguous required construction key; external-name collision: " + key.source() + ", " + String.join(", ", collisions), input, null);
        }

        private Step step(Entry entry, Object input, String path) {
            return switch (entry.role()) {
                case OWNED_OBJECT -> {
                    if (!(input instanceof Map)) throw bad(entry, path, "domain Map for " + entry.payload().type().getName(), input, null);
                    yield new Step(source, path, entry, null, null, domain(entry.payload().type(), input, path,
                            directPosition(entry)), null);
                }
                case KEYED_OWNED_MAP -> {
                    if (!(input instanceof Map<?, ?> map))
                        throw bad(entry, path, "Map of literal String instance keys to domain Maps", input, null);
                    enter(input, path, entry.contract(), entry.externalName());
                    try {
                        List<KeyedChild> children = new ArrayList<>();
                        for (Map.Entry<?, ?> supplied : map.entrySet()) {
                            if (!(supplied.getKey() instanceof String key))
                                throw bad(entry, path, "String instance key", supplied.getKey(), null);
                            children.add(new KeyedChild(key, domain(entry.payload().type(), supplied.getValue(), pointer(path, key), Position.MAP_VALUE)));
                        }
                        yield new Step(source, path, entry, null, null, null, List.copyOf(children));
                    } finally { visiting.remove(input); }
                }
                case PROPERTY, OPERATION -> {
                    Method method = method(entry, path);
                    Value prepared = value(entry, entry.payload(), input, path);
                    yield new Step(source, path, entry, method, prepared, null, null);
                }
            };
        }

        private static Position directPosition(Entry entry) {
            if (entry.schemaKey()) return Position.SCHEMA_CHILD;
            return Position.DIRECT;
        }

        private Method method(Entry entry, String path) {
            Class<?>[] signature;
            if (entry.role() == Role.PROPERTY && entry.payload().kind() == Kind.LIST)
                signature = new Class<?>[]{entry.payload().item().type()};
            else if (entry.role() == Role.PROPERTY && entry.payload().kind() == Kind.MAP)
                signature = new Class<?>[]{String.class, entry.payload().item().type()};
            else signature = new Class<?>[]{entry.payload().type()};
            List<Method> candidates = entry.methods().stream()
                    .filter(method -> Arrays.equals(method.getParameterTypes(), signature)).toList();
            if (candidates.size() != 1)
                throw bad(entry, path, "one compiler-recorded exact payload/population signature; recompile the Schema", null, null);
            return candidates.get(0);
        }

        private Value value(Entry entry, Payload payload, Object input, String path) {
            if (input == null) throw bad(entry, path, payload.kind() + " " + payload.type().getTypeName(), null, null);
            if ((input instanceof Map || input instanceof List) && visiting.containsKey(input))
                throw bad(entry, path, "acyclic input (cyclic container detected)", input, null);
            return switch (payload.kind()) {
                case SCALAR -> scalar(entry, payload.type(), input, path);
                case ENUM -> {
                    if (!(input instanceof String name) || !enumNames(entry, payload.type(), path).contains(name))
                        throw bad(entry, path, "exact declared enum constant of " + payload.type().getName(), input, null);
                    yield new EnumValue(payload.type(), name);
                }
                case LIST -> {
                    if (!(input instanceof List<?> list)) throw bad(entry, path, "List", input, null);
                    enter(input, path, entry.contract(), entry.externalName());
                    try {
                        List<Value> items = new ArrayList<>();
                        for (int i = 0; i < list.size(); i++)
                            items.add(value(entry, payload.item(), list.get(i), pointer(path, Integer.toString(i))));
                        yield new Sequence(List.copyOf(items));
                    } finally { visiting.remove(input); }
                }
                case MAP -> {
                    if (!(input instanceof Map<?, ?> map)) throw bad(entry, path, "Map with String keys", input, null);
                    enter(input, path, entry.contract(), entry.externalName());
                    try {
                        List<MapItem> items = new ArrayList<>();
                        for (Map.Entry<?, ?> item : map.entrySet()) {
                            if (!(item.getKey() instanceof String key)) throw bad(entry, path, "String data-map key", item.getKey(), null);
                            items.add(new MapItem(key, value(entry, payload.item(), item.getValue(), pointer(path, key))));
                        }
                        yield new DataMap(List.copyOf(items));
                    } finally { visiting.remove(input); }
                }
                case NESTED_OBJECT -> throw bad(entry, path, "a compiler-recorded owned relationship", input, null);
            };
        }

        private Value scalar(Entry entry, Class<?> type, Object input, String path) {
            if (type == String.class) {
                if (!(input instanceof String)) throw bad(entry, path, "String", input, null);
                return new Scalar(input);
            }
            if (type == boolean.class || type == Boolean.class) {
                if (!(input instanceof Boolean)) throw bad(entry, path, "boolean", input, null);
                return new Scalar(input);
            }
            if (!NUMBERS.contains(input.getClass())) throw bad(entry, path, type.getTypeName(), input, null);
            try {
                BigDecimal number = input instanceof BigDecimal decimal ? decimal : new BigDecimal(input.toString());
                return new Scalar(switch (type.getName()) {
                    case "byte", "java.lang.Byte" -> number.byteValueExact();
                    case "short", "java.lang.Short" -> number.shortValueExact();
                    case "int", "java.lang.Integer" -> number.intValueExact();
                    case "long", "java.lang.Long" -> number.longValueExact();
                    case "java.math.BigInteger" -> number.toBigIntegerExact();
                    case "java.math.BigDecimal" -> number;
                    case "float", "java.lang.Float" -> finiteFloat(number);
                    case "double", "java.lang.Double" -> finiteDouble(number);
                    default -> throw new IllegalArgumentException("Unsupported scalar target " + type.getTypeName());
                });
            } catch (IllegalArgumentException | ArithmeticException failure) {
                throw bad(entry, path, "finite numeric value exactly representable as " + type.getTypeName(), input, failure);
            }
        }

        private static float finiteFloat(BigDecimal number) {
            float result = number.floatValue();
            if (!Float.isFinite(result)) throw new ArithmeticException("Non-finite/overflow float");
            return result;
        }

        private static double finiteDouble(BigDecimal number) {
            double result = number.doubleValue();
            if (!Double.isFinite(result)) throw new ArithmeticException("Non-finite/overflow double");
            return result;
        }

        private Set<String> enumNames(Entry entry, Class<?> type, String path) {
            try (InputStream bytes = type.getModule().getResourceAsStream(type.getName().replace('.', '/') + ".class")) {
                if (bytes == null) throw new IOException("Missing packaged enum class");
                Set<String> names = new LinkedHashSet<>();
                new ClassReader(bytes).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                        if ((access & Opcodes.ACC_ENUM) != 0) names.add(name);
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return names;
            } catch (IOException | RuntimeException failure) {
                throw bad(entry, path, "packaged enum declarations for " + type.getName(), null, failure);
            }
        }

        private void enter(Object container, String path, Class<?> contract, String name) {
            if (visiting.put(container, Boolean.TRUE) != null)
                throw problem(source, path, contract, name, "acyclic input (cyclic container detected)", container, null);
        }

        private KlumModelException bad(Entry entry, String path, String expected, Object actual, Throwable cause) {
            return problem(source, path, entry.contract(), entry.externalName(), expected, actual, cause);
        }
    }
}
