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

import com.blackbuild.klum.ast.Struct;
import com.blackbuild.klum.ast.runtime.generated.GeneratedInput;
import org.apache.groovy.ast.tools.ExpressionUtils;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Freezes direct and first-level composing field facts at their original source; never copies Bean constraints onto executable sites. */
final class StructuredInputConstraintMetadata {
    private static final String ICD = Struct.ConstraintDescription.class.getName();
    private static final String JAKARTA_CONSTRAINT = "jakarta.validation.Constraint";
    private static final String JAKARTA_CONSTRAINTS = "jakarta.validation.constraints.";
    private static final String VALUE = "value";
    private static final String PLAIN = "plain";
    private static final String PATTERN = "jakarta.validation.constraints.Pattern";
    private static final String MIN = "jakarta.validation.constraints.Min";
    private static final String MAX = "jakarta.validation.constraints.Max";
    private static final Set<String> MARKERS = Set.of(JAKARTA_CONSTRAINT, "javax.validation.Constraint",
            "com.blackbuild.klum.ast.RelationshipConstraint");

    private StructuredInputConstraintMetadata() { }

    static void enrichSchema(ClassNode model, AnnotationNode schema) {
        schema.setMember("groupRedefined", new ConstantExpression(groupRedefined(model, new HashSet<>())));
    }

    static void enrichField(FieldNode field, AnnotationNode fact) {
        List<AnnotationNode> policies = field.getAnnotations(ClassHelper.make(Struct.ConstraintProjection.class));
        Expression selected = policies.isEmpty() ? null : policies.get(0).getMember(VALUE);
        // Let the ordinary annotation compiler fold enum constants, including static imports.
        fact.setMember("constraintProjection", selected == null
                ? new PropertyExpression(new ClassExpression(ClassHelper.make(Struct.ConstraintProjection.Policy.class)), "PROJECT")
                : selected);
        ListExpression constraints = new ListExpression();
        List<AnnotationNode> declarations = field.getAnnotations().stream().filter(StructuredInputConstraintMetadata::constraint).toList();
        declarations.stream().sorted((left, right) -> declaration(left).compareTo(declaration(right)))
                .map(annotation -> rule(field, annotation, declarations))
                .forEach(rule -> constraints.addExpression(new AnnotationConstantExpression(rule)));
        declarations.stream().sorted((left, right) -> declaration(left).compareTo(declaration(right)))
                .forEach(annotation -> composingRules(field, annotation, declarations, constraints));
        // Explicit empty arrays distinguish complete absence of rules from stripped/old field metadata.
        fact.setMember("constraints", constraints);
        fact.setMember("constraintCount", new ConstantExpression(constraints.getExpressions().size()));
    }

    private static AnnotationNode rule(FieldNode field, AnnotationNode annotation, List<AnnotationNode> declarations) {
        String name = annotation.getClassNode().getName();
        // Use Groovy's inert ConstantValue/AST folding, never reflective field reads or evaluation.
        if (name.equals(PATTERN)) {
            annotation.getMembers().replaceAll((member, expression) -> ExpressionUtils.transformInlineConstants(expression));
        }
        AnnotationNode rule = new AnnotationNode(ClassHelper.make(GeneratedInput.Constraint.class));
        rule.setSourcePosition(annotation);
        rule.setMember("annotation", new ConstantExpression(name));
        rule.setMember("declaration", new ConstantExpression(declaration(name.equals(PATTERN)
                ? StructuredInputSourceOrigins.original(field, annotation) : annotation)));
        rule.setMember("origin", new ConstantExpression(documentaryOnly(annotation) ? "DOCUMENTARY" : "DIRECT"));
        rule.setMember("owner", new ConstantExpression(""));
        rule.setMember("description", description(annotation));
        rule.setMember(PLAIN, ConstantExpression.FALSE);
        boolean decimal = name.equals("jakarta.validation.constraints.DecimalMin") || name.equals("jakarta.validation.constraints.DecimalMax");
        if (name.equals(MIN) || name.equals(MAX) || decimal) captureBound(annotation, declarations, rule, decimal);
        if (name.equals(PATTERN)) {
            rule.setMember("regexp", annotation.getMember("regexp"));
            rule.setMember("patternFlagsEmpty", new ConstantExpression(empty(annotation.getMember("flags"))));
            rule.setMember(PLAIN, new ConstantExpression(StructuredInputSourceOrigins.direct(field, annotation) && plain(annotation, declarations)
                    && declarations.stream().noneMatch(other -> other.getClassNode().getName().equals("jakarta.validation.constraints.Pattern$List"))));
        }
        return rule;
    }

