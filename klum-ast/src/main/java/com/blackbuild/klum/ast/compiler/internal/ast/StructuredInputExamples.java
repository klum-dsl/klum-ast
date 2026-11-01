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

import com.blackbuild.klum.ast.Struct.Examples;
import org.apache.groovy.ast.tools.ExpressionUtils;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;

import java.util.List;

/** Carries documentary annotation constants without executing authored code. */
final class StructuredInputExamples {
    private StructuredInputExamples() { }

    static void enrich(AnnotatedNode source, ClassNode owner, AnnotationNode fact, String identity) {
        List<AnnotationNode> annotations = source.getAnnotations(ClassHelper.make(Examples.class));
        ListExpression values = new ListExpression();
        if (!annotations.isEmpty()) {
            Expression value = annotations.get(0).getMember("value");
            if (value == null) throw new IllegalArgumentException("@Struct.Examples requires constant strings on " + identity);
            List<Expression> expressions = value instanceof ListExpression list ? list.getExpressions() : List.of(value);
            for (Expression expression : expressions) {
                // Local constants have not yet acquired an accessed FieldNode at this compiler phase.
                if (expression instanceof VariableExpression variable)
                    expression = new PropertyExpression(new ClassExpression(owner), variable.getName());
                // This overload uses AST constants only; the overload accepting an owner may read static fields reflectively.
                Expression folded = ExpressionUtils.transformInlineConstants(expression);
                if (!(folded instanceof ConstantExpression constant) || !(constant.getValue() instanceof String))
                    throw new IllegalArgumentException("@Struct.Examples on " + identity + " requires compile-time constant strings");
                values.addExpression(folded);
            }
            annotations.get(0).setMember("value", values);
        }
        fact.setMember("examples", values);
    }
}
