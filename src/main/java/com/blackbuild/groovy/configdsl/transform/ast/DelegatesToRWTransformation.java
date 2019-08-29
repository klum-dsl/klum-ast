/*
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
package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.groovy.configdsl.transform.DelegatesToRW;
import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassCodeVisitorSupport;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getRwClassOf;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;

@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
public class DelegatesToRWTransformation extends AbstractASTTransformation {

    private final static ClassNode DELEGATES_TO_RW_TYPE = ClassHelper.make(DelegatesToRW.class);
    private final static ClassNode DELEGATES_TO_TYPE = ClassHelper.make(DelegatesTo.class);
    private ClassNode model;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);

        model = (ClassNode) nodes[1];

        Visitor visitor = new Visitor();
        visitor.visitClass(model);
        visitor.visitClass(getRwClassOf(model));
    }

    private class Visitor extends ClassCodeVisitorSupport {

        @Override
        protected SourceUnit getSourceUnit() {
            return sourceUnit;
        }

        @Override
        public void visitAnnotations(AnnotatedNode node) {
            super.visitAnnotations(node);

            List<AnnotationNode> annotations = node.getAnnotations(DELEGATES_TO_RW_TYPE);
            if (annotations.isEmpty()) return;

            ClassExpression targetValue = (ClassExpression) annotations.get(0).getMember("value");

            ClassNode target = targetValue != null ? targetValue.getType() : model;

            if (!isDSLObject(target)) {
                addError(target + " is no DSL object.", targetValue);
                return;
            }

            AnnotationNode delegatesTo = createDelegatesToAnnotation(target);

            node.addAnnotation(delegatesTo);

        }

        private AnnotationNode createDelegatesToAnnotation(ClassNode target) {
            AnnotationNode delegatesTo = new AnnotationNode(DELEGATES_TO_TYPE);
            delegatesTo.addMember("value", new ClassExpression(getRwClassOf(target)));
            delegatesTo.setMember("strategy", constX(Closure.DELEGATE_ONLY));
            return delegatesTo;
        }
    }

}
