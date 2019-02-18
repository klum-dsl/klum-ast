/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2019 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform.ast.deprecations;

import com.blackbuild.groovy.configdsl.transform.Field;
import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.ReadOnly;
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
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;

/**
 * Converter Transformation for Field Types. Converts {@link ReadOnly} into {@link FieldType#PROTECTED}. Will
 * be remove in a later version.
 *
 * @deprecated don't use, remove later
 */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
@Deprecated
public class FieldTypeDeprecationTransformation extends AbstractASTTransformation {

    private static final ClassNode FIELD_ANNOTATION = make(Field.class);

    FieldNode annotatedField;
    AnnotationNode annotation;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);

        annotatedField = (FieldNode) nodes[1];
        annotation = (AnnotationNode) nodes[0];

        AnnotationNode fieldAnnotation = getOrCreateFieldAnnotation();
        Expression existingType = fieldAnnotation.getMember("value");

        if (existingType != null)
            addError("Combining a FieldType with deprecated @ReadOnly annotation is illegal.", annotatedField);

        fieldAnnotation.addMember("value", propX(classX(FieldType.class), FieldType.PROTECTED.name()));
    }

    private AnnotationNode getOrCreateFieldAnnotation() {
        AnnotationNode result = getAnnotation(annotatedField, FIELD_ANNOTATION);

        if (result != null)
            return result;

        result = new AnnotationNode(FIELD_ANNOTATION);
        annotatedField.addAnnotation(result);

        return result;
    }


}
