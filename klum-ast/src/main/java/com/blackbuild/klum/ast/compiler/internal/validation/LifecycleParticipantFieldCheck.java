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

import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.layer3.AutoLink;
import com.blackbuild.klum.ast.runtime.LifecycleCreator;
import com.blackbuild.klum.ast.runtime.LifecycleMutator;
import com.blackbuild.klum.ast.runtime.LifecycleCreationHandler;
import com.blackbuild.klum.ast.runtime.LifecycleMutationHandler;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.SyntaxException;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getFieldType;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.isDSLObject;

/** LP-1 checks on original Schema declarations, before Builder projection. */
public final class LifecycleParticipantFieldCheck {
    private LifecycleParticipantFieldCheck() {}

    public static void checkUnsupportedPlacements(ClassNode schema, SourceUnit source) {
        checkUnsupportedPlacement(schema, source);
        schema.getMethods().stream().filter(method -> method.getDeclaringClass().equals(schema))
                .forEach(method -> checkUnsupportedPlacement(method, source));
    }

    private static void checkUnsupportedPlacement(AnnotatedNode declaration, SourceUnit source) {
        for (AnnotationNode use : declaration.getAnnotations()) {
            ClassNode domain = use.getClassNode();
            if (!domain.getAnnotations(ClassHelper.make(LifecycleCreator.class)).isEmpty()
                    || !domain.getAnnotations(ClassHelper.make(LifecycleMutator.class)).isEmpty())
                error(source, use, "LP-1 lifecycle participants require direct Schema field placement");
        }
    }

    public static void check(FieldNode field, SourceUnit source) {
        for (AnnotationNode use : field.getAnnotations()) {
            ClassNode domain = use.getClassNode();
            checkRole(field, source, use, domain, LifecycleCreator.class, LifecycleCreationHandler.class);
            checkRole(field, source, use, domain, LifecycleMutator.class, LifecycleMutationHandler.class);
        }
    }

    private static void checkRole(FieldNode field, SourceUnit source, AnnotationNode use, ClassNode domain,
                                  Class<?> markerType, Class<?> handlerRole) {
        for (AnnotationNode marker : domain.getAnnotations(ClassHelper.make(markerType))) {
            if (Modifier.isStatic(field.getModifiers()) || !isDSLObject(field.getType())
                    || getFieldType(field) == FieldType.BUILDER) {
                error(source, use, "Lifecycle participant on " + field.getName()
                        + " requires a non-static direct DSL field retained on the Schema (LP-1)");
            }
            if (!hasRuntimeRetention(domain))
                error(source, use, "Lifecycle participant annotation " + domain.getName() + " requires @Retention(RUNTIME)");
            if (!(marker.getMember("phase") instanceof ClassExpression phase)
                    || !phase.getType().equals(ClassHelper.make(AutoLink.class)))
                error(source, use, "LP-1 lifecycle participants support only phase = AutoLink");
            if (!(marker.getMember("handler") instanceof ClassExpression expression)) {
                error(source, use, "Lifecycle participant handler must be a class literal");
                continue;
            }
            ClassNode handler = expression.getType();
            if (!Modifier.isPublic(handler.getModifiers()) || handler.isAbstract() || handler.isInterface()
                    || (handler.getOuterClass() != null && !Modifier.isStatic(handler.getModifiers()))
                    || (!handler.getDeclaredConstructors().isEmpty() && handler.getDeclaredConstructors().stream()
                    .noneMatch(ctor -> Modifier.isPublic(ctor.getModifiers()) && ctor.getParameters().length == 0)))
                error(source, use, "Lifecycle participant handler " + handler.getName() + " requires a public concrete class and public no-arg constructor");
            ClassNode parameter = annotationParameter(handler, handlerRole.getName(), Map.of());
            if (parameter == null || !parameter.equals(domain))
                error(source, use, "Lifecycle participant handler " + handler.getName() + " annotation parameter must resolve exactly to "
                        + domain.getName() + "; raw, wildcard, unresolved and mismatched parameters are unsupported");
        }
    }

    private static ClassNode annotationParameter(ClassNode type, String role, Map<String, GenericsType> incoming) {
        GenericsType[] arguments = type.getGenericsTypes();
        if (type.getName().equals(role)) {
            if (arguments == null || arguments.length != 1) return null;
            GenericsType resolved = resolve(arguments[0], incoming);
            return resolved == null || resolved.isWildcard() || resolved.isPlaceholder()
                    || resolved.getType().isGenericsPlaceHolder() ? null : resolved.getType();
        }
        Map<String, GenericsType> bindings = new HashMap<>();
        GenericsType[] variables = type.redirect().getGenericsTypes();
        if (variables != null && arguments != null && variables.length == arguments.length) {
            for (int i = 0; i < variables.length; i++) {
                GenericsType resolved = resolve(arguments[i], incoming);
                if (resolved != null) bindings.put(variables[i].getName(), resolved);
            }
        }
        for (ClassNode parent : type.getInterfaces()) {
            ClassNode result = annotationParameter(parent, role, bindings);
            if (result != null) return result;
        }
        ClassNode parent = type.getUnresolvedSuperClass();
        return parent == null ? null : annotationParameter(parent, role, bindings);
    }

    private static GenericsType resolve(GenericsType argument, Map<String, GenericsType> bindings) {
        if (!argument.isPlaceholder() && !argument.getType().isGenericsPlaceHolder()) return argument;
        GenericsType bound = bindings.get(argument.getName());
        return bound == argument ? null : bound;
    }

    private static boolean hasRuntimeRetention(ClassNode domain) {
        if (domain.isResolved()) {
            Retention retention = (Retention) domain.getTypeClass().getAnnotation(Retention.class);
            return retention != null && retention.value() == RetentionPolicy.RUNTIME;
        }
        return domain.getAnnotations(ClassHelper.make(Retention.class)).stream()
                .map(annotation -> annotation.getMember("value"))
                .anyMatch(value -> value != null && value.getText().endsWith("RUNTIME"));
    }

    private static void error(SourceUnit source, AnnotationNode use, String message) {
        source.addError(new SyntaxException(message, use.getLineNumber(), use.getColumnNumber()));
    }
}
