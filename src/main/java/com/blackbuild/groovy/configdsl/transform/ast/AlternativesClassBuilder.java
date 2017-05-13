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

import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;

import java.beans.Introspector;
import java.util.*;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.*;
import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.*;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createOptionalPublicMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createPublicMethod;
import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_FINAL;
import static groovyjarjarasm.asm.Opcodes.ACC_PRIVATE;
import static groovyjarjarasm.asm.Opcodes.ACC_PUBLIC;
import static groovyjarjarasm.asm.Opcodes.ACC_STATIC;
import static groovyjarjarasm.asm.Opcodes.ACC_SYNTHETIC;
import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
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
    private final Map<ClassNode, String> alternatives;

    public AlternativesClassBuilder(FieldNode fieldNode) {
        this.fieldNode = fieldNode;
        this.annotatedClass = fieldNode.getOwner();
        rwClass = getRwClassOf(annotatedClass);
        elementType = getElementType(fieldNode);
        keyType = getKeyType(elementType);
        memberName = getElementNameForCollectionField(fieldNode);
        alternatives = readAlternativesAnnotation();
    }

    private Map<ClassNode, String> readAlternativesAnnotation() {
        AnnotationNode fieldAnnotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);
        if (fieldAnnotation == null)
            return Collections.emptyMap();

        ClosureExpression alternativesClosure = getAlternativesClosureFor(fieldAnnotation);

        if (alternativesClosure == null)
            return Collections.emptyMap();

        assertClosureHasNoParameters(alternativesClosure);
        MapExpression map = getLiteralMapExpressionFromClosure(alternativesClosure);

        if (map == null) {
            addCompileError("Illegal value for 'alternative', must contain a closure with a literal map definition.", fieldNode, fieldAnnotation);
            return Collections.emptyMap();
        }

        Map<ClassNode, String> result = new HashMap<ClassNode, String>();

        for (MapEntryExpression entry : map.getMapEntryExpressions()) {
            String methodName = getKeyString(entry);
            ClassNode classNode = getClassNodeValue(entry);
            if (classNode == null) continue;

            if (!classNode.isDerivedFrom(elementType))
                addCompileError(String.format("Alternatives value '%s' is no subclass of '%s'.", classNode, elementType), fieldNode, entry);

            if (result.containsKey(classNode))
                addCompileError("Values for 'alternatives' must be unique.", fieldNode, entry);

            result.put(classNode, methodName);
        }

        return result;
    }

    private String getKeyString(MapEntryExpression entryExpression) {
        Expression result = entryExpression.getKeyExpression();
        if (result instanceof ConstantExpression && result.getType().equals(STRING_TYPE))
            return result.getText();

        addCompileError("Map for 'alternatives' must only contain literal String to literal Class mappings.", fieldNode, entryExpression);
        return null;
    }

    private ClassNode getClassNodeValue(MapEntryExpression entryExpression) {
        Expression result = entryExpression.getValueExpression();
        if (result instanceof ClassExpression)
            return result.getType();

        addCompileError("Map for 'alternatives' must only contain literal String to literal Class mappings.", fieldNode, entryExpression);
        return null;
    }

    private MapExpression getLiteralMapExpressionFromClosure(ClosureExpression closure) {
        BlockStatement code = (BlockStatement) closure.getCode();
        if (code.getStatements().size() != 1) return null;
        Statement statement = code.getStatements().get(0);
        if (!(statement instanceof ExpressionStatement)) return null;
        Expression expression = ((ExpressionStatement) statement).getExpression();
        if (!(expression instanceof MapExpression)) return null;
        return (MapExpression) expression;
    }

    private void assertClosureHasNoParameters(ClosureExpression alternativesClosure) {
        if (alternativesClosure.getParameters().length != 0)
            addCompileError( "no parameters allowed for alternatives closure.", fieldNode, alternativesClosure);
    }

    private ClosureExpression getAlternativesClosureFor(AnnotationNode fieldAnnotation) {
        Expression codeExpression = fieldAnnotation.getMember("alternatives");
        if (codeExpression == null)
            return null;
        if (codeExpression instanceof ClosureExpression)
            return (ClosureExpression) codeExpression;

        addCompileError("Illegal value for 'alternatives', must contain a closure.", fieldNode, fieldAnnotation);
        return null;
    }


    public void invoke() {
        createInnerClass();
        createClosureForOuterClass();
        createNamedAlternativeMethodsForSubclasses();
    }

    private void createClosureForOuterClass() {
        createOptionalPublicMethod(fieldNode.getName())
                .linkToField(fieldNode)
                .delegatingClosureParam(collectionFactory, MethodBuilder.ClosureDefaultValue.NONE)
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

        ClassNode subRwClass = getRwClassOf(subclass);

        createPublicMethod(methodName)
                .linkToField(fieldNode)
                .returning(elementType)
                .namedParams("values")
                .optionalStringParam( "key", keyType)
                .delegatingClosureParam(subRwClass, MethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
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
                .delegatingClosureParam(subRwClass, MethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .doReturn(callX(varX("rw"), memberName,
                        keyType != null
                                ? args(classX(subclass), varX("key"), varX("closure"))
                                : args(classX(subclass), varX("closure"))
                ))
                .addTo(collectionFactory);
    }

    private String getShortNameFor(ClassNode subclass) {
        String shortName = alternatives.get(subclass);

        if (shortName != null)
            return shortName;

        AnnotationNode annotationNode = getAnnotation(subclass, DSL_CONFIG_ANNOTATION);
        shortName = getMemberStringValue(annotationNode, "shortName");

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
