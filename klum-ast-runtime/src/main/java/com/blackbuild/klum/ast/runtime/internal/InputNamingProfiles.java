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

/** Bounded ASCII member transformation; literals and data keys never enter this function. */
final class InputNamingProfiles {
    private InputNamingProfiles() { }

    static String kebab(String nativeName, String source) {
        requireUtf16(nativeName, source);
        StringBuilder result = new StringBuilder(nativeName.length());
        for (int index = 0; index < nativeName.length(); index++) {
            char current = nativeName.charAt(index);
            if (upper(current)) {
                if (boundary(nativeName, index)) {
                    result.append('-');
                }
                result.append((char) (current + ('a' - 'A')));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    private static boolean boundary(String name, int index) {
        if (index == 0) return false;
        char previous = name.charAt(index - 1);
        return lower(previous) || previous >= '0' && previous <= '9'
                || upper(previous) && index + 1 < name.length() && lower(name.charAt(index + 1));
    }

    private static boolean upper(char value) { return value >= 'A' && value <= 'Z'; }
    private static boolean lower(char value) { return value >= 'a' && value <= 'z'; }

    private static void requireUtf16(String value, String source) {
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 >= value.length() || !Character.isLowSurrogate(value.charAt(++index))) {
                    throw new IllegalArgumentException("Malformed UTF-16 native input name at " + source);
                }
            } else if (Character.isLowSurrogate(current)) {
                throw new IllegalArgumentException("Malformed UTF-16 native input name at " + source);
            }
        }
    }
}
