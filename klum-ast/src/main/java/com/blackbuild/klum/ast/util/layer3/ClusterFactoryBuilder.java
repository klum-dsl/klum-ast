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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.groovy.configdsl.transform.ast.AbstractFactoryBuilder;
import com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper;
import com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder;
import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.runtime.StringGroovyMethods;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createOptionalPublicMethod;
import static com.blackbuild.klum.ast.util.layer3.ClusterTransformation.CLUSTER_ANNOTATION_TYPE;
import static com.blackbuild.klum.common.CommonAstHelper.*;
import static groovyjarjarasm.asm.Opcodes.ACC_PROTECTED;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static java.lang.String.format;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;

public class ClusterFactoryBuilder extends AbstractFactoryBuilder {

    public static final String BOUNDED_MEMBER = "bounded";
    private final MethodNode clusterField;
    private final String fieldName;
    private final AnnotationNode clusterAnnotation;
    private final boolean bounded;

    public ClusterFactoryBuilder(ClassNode targetClass, MethodNode clusterField) {
        super(targetClass);
        this.clusterField = clusterField;

        if (clusterField.getName().startsWith("get"))
            fieldName = StringGroovyMethods.uncapitalize(clusterField.getName().substring(3));
        else
            fieldName = clusterField.getName();

        clusterAnnotation = getAnnotation(clusterField, CLUSTER_ANNOTATION_TYPE);

        bounded = isBounded();
    }

    private boolean isBounded() {
        ConstantExpression boundedMember = (ConstantExpression) clusterAnnotation.getMember(BOUNDED_MEMBER);
        if (boundedMember != null)
            return (boolean) boundedMember.getValue();
        for (ClassNode layer : DslAstHelper.getHierarchyOfDSLObjectAncestors(targetClass)) {
            AnnotationNode layerAnnotation = getAnnotation(layer, CLUSTER_ANNOTATION_TYPE);
            if (layerAnnotation != null)
                return (boolean) ((ConstantExpression) layerAnnotation.getMember(BOUNDED_MEMBER)).getValue();
            AnnotationNode packageAnnotation = getAnnotation(layer.getModule().getPackage(), CLUSTER_ANNOTATION_TYPE);
            if (packageAnnotation != null)
                return (boolean) ((ConstantExpression) packageAnnotation.getMember(BOUNDED_MEMBER)).getValue();
        }
        return false;
    }

    @Override
    public void invoke() {
        ClassNode requiredAnnotation = getNullSafeClassMember(clusterAnnotation, "value", null);
        Predicate<FieldNode> annotationFilter = requiredAnnotation != null ? fieldNode -> DslAstHelper.hasAnnotation(fieldNode, requiredAnnotation) : fieldNode -> true;
        ClassNode elementType = getElementTypeForMap(clusterField.getReturnType());

        List<FieldNode> fieldsToInclude = DslAstHelper.getFieldsOfDslHierarchy(targetClass)
                .filter(field -> CommonAstHelper.isAssignableTo(field.getType(), elementType))
                .filter(annotationFilter).collect(Collectors.toList());

        if (fieldsToInclude.isEmpty()) return;

        createInnerClass(fieldName);
        for (FieldNode fieldNode : fieldsToInclude)
            addMethodsForField(fieldNode);

        createClosureForOuterClass();
    }

    private void createClosureForOuterClass() {
        String closureVarName = "closure";
        String closureVarDescription = "The closure to handle the creation/setting of the instances.";
        createOptionalPublicMethod(fieldName)
                .linkToField(clusterField)
                .delegatingClosureParam(collectionFactory, MethodBuilder.ClosureDefaultValue.NONE)
                .assignS(propX(varX(closureVarName), "delegate"), ctorX(collectionFactory, args("this")))
                .assignS(
                        propX(varX(closureVarName), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_ONLY")
                )
                .callMethod(classX(BreadcrumbCollector.class), "withBreadcrumb",
                        args(constX(fieldName), constX(null), constX(null), varX(closureVarName))
                )
                .withDocumentation(doc -> doc
                        .title(format("Handles the creation/setting of named instances of %s.", fieldName))
                        .param(closureVarName, closureVarDescription)
                )
                .addTo(rwClass);
    }


    private void addMethodsForField(FieldNode fieldNode) {
        rwClass.getAllDeclaredMethods().stream()
                .filter(methodNode -> methodNode.getName().equals(fieldNode.getName()))
                .forEach(this::handleMethod);
    }

    private void handleMethod(MethodNode method) {
        createDelegateMethods(method);
        if (bounded)
            method.setModifiers(method.getModifiers() & ~ACC_PUBLIC | ACC_PROTECTED);
    }
}
