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
package com.blackbuild.klum.ast.compiler.internal.ast;

import groovy.transform.AnnotationCollector;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.Set;

/** Transient compiler-only fact contributed through KlumCast before annotation collector rewriting. */
final class StructuredInputSourceOrigins {
    private static final String ORIGINALS = StructuredInputSourceOrigins.class.getName();
    private static final String NOT_BLANK = "jakarta.validation.constraints.NotBlank";

    private StructuredInputSourceOrigins() { }

    /** Never emitted into class files or public Builder metadata. */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface OriginalDeclarations { }

    /** One authored payload's original declarations, frozen before annotation collectors and Builder projection. */
    @Retention(RetentionPolicy.SOURCE)
    @Target(ElementType.METHOD)
    @interface OriginalParameter { }

    static AnnotationNode captureParameter(MethodNode method) {
        AnnotationNode marker = new AnnotationNode(ClassHelper.make(OriginalParameter.class));
        marker.putNodeMetaData(ORIGINALS, List.copyOf(method.getParameters()[0].getAnnotations()));
        return marker;
    }

    static boolean parameterCaptured(MethodNode method) {
        return method.getAnnotations(ClassHelper.make(OriginalParameter.class)).size() == 1;
    }

    static boolean needed(List<AnnotationNode> annotations) {
        return annotations.stream().anyMatch(annotation -> Set.of(NOT_BLANK, "jakarta.validation.constraints.Pattern")
                .contains(annotation.getClassNode().getName())
                || !annotation.getClassNode().getAnnotations(ClassHelper.make(AnnotationCollector.class)).isEmpty()
                || composedConstraint(annotation));
    }

    private static boolean composedConstraint(AnnotationNode annotation) {
        return !annotation.getClassNode().getName().startsWith("jakarta.validation.constraints.")
                && annotation.getClassNode().getAnnotations().stream()
                .anyMatch(meta -> meta.getClassNode().getName().equals("jakarta.validation.Constraint"));
    }

    static AnnotationNode capture(List<AnnotationNode> annotations) {
        AnnotationNode marker = new AnnotationNode(ClassHelper.make(OriginalDeclarations.class));
        marker.putNodeMetaData(ORIGINALS, List.copyOf(annotations));
        return marker;
    }

    static boolean direct(AnnotatedNode field, AnnotationNode annotation) {
        return originals(field).stream().anyMatch(original -> original == annotation);
    }

    static AnnotationNode original(AnnotatedNode field, AnnotationNode annotation) {
        if (direct(field, annotation)) return annotation;
        return originals(field).stream().filter(original -> original.getLineNumber() == annotation.getLineNumber()
                && original.getColumnNumber() == annotation.getColumnNumber()).findFirst().orElse(annotation);
    }

    private static List<AnnotationNode> originals(AnnotatedNode field) {
        List<AnnotationNode> markers = field.getAnnotations(ClassHelper.make(field instanceof FieldNode ? OriginalDeclarations.class : OriginalParameter.class));
        if (markers.size() != 1) return List.of();
        List<AnnotationNode> originals = markers.get(0).getNodeMetaData(ORIGINALS);
        return originals == null ? List.of() : originals;
    }
}
