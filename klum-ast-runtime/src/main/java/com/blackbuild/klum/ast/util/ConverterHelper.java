/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.Converter;
import com.blackbuild.groovy.configdsl.transform.Converters;

import java.lang.annotation.Annotation;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * Methods to handle converters, either implicit or explicitly using the Converter annotations
 */
public class ConverterHelper {

    private ConverterHelper() {
    }

    private static final String[] DEFAULT_PREFIXES = {"from", "of", "create", "parse"};

    public static List<Executable> getAllConverterMethods(Class<?> clazz) {
        List<Executable> result = new ArrayList<>();

        Converters converters = getConvertersAnnotation(clazz);

        List<String> includes = new ArrayList<>();
        if (!converters.excludeDefaultPrefixes())
            Collections.addAll(includes, DEFAULT_PREFIXES);

        Collections.addAll(includes, converters.includeMethods());
        List<String> excludes = List.of(converters.excludeMethods());

        addConverterMethods(clazz, clazz, includes, excludes, converters.includeConstructors(), result);

        for (Class<?> additionalClass : converters.value())
            addConverterMethods(additionalClass, clazz, includes, excludes, false, result);

        return result;
    }

    public static List<Executable> getAllMatchingConverterMethods(Class<?> clazz, Class<?>... parameterTypes) {
        return getAllConverterMethods(clazz).stream()
                .filter(method -> paramsMatch(method, parameterTypes))
                .collect(toList());
    }

    private static boolean paramsMatch(Executable method, Class<?>... parameterTypes) {
        if (method.getParameterCount() != parameterTypes.length)
            return false;

        for (int i = 0; i < parameterTypes.length; i++) {
            if (!method.getParameterTypes()[i].isAssignableFrom(parameterTypes[i]))
                return false;
        }

        return true;
    }

    private static void addConverterMethods(Class<?> typeToSearch, Class<?> resultType, List<String> includes, List<String> excludes, boolean includeConstructors, List<Executable> result) {
        Arrays.stream(typeToSearch.getMethods())
                .filter(method -> Modifier.isStatic(method.getModifiers()))
                .filter(method -> resultType.isAssignableFrom(method.getReturnType()))
                .filter(method -> isConverterMethod(method, includes, excludes))
                .forEach(result::add);

        if (includeConstructors)
            result.addAll(Arrays.asList(typeToSearch.getConstructors()));
    }

    private static boolean isConverterMethod(Method method, List<String> includes, List<String> excludes) {
        if (method.getAnnotation(Converter.class) != null)
            return true;
        if (method.getDeclaringClass().getAnnotation(Converter.class) != null)
            return true;
        if (!includes.isEmpty() && includes.stream().noneMatch(method.getName()::startsWith))
            return false;
        return excludes.isEmpty() || excludes.stream().noneMatch(method.getName()::startsWith);
    }

    private static Converters getConvertersAnnotation(Class<?> clazz) {
        Converters annotation = clazz.getAnnotation(Converters.class);
        return annotation != null ? annotation : DEFAULT_CONVERTERS;
    }

    static final Converters DEFAULT_CONVERTERS = new ConvertersDefault();

    static class ConvertersDefault implements Converters {

        @Override
        public String[] includeMethods() {
            return new String[0];
        }

        @Override
        public String[] excludeMethods() {
            return new String[0];
        }

        @Override
        public boolean excludeDefaultPrefixes() {
            return false;
        }

        @Override
        public Class[] value() {
            return new Class[0];
        }

        @Override
        public boolean includeConstructors() {
            return false;
        }

        @Override
        public Class<? extends Annotation> annotationType() {
            return Converters.class;
        }
    }
}
