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
package com.blackbuild.klum.ast.util.layer3;

import groovy.lang.Closure;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.stc.StaticTypesMarker;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getRwClassOf;

/**
 * Helper transformation for static type checking. Sets the DelegationMetadata (via reflection, since package local) for
 * the closure to the rw-class of the annotated field.
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class ApplyDefaultTransformation extends AbstractASTTransformation {

    private AnnotationNode applyDefaultAnnotation;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);

        applyDefaultAnnotation = (AnnotationNode) nodes[0];
        FieldNode annotatedField = (FieldNode) nodes[1];

        ClassNode delegateType = getRwClassOf(annotatedField.getType());

        Object dmd = createDelegationMetadata(
                delegateType,
                Closure.DELEGATE_ONLY);

        applyDefaultAnnotation.getMember("value").setNodeMetaData(StaticTypesMarker.DELEGATION_METADATA, dmd);
    }

    private Object createDelegationMetadata(ClassNode delegateType, int delegateOnly) {
        try {
            return InvokerHelper.invokeConstructorOf(
                    "org.codehaus.groovy.transform.stc.DelegationMetadata",
                    new Object[]{delegateType, delegateOnly}
            );
        } catch (ClassNotFoundException e) {
            addError("Could not create DelegationMetadata.", applyDefaultAnnotation);
            return null;
        }
    }
}
