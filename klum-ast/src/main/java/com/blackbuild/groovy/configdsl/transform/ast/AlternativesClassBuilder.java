/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;

import java.beans.Introspector;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.COLLECTION_FACTORY_METADATA_KEY;
import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_CONFIG_ANNOTATION;
import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_FIELD_ANNOTATION;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.createDelegateMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getElementNameForCollectionField;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getHierarchyOfDSLObjectAncestors;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getKeyType;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getRwClassOf;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createOptionalPublicMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createPublicMethod;
import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_FINAL;
import static groovyjarjarasm.asm.Opcodes.ACC_PRIVATE;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC;
import static org.codehaus.groovy.ast.ClassHelper.MAP_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callThisX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.closureX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.param;
import static org.codehaus.groovy.ast.tools.GeneralUtils.params;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.newClass;
import static org.codehaus.groovy.transform.AbstractASTTransformation.getMemberStringValue;

/**
 * Created by steph on 29.04.2017.
 */
class AlternativesClassBuilder {
    private final ClassNode annotatedClass;
    private final FieldNode fieldNode;
    private InnerClassNode collectionFactory;
    private final ClassNode keyType;
    private final ClassNode elementType;
    private final ClassNode rwClass;
    private final String memberName;
    private final Map<ClassNode, String> alternatives;

    public AlternativesClassBuilder(FieldNode fieldNode) {
        this.fieldNode = fieldNode;
        this.annotatedClass = fieldNode.getOwner();
        rwClass = getRwClassOf(annotatedClass);
        elementType = CommonAstHelper.getElementType(fieldNode);
        keyType = getKeyType(elementType);
        memberName = getElementNameForCollectionField(fieldNode);
        alternatives = readAlternativesAnnotation();
    }

    @SuppressWarnings("MixedMutabilityReturnType")
    private Map<ClassNode, String> readAlternativesAnnotation() {
        AnnotationNode fieldAnnotation = CommonAstHelper.getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);
        if (fieldAnnotation == null)
            return Collections.emptyMap();

        ClosureExpression alternativesClosure = getAlternativesClosureFor(fieldAnnotation);

        if (alternativesClosure == null)
            return Collections.emptyMap();

        assertClosureHasNoParameters(alternativesClosure);
        MapExpression map = CommonAstHelper.getLiteralMapExpressionFromClosure(alternativesClosure);

        if (map == null) {
            CommonAstHelper.addCompileError("Illegal value for 'alternative', must contain a closure with a literal map definition.", fieldNode, fieldAnnotation);
            return Collections.emptyMap();
        }

        Map<ClassNode, String> result = new HashMap<>();

        for (MapEntryExpression entry : map.getMapEntryExpressions()) {
            String methodName = CommonAstHelper.getKeyStringFromLiteralMapEntry(entry, fieldNode);
            ClassNode classNode = CommonAstHelper.getClassNodeValueFromLiteralMapEntry(entry, fieldNode);

            if (!classNode.isDerivedFrom(elementType))
                CommonAstHelper.addCompileError(String.format("Alternatives value '%s' is no subclass of '%s'.", classNode, elementType), fieldNode, entry);

            if (result.containsKey(classNode))
                CommonAstHelper.addCompileError("Values for 'alternatives' must be unique.", fieldNode, entry);

            result.put(classNode, methodName);
        }

