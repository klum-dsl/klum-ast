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
package com.blackbuild.klum.ast.validation;

import com.blackbuild.klum.cast.checks.impl.KlumCastCheck;
import com.blackbuild.klum.cast.checks.impl.ValidationException;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.List;

public class ValidateAnnotationCheck extends KlumCastCheck<Annotation> {
    @Override
    protected void doCheck(AnnotationNode annotationToCheck, AnnotatedNode target) throws ValidationException {
        if (target instanceof ClassNode) {
            if (target instanceof InnerClassNode) checkOnInnerClass((InnerClassNode) target);
            else checkOnOuterClass(annotationToCheck);
        } else if (target instanceof MethodNode) {
            checkOnMethod((MethodNode) target);
        } else if (target instanceof FieldNode) {
            checkOnField((FieldNode) target);
        } else {
            throw new ValidationException("@Validate can only be used on (inner) classes, methods or fields!");
        }
    }

    private void checkOnField(FieldNode target) throws ValidationException {
        if (target.isStatic())
            throw new ValidationException("@Validate can only be used on non-static fields!");
    }

    private void checkOnMethod(MethodNode target) throws ValidationException {
        if (target.isStatic())
            throw new ValidationException("@Validate can only be used on non-static methods!");
    }

    private void checkOnOuterClass(AnnotationNode annotationToCheck) throws ValidationException {
        if (annotationToCheck.getMember("level") != null)
            throw new ValidationException("@Validate.level is not allowed on top level classes!");
    }

    private void checkOnInnerClass(InnerClassNode target) throws ValidationException {
        if ((target.getModifiers() & Opcodes.ACC_STATIC) != 0)
            throw new ValidationException("@Validate can only be used on non-static inner classes!");
        List<ConstructorNode> constructors = target.getDeclaredConstructors();

        if (!Modifier.isAbstract(target.getModifiers())) {
            if (constructors.size() > 1)
                throw new ValidationException("@Validate can only be used on inner classes with a maximum of one constructor!", constructors.get(1));

            if (constructors.size() == 1 && constructors.get(0).getParameters().length > 0)
                throw new ValidationException("@Validate can only be used on inner classes with a no-argument constructor!", constructors.get(0));
        }
    }
}
