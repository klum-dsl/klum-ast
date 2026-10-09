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
package com.blackbuild.klum.ast.compiler.internal.ast.converters;

import com.blackbuild.klum.ast.Builder;
import com.blackbuild.klum.ast.WriteAccess;
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessMethodCheck;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Promotes the deprecated {@code Mutator} spelling to the canonical {@link Builder.Method} annotation.
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class MutatorToBuilderMethodTransformation extends AbstractASTTransformation {

    private static final ClassNode BUILDER_METHOD_ANNOTATION = make(Builder.Method.class);
    private static final ClassNode BUILDER_QUERY_ANNOTATION = make(Builder.Query.class);

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);

        AnnotationNode mutatorAnnotation = (AnnotationNode) nodes[0];
        MethodNode annotatedMethod = (MethodNode) nodes[1];

        if (!annotatedMethod.getAnnotations(BUILDER_METHOD_ANNOTATION).isEmpty()) {
            addError(
                    "A Builder-only method cannot declare both @Builder.Method and deprecated @Mutator; use only @Builder.Method",
                    mutatorAnnotation
            );
            annotatedMethod.getAnnotations().remove(mutatorAnnotation);
            return;
        }

        if (!annotatedMethod.getAnnotations(BUILDER_QUERY_ANNOTATION).isEmpty()) {
            addError(
                    "@Builder.Query and @Builder.Method are mutually exclusive method categories",
                    mutatorAnnotation
            );
            annotatedMethod.getAnnotations().remove(mutatorAnnotation);
            return;
        }

        AnnotationNode builderMethodAnnotation = new AnnotationNode(BUILDER_METHOD_ANNOTATION);
        builderMethodAnnotation.setSourcePosition(mutatorAnnotation);
        annotatedMethod.getAnnotations().remove(mutatorAnnotation);
        annotatedMethod.addAnnotation(builderMethodAnnotation);

        WriteAccessMethodCheck.findViolation(annotatedMethod, WriteAccess.Type.MANUAL)
                .ifPresent(message -> addError(message, builderMethodAnnotation));
    }
}
