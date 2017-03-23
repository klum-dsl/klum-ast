/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform.ast;

import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.SourceUnit;

import java.util.HashSet;
import java.util.Set;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.*;

/**
 * Helper class to create lifecycle methods for a given annotation
 */
class LifecycleMethodBuilder {
    private ClassNode annotationType;
    private MethodBuilder lifecycleMethod;
    private Set<String> alreadyHandled = new HashSet<String>();
    private ClassNode annotatedClass;
    private SourceUnit sourceUnit;

    LifecycleMethodBuilder(ClassNode annotatedClass, ClassNode annotationType, SourceUnit sourceUnit) {
        this.annotationType = annotationType;
        this.annotatedClass = annotatedClass;
        this.sourceUnit = annotatedClass.getModule().getContext();
    }

    void invoke() {
        lifecycleMethod = MethodBuilder.createPrivateMethod("$" + annotationType.getNameWithoutPackage());
        for (ClassNode level : getHierarchyOfDSLObjectAncestors(annotatedClass)) {
            addLifecycleMethodsForClass(level);
        }
        lifecycleMethod.addTo(annotatedClass);
    }

    private void addLifecycleMethodsForClass(ClassNode level) {
        for (MethodNode method : level.getMethods()) {
            AnnotationNode postApplyAnnotation = getAnnotation(method, annotationType);

            if (postApplyAnnotation == null)
                continue;

            assertMethodIsParameterless(method, sourceUnit);
            assertMethodIsNotPrivate(method, sourceUnit);

            addCallTo(method.getName());
        }
    }

    private void addCallTo(String method) {
        if (!alreadyHandled.contains(method)) {
            lifecycleMethod.callThis(method);
            alreadyHandled.add(method);
        }
    }
}
