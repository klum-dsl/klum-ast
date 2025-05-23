/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import com.blackbuild.groovy.configdsl.transform.WriteAccess;
import com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;

import java.util.Objects;
import java.util.Optional;

import static com.blackbuild.klum.common.CommonAstHelper.getClassFromClassLoader;

public class WriteAccessHelper {

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

    private static WriteAccess.Type getWriteAccessTypeForAnnotation(AnnotationNode annotation) {
        if (!DslAstHelper.hasAnnotation(annotation.getClassNode(), WriteAccessMethodsMover.WRITE_ACCESS_ANNOTATION)) return null;

        // We need to use the class explicitly, since we cannot access the members of metaAnnotations directly
        // This is safe, since annotations are in a different module and thus already compiled
        Class<?> annotationClass = getClassFromClassLoader(annotation.getClassNode(), WriteAccess.class);
        return annotationClass.getAnnotation(WriteAccess.class).value();
    }
}
