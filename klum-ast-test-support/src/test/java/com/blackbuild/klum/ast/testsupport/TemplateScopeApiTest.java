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
package com.blackbuild.klum.ast.testsupport;

import org.junit.jupiter.api.Test;
import spock.lang.Issue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Issue("658")
class TemplateScopeApiTest {

    @Test
    void exposesOnlyTheLifetimeTokenContract() {
        Set<String> constructors = Arrays.stream(TemplateScope.class.getConstructors())
                .map(Constructor::toString)
                .collect(Collectors.toSet());
        Set<String> methods = Arrays.stream(TemplateScope.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .map(this::signature)
                .collect(Collectors.toSet());

        assertEquals(Set.of("public com.blackbuild.klum.ast.testsupport.TemplateScope()"), constructors);
        assertEquals(Set.of(
                "void close()",
                "TemplateScope with(java.lang.Object[])",
                "TemplateScope with(java.util.Collection)"
        ), methods);
        assertTrue(AutoCloseable.class.isAssignableFrom(TemplateScope.class));
        assertTrue(Modifier.isFinal(TemplateScope.class.getModifiers()));
    }

    private String signature(Method method) {
        return method.getReturnType().getSimpleName() + " " + method.getName() + "(" +
                Arrays.stream(method.getParameterTypes())
                        .map(Class::getTypeName)
                        .collect(Collectors.joining(", ")) + ")";
    }
}
