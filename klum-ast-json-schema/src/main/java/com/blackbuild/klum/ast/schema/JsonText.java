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

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Canonical encoding for the generator's finite JSON values; no parser or wire codec. */
final class JsonText {
    private JsonText() { }

    static String encode(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((key, item) -> sorted.put((String) key, item));
            return "{" + sorted.entrySet().stream().map(entry -> quote(entry.getKey()) + ":" + encode(entry.getValue()))
                    .collect(Collectors.joining(",")) + "}";
        }
        if (value instanceof Collection<?> items) return "[" + items.stream().map(JsonText::encode).collect(Collectors.joining(",")) + "]";
        if (value instanceof Boolean || value instanceof Long || value instanceof Integer) return value.toString();
        if (value instanceof String text) return quote(text);
        throw new IllegalArgumentException("Not a generator JSON value: " + value);
    }

    private static String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (char character : text.toCharArray()) {
            switch (character) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append("\\\"");
                default -> {
                    if (character < 32 || Character.isSurrogate(character)) result.append(String.format("\\u%04x", (int) character));
                    else result.append(character);
                }
            }
        }
        return result.append('"').toString();
    }
}
