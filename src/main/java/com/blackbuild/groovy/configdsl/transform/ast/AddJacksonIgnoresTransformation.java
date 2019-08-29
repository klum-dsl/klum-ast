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

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getAnnotatedFieldsOfHierarchy;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;

/**
 * Transformation that adds relevant annotations to the generated files.
 */
@GroovyASTTransformation(phase = CompilePhase.INSTRUCTION_SELECTION)
public class AddJacksonIgnoresTransformation extends AbstractASTTransformation {

    private ClassNode annotatedClass;
    private ClassNode jsonIngoreType;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {

        try {
            jsonIngoreType = ClassHelper.make(Class.forName("com.fasterxml.jackson.annotation.JsonIgnoreProperties"));
        } catch (ClassNotFoundException ignored) {
            // No jackson in classpath
            return;
        }

        init(nodes, source);

        annotatedClass = (ClassNode) nodes[1];

        if (annotatedClass.isInterface())
            return;

        Set<String> fieldsToIgnore = new HashSet<>();

        fieldsToIgnore.add("$key");
        for (FieldNode fieldNode : getAnnotatedFieldsOfHierarchy(annotatedClass, DSLASTTransformation.OWNER_ANNOTATION)) {
            fieldsToIgnore.add(fieldNode.getName());
        }

        addIgnoreAnnotation(fieldsToIgnore);
    }

    void addIgnoreAnnotation(Collection<String> propertiesToIgnore) {
        ListExpression ignoreArguments = new ListExpression();

        for (String property : propertiesToIgnore) {
            ignoreArguments.addExpression(constX(property));
        }

        AnnotationNode ignoreAnnotation = new AnnotationNode(jsonIngoreType);
        ignoreAnnotation.addMember("value", ignoreArguments);
        annotatedClass.addAnnotation(ignoreAnnotation);
    }

}
