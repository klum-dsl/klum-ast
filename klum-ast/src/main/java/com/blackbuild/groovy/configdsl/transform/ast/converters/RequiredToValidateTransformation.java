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
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static org.codehaus.groovy.ast.ClassHelper.make;

/**
 * Converter Transformation for {@link com.blackbuild.groovy.configdsl.transform.Required} into {@link com.blackbuild.groovy.configdsl.transform.Validate}.
 * Necessary since {@link groovy.transform.AnnotationCollector} does not seem to work here.
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class RequiredToValidateTransformation extends AbstractASTTransformation {

    private static final ClassNode VALIDATE_ANNOTATION = make(Validate.class);

    FieldNode annotatedField;
    AnnotationNode requiredAnnotation;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);

        annotatedField = (FieldNode) nodes[1];
        requiredAnnotation = (AnnotationNode) nodes[0];

        if (getAnnotation(annotatedField, VALIDATE_ANNOTATION) != null)
            addError("Combining a @Validate and @Required annotations on a field is illegal.", annotatedField);

        AnnotationNode validateAnnotation = new AnnotationNode(VALIDATE_ANNOTATION);

        Expression value = requiredAnnotation.getMember("value");

        if (value != null)
            validateAnnotation.addMember("value", value);

        annotatedField.addAnnotation(validateAnnotation);
    }
}