    private static boolean documentaryOnly(AnnotationNode annotation) {
        return !runtimeConstraint(annotation);
    }

    private static Expression description(AnnotationNode annotation) {
        return annotation.getClassNode().getAnnotations().stream()
                .filter(meta -> meta.getClassNode().getName().equals(ICD))
                .map(meta -> ExpressionUtils.transformInlineConstants(meta.getMember(VALUE)))
                .findFirst().orElse(new ConstantExpression(""));
    }

    private static void composingRules(FieldNode field, AnnotationNode outer, List<AnnotationNode> declarations,
                                       ListExpression facts) {
        ClassNode type = outer.getClassNode();
        if (!jakartaConstraint(type)) return;
        List<AnnotationNode> children = type.getAnnotations().stream()
                .filter(meta -> beanConstraint(meta.getClassNode())).toList();
        boolean and = StructuredInputSourceOrigins.direct(field, outer) && plainComposition(outer, declarations)
                && standardAnd(type) && !overrides(type)
                && children.stream().noneMatch(child -> !child.getClassNode().getName().startsWith(JAKARTA_CONSTRAINTS)
                        && jakartaConstraint(child.getClassNode()));
        children.stream().sorted((left, right) -> declaration(left).compareTo(declaration(right))).forEach(child -> {
            AnnotationNode fact = rule(field, child, children);
            boolean eligible = and && plainComposition(child, children);
            fact.setMember("origin", new ConstantExpression(eligible ? "COMPOSING_AND" : "COMPOSING_UNSUPPORTED"));
            fact.setMember("owner", new ConstantExpression(declaration(outer)));
            fact.setMember("description", new ConstantExpression(""));
            fact.setMember(PLAIN, ConstantExpression.FALSE);
            facts.addExpression(new AnnotationConstantExpression(fact));
        });
    }

    private static boolean plainComposition(AnnotationNode annotation, List<AnnotationNode> declarations) {
        return plain(annotation, declarations) && declarations.stream().noneMatch(other ->
                other.getClassNode().getName().equals(annotation.getClassNode().getName() + "$List"));
    }

    private static boolean beanConstraint(ClassNode type) {
        return type.getName().startsWith(JAKARTA_CONSTRAINTS)
                || type.getName().startsWith("javax.validation.constraints.")
                || type.getAnnotations().stream().anyMatch(meta -> Set.of(JAKARTA_CONSTRAINT, "javax.validation.Constraint")
                .contains(meta.getClassNode().getName()));
    }

    private static boolean jakartaConstraint(ClassNode type) {
        return type.getAnnotations().stream().anyMatch(meta -> meta.getClassNode().getName().equals(JAKARTA_CONSTRAINT));
    }

    private static boolean standardAnd(ClassNode type) {
        return type.getAnnotations().stream()
                .filter(meta -> meta.getClassNode().getName().equals("org.hibernate.validator.constraints.ConstraintComposition"))
                .allMatch(meta -> meta.getMember(VALUE) instanceof PropertyExpression property
                        && "AND".equals(property.getPropertyAsString()));
    }

