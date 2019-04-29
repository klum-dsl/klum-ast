/**
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

import com.blackbuild.groovy.configdsl.transform.ParameterAnnotation;
import com.blackbuild.klum.common.GenericsMethodBuilder;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.tools.GeneralUtils;

import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.hasAnnotation;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.notX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;

public final class DslMethodBuilder extends GenericsMethodBuilder<DslMethodBuilder> {

    public static final ClassNode CLASSLOADER_TYPE = ClassHelper.make(ClassLoader.class);
    public static final ClassNode THREAD_TYPE = ClassHelper.make(Thread.class);
    public static final ClassNode PARAMETER_ANNOTATION_TYPE = ClassHelper.make(ParameterAnnotation.class);

    private DslMethodBuilder(String name) {
        super(name);
    }

    public static DslMethodBuilder createMethod(String name) {
        return new DslMethodBuilder(name);
    }

    public static DslMethodBuilder createPublicMethod(String name) {
        return new DslMethodBuilder(name).mod(Opcodes.ACC_PUBLIC);
    }

    public static DslMethodBuilder createOptionalPublicMethod(String name) {
        return new DslMethodBuilder(name).mod(Opcodes.ACC_PUBLIC).optional();
    }

    public static DslMethodBuilder createProtectedMethod(String name) {
        return new DslMethodBuilder(name).mod(Opcodes.ACC_PROTECTED);
    }

    public static DslMethodBuilder createPrivateMethod(String name) {
        return new DslMethodBuilder(name).mod(Opcodes.ACC_PRIVATE);
    }


    public DslMethodBuilder withoutMutatorCheck() {
        metadata.put(DSLASTTransformation.NO_MUTATION_CHECK_METADATA_KEY, Boolean.TRUE);
        return this;
    }

    public DslMethodBuilder callValidationOn(String target) {
        return callValidationMethodOn(varX(target));
    }

    private DslMethodBuilder callValidationMethodOn(Expression targetX) {
        return statement(ifS(notX(propX(targetX,"$manualValidation")), callX(targetX, DSLASTTransformation.VALIDATE_METHOD)));
    }

    public DslMethodBuilder setOwners(String target) {
        return callMethod(varX(target), DSLASTTransformation.SET_OWNERS_METHOD, varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS));
    }

    public DslMethodBuilder params(Parameter... params) {
        for (Parameter param : params) {
            param(param);
        }
        return this;
    }

    public DslMethodBuilder params(List<Parameter>params) {
        for (Parameter param : params) {
            param(param);
        }
        return this;
    }

    public DslMethodBuilder decoratedParam(FieldNode field, ClassNode type, String name) {

        Parameter param = GeneralUtils.param(type, name);

        List<AnnotationNode> annotations = field.getAnnotations();

        for (AnnotationNode annotation : annotations)
            if (hasAnnotation(annotation.getClassNode(), PARAMETER_ANNOTATION_TYPE))
                copyAnnotationsFromMembersToParam(annotation, param);

        return param(param);
    }

    public void copyAnnotationsFromMembersToParam(AnnotationNode source, AnnotatedNode target) {
        for (Expression annotationMember : source.getMembers().values()) {
            if (annotationMember instanceof AnnotationConstantExpression) {
                AnnotationNode annotationNode = (AnnotationNode) ((AnnotationConstantExpression) annotationMember).getValue();
                if (annotationNode.isTargetAllowed(AnnotationNode.PARAMETER_TARGET))
                    target.addAnnotation(annotationNode);
            }
        }
    }

    public DslMethodBuilder optionalClassLoaderParam() {
        return param(CLASSLOADER_TYPE, "loader", callX(callX(THREAD_TYPE, "currentThread"), "getContextClassLoader"));
    }

}
