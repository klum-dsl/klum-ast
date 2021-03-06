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

import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.tools.GenericsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createMethod;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.getAllMethods;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.addMethodGenerics;
import static org.codehaus.groovy.ast.tools.GenericsUtils.correctToGenericsSpecRecurse;
import static org.codehaus.groovy.ast.tools.GenericsUtils.createGenericsSpec;
import static org.codehaus.groovy.ast.tools.GenericsUtils.extractSuperClassGenerics;

// Heavily copied from DelegateASTTransformation
class DelegateFromRwToModel {

    private final static List<String> IGNORED_FIELDS_FOR_RW_TO_MODEL_DELEGATION =
            Arrays.asList("canEqual", "methodMissing", "propertyMissing");


    private ClassNode annotatedClass;
    private ClassNode rwClass;

    DelegateFromRwToModel(ClassNode annotatedClass) {
        this.annotatedClass = annotatedClass;
        this.rwClass = DslAstHelper.getRwClassOf(annotatedClass);
    }

    void invoke() {
        for (MethodNode method : annotatedClass.getMethods()) {
            if (method.isStatic()) continue;
            if ((method.getModifiers() & Opcodes.ACC_SYNTHETIC) != 0) continue;
            if (method.getName().contains("$")) continue;
            if (IGNORED_FIELDS_FOR_RW_TO_MODEL_DELEGATION.contains(method.getName())) continue;
            if (method.isPrivate()) continue;

            delegateMethodToRw(method);
        }
    }

    private void delegateMethodToRw(MethodNode candidate) {
        Map<String, ClassNode> genericsSpec = createGenericsSpec(rwClass);
        genericsSpec = addMethodGenerics(candidate, genericsSpec);
        extractSuperClassGenerics(annotatedClass, candidate.getDeclaringClass(), genericsSpec);

        // ignore methods already in owner
        for (MethodNode mn : getAllMethods(rwClass)) {
            if (mn.getTypeDescriptor().equals(candidate.getTypeDescriptor())) {
                return;
            }
        }

        final Parameter[] params = candidate.getParameters();
        final Parameter[] newParams = new Parameter[params.length];

        List<String> currentMethodGenPlaceholders = genericPlaceholderNames(candidate);
        for (int i = 0; i < newParams.length; i++) {
            ClassNode newParamType = correctToGenericsSpecRecurse(genericsSpec, params[i].getType(), currentMethodGenPlaceholders);
            Parameter newParam = new Parameter(newParamType, params[i].getName());
            newParam.setInitialExpression(params[i].getInitialExpression());

            newParams[i] = newParam;
        }

        MethodNode newMethod = createMethod(candidate.getName())
                .optional()
                .mod(candidate.getModifiers() & (~Opcodes.ACC_ABSTRACT) & (~Opcodes.ACC_NATIVE))
                .returning(correctToGenericsSpecRecurse(genericsSpec, candidate.getReturnType(), currentMethodGenPlaceholders))
                .params(newParams)
                .callMethod(varX(DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS, GenericsUtils.correctToGenericsSpecRecurse(genericsSpec, annotatedClass)),
                        candidate.getName(),
                        args(newParams))
                .addTo(rwClass);

        newMethod.setGenericsTypes(candidate.getGenericsTypes());
    }

    private List<String> genericPlaceholderNames(MethodNode candidate) {
        GenericsType[] candidateGenericsTypes = candidate.getGenericsTypes();
        List<String> names = new ArrayList<String>();
        if (candidateGenericsTypes != null) {
            for (GenericsType gt : candidateGenericsTypes) {
                names.add(gt.getName());
            }
        }
        return names;
    }
}