    private static boolean overrides(ClassNode type) {
        return type.getMethods().stream().flatMap(method -> method.getAnnotations().stream())
                .anyMatch(meta -> meta.getClassNode().getName().startsWith("jakarta.validation.OverridesAttribute"));
    }

    private static void captureBound(AnnotationNode annotation, List<AnnotationNode> declarations, AnnotationNode rule, boolean decimal) {
        Expression value = annotation.getMember(VALUE);
        if (value == null) return;
        // The ordinary annotation compiler folds constants into inert typed members. Capture never evaluates them.
        rule.setMember(decimal ? "decimalValue" : VALUE, value);
        if (decimal) {
            Expression inclusive = annotation.getMember("inclusive");
            rule.setMember("inclusive", inclusive == null ? ConstantExpression.TRUE : inclusive);
        }
        boolean plain = plain(annotation, declarations);
        rule.setMember(PLAIN, new ConstantExpression(plain));
    }

    private static boolean plain(AnnotationNode annotation, List<AnnotationNode> declarations) {
        return empty(annotation.getMember("groups")) && empty(annotation.getMember("payload"))
                && declarations.stream().filter(other -> other.getClassNode().getName().equals(annotation.getClassNode().getName())).count() == 1;
    }

    private static boolean empty(Expression expression) {
        return expression == null || expression instanceof ListExpression list && list.getExpressions().isEmpty();
    }

    private static boolean constraint(AnnotationNode annotation) {
        return runtimeConstraint(annotation) || annotation.getClassNode().getAnnotations().stream()
                .anyMatch(meta -> meta.getClassNode().getName().equals(ICD));
    }

    private static boolean runtimeConstraint(AnnotationNode annotation) {
        String name = annotation.getClassNode().getName();
        return name.startsWith(JAKARTA_CONSTRAINTS) || name.startsWith("javax.validation.constraints.")
                || name.equals("com.blackbuild.klum.ast.Required") || name.equals("com.blackbuild.klum.ast.Validate")
                || constraintType(annotation.getClassNode())
                || annotation.getClassNode().getMethods(VALUE).stream().anyMatch(method -> method.getReturnType().isArray()
                && constraintType(method.getReturnType().getComponentType()));
    }

    private static boolean constraintType(ClassNode type) {
        return type.getAnnotations().stream().anyMatch(meta -> MARKERS.contains(meta.getClassNode().getName()));
    }

    private static String declaration(AnnotationNode annotation) {
        return "@" + annotation.getClassNode().getName() + annotation.getMembers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + "=" + render(entry.getValue()))
                .collect(Collectors.joining(", ", "(", ")"));
    }

    /** AnnotationConstantExpression.getText embeds object identities; render only inert source syntax instead. */
    private static String render(Expression expression) {
        if (expression instanceof AnnotationConstantExpression nested && nested.getValue() instanceof AnnotationNode annotation)
            return declaration(annotation);
        if (expression instanceof ListExpression list)
            return list.getExpressions().stream().map(StructuredInputConstraintMetadata::render).collect(Collectors.joining(", ", "[", "]"));
        if (expression instanceof ArrayExpression array)
            return array.getExpressions().stream().map(StructuredInputConstraintMetadata::render).collect(Collectors.joining(", ", "[", "]"));
        if (expression instanceof ClassExpression type) return type.getType().getName();
        if (expression instanceof ConstantExpression constant) {
            Object value = constant.getValue();
            return value instanceof String text ? "\"" + escape(text) + "\"" : String.valueOf(value);
        }
        if (expression instanceof PropertyExpression property)
            return render(property.getObjectExpression()) + "." + property.getPropertyAsString();
        return "<runtime expression: " + expression.getClass().getSimpleName() + ">";
    }

    private static String escape(String text) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' || c == '"') {
                result.append('\\').append(c);
            } else if (Character.isISOControl(c) || Character.isSurrogate(c) || c == '\u2028' || c == '\u2029') {
                result.append(String.format("\\u%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
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
