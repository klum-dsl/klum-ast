/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform.ast.converters;

import com.blackbuild.groovy.configdsl.transform.Validate;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import static com.blackbuild.klum.common.CommonAstHelper.addCompileWarning;
import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Converter Transformation for {@link com.blackbuild.groovy.configdsl.transform.Validation} into {@link Validate}.
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
@Deprecated(forRemoval = true)
public class ValidationToValidateTransformation extends AbstractASTTransformation {

    private static final ClassNode VALIDATE_ANNOTATION = make(Validate.class);

    ClassNode annotatedClass;
    AnnotationNode validationNode;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);

        annotatedClass = (ClassNode) nodes[1];
        validationNode = (AnnotationNode) nodes[0];

        Expression option = validationNode.getMember("option");
        if (option == null) {
            addCompileWarning(source, "Validation without option has no effect", validationNode);
            return;
        }

        if (!option.getText().endsWith("VALIDATE_UNMARKED")) {
            addCompileWarning(source, "Validation without VALIDATE_UNMARKED has no effect", validationNode);
            return;
        }

        if (hasAnnotation(annotatedClass, VALIDATE_ANNOTATION))
            return;

        AnnotationNode validateAnnotation = new AnnotationNode(VALIDATE_ANNOTATION);
        annotatedClass.addAnnotation(validateAnnotation);
    }
}
