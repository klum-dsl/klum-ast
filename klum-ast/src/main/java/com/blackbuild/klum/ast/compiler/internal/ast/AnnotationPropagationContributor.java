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
    @Override
    public EffectiveDeclarationContribution contribute(EffectiveDeclaration declaration) {
        AnnotatedNode target = declaration.getTarget();
        ClassNode owner = eligibleOwner(target);
        if (owner == null) return EffectiveDeclarationContribution.none();

        List<EffectiveAnnotation> additions = new ArrayList<>();
        Map<String, String> origins = new LinkedHashMap<>();
        List<AnnotationNode> annotations = normalized(declaration.getAnnotations());
        for (AnnotationNode explicit : annotations)
            origins.put(explicit.getClassNode().getName(), "explicit @" + explicit.getClassNode().getName());
        List<String> errors = new ArrayList<>();
        for (AnnotationNode domain : annotations) {
            for (AnnotationNode request : requests(domain.getClassNode())) {
                Expression value = request.getMember("value");
                String origin = "@" + domain.getClassNode().getName() + " requests ";
                if (!(value instanceof ClassExpression)) {
                    errors.add(origin + "an annotation class literal on " + location(owner, target));
                    continue;
                }
                ClassNode selected = ((ClassExpression) value).getType();
                origin += "@" + selected.getName() + " on " + location(owner, target);
                checkSelection(selected, target, origin, origins, errors);
                AnnotationNode copy = new AnnotationNode(selected);
                copy.setSourcePosition(domain);
                additions.add(new EffectiveAnnotation(copy, origin, domain));
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
        if (target instanceof ClassNode) owner = (ClassNode) target;
        else if (target instanceof FieldNode) owner = ((FieldNode) target).getOwner();
        else if (target instanceof MethodNode) owner = ((MethodNode) target).getDeclaringClass();
        else return null;
        if (owner.isAnnotationDefinition() || owner.isInterface() || !DslAstHelper.isDSLObject(owner)) return null;
        return owner;
    }

    private static String location(ClassNode owner, AnnotatedNode target) {
        if (target instanceof FieldNode) return owner.getName() + "." + ((FieldNode) target).getName();
        if (target instanceof MethodNode) return owner.getName() + "." + ((MethodNode) target).getName() + "()";
        return owner.getName();
    }

    private static List<AnnotationNode> requests(ClassNode type) {
        List<AnnotationNode> result = new ArrayList<>();
        for (AnnotationNode annotation : normalized(type.getAnnotations()))
            if (annotation.getClassNode().getName().equals(PropagateAnnotation.class.getName())) result.add(annotation);
        return result;
    }

    /** Unwrap only genuine repeatable containers, never arbitrary annotation-array members. */
    private static List<AnnotationNode> normalized(List<AnnotationNode> annotations) {
        List<AnnotationNode> result = new ArrayList<>();
        for (AnnotationNode annotation : annotations) {
            // Retain explicit container identity as well as its logical repeated annotations.
            result.add(annotation);
            result.addAll(repeatedAnnotations(annotation));
        }
        return result;
    }

    private static List<AnnotationNode> repeatedAnnotations(AnnotationNode container) {
        ClassNode repeated = repeatedType(container.getClassNode());
        if (repeated == null) return List.of();
        Expression value = container.getMember("value");
        if (value == null) {
            if (container.getClassNode().isResolved()) return defaultDomainTypes(container);
            MethodNode member = container.getClassNode().getMethods("value").get(0);
            if (member.getCode() instanceof ExpressionStatement)
                value = ((ExpressionStatement) member.getCode()).getExpression();
            else if (member.getCode() instanceof ReturnStatement)
                value = ((ReturnStatement) member.getCode()).getExpression();
        }
        List<AnnotationNode> result = new ArrayList<>();
        for (Expression entry : annotationValues(value)) {
            if (entry instanceof AnnotationConstantExpression) {
                AnnotationNode nested = (AnnotationNode) ((AnnotationConstantExpression) entry).getValue();
                if (nested.getClassNode().getName().equals(repeated.getName())) result.add(nested);
            }
        }
        return result;
    }

    /** Inspect only default domain types; never copy a runtime instance or its configured members. */
    private static List<AnnotationNode> defaultDomainTypes(AnnotationNode container) {
        List<AnnotationNode> result = new ArrayList<>();
        for (Method member : container.getClassNode().getTypeClass().getDeclaredMethods()) {
            if (!member.getName().equals("value")) continue;
            Object defaults = member.getDefaultValue();
            if (!(defaults instanceof Annotation[])) continue;
            for (Annotation domain : (Annotation[]) defaults) {
                AnnotationNode use = new AnnotationNode(ClassHelper.make(domain.annotationType()));
                use.setSourcePosition(container);
                result.add(use);
            }
        }
        return result;
    }

    private static List<Expression> annotationValues(Expression value) {
        if (value instanceof ListExpression) return ((ListExpression) value).getExpressions();
        if (value instanceof ArrayExpression) return ((ArrayExpression) value).getExpressions();
        return value == null ? List.of() : List.of(value);
    }

    private static ClassNode repeatedType(ClassNode container) {
        for (MethodNode method : container.getMethods("value")) {
            if (!method.getReturnType().isArray()) continue;
            ClassNode component = method.getReturnType().getComponentType();
            for (AnnotationNode marker : component.getAnnotations()) {
                if (!marker.getClassNode().getName().equals(Repeatable.class.getName())) continue;
                Expression value = marker.getMember("value");
                if (value instanceof ClassExpression && ((ClassExpression) value).getType().getName().equals(container.getName()))
                    return component;
            }
        }
        return null;
    }

    private static String illegalSelection(ClassNode selected, AnnotatedNode target) {
        if (!selected.isAnnotationDefinition()) return "selected type is not an annotation";
        if (selected.getName().equals(DSL.class.getName())) return "DSL cannot be propagated";
        if (!requests(selected).isEmpty()) return "nested propagation is not supported";
        ElementType required = target instanceof ClassNode ? ElementType.TYPE :
                target instanceof FieldNode ? ElementType.FIELD : ElementType.METHOD;
        for (AnnotationNode marker : selected.getAnnotations()) {
            if (marker.getClassNode().getName().equals(Target.class.getName())) {
                Expression value = marker.getMember("value");
                List<Expression> entries = annotationValues(value);
                if (entries.stream().noneMatch(entry -> entry.getText().endsWith("." + required.name())))
                    return "selected annotation does not permit target " + required;
            }
        }
        return missingDefaultProblem(selected);
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
