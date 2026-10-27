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

import java.util.HashSet;
import java.util.Set;

/** Private syntax-checked JSON value. No numeric conversion, executable payloads or nullable writer path. */
final class JsonExample {
    private final String text;

    private JsonExample(String text) { this.text = text; }

    static JsonExample parse(String text, String source, Class<?> contract, int index) {
        try {
            new Parser(text).parse();
            return new JsonExample(text);
        } catch (Syntax failure) {
            throw new IllegalArgumentException("Invalid input examples on " + safe(source) + " [public Builder "
                    + safe(contract.getName()) + ", artifact " + safe(String.valueOf(contract.getProtectionDomain().getCodeSource()))
                    + "], example " + index + ", UTF-16 offset " + failure.offset + ": " + failure.getMessage());
        }
    }

    String json() { return text; }

    private static String safe(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) || Character.isSurrogate(c)) result.append('?');
            else result.append(c);
        }
        return result.toString();
    }

    private static final class Syntax extends RuntimeException {
        private final int offset;
        private Syntax(int offset, String reason) { super(reason); this.offset = offset; }
    }

    private static final class Parser {
        private final String text;
        private int offset;

        private Parser(String text) { this.text = text; }

        private void parse() {
            whitespace();
            value(0);
            whitespace();
            if (offset != text.length()) fail("trailing data");
        }

        private void value(int depth) {
            if (offset == text.length()) fail("expected JSON value");
            char c = text.charAt(offset);
            switch (c) {
                case '{' -> object(depth + 1);
                case '[' -> array(depth + 1);
                case '"' -> string();
                case 't' -> literal("true");
                case 'f' -> literal("false");
                case 'n' -> literal("null");
                default -> number();
            }
        }

        private void container(int depth) {
            if (depth > 32) fail("container depth exceeds 32");
            offset++;
            whitespace();
        }

        private void object(int depth) {
            container(depth);
            if (take('}')) return;
            Set<String> keys = new HashSet<>();
            do {
                whitespace();
                int start = offset;
                if (!peek('"')) fail("expected object key string");
                String key = string();
                if (!keys.add(key)) throw new Syntax(start, "duplicate decoded object key");
                whitespace();
                require(':');
                whitespace();
                value(depth);
                whitespace();
                if (take('}')) return;
                require(',');
            } while (true);
        }

        private void array(int depth) {
            container(depth);
            if (take(']')) return;
            do {
                whitespace();
                value(depth);
                whitespace();
                if (take(']')) return;
                require(',');
            } while (true);
        }

        private String string() {
            require('"');
            StringBuilder decoded = new StringBuilder();
            boolean high = false;
            while (offset < text.length()) {
                char c = text.charAt(offset++);
                if (c == '"') {
                    if (high) fail("unpaired surrogate");
                    return decoded.toString();
                }
                if (c < 32) fail("raw control in string");
                if (c == '\\') c = escape();
                if (c == '\ufeff') fail("BOM is not supported");
                if (high != Character.isLowSurrogate(c)) fail("unpaired surrogate");
                high = Character.isHighSurrogate(c);
                decoded.append(c);
            }
            fail("unterminated string");
            return ""; // fail always throws.
        }

        private char escape() {
            if (offset == text.length()) fail("incomplete escape");
            return switch (text.charAt(offset++)) {
                case '"' -> '"';
                case '\\' -> '\\';
                case '/' -> '/';
                case 'b' -> '\b';
                case 'f' -> '\f';
                case 'n' -> '\n';
                case 'r' -> '\r';
                case 't' -> '\t';
                case 'u' -> unicode();
                default -> throw new Syntax(offset - 1, "invalid escape");
            };
        }

        private char unicode() {
            int code = 0;
            for (int i = 0; i < 4; i++) {
                if (offset == text.length()) fail("incomplete Unicode escape");
                char c = text.charAt(offset++);
                int digit = c >= '0' && c <= '9' ? c - '0' : c >= 'a' && c <= 'f' ? c - 'a' + 10 : c >= 'A' && c <= 'F' ? c - 'A' + 10 : -1;
                if (digit < 0) fail("invalid Unicode escape");
                code = 16 * code + digit;
            }
            return (char) code;
        }

        private void literal(String expected) {
            if (!text.startsWith(expected, offset)) fail("invalid literal");
            offset += expected.length();
        }

        private void number() {
            take('-');
            if (!take('0')) digits();
            if (take('.')) digits();
            if (take('e') || take('E')) {
                if (!take('+')) take('-');
                digits();
            }
        }

        private void digits() {
            int start = offset;
            while (offset < text.length() && text.charAt(offset) >= '0' && text.charAt(offset) <= '9') offset++;
            if (offset == start) fail("expected number digit");
        }

        private void whitespace() {
            while (offset < text.length() && " \t\r\n".indexOf(text.charAt(offset)) >= 0) offset++;
        }

        private boolean peek(char c) { return offset < text.length() && text.charAt(offset) == c; }
        private boolean take(char c) {
            if (!peek(c)) return false;
            offset++;
            return true;
        }
        private void require(char c) { if (!take(c)) fail("expected JSON delimiter"); }
        private void fail(String reason) { throw new Syntax(offset, reason); }
    }
}
