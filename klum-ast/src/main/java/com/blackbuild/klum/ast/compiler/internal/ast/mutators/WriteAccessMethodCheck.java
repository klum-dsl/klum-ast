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
package com.blackbuild.klum.ast.compiler.internal.ast.mutators;

import com.blackbuild.klum.ast.WriteAccess;
import com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper;
import com.blackbuild.klum.cast.spi.Check;
import com.blackbuild.klum.cast.spi.CheckContext;
import com.blackbuild.klum.cast.spi.Diagnostic;
import org.codehaus.groovy.ast.MethodNode;

import java.util.List;
import java.util.Optional;

public class WriteAccessMethodCheck implements Check {
    @Override
    public List<Diagnostic> check(CheckContext context) {
        MethodNode method = (MethodNode) context.getTarget();

        WriteAccess.Type writeAccessType = context.getControlAnnotation(WriteAccess.class)
                .orElseThrow(() -> new IllegalStateException("WriteAccessMethodCheck requires a WriteAccess control annotation"))
                .value();

        return findViolation(method, writeAccessType)
                .map(message -> List.of(new Diagnostic(getClass().getName(), message, context.getValidatedAnnotation())))
                .orElseGet(List::of);
    }

    public static Optional<String> findViolation(MethodNode method, WriteAccess.Type writeAccessType) {
        if (writeAccessType == WriteAccess.Type.MANUAL && !DslAstHelper.isDSLObject(method.getDeclaringClass()))
            return Optional.of("Builder-only methods can only be declared by a @DSL class");

        if (method.isPrivate())
            return Optional.of(writeAccessType == WriteAccess.Type.MANUAL
                    ? "Builder-only methods must not be private"
                    : "Lifecycle methods must not be private!");

        if (writeAccessType == WriteAccess.Type.LIFECYCLE && method.getParameters().length > 0)
            return Optional.of(String.format(
                    "Method %s.%s is annotated with @WriteAccess(LIFECYCLE) but has parameters",
                    method.getDeclaringClass().getName(),
                    method.getName()
            ));

        return Optional.empty();
    }
}
