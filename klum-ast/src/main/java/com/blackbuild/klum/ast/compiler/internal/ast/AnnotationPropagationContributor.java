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

import com.blackbuild.klum.ast.DSL;
import com.blackbuild.klum.ast.PropagateAnnotation;
import com.blackbuild.klum.cast.compiler.EffectiveAnnotation;
import com.blackbuild.klum.cast.compiler.EffectiveDeclaration;
import com.blackbuild.klum.cast.compiler.EffectiveDeclarationContribution;
import com.blackbuild.klum.cast.compiler.EffectiveDeclarationContributor;
import groovy.transform.AnnotationCollector;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;

import java.lang.annotation.ElementType;
import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Default-only expansion inside KlumCast's effective-declaration handoff; never mutates the target. */
public final class AnnotationPropagationContributor implements EffectiveDeclarationContributor {
    private static final String VALUE_MEMBER = "value";

    @Override
    public EffectiveDeclarationContribution contribute(EffectiveDeclaration declaration) {
        AnnotatedNode target = declaration.getTarget();
        ClassNode owner = eligibleOwner(target);
        if (owner == null) return EffectiveDeclarationContribution.none();

        List<EffectiveAnnotation> additions = new ArrayList<>();
        Map<String, String> origins = new LinkedHashMap<>();
        List<AnnotationUse> annotations = normalized(declaration.getAnnotations());
        for (AnnotationUse explicit : annotations)
            origins.putIfAbsent(explicit.annotation().getClassNode().getName(), "explicit " + explicit.path());
        List<String> errors = new ArrayList<>();
        for (AnnotationUse domain : annotations) {
            for (AnnotationUse request : requests(domain.annotation().getClassNode())) {
                Expression value = request.annotation().getMember(VALUE_MEMBER);
                String origin = "@" + domain.annotation().getClassNode().getName() + " requests ";
                if (!(value instanceof ClassExpression selectedType)) {
                    errors.add(origin + "an annotation class literal on " + location(owner, target));
                    continue;
                }
                ClassNode selected = selectedType.getType();
                origin += "@" + selected.getName() + " on " + location(owner, target)
                        + " (domain " + domain.path() + ", request " + request.path() + ")";
                checkSelection(selected, target, origin, origins, errors);
                AnnotationNode copy = new AnnotationNode(selected);
                copy.setSourcePosition(domain.source());
                additions.add(new EffectiveAnnotation(copy, origin, domain.source()));
            }
        }
        if (!errors.isEmpty()) {
            // The neutral API has no contribution-diagnostic channel. These are propagation errors,
            // not KlumCast constraint violations; submit them to Groovy without attaching a partial batch.
            for (String error : errors) {
                SyntaxException syntax = new SyntaxException(error, target.getLineNumber(), target.getColumnNumber());
                owner.getModule().getContext().getErrorCollector().addErrorAndContinue(
                        new SyntaxErrorMessage(syntax, owner.getModule().getContext()));
            }
            return EffectiveDeclarationContribution.none();
        }
        return additions.isEmpty() ? EffectiveDeclarationContribution.none() : new EffectiveDeclarationContribution(additions);
    }

    private static void checkSelection(ClassNode selected, AnnotatedNode target, String origin,
                                       Map<String, String> origins, List<String> errors) {
        String problem = illegalSelection(selected, target);
        String previous = origins.putIfAbsent(selected.getName(), origin);
        if (problem != null) errors.add(origin + ": " + problem);
        if (previous != null) errors.add(origin + ": duplicate propagated annotation; already supplied by " + previous);
    }

    private static ClassNode eligibleOwner(AnnotatedNode target) {
        if (target instanceof ConstructorNode || target.getLineNumber() < 0) return null;
        ClassNode owner;
        if (target instanceof ClassNode schema) owner = schema;
        else if (target instanceof FieldNode field) owner = field.getOwner();
        else if (target instanceof MethodNode method) owner = method.getDeclaringClass();
        else return null;
        if (owner.isAnnotationDefinition() || owner.isInterface() || !DslAstHelper.isDSLObject(owner)) return null;
        return owner;
    }

    private static String location(ClassNode owner, AnnotatedNode target) {
        if (target instanceof FieldNode field) return owner.getName() + "." + field.getName();
        if (target instanceof MethodNode method) return owner.getName() + "." + method.getName() + "()";
        return owner.getName();
    }

    private static List<AnnotationUse> requests(ClassNode type) {
        return normalized(type.getAnnotations()).stream()
                .filter(use -> use.annotation().getClassNode().getName().equals(PropagateAnnotation.class.getName()))
                .toList();
    }

