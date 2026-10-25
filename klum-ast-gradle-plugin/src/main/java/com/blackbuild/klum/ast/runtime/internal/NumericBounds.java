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
import java.math.RoundingMode;
import java.util.Map;

/** Exact endpoint conjunction for the bounded authoring profiles; never passes through a floating type. */
final class NumericBounds {
    private static final String MINIMUM = "minimum";
    private static final String MAXIMUM = "maximum";
    private static final String EXCLUSIVE_MINIMUM = "exclusiveMinimum";
    private static final String EXCLUSIVE_MAXIMUM = "exclusiveMaximum";
    private record Endpoint(BigDecimal value, boolean inclusive) { }
    private Endpoint lower;
    private Endpoint upper;

    NumericBounds(Map<String, Object> schema) {
        if (schema.get(MINIMUM) instanceof BigInteger minimum) lower = new Endpoint(new BigDecimal(minimum), true);
        if (schema.get(MAXIMUM) instanceof BigInteger maximum) upper = new Endpoint(new BigDecimal(maximum), true);
    }

    void add(BigDecimal value, boolean inclusive, boolean minimum) {
        Endpoint previous = minimum ? lower : upper;
        int comparison = previous == null ? 0 : value.compareTo(previous.value());
        if (previous != null && (minimum ? comparison < 0 : comparison > 0)) return;
        Endpoint next = new Endpoint(value, inclusive && (comparison != 0 || previous == null || previous.inclusive()));
        if (minimum) {
            lower = next;
        } else {
            upper = next;
        }
    }

    boolean possible(boolean integral) {
        if (lower == null || upper == null) return true;
        int comparison = lower.value().compareTo(upper.value());
        if (comparison > 0 || comparison == 0 && (!lower.inclusive() || !upper.inclusive())) return false;
        return !integral || containsInteger();
    }

    private boolean containsInteger() {
        BigDecimal first = round(lower.value(), RoundingMode.CEILING);
        BigDecimal last = round(upper.value(), RoundingMode.FLOOR);
        boolean excludesFirst = !lower.inclusive() && first.compareTo(lower.value()) == 0;
        boolean excludesLast = !upper.inclusive() && last.compareTo(upper.value()) == 0;
        int excludedEndpoints = (excludesFirst ? 1 : 0) + (excludesLast ? 1 : 0);
        BigDecimal adjustment = BigDecimal.valueOf(excludedEndpoints);

        // Smallest <= largest is equivalent to ceil(L) + adjustment <= floor(U).
        // Adjust only a scale-zero coefficient, never expand a negative-scale power of ten.
        if (first.scale() == 0) return first.add(adjustment).compareTo(last) <= 0;
        if (last.scale() == 0) return first.compareTo(last.subtract(adjustment)) <= 0;

        // Rounding leaves only scales <= 0. Here both integers are multiples of ten:
        // a positive gap is at least ten, enough for either or both unit exclusions.
        int comparison = first.compareTo(last);
        return comparison < 0 || comparison == 0 && excludedEndpoints == 0;
    }

    private static BigDecimal round(BigDecimal value, RoundingMode mode) {
        if (value.scale() <= 0) return value;
        // Avoid expanding enormous scales for tiny, valid BigDecimal exponents.
        if (value.abs().compareTo(BigDecimal.ONE) < 0) {
            if (mode == RoundingMode.CEILING && value.signum() > 0) {
                return BigDecimal.ONE;
            }
            if (mode == RoundingMode.FLOOR && value.signum() < 0) {
                return BigDecimal.ONE.negate();
            }
            return BigDecimal.ZERO;
        }
        return value.setScale(0, mode);
    }

    void write(Map<String, Object> schema) {
        write(schema, lower, MINIMUM, EXCLUSIVE_MINIMUM);
        write(schema, upper, MAXIMUM, EXCLUSIVE_MAXIMUM);
    }

    private static void write(Map<String, Object> schema, Endpoint endpoint, String inclusive, String exclusive) {
        schema.remove(inclusive);
        schema.remove(exclusive);
        if (endpoint != null) schema.put(endpoint.inclusive() ? inclusive : exclusive, endpoint.value());
    }

    @Override public String toString() {
        return "minimum " + (lower == null ? "unbounded" : lower.value())
                + "; maximum " + (upper == null ? "unbounded" : upper.value());
    }
}
