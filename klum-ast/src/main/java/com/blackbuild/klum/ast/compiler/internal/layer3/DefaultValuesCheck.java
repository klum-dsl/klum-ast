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
package com.blackbuild.klum.ast.compiler.internal.layer3;

import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues;
import com.blackbuild.klum.cast.spi.Check;
import com.blackbuild.klum.cast.spi.CheckContext;
import com.blackbuild.klum.cast.spi.Diagnostic;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;

import java.util.List;

public class DefaultValuesCheck implements Check {
    @Override
    public List<Diagnostic> check(CheckContext context) {
        boolean controlAnnotationHasValuesMapping = context.getControlAnnotation(DefaultValues.class)
                .map(DefaultValues::valueTarget)
                .filter(valueTarget -> !valueTarget.isEmpty())
                .isPresent();
        AnnotationNode annotationToCheck = context.getValidatedAnnotation();
        ClassNode targetAnnotation = annotationToCheck.getClassNode();
        boolean targetAnnotationHasValueMember = !targetAnnotation.getMethods("value").isEmpty();

        if (controlAnnotationHasValuesMapping && !targetAnnotationHasValueMember)
            return List.of(new Diagnostic(getClass().getName(),
                String.format("DefaultValues has a 'valueTarget' member, but the target annotation %s does not have a 'value' member", targetAnnotation.getName()), context.getTarget()));

        if (!controlAnnotationHasValuesMapping && targetAnnotationHasValueMember)
            return List.of(new Diagnostic(getClass().getName(),
                String.format("Target annotation %s does have a 'value' member, but DefaultValues does not have a 'valueTarget' member", targetAnnotation.getName()), context.getTarget()));

        return List.of();
    }
}
