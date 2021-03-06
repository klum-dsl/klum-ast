/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2019 Stephan Pauxberger
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

import com.blackbuild.klum.common.CommonAstHelper;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.control.SourceUnit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getHierarchyOfDSLObjectAncestors;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.moveMethodFromModelToRWClass;
import static groovyjarjarasm.asm.Opcodes.ACC_PROTECTED;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;

/**
 * Helper class to create lifecycle methods for a given annotation
 */
class LifecycleMethodBuilder {
    private InnerClassNode rwClass;
    private ClassNode annotationType;
    private DslMethodBuilder lifecycleMethod;
    private Set<String> alreadyHandled = new HashSet<String>();
    private ClassNode annotatedClass;
    private SourceUnit sourceUnit;

    LifecycleMethodBuilder(InnerClassNode rwClass, ClassNode annotationType) {
        this.rwClass = rwClass;
        this.annotationType = annotationType;
        this.annotatedClass = rwClass.getOuterClass();
        this.sourceUnit = annotatedClass.getModule().getContext();
    }

    void invoke() {
        createLifecycleControlField();
        moveMethodsFromModelToRWClass();
        createLifecycleCallerMethod();
    }

    private void createLifecycleControlField() {
        rwClass.addField(
                "$skip" + annotationType.getNameWithoutPackage(),
                ACC_SYNTHETIC | ACC_PUBLIC,
                ClassHelper.boolean_TYPE, constX(false)
                );
    }

    private void createLifecycleCallerMethod() {
        lifecycleMethod = DslMethodBuilder
                .createPrivateMethod("$" + annotationType.getNameWithoutPackage())
                .mod(Opcodes.ACC_SYNTHETIC)
                .statement(ifS(
                        varX("$skip" + annotationType.getNameWithoutPackage()),
                        ReturnStatement.RETURN_NULL_OR_VOID
                ));
        for (ClassNode level : getHierarchyOfDSLObjectAncestors(annotatedClass)) {
            addLifecycleMethodsForClass(level);
        }
        lifecycleMethod.addTo(rwClass);
    }

    private void addLifecycleMethodsForClass(ClassNode level) {
        ClassNode rwLevel = level.getNodeMetaData(DSLASTTransformation.RWCLASS_METADATA_KEY);
        List<MethodNode> lifecycleMethods = getAllValidLifecycleMethods(level);
        // lifecycle methods form parent classes have already been removed, so
        // we take the lifecycle methods from RW class as well
        lifecycleMethods.addAll(getAllValidLifecycleMethods(rwLevel));
        addCallToAllMethods(lifecycleMethods);
    }

    private List<MethodNode> getAllValidLifecycleMethods(ClassNode level) {
        if (level == null)
            return Collections.emptyList();

        List<MethodNode> lifecycleMethods = new ArrayList<MethodNode>();

        for (MethodNode method : level.getMethods()) {
            AnnotationNode targetAnnotation = CommonAstHelper.getAnnotation(method, annotationType);

            if (targetAnnotation == null)
                continue;

            CommonAstHelper.assertMethodIsParameterless(method, sourceUnit);
            CommonAstHelper.assertMethodIsNotPrivate(method, sourceUnit);

            lifecycleMethods.add(method);
        }
        return lifecycleMethods;
    }

    private void moveMethodsFromModelToRWClass() {
        List<MethodNode> lifecycleMethods = getAllValidLifecycleMethods(annotatedClass);
        for (MethodNode method : lifecycleMethods) {
            moveMethodFromModelToRWClass(method);
            int modifiers = method.getModifiers() & ~ACC_PUBLIC | ACC_PROTECTED;
            method.setModifiers(modifiers);
        }
    }

    private void addCallToAllMethods(List<MethodNode> lifecycleMethods) {
        for (MethodNode method : lifecycleMethods) {
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
