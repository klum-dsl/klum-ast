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
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.FieldNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;

/** Transient compiler-only fact contributed through KlumCast before annotation collector rewriting. */
final class StructuredInputSourceOrigins {
    private static final String ORIGINALS = StructuredInputSourceOrigins.class.getName();

    private StructuredInputSourceOrigins() { }

    /** Never emitted into class files or public Builder metadata. */
    @Retention(RetentionPolicy.SOURCE)
    @Target({ElementType.FIELD, ElementType.METHOD})
    @interface OriginalDeclarations { }

    static boolean needed(List<AnnotationNode> annotations) {
        return annotations.stream().anyMatch(annotation -> annotation.getClassNode().getName().equals("jakarta.validation.constraints.Pattern")
                || !annotation.getClassNode().getAnnotations(ClassHelper.make(AnnotationCollector.class)).isEmpty());
    }

    static AnnotationNode capture(List<AnnotationNode> annotations) {
        AnnotationNode marker = new AnnotationNode(ClassHelper.make(OriginalDeclarations.class));
        marker.putNodeMetaData(ORIGINALS, List.copyOf(annotations));
        return marker;
    }

    static boolean direct(FieldNode field, AnnotationNode annotation) {
        return originals(field).stream().anyMatch(original -> original == annotation);
    }

    static AnnotationNode original(FieldNode field, AnnotationNode annotation) {
        if (direct(field, annotation)) return annotation;
        return originals(field).stream().filter(original -> original.getLineNumber() == annotation.getLineNumber()
                && original.getColumnNumber() == annotation.getColumnNumber()).findFirst().orElse(annotation);
    }

    private static List<AnnotationNode> originals(FieldNode field) {
        List<AnnotationNode> markers = field.getAnnotations(ClassHelper.make(OriginalDeclarations.class));
        if (markers.size() != 1) return List.of();
        List<AnnotationNode> originals = markers.get(0).getNodeMetaData(ORIGINALS);
        return originals == null ? List.of() : originals;
    }
}
