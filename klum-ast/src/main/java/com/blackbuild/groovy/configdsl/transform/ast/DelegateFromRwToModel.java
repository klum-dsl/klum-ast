/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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

import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.codehaus.groovy.ast.tools.GeneralUtils.getAllMethods;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.*;

// Heavily copied from DelegateASTTransformation
class DelegateFromRwToModel {

    private static final List<String> IGNORED_FIELDS_FOR_RW_TO_MODEL_DELEGATION =
            Arrays.asList("canEqual", "methodMissing", "propertyMissing");

    private static final List<ClassNode> IGNORED_ANNOTATIONS_FOR_RW_TO_MODEL_DELEGATION =
            List.of(
                    ClassHelper.make(Override.class),
                    DslAstHelper.KLUM_GENERATED_CLASSNODE);

    private final ClassNode annotatedClass;
    private final ClassNode rwClass;

    DelegateFromRwToModel(ClassNode annotatedClass) {
        this.annotatedClass = annotatedClass;
        this.rwClass = DslAstHelper.getRwClassOf(annotatedClass);
    }

    void invoke() {
        annotatedClass.getMethods().stream()
                .filter(method -> !method.isStatic())
                .filter(method -> (method.getModifiers() & Opcodes.ACC_SYNTHETIC) == 0)
                .filter(method -> !method.getName().contains("$"))
                .filter(method -> !IGNORED_FIELDS_FOR_RW_TO_MODEL_DELEGATION.contains(method.getName()))
                .filter(method -> !method.isPrivate())
                .forEach(this::delegateMethodToRw);
    }

    private void delegateMethodToRw(MethodNode candidate) {
        Map<String, ClassNode> genericsSpec = createGenericsSpec(rwClass);
        genericsSpec = addMethodGenerics(candidate, genericsSpec);
        extractSuperClassGenerics(annotatedClass, candidate.getDeclaringClass(), genericsSpec);

        if (matchingMethodAlreadyExists(candidate)) return;

        final Parameter[] params = candidate.getParameters();
        final Parameter[] newParams = new Parameter[params.length];

        List<String> currentMethodGenPlaceholders = genericPlaceholderNames(candidate);
        for (int i = 0; i < newParams.length; i++) {
            ClassNode newParamType = correctToGenericsSpecRecurse(genericsSpec, params[i].getType(), currentMethodGenPlaceholders);
            Parameter newParam = new Parameter(newParamType, params[i].getName());
            newParam.setInitialExpression(params[i].getInitialExpression());

            newParams[i] = newParam;
        }

        MethodNode newMethod = new ProxyMethodBuilder(
                varX(DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS, correctToGenericsSpecRecurse(genericsSpec, annotatedClass)),
                candidate.getName(),
                candidate.getName()
        )
                .optional()
                .targetType(correctToGenericsSpecRecurse(genericsSpec, annotatedClass))
                .setGenericsTypes(candidate.getGenericsTypes())
                .mod(candidate.getModifiers() & ~Opcodes.ACC_ABSTRACT & ~Opcodes.ACC_NATIVE)
                .returning(correctToGenericsSpecRecurse(genericsSpec, candidate.getReturnType(), currentMethodGenPlaceholders))
                .params(newParams)
                .addTo(rwClass);

        if (newMethod != null) {
            DslAstHelper.copyAnnotationsFromSourceToTarget(candidate, newMethod, IGNORED_ANNOTATIONS_FOR_RW_TO_MODEL_DELEGATION);
        }
    }

    private boolean matchingMethodAlreadyExists(MethodNode candidate) {
        String candidateTypeDescriptor = candidate.getTypeDescriptor();
        return getAllMethods(rwClass)
                .stream()
                .map(MethodNode::getTypeDescriptor)
                .anyMatch(candidateTypeDescriptor::equals);
    }

    private List<String> genericPlaceholderNames(MethodNode candidate) {
        GenericsType[] candidateGenericsTypes = candidate.getGenericsTypes();
        List<String> names = new ArrayList<>();
        if (candidateGenericsTypes != null) {
            for (GenericsType gt : candidateGenericsTypes) {
                names.add(gt.getName());
            }
        }
        return names;
    }
}
