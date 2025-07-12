/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.klum.ast.process;

/**
 * Default phases for Klum model creation. Note that the phases are not used directly, but
 * rather the phase numbers are used to order the actions. This allows to add custom phases
 * if needed.
 */
public enum DefaultKlumPhase implements KlumPhase {

    /** The creation phase is not encountered in the PhaseDriver, it handles the actual creation of the objects. */
    CREATE(0),
    APPLY_LATER(1),
    /** Phase for automatic creation of missing objects, usually from annotations. */
    AUTO_CREATE(10),
    OWNER(15),
    AUTO_LINK(20),
    DEFAULT(25),
    POST_TREE(30),
    VALIDATE(50),
    COMPLETE(100);
    final int number;

    DefaultKlumPhase(int number) {
        this.number = number;
    }

    @Override
    public int getNumber() {
        return number;
    }

    @Override
    public String getName() {
        return name().toLowerCase();
    }

    public static String getPhaseName(int number) {
        for (DefaultKlumPhase value : values()) {
            if (value.number == number) {
                return value.getName() + ":" + number;
            }
        }
        return "unknown:" + number;
    }
}
