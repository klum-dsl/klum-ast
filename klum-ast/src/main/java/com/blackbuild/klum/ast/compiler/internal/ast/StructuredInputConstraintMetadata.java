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

import com.blackbuild.klum.ast.runtime.generated.GeneratedInput;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Freezes direct field facts at their original source; never copies Bean constraints onto executable sites. */
final class StructuredInputConstraintMetadata {
    private static final String MIN = "jakarta.validation.constraints.Min";
    private static final String MAX = "jakarta.validation.constraints.Max";
    private static final Set<String> MARKERS = Set.of("jakarta.validation.Constraint", "javax.validation.Constraint",
            "com.blackbuild.klum.ast.RelationshipConstraint");

    private StructuredInputConstraintMetadata() { }

    static void enrichSchema(ClassNode model, AnnotationNode schema) {
        schema.setMember("constraintVersion", new ConstantExpression(1));
        schema.setMember("groupRedefined", new ConstantExpression(groupRedefined(model, new HashSet<>())));
    }

    static void enrichField(FieldNode field, AnnotationNode fact) {
        ListExpression constraints = new ListExpression();
        List<AnnotationNode> declarations = field.getAnnotations().stream().filter(StructuredInputConstraintMetadata::constraint).toList();
        declarations.stream().sorted((left, right) -> declaration(left).compareTo(declaration(right))).forEach(annotation -> {
            String name = annotation.getClassNode().getName();
            AnnotationNode rule = new AnnotationNode(ClassHelper.make(GeneratedInput.Constraint.class));
            rule.setMember("annotation", new ConstantExpression(name));
            rule.setMember("declaration", new ConstantExpression(declaration(annotation)));
            if (name.equals(MIN) || name.equals(MAX)) {
                Expression value = annotation.getMember("value");
                // A nonliteral bound remains a runtime description; never evaluate an expression to recover it.
                if (value instanceof ConstantExpression constant && constant.getValue() instanceof Number) {
                    rule.setMember("value", new ConstantExpression(constant.getValue().toString()));
                    boolean plain = empty(annotation.getMember("groups")) && empty(annotation.getMember("payload"))
                            && declarations.stream().filter(other -> other.getClassNode().getName().equals(name)).count() == 1;
                    rule.setMember("plain", new ConstantExpression(plain));
                }
            }
            constraints.addExpression(new AnnotationConstantExpression(rule));
        });
        // Explicit empty arrays distinguish complete absence of rules from stripped/old field metadata.
        fact.setMember("constraints", constraints);
    }

    private static boolean empty(Expression expression) {
        return expression == null || expression instanceof ListExpression list && list.getExpressions().isEmpty();
    }

    private static boolean constraint(AnnotationNode annotation) {
        String name = annotation.getClassNode().getName();
        return name.startsWith("jakarta.validation.constraints.") || name.startsWith("javax.validation.constraints.")
                || name.equals("com.blackbuild.klum.ast.Required") || name.equals("com.blackbuild.klum.ast.Validate")
                || annotation.getClassNode().getAnnotations().stream().anyMatch(meta -> MARKERS.contains(meta.getClassNode().getName()));
    }

    private static String declaration(AnnotationNode annotation) {
        return "@" + annotation.getClassNode().getName() + annotation.getMembers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + entry.getValue().getText())
                .collect(Collectors.joining(", ", "(", ")"));
    }

    private static boolean groupRedefined(ClassNode type, Set<String> visited) {
        if (type == null || !visited.add(type.getName())) return false;
        if (type.getAnnotations().stream().anyMatch(annotation -> Set.of("jakarta.validation.GroupSequence",
                "javax.validation.GroupSequence", "org.hibernate.validator.group.GroupSequenceProvider")
                .contains(annotation.getClassNode().getName()))) return true;
        if (groupRedefined(type.getSuperClass(), visited)) return true;
        for (ClassNode parent : type.getInterfaces()) if (groupRedefined(parent, visited)) return true;
        return false;
    }
}
