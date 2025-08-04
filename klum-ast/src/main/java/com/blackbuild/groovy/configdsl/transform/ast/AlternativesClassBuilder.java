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
package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import com.blackbuild.klum.ast.util.KlumFactory;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;

import java.beans.Introspector;
import java.util.*;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_CONFIG_ANNOTATION;
import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.DSL_FIELD_ANNOTATION;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.*;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createOptionalPublicMethod;
import static com.blackbuild.klum.ast.util.reflect.AstReflectionBridge.cloneParamsWithAdjustedNames;
import static com.blackbuild.klum.common.CommonAstHelper.*;
import static groovyjarjarasm.asm.Opcodes.*;
import static java.lang.String.format;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.*;
import static org.codehaus.groovy.transform.AbstractASTTransformation.getMemberStringValue;

/**
 * Created by steph on 29.04.2017.
 */
class AlternativesClassBuilder extends AbstractFactoryBuilder {
    private static final ClassNode KLUM_FACTORY = ClassHelper.make(KlumFactory.class);
    private final DSLASTTransformation transformation;
    private final FieldNode fieldNode;
    private final ClassNode keyType;
    private final ClassNode elementType;
    private final String memberName;
    private final Map<ClassNode, String> alternatives;
    private Map<String, ClassNode> genericsSpec;

    public AlternativesClassBuilder(DSLASTTransformation transformation, FieldNode fieldNode) {
        super(fieldNode.getOwner());
        this.transformation = transformation;
        this.fieldNode = fieldNode;
        elementType = CommonAstHelper.getElementType(fieldNode);
        Objects.requireNonNull(elementType);
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
                CommonAstHelper.addCompileError(format("Alternatives value '%s' is no subclass of '%s'.", classNode, elementType), fieldNode, entry);

            if (result.containsKey(classNode))
                CommonAstHelper.addCompileError("Values for 'alternatives' must be unique.", fieldNode, entry);

            result.put(classNode, methodName);
        }

        return result;
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

    private void assertClosureHasNoParameters(ClosureExpression alternativesClosure) {
        if (alternativesClosure.getParameters().length != 0)
            CommonAstHelper.addCompileError("no parameters allowed for alternatives closure.", fieldNode, alternativesClosure);
    }

    @Override
    public void invoke() {
        createInnerClass(fieldNode.getName());
        createClosureForOuterClass();
        delegateDefaultCreationMethodsToOuterInstance();
        if (fieldNodeIsNoLink()) {
            createMethodsFromFactory();
            createNamedAlternativeMethodsForSubclasses();
        }
    }

