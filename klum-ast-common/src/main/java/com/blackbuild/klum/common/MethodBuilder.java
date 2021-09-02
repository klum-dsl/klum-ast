/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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
package com.blackbuild.klum.common;

import groovyjarjarasm.asm.Opcodes;

public final class MethodBuilder extends GenericsMethodBuilder<MethodBuilder> {

    private MethodBuilder(String name) {
        super(name);
    }

    public static MethodBuilder createMethod(String name) {
        return new MethodBuilder(name);
    }

    public static MethodBuilder createPublicMethod(String name) {
        return new MethodBuilder(name).mod(Opcodes.ACC_PUBLIC);
    }

    public static MethodBuilder createOptionalPublicMethod(String name) {
        return new MethodBuilder(name).mod(Opcodes.ACC_PUBLIC).optional();
    }

    public static MethodBuilder createProtectedMethod(String name) {
        return new MethodBuilder(name).mod(Opcodes.ACC_PROTECTED);
    }

    public static MethodBuilder createPrivateMethod(String name) {
        return new MethodBuilder(name).mod(Opcodes.ACC_PRIVATE);
    }

}
