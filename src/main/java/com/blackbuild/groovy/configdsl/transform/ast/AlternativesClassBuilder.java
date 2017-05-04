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

import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.jetbrains.annotations.Nullable;

import java.beans.Introspector;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.*;
import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.COLLECTION_FACTORY_METADATA_KEY;
import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_CONFIG_ANNOTATION;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createOptionalPublicMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createPublicMethod;
import static groovyjarjarasm.asm.Opcodes.*;
import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
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

    public AlternativesClassBuilder(FieldNode fieldNode) {
        this.fieldNode = fieldNode;
        this.annotatedClass = fieldNode.getOwner();
        rwClass = getRwClassOf(annotatedClass);
        elementType = getElementType(fieldNode);
        keyType = getKeyType(elementType);
        memberName = getElementNameForCollectionField(fieldNode);
    }

    public void invoke() {
        createInnerClass();
        createClosureForOuterClass();
        createNamedAlternativeMethodsForSubclasses();
    }

    private void createClosureForOuterClass() {
        createOptionalPublicMethod(fieldNode.getName())
                .linkToField(fieldNode)
                .closureParam("closure")
                .assignS(propX(varX("closure"), "delegate"), ctorX(collectionFactory, args("this")))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .callMethod("closure", "call")
                .addTo(rwClass);
    }

    private void createNamedAlternativeMethodsForSubclasses() {
        List<ClassNode> subclasses = findAllKnownSubclassesOf(elementType);
        for (ClassNode subclass : subclasses) {
            createNamedAlternativeMethodsForSingleSubclass(subclass);
        }
    }

    private void createNamedAlternativeMethodsForSingleSubclass(ClassNode subclass) {
        if ((subclass.getModifiers() & ACC_ABSTRACT) != 0)
            return;

        String methodName = getShortNameFor(subclass);

        createPublicMethod(methodName)
                .linkToField(fieldNode)
                .returning(elementType)
                .namedParams("values")
                .optionalStringParam( "key", keyType)
                .delegatingClosureParam(subclass)
                .doReturn(callX(varX("rw"), memberName,
                        keyType != null
                                ? args(varX("values"), classX(subclass), varX("key"), varX("closure"))
                                : args(varX("values"), classX(subclass), varX("closure"))
                ))
                .addTo(collectionFactory);

        createPublicMethod(methodName)
                .linkToField(fieldNode)
                .returning(elementType)
                .optionalStringParam( "key", keyType)
                .delegatingClosureParam(subclass)
                .doReturn(callX(varX("rw"), memberName,
                        keyType != null
                                ? args(classX(subclass), varX("key"), varX("closure"))
                                : args(classX(subclass), varX("closure"))
                ))
                .addTo(collectionFactory);
    }

    private String getShortNameFor(ClassNode subclass) {
        AnnotationNode annotationNode = subclass.getAnnotations(DSL_CONFIG_ANNOTATION).get(0);

        String shortName = getMemberStringValue(annotationNode, "shortName");

        if (shortName != null)
            return shortName;

        return Introspector.decapitalize(subclass.getNameWithoutPackage());
    }

    private void createInnerClass() {
        collectionFactory = new InnerClassNode(annotatedClass, annotatedClass.getName() + "$_" + fieldNode.getName(), ACC_PUBLIC | ACC_STATIC, OBJECT_TYPE);
        collectionFactory.addField("rw", ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, null);
        collectionFactory.addConstructor(ACC_PUBLIC | ACC_SYNTHETIC,
                params(param(rwClass, "rw")),
                NO_EXCEPTIONS,
                block(
                        assignS(propX(varX("this"), "rw"), varX("rw"))
                )
        );
        annotatedClass.getModule().addClass(collectionFactory);
        fieldNode.putNodeMetaData(COLLECTION_FACTORY_METADATA_KEY, collectionFactory);
    }
}
