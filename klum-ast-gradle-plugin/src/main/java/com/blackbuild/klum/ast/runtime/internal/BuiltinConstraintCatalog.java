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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Closed, stateless plugin catalog for already-qualified numeric and ASCII-BOUNDED-1 Pattern mappings.
 * Consumes validated inert facts at one effective field use; never reads bytes, reflects annotations or executes
 * Schema/validator code. The compiler, contract reader and interpreter have no dependency on this catalog.
 * Results own immutable assertion data or the existing descriptive unsupported reason. The renderer alone applies
 * field policy, combines contributions with structural fences, and places descriptions/assertions at the input use.
 * No registration, discovery, custom projector or satisfiability/normalization mechanism is provided.
 */
final class BuiltinConstraintCatalog {
    private static final String MINIMUM = "minimum";
    private static final String MAXIMUM = "maximum";
    private static final Set<Class<?>> INTEGRAL_TYPES = Set.of(byte.class, Byte.class, short.class, Short.class,
            int.class, Integer.class, long.class, Long.class, BigInteger.class);

    private BuiltinConstraintCatalog() { }

    /** Numeric assertions retain their legacy keyword placement; Pattern assertions remain an ordered conjunction. */
    record Result(List<Map<String, Object>> bounds, List<Map<String, Object>> patterns, String reason) {
        Result {
            bounds = List.copyOf(bounds);
            patterns = List.copyOf(patterns);
        }

        boolean projected() { return !bounds.isEmpty() || !patterns.isEmpty(); }
    }

    static Result project(CompiledBuilderSchema.Entry entry, CompiledBuilderSchema.ConstraintRule rule,
                          boolean groupRedefined) {
        return switch (rule.annotation()) {
            case "jakarta.validation.constraints.Min", "jakarta.validation.constraints.Max",
                 "jakarta.validation.constraints.DecimalMin", "jakarta.validation.constraints.DecimalMax" ->
                    projectBound(entry, rule, groupRedefined);
            case "jakarta.validation.constraints.Pattern" -> projectPattern(entry, rule, groupRedefined);
            default -> unsupported(numericReason(groupRedefined));
        };
    }

    private static Result projectBound(CompiledBuilderSchema.Entry entry, CompiledBuilderSchema.ConstraintRule rule,
                                       boolean groupRedefined) {
        if (!rule.eligible() || groupRedefined) return unsupported(numericReason(groupRedefined));
        boolean ordinaryScalar = entry.role() == CompiledBuilderSchema.Role.PROPERTY
                && entry.payload().kind() == CompiledBuilderSchema.Kind.SCALAR;
        boolean integral = ordinaryScalar && INTEGRAL_TYPES.contains(entry.payload().type());
        boolean decimal = ordinaryScalar && entry.payload().type() == BigDecimal.class;
        if (integral && (rule.annotation().equals("jakarta.validation.constraints.Min")
                || rule.annotation().equals("jakarta.validation.constraints.Max"))) {
            return bound(rule.annotation().endsWith(".Min") ? MINIMUM : MAXIMUM, new BigDecimal(rule.value()));
        }
        if (!(integral || decimal) || !(rule.annotation().equals("jakarta.validation.constraints.DecimalMin")
                || rule.annotation().equals("jakarta.validation.constraints.DecimalMax")))
            return unsupported(numericReason(groupRedefined));
        try {
            String keyword = rule.annotation().endsWith(".DecimalMin") ? MINIMUM : MAXIMUM;
            if (!Boolean.TRUE.equals(rule.inclusive())) keyword = rule.annotation().endsWith(".DecimalMin") ? "exclusiveMinimum" : "exclusiveMaximum";
            return bound(keyword, new BigDecimal(rule.decimalValue()));
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Invalid decimal bound for '" + entry.source() + "', external input '"
                    + entry.externalName() + "', public Builder '" + entry.contract().getName() + "' (artifact "
                    + entry.contract().getProtectionDomain().getCodeSource() + "): " + rule.decimalValue()
                    + "; recompile and package the Schema with matching KlumAST versions", failure);
        }
    }

    private static Result projectPattern(CompiledBuilderSchema.Entry entry, CompiledBuilderSchema.ConstraintRule rule,
                                         boolean groupRedefined) {
        if (entry.role() != CompiledBuilderSchema.Role.PROPERTY || entry.payload().kind() != CompiledBuilderSchema.Kind.SCALAR
                || entry.payload().type() != String.class || entry.schemaKey()) return unsupported("unsupported Pattern site/type");
        if (!rule.eligible() || groupRedefined) return unsupported("Pattern not a direct singleton, nonempty groups/payload or group redefinition/sequence");
        if (!Boolean.TRUE.equals(rule.patternFlagsEmpty())) return unsupported("nonempty Pattern flags");
        BoundedAsciiPattern.Result result = BoundedAsciiPattern.recognize(rule.regexp());
        if (result.reason() != null) return unsupported(result.reason());
        return new Result(List.of(), List.of(Map.of("pattern", result.assertion()),
                Map.of("not", Map.of("type", "string", "pattern", "[^ -~]"))), null);
    }

    private static Result bound(String keyword, BigDecimal value) {
        return new Result(List.of(Map.of(keyword, value)), List.of(), null);
    }

    private static Result unsupported(String reason) {
        return new Result(List.of(), List.of(), reason);
    }

    private static String numericReason(boolean groupRedefined) {
        return groupRedefined ? "group redefinition/sequence" : "outside the supported direct numeric, empty groups/payload subset";
    }
}
