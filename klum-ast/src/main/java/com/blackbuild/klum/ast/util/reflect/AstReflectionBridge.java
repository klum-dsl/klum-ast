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
package com.blackbuild.klum.ast.util.reflect;

import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class AstReflectionBridge {

    private AstReflectionBridge() {
        // Utility class
    }

    private static Method getMatchingMethod(MethodNode methodNode) {
        if (!methodNode.getDeclaringClass().isResolved())
            return null;

        String[] parameterTypes = Arrays.stream(methodNode.getParameters())
                .map(Parameter::getType)
                .map(ClassNode::getName)
                .toArray(String[]::new);
        Class<?> declaringClass = methodNode.getDeclaringClass().getTypeClass();

        return Arrays.stream(declaringClass.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodNode.getName()))
                .filter(m -> Arrays.equals(Arrays.stream(m.getParameterTypes())
                        .map(Class::getName)
                        .toArray(String[]::new), parameterTypes))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Method not found: " + methodNode.getName()));
    }

    private static String[] getParameterNames(Method method) {
        return method != null ? Arrays.stream(method.getParameters())
                .map(java.lang.reflect.Parameter::getName)
                .toArray(String[]::new) : null;
    }

    public static Parameter[] cloneParamsWithAdjustedNames(MethodNode methodNode) {
        String[] reflectionParameterNames = getParameterNames(getMatchingMethod(methodNode));
        Parameter[] parameters = methodNode.getParameters();
        Parameter[] adjustedParameters = new Parameter[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            adjustedParameters[i] = new Parameter(
                    parameters[i].getType(),
                    reflectionParameterNames != null ? reflectionParameterNames[i] : parameters[i].getName(),
                    parameters[i].getInitialExpression());
        }

        return adjustedParameters;
    }

    public static List<String> parameterNames(MethodNode methodNode) {
        return Arrays.stream(cloneParamsWithAdjustedNames(methodNode))
                .map(Parameter::getName)
                .collect(Collectors.toList());
    }

}
