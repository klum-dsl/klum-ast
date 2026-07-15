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
package com.blackbuild.klum.ast.util;

import groovy.lang.MissingMethodException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/** Runtime matcher for the internal catalog of deliberately omitted composition projections. */
public final class OmittedProjectionSupport {

    private static final Map<String, Class<?>> PRIMITIVES = primitiveTypes();

    private OmittedProjectionSupport() {
    }

    public static Object handle(Object receiver, String methodName, Object arguments, String encodedCatalog) {
        Object[] actualArguments = normalizeArguments(arguments);
        for (String encodedEntry : encodedCatalog.split(";")) {
            if (encodedEntry.isEmpty()) continue;
            String[] fields = encodedEntry.split("\\.", -1);
            String candidateName = decode(fields[0]);
            if (!candidateName.equals(methodName)) continue;
            int minimumArguments = Integer.parseInt(fields[1]);
            boolean varargs = Boolean.parseBoolean(fields[2]);
            String[] parameterTypes = decode(fields[3]).isEmpty() ? new String[0] : decode(fields[3]).split(",");
            if (!matches(receiver.getClass().getClassLoader(), actualArguments, parameterTypes, minimumArguments, varargs))
                continue;
            String signature = decode(fields[4]);
            String reason = decode(fields[5]);
            throw new KlumModelException("Cannot use omitted Builder-producing projection " + signature + ": " + reason
                    + ". Use an active-session Create.AsBuilder recipe or move the producer into source visible to the schema compiler.");
        }
        throw new MissingMethodException(methodName, receiver.getClass(), actualArguments);
    }

    private static boolean matches(ClassLoader loader, Object[] arguments, String[] parameterTypeNames,
                                   int minimumArguments, boolean varargs) {
        if (arguments.length < minimumArguments) return false;
        if (!varargs && arguments.length > parameterTypeNames.length) return false;
        if (varargs && parameterTypeNames.length == 0) return false;

        int fixed = varargs ? parameterTypeNames.length - 1 : arguments.length;
        for (int index = 0; index < fixed; index++) {
            if (index >= arguments.length || !accepts(loadType(parameterTypeNames[index], loader), arguments[index]))
                return false;
        }
        if (!varargs) return true;

        Class<?> arrayType = loadType(parameterTypeNames[parameterTypeNames.length - 1], loader);
        if (arguments.length == parameterTypeNames.length && accepts(arrayType, arguments[arguments.length - 1]))
            return true;
        Class<?> componentType = arrayType.getComponentType();
        for (int index = fixed; index < arguments.length; index++)
            if (!accepts(componentType, arguments[index])) return false;
        return true;
    }

    private static boolean accepts(Class<?> declaredType, Object argument) {
        if (argument == null) return !declaredType.isPrimitive();
        Class<?> effectiveType = declaredType.isPrimitive() ? wrapperFor(declaredType) : declaredType;
        return effectiveType.isInstance(argument);
    }

    private static Class<?> loadType(String name, ClassLoader loader) {
        Class<?> primitive = PRIMITIVES.get(name);
        if (primitive != null) return primitive;
        try {
            return Class.forName(name, false, loader);
        } catch (ClassNotFoundException exception) {
            throw new KlumModelException("Cannot resolve omitted projection parameter type " + name, exception);
        }
    }

    private static Object[] normalizeArguments(Object arguments) {
        if (arguments == null) return new Object[0];
        if (arguments instanceof Object[]) return (Object[]) arguments;
        return new Object[] { arguments };
    }

    private static String decode(String value) {
        return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
    }

    private static Class<?> wrapperFor(Class<?> primitive) {
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == byte.class) return Byte.class;
        if (primitive == short.class) return Short.class;
        if (primitive == int.class) return Integer.class;
        if (primitive == long.class) return Long.class;
        if (primitive == float.class) return Float.class;
        if (primitive == double.class) return Double.class;
        if (primitive == char.class) return Character.class;
        return Void.class;
    }

    private static Map<String, Class<?>> primitiveTypes() {
        Map<String, Class<?>> result = new HashMap<>();
        result.put("boolean", boolean.class);
        result.put("byte", byte.class);
        result.put("short", short.class);
        result.put("int", int.class);
        result.put("long", long.class);
        result.put("float", float.class);
        result.put("double", double.class);
        result.put("char", char.class);
        result.put("void", void.class);
        return result;
    }
}