        return result;
    }

    private void assertClosureHasNoParameters(ClosureExpression alternativesClosure) {
        if (alternativesClosure.getParameters().length != 0)
            CommonAstHelper.addCompileError( "no parameters allowed for alternatives closure.", fieldNode, alternativesClosure);
    }

    private ClosureExpression getAlternativesClosureFor(AnnotationNode fieldAnnotation) {
        Expression codeExpression = fieldAnnotation.getMember("alternatives");
        if (codeExpression == null)
            return null;
        if (codeExpression instanceof ClosureExpression)
            return (ClosureExpression) codeExpression;

        CommonAstHelper.addCompileError("Illegal value for 'alternatives', must contain a closure.", fieldNode, fieldAnnotation);
        return null;
    }


    public void invoke() {
        createInnerClass();
        createClosureForOuterClass();
        if (fieldNodeIsNoLink())
            createNamedAlternativeMethodsForSubclasses();
        delegateDefaultCreationMethodsToOuterInstance();
    }

    private boolean fieldNodeIsNoLink() {
        return DslAstHelper.getFieldType(fieldNode) != FieldType.LINK;
    }

    private void delegateDefaultCreationMethodsToOuterInstance() {
        for (MethodNode methodNode : rwClass.getMethods(memberName)) {
            createDelegateMethod(methodNode, collectionFactory, "rw");
        }
    }

    private void createClosureForOuterClass() {
        String factoryMethod = fieldNode.getName();
        String closureVarName = "closure";
        createOptionalPublicMethod(factoryMethod)
                .linkToField(fieldNode)
                .delegatingClosureParam(collectionFactory, MethodBuilder.ClosureDefaultValue.NONE)
                .assignS(propX(varX(closureVarName), "delegate"), ctorX(collectionFactory, args("this")))
                .assignS(
                        propX(varX(closureVarName), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_ONLY")
                )
                .callMethod(closureVarName, "call")
                .addTo(rwClass);

        createOptionalPublicMethod(factoryMethod)
                .linkToField(fieldNode)
                .param(newClass(MAP_TYPE), "templateMap")
                .delegatingClosureParam(collectionFactory, MethodBuilder.ClosureDefaultValue.NONE)
                .statement(
                        callX(
                                elementType,
                                TemplateMethods.WITH_TEMPLATE,
                                args(varX("templateMap"), closureX(stmt(callThisX(factoryMethod, varX(closureVarName)))))
                        )
                )
                .addTo(rwClass);

        createOptionalPublicMethod(factoryMethod)
                .linkToField(fieldNode)
                .param(elementType, "template")
                .delegatingClosureParam(collectionFactory, MethodBuilder.ClosureDefaultValue.NONE)
                .statement(
                        callX(
                                elementType,
                                TemplateMethods.WITH_TEMPLATE,
                                args(varX("template"), closureX(stmt(callThisX(factoryMethod, varX(closureVarName)))))
                        )
                )
                .addTo(rwClass);
    }

    private void createNamedAlternativeMethodsForSubclasses() {
        CommonAstHelper.findAllKnownSubclassesOf(elementType, annotatedClass.getCompileUnit())
                .forEach(this::createNamedAlternativeMethodsForSingleSubclass);
    }

    private void createNamedAlternativeMethodsForSingleSubclass(ClassNode subclass) {
        if ((subclass.getModifiers() & ACC_ABSTRACT) != 0)
            return;

        if (!isDSLObject(subclass))
            return;

        String methodName = getShortNameFor(subclass);

        ClassNode subRwClass = getRwClassOf(subclass);

        String valuesVarName = "values";
        String closureVarName = "closure";
        createPublicMethod(methodName)
                .linkToField(fieldNode)
                .returning(elementType)
                .namedParams(valuesVarName).optionalStringParam("key", keyType != null)
                .delegatingClosureParam(subRwClass, MethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .doReturn(callX(varX("rw"), memberName,
                        keyType != null
                                ? args(varX(valuesVarName), classX(subclass), varX("key"), varX(closureVarName))
                                : args(varX(valuesVarName), classX(subclass), varX(closureVarName))
                ))
                .addTo(collectionFactory);

        createPublicMethod(methodName)
                .linkToField(fieldNode)
                .returning(elementType).optionalStringParam("key", keyType != null)
                .delegatingClosureParam(subRwClass, MethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .doReturn(callX(varX("rw"), memberName,
                        keyType != null
                                ? args(classX(subclass), varX("key"), varX(closureVarName))
                                : args(classX(subclass), varX(closureVarName))
                ))
                .addTo(collectionFactory);
    }

    private String getShortNameFor(ClassNode subclass) {
        String shortName = alternatives.get(subclass);

        if (shortName != null)
            return shortName;

        Optional<AnnotationNode> annotationNode = CommonAstHelper.getOptionalAnnotation(subclass, DSL_CONFIG_ANNOTATION);

        if (annotationNode.isPresent())
            shortName = getMemberStringValue(annotationNode.get(), "shortName");

        if (shortName != null)
            return shortName;

        String stringSuffix = findStripSuffixForHierarchy(subclass);
        String subclassName = subclass.getNameWithoutPackage();

        if (stringSuffix != null && subclassName.endsWith(stringSuffix))
            subclassName = subclassName.substring(0, subclassName.length() - stringSuffix.length());

        return Introspector.decapitalize(subclassName);
    }

    private String findStripSuffixForHierarchy(ClassNode subclass) {
        Deque<ClassNode> superSchemaClasses = getHierarchyOfDSLObjectAncestors(subclass.getSuperClass());

        String stringSuffix = null;
        for (Iterator<ClassNode> it = superSchemaClasses.descendingIterator(); it.hasNext() && stringSuffix == null; ) {
            ClassNode ancestor = it.next();
            AnnotationNode ancestorAnnotation = ancestor.getAnnotations(DSL_CONFIG_ANNOTATION).get(0);
            stringSuffix = getMemberStringValue(ancestorAnnotation, "stripSuffix");
        }
        return stringSuffix;
    }

    private void createInnerClass() {
        collectionFactory = new InnerClassNode(annotatedClass, annotatedClass.getName() + "$_" + fieldNode.getName(), ACC_PUBLIC | ACC_STATIC, OBJECT_TYPE);
        collectionFactory.addField("rw", ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, null);
        collectionFactory.addConstructor(ACC_PUBLIC,
                params(param(rwClass, "rw")),
                CommonAstHelper.NO_EXCEPTIONS,
                block(
                        assignS(propX(varX("this"), "rw"), varX("rw"))
                )
        );
        annotatedClass.getModule().addClass(collectionFactory);
        fieldNode.putNodeMetaData(COLLECTION_FACTORY_METADATA_KEY, collectionFactory);
    }
}
