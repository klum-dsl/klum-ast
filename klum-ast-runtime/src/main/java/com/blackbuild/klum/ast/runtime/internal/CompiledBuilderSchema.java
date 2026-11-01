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

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable, normalized result of source-free public Builder/input resolution.
 * Instances have loader-local identity; they do not load, resolve, render, execute or lazily cache anything.
 * Ordered maps and every nested list/map are defensively copied. Method/Class handles remain local to the
 * consumer session, are never serialized, and are invoked only by normal interpreter execution after preflight.
 * Source declarations, operation payloads, construction keys and relationship-use facts remain independent.
 * Consumers validate referenced ownership contracts separately, registering identities before following cycles.
 */
final class CompiledBuilderSchema {
    static final String DEFAULT_STATUS = "UNAVAILABLE_WITHOUT_EXECUTION";
    private final Class<?> contract;
    private final Class<?> model;
    private final Key key;
    private final List<String> keyCollisions;
    private final Map<Class<?>, ConstraintProfile> constraintProfiles;
    private final String keyName;
    private final Map<String, Entry> entries;
    private final List<Rejection> rejections;
    private final List<Declaration> declarations;

    enum Role { PROPERTY, OWNED_OBJECT, KEYED_OWNED_MAP, OPERATION, COMMAND }
    enum Kind { SCALAR, ENUM, LIST, MAP, NESTED_OBJECT, EMPTY_OBJECT }

    /** Owned types use stable Class references; data-container items are finite, recursively immutable shapes. */
    record Payload(Kind kind, Class<?> type, Class<?> builder, String keyType, Payload item) { }

    /** A selected input use; source identity stays distinct from its effective external name and dispatch handle. */
    record Entry(String externalName, Role role, String source, Class<?> contract, String operation,
                 List<Method> methods, Payload payload, String keyType, String excludedKey, String fixedChildKey,
                 String nameOrigin, String documentation, boolean schemaKey, boolean sourceDeprecated, List<String> examples) {
        Entry {
            methods = List.copyOf(methods);
            examples = List.copyOf(examples);
        }
    }

    /**
     * Compiler-frozen constraint facts, not executable annotations. {@code plain} states direct-origin/context
     * eligibility, never validity or representability. Composing eligibility has a separate explicit origin.
     * Declaration text is a canonical description, not source text.
     * Numeric/Pattern mapping belongs to the plugin catalog; PROJECT/TEXT_ONLY/OMIT belongs to its renderer.
     */
    record ConstraintRule(String annotation, String value, boolean plain, String declaration, String decimalValue,
                          Boolean inclusive, String regexp, Boolean patternFlagsEmpty,
                          String origin, String owner, String description) {
        boolean eligible() { return plain || origin.equals("COMPOSING_AND") || origin.equals("PARAMETER"); }
    }
    /** Effective compiled parameter facts; field constraints remain a separate domain-value source. */
    record ConverterConstraints(String source, String artifact, Policy policy, List<ConstraintRule> rules) {
        ConverterConstraints { rules = List.copyOf(rules); }
    }

    record FieldConstraints(Policy policy, List<ConstraintRule> rules) {
        FieldConstraints { rules = List.copyOf(rules); }
    }

    /** All captured field facts on one declaring Builder, including excluded and output-suppressed declarations. */
    record ConstraintProfile(boolean groupRedefined, Map<String, FieldConstraints> fields,
                             Map<String, ConverterConstraints> converters) {
        ConstraintProfile {
            fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            converters = Collections.unmodifiableMap(new LinkedHashMap<>(converters));
        }
    }

    record Key(String nativeName, String externalName, boolean ignored, String source, Class<?> contract,
               boolean sourceDeprecated, String ignoreReason, List<String> examples) {
        Key { examples = List.copyOf(examples); }
    }
    record Rejection(String source, String reason, String collisionName) {
        Rejection(String source, String reason) { this(source, reason, null); }
    }
    record Declaration(String owner, String name, List<String> parameters, String returns, String documentation) {
        Declaration { parameters = List.copyOf(parameters); }
    }

    CompiledBuilderSchema(Class<?> contract, Class<?> model, Map<String, Entry> entries,
                          List<Rejection> rejections, List<Declaration> declarations, Key key, String keyName,
                          List<String> keyCollisions, Map<Class<?>, ConstraintProfile> constraintProfiles) {
        this.keyCollisions = List.copyOf(keyCollisions);
        this.constraintProfiles = Collections.unmodifiableMap(new LinkedHashMap<>(constraintProfiles));
        this.key = key;
        this.keyName = keyName;
        this.contract = contract;
        this.model = model;
        this.entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries));
        this.rejections = List.copyOf(rejections);
        this.declarations = List.copyOf(declarations);
    }

    Key key() { return key; }
    List<String> keyCollisions() { return keyCollisions; }
    String keyName() { return keyName; }
    Class<?> contract() { return contract; }
    Class<?> model() { return model; }
    Map<String, Entry> entries() { return entries; }
    List<Rejection> rejections() { return rejections; }
    List<Declaration> declarations() { return declarations; }

    Map<Class<?>, ConstraintProfile> constraintProfiles() { return constraintProfiles; }
}
