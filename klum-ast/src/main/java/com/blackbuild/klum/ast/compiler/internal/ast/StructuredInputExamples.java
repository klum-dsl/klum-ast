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

import com.blackbuild.klum.ast.InputExamples;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.syntax.Types;

/** Folds only inert annotation constants, before they enter generated public contracts. */
final class StructuredInputExamples {
    private StructuredInputExamples() { }

    static void enrich(AnnotatedNode source, ClassNode owner, AnnotationNode fact, String identity) {
        List<AnnotationNode> annotations = source.getAnnotations(ClassHelper.make(InputExamples.class));
        ListExpression values = new ListExpression();
        if (!annotations.isEmpty()) {
            if (annotations.size() != 1) throw invalid(identity, 1, "repeated annotation");
            Expression value = annotations.get(0).getMember("value");
            if (value == null) throw invalid(identity, 1, "requires constant String examples");
            List<Expression> expressions = value instanceof ListExpression list ? list.getExpressions() : List.of(value);
            if (expressions.isEmpty() || expressions.size() > 16) throw invalid(identity, 1, "requires 1–16 examples");
            int total = 0;
            for (Expression expression : expressions) {
                int index = values.getExpressions().size() + 1;
                Expression folded = fold(expression, owner, new HashSet<>());
                if (!(folded instanceof ConstantExpression constant) || !(constant.getValue() instanceof String text))
                    throw invalid(identity, index, "requires a compile-time constant String");
                int bytes = modifiedUtf8Bytes(text);
                total += bytes;
                if (bytes > 8192 || total > 32768) throw invalid(identity, index, "modified-UTF-8 size limit exceeded");
                values.addExpression(new ConstantExpression(text));
            }
        }
        if (!annotations.isEmpty()) annotations.get(0).setMember("value", values);
        fact.setMember("examples", values);
    }

    // Groovy's general constant-folder can reflectively read a static field and initialize its class.
    // This restricted folder reads AST initializers only and never invokes that fallback.
    private static Expression fold(Expression expression, ClassNode owner, Set<FieldNode> visiting) {
        if (expression instanceof ConstantExpression) return expression;
        if (expression instanceof BinaryExpression binary && binary.getOperation().getType() == Types.PLUS) {
            Expression left = fold(binary.getLeftExpression(), owner, visiting);
            Expression right = fold(binary.getRightExpression(), owner, visiting);
            if (left instanceof ConstantExpression a && a.getValue() instanceof String first
                    && right instanceof ConstantExpression b && b.getValue() instanceof String second
                    && first.length() + second.length() <= 8192)
                return new ConstantExpression(first + second);
            return expression;
        }
        FieldNode field = null;
        if (expression instanceof VariableExpression variable)
            field = variable.getAccessedVariable() instanceof FieldNode accessed ? accessed : owner.getField(variable.getName());
        else if (expression instanceof PropertyExpression property && property.getObjectExpression() instanceof ClassExpression type)
            field = type.getType().getField(property.getPropertyAsString());
        if (field == null || !field.isStatic() || !field.isFinal() || visiting.size() >= 32 || !visiting.add(field)) return expression;
        Expression result = fold(field.getInitialValueExpression(), field.getOwner(), visiting);
        visiting.remove(field);
        return result;
    }

    private static int modifiedUtf8Bytes(String text) {
        int bytes = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            bytes += c >= 1 && c <= 127 ? 1 : c <= 2047 ? 2 : 3;
        }
        return bytes;
    }

    private static IllegalArgumentException invalid(String source, int index, String reason) {
        return new IllegalArgumentException("@InputExamples on " + source + ", example " + index + ": " + reason);
    }
}
