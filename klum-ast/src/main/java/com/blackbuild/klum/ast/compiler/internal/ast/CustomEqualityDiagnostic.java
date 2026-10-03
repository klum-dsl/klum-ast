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

import com.blackbuild.klum.ast.FieldType;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.FieldExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.control.SourceUnit;

import java.util.LinkedHashSet;
import java.util.Set;

import static com.blackbuild.klum.ast.compiler.internal.ast.DSLASTTransformation.EQUALS_HASHCODE_ANNOT;
import static com.blackbuild.klum.ast.compiler.internal.ast.DSLASTTransformation.OWNER_ANNOTATION;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getBooleanGetterName;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getFieldType;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getGetterName;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.hasAnnotation;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.addCompileWarning;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getAnnotation;
import static groovyjarjarasm.asm.Opcodes.ACC_TRANSIENT;
import static org.apache.groovy.ast.tools.AnnotatedNodeUtils.isGenerated;

/** Diagnoses actual Groovy-generated selection after both canonicalization transforms have run. */
public final class CustomEqualityDiagnostic extends CodeVisitorSupport {
    private final ClassNode model;
    private final Set<String> selectedManagedFields = new LinkedHashSet<>();

    private CustomEqualityDiagnostic(ClassNode model) {
        this.model = model;
    }

    public static void warn(SourceUnit source, ClassNode model) {
        AnnotationNode annotation = getAnnotation(model, EQUALS_HASHCODE_ANNOT);
        if (annotation == null || annotation.getMember("includes") != null) return;

        CustomEqualityDiagnostic diagnostic = new CustomEqualityDiagnostic(model);
        for (MethodNode method : model.getMethods()) {
            if (method.getDeclaringClass() != model || !isGenerated(method) || method.getLineNumber() >= 0) continue;
            boolean equals = method.getName().equals("equals") && method.getParameters().length == 1;
            boolean hashCode = method.getName().equals("hashCode") && method.getParameters().length == 0;
            if ((equals || hashCode) && method.getCode() != null)
                method.getCode().visit(diagnostic);
        }
        if (!diagnostic.selectedManagedFields.isEmpty())
            addCompileWarning(source,
                    "Custom @EqualsAndHashCode on '" + model.getName() + "' includes Klum-managed non-semantic fields: "
                            + String.join(", ", diagnostic.selectedManagedFields)
                            + ". Use explicit excludes or deliberate includes to choose equality state.", annotation);
    }

    private void record(String name) {
        // Groovy's own synthetic hash cache is deliberate support for cache=true, not Klum state.
        FieldNode field = model.getDeclaredField(name);
        if (field != null && !field.isStatic() && ((field.getName().startsWith("$")
                && (field.getLineNumber() >= 0 || field.getName().equals("$state")))
                || (field.getModifiers() & ACC_TRANSIENT) != 0 || getFieldType(field) == FieldType.TRANSIENT
                || hasAnnotation(field, OWNER_ANNOTATION)))
            selectedManagedFields.add(name);
    }

    @Override
    public void visitVariableExpression(VariableExpression expression) {
        if (expression.getAccessedVariable() instanceof FieldNode)
            record(expression.getName());
        super.visitVariableExpression(expression);
    }

    @Override
    public void visitFieldExpression(FieldExpression expression) {
        record(expression.getFieldName());
        super.visitFieldExpression(expression);
    }

    @Override
    public void visitPropertyExpression(PropertyExpression expression) {
        record(expression.getPropertyAsString());
        super.visitPropertyExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(MethodCallExpression expression) {
        // Groovy 3/4/5 emit getter calls for declared properties (including boolean is-getters).
        String method = expression.getMethodAsString();
        for (FieldNode field : model.getFields()) {
            String name = field.getName();
            if (getGetterName(name).equals(method) || getBooleanGetterName(name).equals(method))
                record(name);
        }
        super.visitMethodCallExpression(expression);
    }
}