    private void createClosureForOuterClass() {
        String factoryMethod = fieldNode.getName();
        String closureVarName = "closure";
        String factoryExplanation = "In addition to the other '%s' and '%s' methods, this method provides additional features like alternative syntaxes or custom factory methods.";
        String closureVarDescription = "The closure to handle the creation/setting of the instances.";
        createOptionalPublicMethod(factoryMethod)
                .linkToField(fieldNode)
                .delegatingClosureParam(collectionFactory, MethodBuilder.ClosureDefaultValue.NONE)
                .assignS(propX(varX(closureVarName), "delegate"), ctorX(collectionFactory, args("this")))
                .assignS(
                        propX(varX(closureVarName), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_ONLY")
                )
                .callMethod(classX(BreadcrumbCollector.class), "withBreadcrumb",
                        args(constX(fieldNode.getName()), constX(null), constX(null), varX(closureVarName))
                )
                .withDocumentation(doc -> doc
                        .title(format("Handles the creation/setting of the instances for the %s field.", factoryMethod))
                        .p(format(factoryExplanation, memberName, factoryMethod))
                        .param(closureVarName, closureVarDescription)

                )
                .addTo(rwClass);

        String templateMapVarName = "templateMap";
        createOptionalPublicMethod(factoryMethod)
                .linkToField(fieldNode)
                .param(newClass(MAP_TYPE), templateMapVarName)
                .delegatingClosureParam(collectionFactory, MethodBuilder.ClosureDefaultValue.NONE)
                .statement(
                        callX(
                                propX(classX(elementType), TemplateMethods.TEMPLATE_FIELD_NAME),
                                "With",
                                args(varX(templateMapVarName), closureX(stmt(callThisX(factoryMethod, varX(closureVarName)))))
                        )
                )
                .withDocumentation(doc -> doc
                        .title(format("Handles the creation/setting of the instances for the %s field using the given anonymous template.", factoryMethod))
                        .p(format("Creates an anonymous template of type {@link %s} from the given map and applies it for the closure.", elementType.getName()))
                        .p(format(factoryExplanation, memberName, factoryMethod))
                        .seeAlso("com.blackbuild.klum.ast.util.TemplateManager#withTemplate(Class,Map,Closure)")
                        .param(templateMapVarName, "The anonymous template to use for the creation/setting of the instances.")
                        .param(closureVarName, closureVarDescription)
                )
                .addTo(rwClass);

        String templateVarName = "template";
        createOptionalPublicMethod(factoryMethod)
                .linkToField(fieldNode)
                .param(elementType, templateVarName)
                .delegatingClosureParam(collectionFactory, MethodBuilder.ClosureDefaultValue.NONE)
                .statement(
                        callX(
                                propX(classX(elementType), TemplateMethods.TEMPLATE_FIELD_NAME),
                                "With",
                                args(varX(templateVarName), closureX(stmt(callThisX(factoryMethod, varX(closureVarName)))))
                        )
                )
                .withDocumentation(doc -> doc
                        .title(format("Handles the creation/setting of the instances for the %s field using the given template.", factoryMethod))
                        .p("Applies the given template to the closure.")
                        .p(format(factoryExplanation, memberName, factoryMethod))
                        .seeAlso("com.blackbuild.klum.ast.util.TemplateManager#withTemplate(Class,Object,Closure)")
                        .param(templateVarName, "The anonymous template to use for the creation/setting of the instances.")
                        .param(closureVarName, closureVarDescription)
                )
                .addTo(rwClass);
    }

    private boolean fieldNodeIsNoLink() {
        return DslAstHelper.getFieldType(fieldNode) != FieldType.LINK;
    }

    private void createMethodsFromFactory() {
        ClassNode factory = CommonAstHelper.getInnerClass(elementType, "_Factory");
        if (factory != null)
            doCreateMethodsFromFactory();
        else
            addDelayedAction(elementType, this::doCreateMethodsFromFactory);
    }

    private void createNamedAlternativeMethodsForSubclasses() {
        CommonAstHelper.findAllKnownSubclassesOf(elementType, targetClass.getCompileUnit())
                .forEach(this::createNamedAlternativeMethodsForSingleSubclass);
    }

    private void delegateDefaultCreationMethodsToOuterInstance() {
        for (MethodNode methodNode : rwClass.getMethods(memberName))
            createDelegateMethods(methodNode);
        for (MethodNode methodNode : rwClass.getMethods(fieldNode.getName()))
            createDelegateMethods(methodNode);
    }

    private void doCreateMethodsFromFactory() {
        ClassNode factory = CommonAstHelper.getInnerClass(elementType, "_Factory");
        genericsSpec = new HashMap<>();

        while (factory != null && factory.isDerivedFrom(KLUM_FACTORY)) {
            genericsSpec = createGenericsSpec(factory, genericsSpec);
            factory.getMethods().forEach(this::createDelegateFactoryMethod);
            factory = factory.getUnresolvedSuperClass();
        }
    }

    private void createNamedAlternativeMethodsForSingleSubclass(ClassNode subclass) {
        if ((subclass.getModifiers() & ACC_ABSTRACT) != 0)
            return;

        if (!isDSLObject(subclass))
            return;

        String methodName = getShortNameFor(subclass);
        ClassNode subRwClass = getRwClassOf(subclass);
        ClassNode subClassSafe = newClass(subclass);

        new ProxyMethodBuilder(varX("rw"), methodName, memberName)
                .optional()
                .targetType(rwClass)
                .linkToField(fieldNode)
                .mod(ACC_PUBLIC)
                .returning(subClassSafe)
                .namedParams("values", null)
                .constantClassParam(subClassSafe)
                .conditionalParam(STRING_TYPE, "key", keyType != null, null)
                .delegatingClosureParam(subRwClass, null)
                .documentationTitle("Creates a new instance of " + subclass.getName() + " and adds it to " + fieldNode.getName() + ".")
                .addTo(collectionFactory);

        new ConverterBuilder(transformation, fieldNode, methodName, false, collectionFactory).createConverterMethodsFromFactoryMethods(subclass);
    }

    private void createDelegateFactoryMethod(MethodNode methodNode) {
        if (methodNode.getName().startsWith("$")) return;
        if (!methodNode.isPublic()) return;
        if (methodNode.getName().startsWith(TemplateMethods.TEMPLATE_FIELD_NAME)) return;

        ClassNode returnType = correctToGenericsSpec(genericsSpec, methodNode).getReturnType();

        if (isCollection(returnType) && isAssignableTo(getElementTypeForCollection(returnType), elementType)) {
            MethodBuilder.createPublicMethod(methodNode.getName())
                    .returning(newClass(returnType))
                    .optional()
                    .cloneParamsFrom(methodNode)
                    .callThis(fieldNode.getName(),
                            callX(
                                    propX(classX(elementType), DSLASTTransformation.FACTORY_FIELD_NAME),
                                    methodNode.getName(),
                                    args(cloneParamsWithAdjustedNames(methodNode))
                            )
                    )
                    .copyDocFrom(methodNode)
                    .addTo(collectionFactory);
        } else if (isMap(returnType) && isAssignableTo(getElementTypeForMap(returnType), elementType)) {
            MethodBuilder.createPublicMethod(methodNode.getName())
                    .returning(newClass(returnType))
                    .optional()
                    .cloneParamsFrom(methodNode)
                    .callThis(fieldNode.getName(),
                            callX(
                                    callX(
                                            propX(classX(elementType), DSLASTTransformation.FACTORY_FIELD_NAME),
                                            methodNode.getName(),
                                            args(cloneParamsWithAdjustedNames(methodNode))
                                    ),
                                    "values"
                            )
                    )
                    .copyDocFrom(methodNode)
                    .addTo(collectionFactory);
        } else if (isAssignableTo(returnType, elementType)) {
            MethodBuilder.createPublicMethod(methodNode.getName())
                    .returning(newClass(returnType))
                    .optional()
                    .cloneParamsFrom(methodNode)
                    .callThis(
                            memberName,
                            callX(
                                    propX(classX(elementType), DSLASTTransformation.FACTORY_FIELD_NAME),
                                    methodNode.getName(),
                                    args(cloneParamsWithAdjustedNames(methodNode))
                            )
                    )
                    .copyDocFrom(methodNode)
                    .addTo(collectionFactory);
        }
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
}
