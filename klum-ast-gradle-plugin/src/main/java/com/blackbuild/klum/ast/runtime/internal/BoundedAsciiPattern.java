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

/** Complete-input recognizer for the accepted ASCII-BOUNDED-1 language; never executes a regex. */
final class BoundedAsciiPattern {
    private static final String SYNTAX = "unsupported Pattern source syntax";
    private static final String LIMIT = "Pattern admission ceiling";
    record Result(String assertion, String reason) { }
    private final String body;
    private int cursor;
    private boolean exceeded;

    private BoundedAsciiPattern(String body) { this.body = body; }

    static Result recognize(String source) {
        if (source.isEmpty()) return new Result(null, SYNTAX);
        if (source.length() > 256) return new Result(null, LIMIT);
        String body = source;
        if (source.startsWith("^") && source.endsWith("$")) body = source.substring(1, source.length() - 1);
        BoundedAsciiPattern parser = new BoundedAsciiPattern(body);
        return parser.parse();
    }

    private Result parse() {
        int pieces = 0;
        int maximum = 0;
        if (body.isEmpty()) return new Result(null, SYNTAX);
        while (cursor < body.length()) {
            if (!atom()) return new Result(null, SYNTAX);
            int repetitions = repetitions();
            if (repetitions < 0) return new Result(null, SYNTAX);
            maximum += repetitions;
            pieces++;
        }
        if (exceeded || pieces > 32 || maximum > 1024) return new Result(null, LIMIT);
        return new Result("^" + body + "$", null);
    }

    private boolean atom() {
        char c = body.charAt(cursor++);
        if (word(c) || c == '-') return true;
        return c == '[' && characterClass();
    }

    private boolean characterClass() {
        int terms = 0;
        while (cursor < body.length()) {
            char c = body.charAt(cursor);
            if (c == ']') { cursor++; return terms > 0; }
            if (c == '-') {
                cursor++;
                return cursor < body.length() && body.charAt(cursor++) == ']';
            }
            if (!classTerm()) return false;
            terms++;
        }
        return false;
    }

    private boolean classTerm() {
        if (body.startsWith("A-Z", cursor) || body.startsWith("a-z", cursor) || body.startsWith("0-9", cursor)) {
            cursor += 3;
            return true;
        }
        char c = body.charAt(cursor);
        if (!word(c) && c != '.') return false;
        cursor++;
        return true;
    }

    private int repetitions() {
        if (cursor == body.length()) return 1;
        char c = body.charAt(cursor);
        if (c == '?') { cursor++; return 1; }
        if (c != '{') return 1;
        cursor++;
        int lower = number();
        if (lower < 0 || cursor == body.length()) return -1;
        int upper = lower;
        if (body.charAt(cursor) == ',') { cursor++; upper = number(); }
        if (upper < lower || cursor == body.length() || body.charAt(cursor++) != '}') return -1;
        return upper;
    }

    private int number() {
        int start = cursor;
        int value = 0;
        while (cursor < body.length() && digit(body.charAt(cursor))) {
            // Saturate beyond the admitted range, preserving full syntax classification without overflow.
            value = Math.min(256, value * 10 + body.charAt(cursor++) - '0');
        }
        if (cursor == start || cursor - start > 1 && body.charAt(start) == '0') return -1;
        if (value > 255) exceeded = true;
        return value;
    }

    private static boolean word(char c) { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || digit(c) || c == '_'; }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
}
