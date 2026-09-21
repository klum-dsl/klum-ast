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
package com.blackbuild.klum.ast.compiler.internal.ast.mutators;

import com.blackbuild.klum.ast.Builder;
import com.blackbuild.klum.ast.Mutator;
import com.blackbuild.klum.ast.WriteAccess;
import com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;

import java.util.Objects;
import java.util.Optional;

import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getClassFromClassLoader;

public class WriteAccessHelper {

    private static final ClassNode WRITE_ACCESS_ANNOTATION = ClassHelper.make(WriteAccess.class);
    private static final ClassNode BUILDER_METHOD_ANNOTATION = ClassHelper.make(Builder.Method.class);
    @SuppressWarnings("deprecation") // @Mutator is intentionally classified throughout its 4.1 compatibility window.
    private static final ClassNode MUTATOR_ANNOTATION = ClassHelper.make(Mutator.class);

    private WriteAccessHelper() {
        // helper class
    }

    public static Optional<WriteAccess.Type> getWriteAccessTypeForMethodOrField(AnnotatedNode fieldOrMethod) {
        if (fieldOrMethod == null) return Optional.empty();
        return fieldOrMethod.getAnnotations().stream()
                .map(WriteAccessHelper::getWriteAccessTypeForAnnotation)
                .filter(Objects::nonNull)
                .findAny();
    }

    public static boolean isManualWriteAccess(AnnotatedNode fieldOrMethod) {
        return getWriteAccessTypeForMethodOrField(fieldOrMethod)
                .filter(type -> type == WriteAccess.Type.MANUAL)
                .isPresent();
    }

    public static boolean isBuilderMethod(AnnotatedNode method) {
        return hasAnnotation(method, BUILDER_METHOD_ANNOTATION) || hasAnnotation(method, MUTATOR_ANNOTATION);
    }

    public static boolean hasConflictingBuilderMethodAnnotations(AnnotatedNode method) {
        return hasAnnotation(method, BUILDER_METHOD_ANNOTATION) && hasAnnotation(method, MUTATOR_ANNOTATION);
    }

    static boolean isCanonicalBuilderMethodAnnotation(AnnotationNode annotation) {
        return annotation != null && annotation.getClassNode().getName().equals(BUILDER_METHOD_ANNOTATION.getName());
    }

    private static boolean hasAnnotation(AnnotatedNode node, ClassNode annotationType) {
        return node != null && !node.getAnnotations(annotationType).isEmpty();
    }

    private static WriteAccess.Type getWriteAccessTypeForAnnotation(AnnotationNode annotation) {
        if (!DslAstHelper.hasAnnotation(annotation.getClassNode(), WRITE_ACCESS_ANNOTATION)) return null;

        // We need to use the class explicitly, since we cannot access the members of metaAnnotations directly
        // This is safe, since annotations are in a different module and thus already compiled
        Class<?> annotationClass = getClassFromClassLoader(annotation.getClassNode(), WriteAccess.class);
        return annotationClass.getAnnotation(WriteAccess.class).value();
    }
}
