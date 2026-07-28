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
package com.blackbuild.klum.ast.compiler.internal.validation;

import com.blackbuild.klum.ast.copy.Overwrite;
import com.blackbuild.klum.cast.spi.Check;
import com.blackbuild.klum.cast.spi.CheckContext;
import com.blackbuild.klum.cast.spi.Diagnostic;
import org.codehaus.groovy.ast.AnnotationNode;

import java.util.List;

public class OverwriteStrategiesCheck implements Check {

    private static final List<String> STRATEGY_MEMBERS = List.of("singles", "collections", "maps");

    @Override
    public List<Diagnostic> check(CheckContext context) {
        AnnotationNode configuredOverwrite = getConfiguredOverwrite(context.getValidatedAnnotation());
        boolean hasStrategy = STRATEGY_MEMBERS.stream().anyMatch(configuredOverwrite.getMembers()::containsKey);
        if (hasStrategy) return List.of();
        return List.of(new Diagnostic(getClass().getName(),
                "At least one of " + STRATEGY_MEMBERS + " must be set", configuredOverwrite));
    }

    private AnnotationNode getConfiguredOverwrite(AnnotationNode validatedAnnotation) {
        if (isOverwrite(validatedAnnotation)) return validatedAnnotation;
        return validatedAnnotation.getClassNode().getAnnotations().stream()
                .filter(this::isOverwrite)
                .findFirst()
                .orElse(validatedAnnotation);
    }

    private boolean isOverwrite(AnnotationNode annotation) {
        return annotation.getClassNode().getName().equals(Overwrite.class.getName());
    }
}
