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
package com.blackbuild.klum.ast.compiler.internal.validation;

import com.blackbuild.klum.ast.RelationshipConstraint;
import groovy.lang.Closure;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.syntax.SyntaxException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Modifier;
import java.util.Optional;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getElementType;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isCollectionOrMap;

/** Checks only annotations on declared DSL fields; source-defined annotation declarations remain in their own AST. */
public final class RelationshipConstraintFieldCheck {
    private static final ClassNode MARKER = ClassHelper.make(RelationshipConstraint.class);
    private static final String VALUE_MEMBER = "value";

    private RelationshipConstraintFieldCheck() {}

    public static void check(FieldNode field, SourceUnit source) {
        for (AnnotationNode use : field.getAnnotations()) {
            ClassNode domain = use.getClassNode();
            AnnotationNode marker = domain.getAnnotations(MARKER).stream().findFirst().orElse(null);
            if (marker != null) checkRelationshipUse(field, source, use, domain, marker);
        }
    }

    private static void checkRelationshipUse(FieldNode field, SourceUnit source, AnnotationNode use,
                                             ClassNode domain, AnnotationNode marker) {
        if (domain.isResolved()) checkDeclaration(domain, marker, use, source);
        ClassNode target = isCollectionOrMap(field.getType()) ? getElementType(field) : field.getType();
        if (Modifier.isStatic(field.getModifiers()) || target == null || !isDSLObject(target)) {
            error(source, use, "Relationship constraint on field '" + field.getName()
                    + "' must declare a non-static DSL relationship target or collection/map of DSL relationships");
            return;
        }
        callbackParameters(domain, marker).ifPresent(parameters -> {
            if (parameters.length == 2 && !accepts(parameters[1], target))
                error(source, use, "Relationship constraint callback target " + parameters[1].getName()
                        + " cannot accept declared relationship " + target.getName());
        });
    }

    public static void checkDeclaration(ClassNode domain, AnnotationNode marker, AnnotationNode location, SourceUnit source) {
        if (!hasRuntimeRetention(domain))
            error(source, location, "Relationship constraint annotation " + domain.getName() + " must have @Retention(RUNTIME)");
        if (!targetsOnlyFields(domain))
            error(source, location, "Relationship constraint annotation " + domain.getName() + " must have @Target(FIELD)");

        Optional<ClassNode[]> maybeParameters = callbackParameters(domain, marker);
        if (maybeParameters.isEmpty())
            error(source, location, "@RelationshipConstraint.value must denote a Groovy Closure");
        else {
            ClassNode[] parameters = maybeParameters.get();
            if (parameters.length != 2)
                error(source, location, "@RelationshipConstraint callback must declare exactly two authored parameters: (domain annotation, completed relationship target)");
            else if (!accepts(parameters[0], domain))
                error(source, location, "Relationship constraint callback first parameter " + parameters[0].getName()
                        + " cannot accept annotation " + domain.getName());
        }
    }

    private static Optional<ClassNode[]> callbackParameters(ClassNode domain, AnnotationNode marker) {
        Expression expression = marker.getMember(VALUE_MEMBER);
        if (expression instanceof ClosureExpression closure) {
            Parameter[] authored = closure.getParameters();
            if (authored == null) return Optional.of(new ClassNode[0]);
            ClassNode[] parameters = new ClassNode[authored.length];
            for (int i = 0; i < authored.length; i++) parameters[i] = authored[i].getType();
            return Optional.of(parameters);
        }
        if (!domain.isResolved()) return Optional.empty();
        RelationshipConstraint compiled = (RelationshipConstraint) domain.getTypeClass().getAnnotation(RelationshipConstraint.class);
        if (compiled == null || !Closure.class.isAssignableFrom(compiled.value())) return Optional.empty();
        try {
            Closure<?> callback = (Closure<?>) InvokerHelper.invokeConstructorOf(compiled.value(), new Object[]{null, null});
            Class<?>[] types = callback.getParameterTypes();
            ClassNode[] parameters = new ClassNode[types.length];
            for (int i = 0; i < types.length; i++) parameters[i] = ClassHelper.make(types[i]);
            return Optional.of(parameters);
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private static boolean accepts(ClassNode parameter, ClassNode value) {
        return parameter.equals(value) || value.isDerivedFrom(parameter) || value.implementsInterface(parameter);
    }

    private static boolean hasRuntimeRetention(ClassNode domain) {
        if (domain.isResolved()) {
            Retention retention = (Retention) domain.getTypeClass().getAnnotation(Retention.class);
            return retention != null && retention.value() == RetentionPolicy.RUNTIME;
        }
        return domain.getAnnotations(ClassHelper.make(Retention.class)).stream()
                .map(annotation -> annotation.getMember(VALUE_MEMBER))
                .anyMatch(value -> value != null && value.getText().endsWith("RUNTIME"));
    }

    private static boolean targetsOnlyFields(ClassNode domain) {
        if (domain.isResolved()) {
            Target target = (Target) domain.getTypeClass().getAnnotation(Target.class);
            return target != null && target.value().length == 1 && target.value()[0] == ElementType.FIELD;
        }
        return domain.getAnnotations(ClassHelper.make(Target.class)).stream()
                .map(annotation -> annotation.getMember(VALUE_MEMBER))
                .anyMatch(RelationshipConstraintFieldCheck::isOnlyFieldTarget);
    }

    private static boolean isOnlyFieldTarget(Expression value) {
        if (value instanceof ListExpression list)
            return list.getExpressions().size() == 1 && isFieldConstant(list.getExpressions().get(0));
        if (value instanceof ArrayExpression array)
            return array.getExpressions().size() == 1 && isFieldConstant(array.getExpressions().get(0));
        return isFieldConstant(value);
    }

    private static boolean isFieldConstant(Expression value) {
        if (value instanceof PropertyExpression property)
            return "FIELD".equals(property.getPropertyAsString());
        if (value instanceof VariableExpression variable)
            return "FIELD".equals(variable.getName());
        return false;
    }

    private static void error(SourceUnit source, AnnotationNode use, String message) {
        source.addError(new SyntaxException(message, use.getLineNumber(), use.getColumnNumber()));
    }
}
