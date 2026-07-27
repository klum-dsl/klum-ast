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

import com.blackbuild.klum.cast.spi.Check;
import com.blackbuild.klum.cast.spi.CheckContext;
import com.blackbuild.klum.cast.spi.Diagnostic;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;

import java.lang.reflect.Modifier;
import java.util.List;

public class ValidateAnnotationCheck implements Check {
    @Override
    public List<Diagnostic> check(CheckContext context) {
        AnnotationNode annotationToCheck = context.getValidatedAnnotation();
        AnnotatedNode target = context.getTarget();
        if (target instanceof ClassNode) {
            if (target instanceof InnerClassNode innerClass) return checkOnInnerClass(innerClass, annotationToCheck);
            return checkOnOuterClass(annotationToCheck);
        } else if (target instanceof MethodNode) {
            return checkOnMethod((MethodNode) target, annotationToCheck);
        } else if (target instanceof FieldNode) {
            return checkOnField((FieldNode) target, annotationToCheck);
        } else {
            return violation("@Validate can only be used on (inner) classes, methods or fields!", annotationToCheck);
        }
    }

    private List<Diagnostic> checkOnField(FieldNode target, AnnotationNode annotationToCheck) {
        if (target.isStatic())
            return violation("@Validate can only be used on non-static fields!", annotationToCheck);
        return List.of();
    }

    private List<Diagnostic> checkOnMethod(MethodNode target, AnnotationNode annotationToCheck) {
        if (target.isStatic())
            return violation("@Validate can only be used on non-static methods!", annotationToCheck);
        return List.of();
    }

    private List<Diagnostic> checkOnOuterClass(AnnotationNode annotationToCheck) {
        if (annotationToCheck.getMember("level") != null)
            return violation("@Validate.level is not allowed on top level classes!", annotationToCheck);
        return List.of();
    }

    private List<Diagnostic> checkOnInnerClass(InnerClassNode target, AnnotationNode annotationToCheck) {
        if ((target.getModifiers() & Opcodes.ACC_STATIC) != 0)
            return violation("@Validate can only be used on non-static inner classes!", annotationToCheck);
        List<ConstructorNode> constructors = target.getDeclaredConstructors();

        if (!Modifier.isAbstract(target.getModifiers())) {
            if (constructors.size() > 1)
                return violation("@Validate can only be used on inner classes with a maximum of one constructor!", constructors.get(1));

            if (constructors.size() == 1 && constructors.get(0).getParameters().length > 0)
                return violation("@Validate can only be used on inner classes with a no-argument constructor!", constructors.get(0));
        }
        return List.of();
    }

    private List<Diagnostic> violation(String message, ASTNode node) {
        return List.of(new Diagnostic(getClass().getName(), message, node));
    }
}