    /** Unwrap only genuine repeatable containers, never arbitrary annotation-array members. */
    private static List<AnnotationUse> normalized(List<AnnotationNode> annotations) {
        List<AnnotationUse> result = new ArrayList<>();
        Map<String, Integer> occurrences = new LinkedHashMap<>();
        for (AnnotationNode annotation : annotations) {
            String name = annotation.getClassNode().getName();
            int occurrence = occurrences.merge(name, 1, Integer::sum);
            String path = "@" + name + " occurrence " + occurrence;
            // Keep physical container identity; normalization is only a logical discovery view.
            result.add(new AnnotationUse(annotation, annotation, path));
            List<AnnotationNode> nested = repeatedAnnotations(annotation);
            for (int index = 0; index < nested.size(); index++) {
                AnnotationNode repeated = nested.get(index);
                // Defaults belong to the annotation library, so their source is the applied container.
                AnnotationNode source = annotation.getMember(VALUE_MEMBER) != null && repeated.getLineNumber() > 0
                        ? repeated : annotation;
                result.add(new AnnotationUse(repeated, source, path + ".value[" + (index + 1) + "]"));
            }
        }
        return result;
    }

    private record AnnotationUse(AnnotationNode annotation, AnnotationNode source, String path) {}

    private static List<AnnotationNode> repeatedAnnotations(AnnotationNode container) {
        ClassNode repeated = repeatedType(container.getClassNode());
        if (repeated == null) return List.of();
        Expression value = container.getMember(VALUE_MEMBER);
        if (value == null) {
            if (container.getClassNode().isResolved()) return defaultDomainTypes(container);
            MethodNode member = container.getClassNode().getMethods(VALUE_MEMBER).get(0);
            if (member.getCode() instanceof ExpressionStatement expressionStatement)
                value = expressionStatement.getExpression();
            else if (member.getCode() instanceof ReturnStatement returnStatement)
                value = returnStatement.getExpression();
        }
        List<AnnotationNode> result = new ArrayList<>();
        for (Expression entry : annotationValues(value)) {
            if (entry instanceof AnnotationConstantExpression constant) {
                AnnotationNode nested = (AnnotationNode) constant.getValue();
                if (nested.getClassNode().getName().equals(repeated.getName())) result.add(nested);
            }
        }
        return result;
    }

    /** Inspect only default domain types; never copy a runtime instance or its configured members. */
    private static List<AnnotationNode> defaultDomainTypes(AnnotationNode container) {
        List<AnnotationNode> result = new ArrayList<>();
        for (Method member : container.getClassNode().getTypeClass().getDeclaredMethods()) {
            if (member.getName().equals(VALUE_MEMBER) && member.getDefaultValue() instanceof Annotation[] defaults) {
                for (Annotation domain : defaults) {
                    AnnotationNode use = new AnnotationNode(ClassHelper.make(domain.annotationType()));
                    use.setSourcePosition(container);
                    result.add(use);
                }
            }
        }
        return result;
    }

    private static List<Expression> annotationValues(Expression value) {
        if (value instanceof ListExpression list) return list.getExpressions();
        if (value instanceof ArrayExpression array) return array.getExpressions();
        return value == null ? List.of() : List.of(value);
    }

    private static ClassNode repeatedType(ClassNode container) {
        for (MethodNode method : container.getMethods(VALUE_MEMBER)) {
            if (!method.getReturnType().isArray()) continue;
            ClassNode component = method.getReturnType().getComponentType();
            for (AnnotationNode marker : component.getAnnotations()) {
                if (!marker.getClassNode().getName().equals(Repeatable.class.getName())) continue;
                Expression value = marker.getMember(VALUE_MEMBER);
                if (value instanceof ClassExpression repeatable && repeatable.getType().getName().equals(container.getName()))
                    return component;
            }
        }
        return null;
    }

    private static String illegalSelection(ClassNode selected, AnnotatedNode target) {
        if (!selected.getAnnotations(ClassHelper.make(AnnotationCollector.class)).isEmpty())
            return "AnnotationCollector cannot be propagated; rewriting is not default-only annotation composition";
        if (!selected.isAnnotationDefinition()) return "selected type is not an annotation";
        if (selected.getName().equals(DSL.class.getName())) return "DSL cannot be propagated";
        if (!requests(selected).isEmpty()) return "nested propagation is not supported";
        ElementType required = declarationTarget(target);
        for (AnnotationNode marker : selected.getAnnotations()) {
            if (marker.getClassNode().getName().equals(Target.class.getName())) {
                Expression value = marker.getMember(VALUE_MEMBER);
                List<Expression> entries = annotationValues(value);
                if (entries.stream().noneMatch(entry -> entry.getText().endsWith("." + required.name())))
                    return "selected annotation does not permit target " + required;
            }
        }
        return missingDefaultProblem(selected);
    }

    private static ElementType declarationTarget(AnnotatedNode target) {
        if (target instanceof ClassNode) return ElementType.TYPE;
        if (target instanceof FieldNode) return ElementType.FIELD;
        return ElementType.METHOD;
    }

    private static String missingDefaultProblem(ClassNode selected) {
        if (selected.isResolved()) {
            for (Method member : selected.getTypeClass().getDeclaredMethods())
                if (member.getDefaultValue() == null) return "member '" + member.getName() + "' has no default";
        } else {
            for (MethodNode member : selected.getMethods())
                if (member.getDeclaringClass().equals(selected) && !member.hasAnnotationDefault())
                    return "member '" + member.getName() + "' has no default";
        }
        return null;
    }
}
