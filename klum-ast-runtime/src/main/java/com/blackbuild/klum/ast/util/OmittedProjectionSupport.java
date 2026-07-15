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

/**
 * Runtime matcher for the internal catalog of deliberately omitted composition projections.
 *
 * <p>The wire format is a semicolon-separated list of entries. Each entry contains six period-separated fields:
 * Base64 name, minimum argument count, varargs flag, Base64 comma-separated parameter types, Base64 signature, and Base64
 * omission reason. URL-safe Base64 without padding keeps the two delimiters unambiguous.</p>
 */
public final class OmittedProjectionSupport {

    private static final Map<String, Class<?>> PRIMITIVES = primitiveTypes();

    private OmittedProjectionSupport() {
    }

    public static Object handle(Object receiver, String methodName, Object arguments, String encodedCatalog) {
        Object[] actualArguments = normalizeArguments(arguments);
        for (String encodedEntry : encodedCatalog.split(";")) {
            if (!encodedEntry.isEmpty()) {
                CatalogEntry entry = CatalogEntry.decode(encodedEntry);
                if (entry.matches(receiver.getClass().getClassLoader(), methodName, actualArguments))
                    throw entry.toException();
            }
        }
        throw new MissingMethodException(methodName, receiver.getClass(), actualArguments);
    }

    private static boolean matches(ClassLoader loader, Object[] arguments, String[] parameterTypeNames,
                                   int minimumArguments, boolean varargs) {
        if (!hasCompatibleArity(arguments.length, parameterTypeNames.length, minimumArguments, varargs)) return false;

        int fixed = varargs ? parameterTypeNames.length - 1 : arguments.length;
        if (!fixedArgumentsMatch(loader, arguments, parameterTypeNames, fixed)) return false;
        return !varargs || varargsMatch(loader, arguments, parameterTypeNames, fixed);
    }

    private static boolean hasCompatibleArity(int argumentCount, int parameterCount, int minimumArguments,
                                              boolean varargs) {
        if (argumentCount < minimumArguments) return false;
        if (varargs) return parameterCount > 0;
        return argumentCount <= parameterCount;
    }

    private static boolean fixedArgumentsMatch(ClassLoader loader, Object[] arguments, String[] parameterTypeNames,
                                               int fixed) {
        for (int index = 0; index < fixed; index++) {
            if (index >= arguments.length || !accepts(loadType(parameterTypeNames[index], loader), arguments[index]))
                return false;
        }
        return true;
    }

    private static boolean varargsMatch(ClassLoader loader, Object[] arguments, String[] parameterTypeNames, int fixed) {
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
        if (arguments instanceof Object[] objectArray) return objectArray;
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

    private record CatalogEntry(String name, int minimumArguments, boolean varargs, String[] parameterTypes,
                                String signature, String reason) {

        private static CatalogEntry decode(String encodedEntry) {
            String[] fields = encodedEntry.split("\\.", -1);
            String decodedParameterTypes = OmittedProjectionSupport.decode(fields[3]);
            String[] parameterTypes = decodedParameterTypes.isEmpty()
                    ? new String[0]
                    : decodedParameterTypes.split(",");
            return new CatalogEntry(
                    OmittedProjectionSupport.decode(fields[0]),
                    Integer.parseInt(fields[1]),
                    Boolean.parseBoolean(fields[2]),
                    parameterTypes,
                    OmittedProjectionSupport.decode(fields[4]),
                    OmittedProjectionSupport.decode(fields[5])
            );
        }

        private boolean matches(ClassLoader loader, String methodName, Object[] arguments) {
            return name.equals(methodName)
                    && OmittedProjectionSupport.matches(
                            loader,
                            arguments,
                            parameterTypes,
                            minimumArguments,
                            varargs
                    );
        }

        private KlumModelException toException() {
            return new KlumModelException("Cannot use omitted Builder-producing projection " + signature + ": " + reason
                    + ". Use an active-session Create.AsBuilder recipe or move the producer into source visible to the schema compiler.");
        }
    }
}
