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

import com.blackbuild.annodocimal.ast.extractor.ASTExtractor;
import com.blackbuild.annodocimal.ast.formatting.AnnoDocUtil;
import com.blackbuild.groovy.configdsl.transform.*;
import com.blackbuild.groovy.configdsl.transform.ast.mutators.WriteAccessMethodsMover;
import com.blackbuild.klum.ast.KlumKeyedModelObject;
import com.blackbuild.klum.ast.KlumModelObject;
import com.blackbuild.klum.ast.KlumRwObject;
import com.blackbuild.klum.ast.KlumUnkeyedModelObject;
import com.blackbuild.klum.ast.doc.DocUtil;
import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.util.BoundTemplateHandler;
import com.blackbuild.klum.ast.util.KlumFactory;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.ast.util.layer3.ClusterFactoryBuilder;
import com.blackbuild.klum.ast.util.reflect.AstReflectionBridge;
import com.blackbuild.klum.common.CommonAstHelper;
import groovy.lang.Closure;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.classgen.VariableScopeVisitor;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.*;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.*;
import static com.blackbuild.groovy.configdsl.transform.ast.ProxyMethodBuilder.createFactoryMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.ProxyMethodBuilder.createProxyMethod;
import static com.blackbuild.klum.ast.util.layer3.ClusterTransformation.CLUSTER_ANNOTATION_TYPE;
import static com.blackbuild.klum.common.CommonAstHelper.*;
import static java.util.stream.Collectors.toList;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.expr.MethodCallExpression.NO_ARGUMENTS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.*;
import static org.codehaus.groovy.transform.EqualsAndHashCodeASTTransformation.createEquals;
import static org.codehaus.groovy.transform.ToStringASTTransformation.createToString;

/**
 * Transformation class for the @DSL annotation.
 *
 * @author Stephan Pauxberger
 */
@SuppressWarnings({"WeakerAccess"})
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DSLASTTransformation extends AbstractASTTransformation {

    public static final ClassNode DSL_CONFIG_ANNOTATION = make(DSL.class);
    public static final ClassNode DSL_FIELD_ANNOTATION = make(Field.class);
    public static final ClassNode VALIDATE_ANNOTATION = make(Validate.class);
    public static final ClassNode KEY_ANNOTATION = make(Key.class);

    private static final ClassNode DELEGATES_TO_RW_TYPE = ClassHelper.make(DelegatesToRW.class);

    public static final ClassNode OWNER_ANNOTATION = make(Owner.class);
    public static final ClassNode KLUM_FACTORY = make(KlumFactory.class);
    public static final ClassNode KEYED_FACTORY = make(KlumFactory.Keyed.class);
    public static final ClassNode UNKEYED_FACTORY = make(KlumFactory.Unkeyed.class);
    public static final ClassNode INSTANCE_PROXY = make(KlumInstanceProxy.class);
    public static final ClassNode EQUALS_HASHCODE_ANNOT = make(EqualsAndHashCode.class);
    public static final ClassNode TOSTRING_ANNOT = make(ToString.class);
    public static final String VALIDATE_METHOD = "validate";
    public static final String RW_CLASS_SUFFIX = "$_RW";
    public static final String RWCLASS_METADATA_KEY = DSLASTTransformation.class.getName() + ".rwclass";
    public static final String CREATE_FROM = "createFrom";
    public static final ClassNode INVOKER_HELPER_CLASS = ClassHelper.make(InvokerHelper.class);
    public static final String CREATE_METHOD_NAME = "create";
    public static final String CREATE_FROM_CLASSPATH = "createFromClasspath";
    public static final String FACTORY_FIELD_NAME = "Create";
    public static final String TEMPLATE_FIELD_NAME = "Template";
    public static final ClassNode KLUM_KEYED_MODEL_OBJECT = make(KlumKeyedModelObject.class);
    public static final ClassNode KLUM_MODEL_OBJECT = make(KlumModelObject.class);
    public static final ClassNode KLUM_UNKEYED_MODEL_OBJECT = make(KlumUnkeyedModelObject.class);
    public static final ClassNode TEMPLATE_TYPE = make(BoundTemplateHandler.class);

    ClassNode annotatedClass;
    ClassNode dslParent;
    FieldNode keyField;
    List<FieldNode> ownerFields;
    AnnotationNode dslAnnotation;
    InnerClassNode rwClass;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);

        annotatedClass = (ClassNode) nodes[1];
        dslAnnotation = (AnnotationNode) nodes[0];

        if (annotatedClass.isInterface()) return;

        keyField = getKeyField(annotatedClass);
        ownerFields = getOwnerFields(annotatedClass);

        if (isDSLObject(annotatedClass.getSuperClass()))
            dslParent = annotatedClass.getSuperClass();

        if (keyField != null)
            createKeyConstructor();
        else
            createExplicitEmptyConstructor();

        implementMarkerInterfaces();

        checkFieldNames();
        warnIfAFieldIsNamedOwner();

        createRWClass();

        setPropertyAccessors();
        createCanonicalMethods();
        assertMembersNamesAreUnique();
        makeClassSerializable();
        createApplyMethods();
        createTemplateMethods();
        createFactoryField();
        createFactoryMethods();
        createConvenienceFactories();

        createTemplateField();

        createFieldDSLMethods();
        createClusterFactories();
        createValidateMethod();
        moveMutatorsToRWClass();

        createOwnerClosureMethods();

        delegateRwToModel();

        runDelayedActions(annotatedClass);

        new VariableScopeVisitor(sourceUnit, true).visitClass(annotatedClass);
    }

    private void implementMarkerInterfaces() {
        if (keyField != null) annotatedClass.addInterface(KLUM_KEYED_MODEL_OBJECT);
        else if (isAbstract(annotatedClass)) annotatedClass.addInterface(KLUM_MODEL_OBJECT);
        else annotatedClass.addInterface(KLUM_UNKEYED_MODEL_OBJECT);
    }

    private void createExplicitEmptyConstructor() {
        annotatedClass.addConstructor(
                ACC_PROTECTED,
                Parameter.EMPTY_ARRAY,
                NO_EXCEPTIONS,
                block()
        );
    }

    private void delegateRwToModel() {
        new DelegateFromRwToModel(annotatedClass).invoke();
    }

    private void warnIfAFieldIsNamedOwner() {
        FieldNode ownerNamedField = annotatedClass.getDeclaredField("owner");

        if (ownerNamedField != null)
            addCompileWarning(sourceUnit, "Fields should not be named 'owner' to prevent naming clash with Closure.owner!", ownerNamedField);
    }

    private void checkFieldNames() {
        annotatedClass.getFields().forEach(this::warnIfInvalid);
    }

    private void warnIfInvalid(FieldNode fieldNode) {
        if (fieldNode.getName().startsWith("$") && (fieldNode.getModifiers() & ACC_SYNTHETIC) != 0)
            addCompileWarning(sourceUnit, "fields starting with '$' are strongly discouraged", fieldNode);
    }

    private void moveMutatorsToRWClass() {
        new WriteAccessMethodsMover(annotatedClass).invoke();
    }

    private void setPropertyAccessors() {
        new PropertyAccessors(this).invoke();
    }

    private void createRWClass() {
        ClassNode parentRW = getRwClassOfDslParent();

        rwClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + RW_CLASS_SUFFIX,
                ACC_PUBLIC | ACC_STATIC,
                parentRW != null ? parentRW : OBJECT_TYPE,
                new ClassNode[] { make(Serializable.class), make(KlumRwObject.class) },
                MixinNode.EMPTY_ARRAY);
        AnnoDocUtil.addDocumentation(rwClass, "The mutator class for " + annotatedClass.getName() + ". Allows modifying the state.");

        DslAstHelper.registerAsVerbProvider(rwClass);

        rwClass.addField(KlumInstanceProxy.NAME_OF_MODEL_FIELD_IN_RW_CLASS, ACC_FINAL | ACC_PRIVATE | ACC_SYNTHETIC, newClass(annotatedClass), null);

        BlockStatement block = new BlockStatement();
        if (parentRW != null)
            block.addStatement(ctorSuperS(varX("model")));
        block.addStatement(assignS(varX(KlumInstanceProxy.NAME_OF_MODEL_FIELD_IN_RW_CLASS), varX("model")));

        rwClass.addConstructor(
                ACC_PROTECTED,
                params(param(newClass(annotatedClass), "model")),
                ClassNode.EMPTY_ARRAY,
                block
        );

        MethodBuilder.createProtectedMethod("get$proxy")
                .returning(make(KlumInstanceProxy.class))
                .doReturn(propX(varX(KlumInstanceProxy.NAME_OF_MODEL_FIELD_IN_RW_CLASS), KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS))
                .addTo(rwClass);

        annotatedClass.getModule().addClass(rwClass);
        annotatedClass.addField(KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS, ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, ctorX(rwClass, varX("this")));
        if (dslParent == null)
            annotatedClass.addField(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS, ACC_PUBLIC | ACC_SYNTHETIC | ACC_FINAL, INSTANCE_PROXY, ctorX(INSTANCE_PROXY, varX("this")));

        ClassNode parentProxy = annotatedClass.getNodeMetaData(RWCLASS_METADATA_KEY);
        if (parentProxy == null)
            annotatedClass.setNodeMetaData(RWCLASS_METADATA_KEY, rwClass);
        else
            parentProxy.setRedirect(rwClass);

        rwClass.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class));

        createCoercionMethod();
    }

    private void createCoercionMethod() {
        createPublicMethod("asType")
                .returning(OBJECT_TYPE)
                .param(makeClassSafe(Class.class), "type")
                .statement(
                        ifS(
                                callX(varX("type"), "isAssignableFrom", classX(annotatedClass)),
                                returnS(varX(KlumInstanceProxy.NAME_OF_MODEL_FIELD_IN_RW_CLASS))
                        )
                )
                .addTo(rwClass);
    }

    private ClassNode getRwClassOfDslParent() {
        return DslAstHelper.getRwClassOf(dslParent);
    }

    private void makeClassSerializable() {
        annotatedClass.addInterface(make(Serializable.class));
    }

    private void createClusterFactories() {
        annotatedClass.getAllDeclaredMethods().stream()
                .filter(methodNode -> DslAstHelper.hasAnnotation(methodNode, CLUSTER_ANNOTATION_TYPE))
                .forEach(this::createClusterFactory);
    }

    private void createClusterFactory(MethodNode methodNode) {
        new ClusterFactoryBuilder(annotatedClass, methodNode).invoke();
    }


    private void createValidateMethod() {
        convertValidationClosures();

        // TODO: to proxy
        if (dslParent == null) {
            // add manual validation only to root of hierarchy
            createProxyMethod("manualValidation")
                    .mod(ACC_PUBLIC)
                    .param(Boolean_TYPE, "validation", constX(true), "true to enable manual validation, false to disable")
                    .documentationTitle("Prevent automatic validation of this instance during Validation phase.")
                    .addTo(rwClass);
        }

        createProxyMethod(VALIDATE_METHOD)
                .mod(ACC_PUBLIC)
                .optional()
                .forRemoval("Use ")
                .addTo(annotatedClass);
    }

    private void convertValidationClosures() {
        annotatedClass.getFields().stream()
                .filter(fieldNode -> DslAstHelper.hasAnnotation(fieldNode, VALIDATE_ANNOTATION))
                .filter(fieldNode -> getAnnotation(fieldNode, VALIDATE_ANNOTATION).getMember("value") != null)
                .forEach(this::convertValidationClosureOnSingleField);
    }

    private void convertValidationClosureOnSingleField(FieldNode fieldNode) {
        AnnotationNode validateAnnotation = getAnnotation(fieldNode, VALIDATE_ANNOTATION);
        String message = getMemberStringValue(validateAnnotation, "message");
        Expression validationExpression = validateAnnotation.getMember("value");

        if (validationExpression instanceof ClosureExpression) {
            ClosureExpression validationClosure = toStronglyTypedClosure((ClosureExpression) validationExpression, fieldNode.getType());
            convertClosureExpressionToAssertStatement(validationClosure, message);
            // replace closure with strongly typed one
            validateAnnotation.setMember("value", validationClosure);
        } else {
            addCompileWarning(sourceUnit, "Only closures are supported for validation, consider using a @Validate method instead", validateAnnotation);
        }
    }

    private void convertClosureExpressionToAssertStatement(ClosureExpression closure, String message) {
        BlockStatement block = (BlockStatement) closure.getCode();

        if (block.getStatements().size() != 1)
            addError("Only a single statement is allowed for validations, consider using a @Validate method instead", block);

        Parameter closureParameter = closure.getParameters()[0];

        Statement codeStatement = block.getStatements().get(0);

        AssertStatement assertStatement;

        if (codeStatement instanceof AssertStatement) {
            assertStatement = (AssertStatement) codeStatement;
        } else if (codeStatement instanceof ExpressionStatement) {
            Expression check = ((ExpressionStatement) codeStatement).getExpression();
            assertStatement = assertStmt(new BooleanExpression(check), message);
        } else {
            addError("Content of validation closure must either be an assert statement or an expression", codeStatement);
            return;
        }

        String closureParameterName = closureParameter.getName();
        if (assertStatement.getMessageExpression() == ConstantExpression.NULL) {
            assertStatement.setMessageExpression(
                    new GStringExpression(
                            "$" + closureParameterName + " does not match",
                            Arrays.asList(constX(""), constX(" does not match")),
                            Collections.singletonList(
                                    callX(
                                            INVOKER_HELPER_CLASS,
                                            "format",
                                            args(varX(closureParameterName), ConstantExpression.PRIM_TRUE)
                                    )
                            )
                    )
            );
        }

        closure.setCode(assertStatement);
    }

    private AssertStatement assertStmt(Expression check, String message) {
        if (message == null) return new AssertStatement(new BooleanExpression(check), ConstantExpression.NULL);
        else return new AssertStatement(new BooleanExpression(check), new ConstantExpression(message));
    }

    private void assertMembersNamesAreUnique() {
        Map<String, FieldNode> allDslCollectionFieldNodesOfHierarchy = new HashMap<>();

        for (ClassNode level : DslAstHelper.getHierarchyOfDSLObjectAncestors(annotatedClass)) {
            for (FieldNode field : level.getFields()) {
                if (!CommonAstHelper.isCollectionOrMap(field.getType())) continue;

                String memberName = getElementNameForCollectionField(field);

                FieldNode conflictingField = allDslCollectionFieldNodesOfHierarchy.get(memberName);

                if (conflictingField != null) {
                    addCompileError(
                            sourceUnit, String.format("Member name %s is used more than once: %s:%s and %s:%s", memberName, field.getOwner().getName(), field.getName(), conflictingField.getOwner().getName(), conflictingField.getName()),
                            field
                    );
                    return;
                }

                allDslCollectionFieldNodesOfHierarchy.put(memberName, field);
            }
        }
    }

    private void createTemplateMethods() {
        new TemplateMethods(this).invoke();
    }

    private void createOwnerClosureMethods() {
        annotatedClass.getFields()
                .stream()
                .filter(fieldNode -> DslAstHelper.hasAnnotation(fieldNode, OWNER_ANNOTATION))
                .filter(fieldNode -> fieldNode.getType().equals(CLOSURE_TYPE))
                .forEach(this::createSingleFieldSetterMethod);
    }

    private void createKeyConstructor() {
        boolean hasKeyedParent = !keyField.getOwner().equals(annotatedClass);

        BlockStatement constructorBody = new BlockStatement();
        if (hasKeyedParent) {
            constructorBody.addStatement(ctorSuperS(args("key")));
        } else {
            constructorBody.addStatement(ctorSuperS());
            constructorBody.addStatement(assignS(propX(varX("this"), keyField.getName()), varX("key")));
        }

        annotatedClass.addConstructor(
                ACC_PUBLIC,
                params(param(STRING_TYPE, "key")),
                CommonAstHelper.NO_EXCEPTIONS,
                constructorBody
        );

        if (!hasKeyedParent)
            keyField.setModifiers(keyField.getModifiers() | ACC_FINAL);

        ConstructorNode dummyConstructor = annotatedClass.addConstructor(
                ACC_PROTECTED,
                Parameter.EMPTY_ARRAY,
                NO_EXCEPTIONS,
                block(
                        throwS(ctorX(make(UnsupportedOperationException.class), constX("empty constructor is only to satisfy IDEs")))
                )
        );

        dummyConstructor.addAnnotation(new AnnotationNode(DEPRECATED_NODE));
    }

    private void createCanonicalMethods() {
        if (!hasAnnotation(annotatedClass, EQUALS_HASHCODE_ANNOT)) {
            createHashCodeIfNotDefined();
            createEquals(annotatedClass, true, dslParent != null, true, getAllIgnoredFieldNames(), null);
        }
        if (!hasAnnotation(annotatedClass, TOSTRING_ANNOT)) {
            createToString(annotatedClass, false, true, getOwnerFieldNames(annotatedClass), null, false);
        }
    }

    private List<String> getAllIgnoredFieldNames() {
        return annotatedClass.getFields()
                .stream()
                .filter(DSLASTTransformation::isFieldIgnoredForEquals)
                .map(FieldNode::getName)
                .collect(toList());
    }

    private static boolean isFieldIgnoredForEquals(FieldNode fieldNode) {
        return fieldNode.getName().startsWith("$") || getFieldType(fieldNode) == FieldType.TRANSIENT || DslAstHelper.hasAnnotation(fieldNode, OWNER_ANNOTATION);
    }

    private static final String HASH_CODE_METHOD_NAME = "hashCode";

    private void createHashCodeIfNotDefined() {
        if (hasDeclaredMethod(annotatedClass, HASH_CODE_METHOD_NAME, 0))
            return;

        if (keyField != null) {
            createPublicMethod(HASH_CODE_METHOD_NAME)
                    .returning(ClassHelper.int_TYPE)
                    .doReturn(callX(varX(keyField.getName()), HASH_CODE_METHOD_NAME))
                    .addTo(annotatedClass);
        } else {
            createPublicMethod(HASH_CODE_METHOD_NAME)
                    .returning(ClassHelper.int_TYPE)
                    .doReturn(constX(0))
                    .addTo(annotatedClass);
        }
    }

    private void createFieldDSLMethods() {
        annotatedClass.getFields().forEach(this::createDSLMethodsForSingleField);
        annotatedClass
                .getMethods()
                .stream()
                .filter(methodNode -> DslAstHelper.hasAnnotation(methodNode, DSL_FIELD_ANNOTATION))
                .forEach(this::createDSLMethodsForVirtualFields);
    }

    private void createDSLMethodsForVirtualFields(MethodNode methodNode) {
        String methodName = methodNode.getName();

        ClassNode parameterType = methodNode.getParameters()[0].getType();
        FieldNode virtualField = new FieldNode(methodName, ACC_PUBLIC, parameterType, annotatedClass, null);
        virtualField.addAnnotations(methodNode.getAnnotations());
        virtualField.setSourcePosition(methodNode);

        if (hasDefaultImpl(virtualField) || hasDefaultImpl(parameterType) || isDSLObject(parameterType))
            createSingleDSLObjectFieldCreationMethods(virtualField, methodName);

        createConverterMethods(virtualField, methodName, false);
    }

    private boolean hasDefaultImpl(FieldNode field) {
        AnnotationNode fieldAnno = getAnnotation(field, DSL_FIELD_ANNOTATION);
        return fieldAnno != null && fieldAnno.getMember("defaultImpl") != null;
    }
    private boolean hasDefaultImpl(ClassNode classNode) {
        AnnotationNode fieldAnno = getAnnotation(classNode, DSL_CONFIG_ANNOTATION);
        return fieldAnno != null && fieldAnno.getMember("defaultImpl") != null;
    }

    private void createDSLMethodsForSingleField(FieldNode fieldNode) {
        if (shouldFieldBeIgnored(fieldNode)) return;
        if (getFieldType(fieldNode) == FieldType.IGNORED) return;

        ClassNode fieldType = fieldNode.getType();

        if (isMap(fieldType))
            createMapMethods(fieldNode);
        else if (isCollection(fieldType))
            createCollectionMethods(fieldNode);
        else {
            if (hasDefaultImpl(fieldNode) || hasDefaultImpl(fieldType) || isDSLObject(fieldType))
                createSingleDSLObjectFieldCreationMethods(fieldNode, fieldNode.getName());
            createSingleFieldSetterMethod(fieldNode);
        }
    }

    @SuppressWarnings({"RedundantIfStatement", "java:S1126"})
    boolean shouldFieldBeIgnored(FieldNode fieldNode) {
        if ((fieldNode.getModifiers() & ACC_SYNTHETIC) != 0) return true;
        if (fieldNode.isFinal()) return true;
        if (fieldNode.getName().startsWith("$")) return true;
        if ((fieldNode.getModifiers() & ACC_TRANSIENT) != 0) return true;
        if (getFieldType(fieldNode) == FieldType.TRANSIENT) return true;
        if (isKeyField(fieldNode)) return true;
        if (isOwnerField(fieldNode)) return true;
        return false;
    }

    private boolean isOwnerField(FieldNode fieldNode) {
        return DslAstHelper.hasAnnotation(fieldNode, OWNER_ANNOTATION);
    }

    private boolean isKeyField(FieldNode fieldNode) {
        return fieldNode == keyField;
    }

    private void createSingleFieldSetterMethod(FieldNode fieldNode) {
        int visibility = DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;
        String fieldName = fieldNode.getName();

        createProxyMethod(fieldName, "setSingleField")
                .optional()
                .returning(fieldNode.getType(), "The set value")
                .mod(visibility)
                .linkToField(fieldNode)
                .documentationTitle(DocUtil.getSetterText(fieldNode))
                .constantParam(fieldName)
                .decoratedParam(fieldNode, "value", "the value to set")
                .addTo(rwClass);

        if (fieldNode.getType().equals(ClassHelper.boolean_TYPE)) {
            createProxyMethod(fieldName, "setSingleField")
                    .optional()
                    .returning(Boolean_TYPE, "always true")
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .documentationTitle(DocUtil.getFlagSetterText(fieldNode))
                    .constantParam(fieldName)
                    .constantPrimitveParam(true)
                    .addTo(rwClass);
        }

        createConverterMethods(fieldNode, fieldName, false);
    }

    private void createConverterMethods(FieldNode fieldNode, String methodName, boolean withKey) {
        if (getFieldType(fieldNode) != FieldType.LINK)
            new ConverterBuilder(this, fieldNode, methodName, withKey, getRwClassOf(this.annotatedClass)).execute();
    }

    private void createCollectionMethods(FieldNode fieldNode) {
        initializeCollectionOrMap(fieldNode);

        ClassNode elementType = getElementTypeForCollection(fieldNode.getType());

        if (elementType == null) {
            addCompileError("Collection must have a generic type.", fieldNode);
            return;
        }

        if (hasDefaultImpl(fieldNode) || hasDefaultImpl(elementType) || isDSLObject(elementType))
            createCollectionOfDSLObjectMethods(fieldNode, elementType);
        else
            createCollectionOfSimpleElementsMethods(fieldNode, elementType);
    }

    private void createCollectionOfSimpleElementsMethods(FieldNode fieldNode, ClassNode elementType) {
        int visibility = DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        String elementName = getElementNameForCollectionField(fieldNode);
        String fieldName = fieldNode.getName();
        createProxyMethod(fieldName, "addElementsToCollection")
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .documentationTitle(DocUtil.getCollectionMultiAdderText(fieldNode))
                .constantParam(fieldName)
                .arrayParam(elementType, "values", "The values to add")
                .addTo(rwClass);

        createProxyMethod(fieldName, "addElementsToCollection")
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .documentationTitle(DocUtil.getCollectionMultiAdderText(fieldNode))
                .constantParam(fieldName)
                .param(GenericsUtils.makeClassSafeWithGenerics(Iterable.class, elementType), "values", "The values to add")
                .addTo(rwClass);

        createProxyMethod(elementName, "addElementToCollection")
                .optional()
                .mod(visibility)
                .returning(elementType)
                .linkToField(fieldNode)
                .documentationTitle(DocUtil.getCollectionAdderText(fieldNode))
                .constantParam(fieldName)
                .param(elementType, "value", "The value to add")
                .addTo(rwClass);

        createConverterMethods(fieldNode, elementName, false);
    }

    private void createCollectionOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        String methodName = getElementNameForCollectionField(fieldNode);
        ClassNode defaultImpl = getDefaultImplOfFieldOrMethod(fieldNode, elementType);
        ClassNode dslBaseType = getDslBaseType(elementType, defaultImpl);
        ClassNode elementRwType = DslAstHelper.getRwClassOf(defaultImpl);

        FieldNode fieldKey = getKeyField(dslBaseType);

        warnIfSetWithoutKeyedElements(fieldNode, elementType, fieldKey);

        String fieldName = fieldNode.getName();

        int visibility = DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (getFieldType(fieldNode) != FieldType.LINK) {
            String fieldKeyName = fieldKey != null ? fieldKey.getName() : null;
            if (isInstantiable(defaultImpl)) {
                createProxyMethod(methodName, KlumInstanceProxy.ADD_NEW_DSL_ELEMENT_TO_COLLECTION)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .namedParams("values")
                        .constantParam(fieldName)
                        .constantClassParam(defaultImpl)
                        .constantPrimitveParam(false)
                        .optionalStringParam(fieldKeyName, fieldKey != null, null)
                        .delegatingClosureParam(elementRwType, null)
                        .addTo(rwClass);
            }

            if (!isFinal(elementType)) {
                createProxyMethod(methodName, KlumInstanceProxy.ADD_NEW_DSL_ELEMENT_TO_COLLECTION)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .namedParams("values")
                        .constantParam(fieldName)
                        .delegationTargetClassParam("typeToCreate", dslBaseType)
                        .constantPrimitveParam(true)
                        .optionalStringParam(fieldKeyName, fieldKey != null)
                        .delegatingClosureParam()
                        .addTo(rwClass);
            }

            createProxyMethod(fieldName, KlumInstanceProxy.ADD_ELEMENTS_FROM_SCRIPTS_TO_COLLECTION)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .constantParam(fieldName)
                    .arrayParam(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(ClassHelper.SCRIPT_TYPE)), "scripts")
                    .addTo(rwClass);
        }

        createProxyMethod(fieldName, "addElementsToCollection")
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .constantParam(fieldName)
                .arrayParam(elementType, "values")
                .addTo(rwClass);

        createProxyMethod(fieldName, "addElementsToCollection")
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .constantParam(fieldName)
                .param(GenericsUtils.makeClassSafeWithGenerics(Iterable.class, elementType), "values")
                .addTo(rwClass);

        createProxyMethod(methodName, "addElementToCollection")
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .returning(elementType)
                .constantParam(fieldName)
                .param(elementType, "value")
                .addTo(rwClass);

        createAlternativesClassFor(fieldNode);

        createConverterMethods(fieldNode, methodName, false);
    }

    private void warnIfSetWithoutKeyedElements(FieldNode fieldNode, ClassNode elementType, FieldNode fieldKey) {
        if (fieldNode.getType().getNameWithoutPackage().equals("Set") && fieldKey == null) {
            CommonAstHelper.addCompileWarning(sourceUnit,
                    String.format(
                            "WARNING: Field %s.%s is of type Set<%s>, but %s has no Key field. This might severely impact performance",
                            annotatedClass.getName(), fieldNode.getName(), elementType.getNameWithoutPackage(), elementType.getName()), fieldNode);
        }
    }

    private Expression optionalKeyArg(Object fieldKey, String keyFieldName) {
        return fieldKey != null ? args(keyFieldName) : NO_ARGUMENTS;
    }

    private void createMapMethods(FieldNode fieldNode) {
        initializeCollectionOrMap(fieldNode);

        ClassNode valueType = getElementTypeForMap(fieldNode.getType());

        if (valueType == null) {
            addCompileError("Collection must have a generic type.", fieldNode);
            return;
        }

        if (hasDefaultImpl(fieldNode) || hasDefaultImpl(valueType) || isDSLObject(valueType))
            createMapOfDSLObjectMethods(fieldNode, valueType);
        else
            createMapOfSimpleElementsMethods(fieldNode, valueType);
    }

    private void createMapOfSimpleElementsMethods(FieldNode fieldNode, ClassNode valueType) {
        String methodName = fieldNode.getName();
        String singleElementMethod = getElementNameForCollectionField(fieldNode);

        ClassNode keyType = getKeyTypeForMap(fieldNode.getType());

        int visibility = DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        ClosureExpression keyMappingClosure = getTypedKeyMappingClosure(fieldNode, valueType);

        if (keyMappingClosure == null) {
            createProxyMethod(methodName, "addElementsToMap")
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .constantParam(methodName)
                    .param(makeClassSafeWithGenerics(MAP_TYPE, new GenericsType(keyType), new GenericsType(valueType)), "values")
                    .addTo(rwClass);
        } else {
            createProxyMethod(methodName, "addElementsToMap")
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .constantParam(methodName)
                    .param(makeClassSafeWithGenerics(CommonAstHelper.COLLECTION_TYPE, new GenericsType(valueType)), "values")
                    .addTo(rwClass);
            createProxyMethod(methodName, "addElementsToMap")
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .constantParam(methodName)
                    .arrayParam(valueType, "values")
                    .addTo(rwClass);
        }

        createProxyMethod(singleElementMethod, "addElementToMap")
                .optional()
                .mod(visibility)
                .returning(valueType)
                .linkToField(fieldNode)
                .constantParam(methodName)
                .optionalParam(keyType, "key", keyMappingClosure == null)
                .param(valueType, "value")
                .addTo(rwClass);

        createConverterMethods(fieldNode, singleElementMethod, true);
    }

    private void createMapOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        ClassNode defaultImpl = getDefaultImplOfFieldOrMethod(fieldNode, elementType);
        ClassNode dslBaseType = getDslBaseType(elementType, defaultImpl);

        FieldNode elementKeyField = getKeyField(dslBaseType);

        ClosureExpression keyMappingClosure = getTypedKeyMappingClosure(fieldNode, elementType);

        if (keyMappingClosure == null && elementKeyField == null) {
            addCompileError(
                    String.format("Value type of map %s (%s) has no key field and no keyMapping", fieldNode.getName(), elementType.getName()),
                    fieldNode
            );
            return;
        }

        String elementToAddVarName = "elementToAdd";
        String methodName = getElementNameForCollectionField(fieldNode);
        String fieldName = fieldNode.getName();

        ClassNode elementRwType = DslAstHelper.getRwClassOf(defaultImpl);
        int visibility = DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (getFieldType(fieldNode) != FieldType.LINK) {
            if (isInstantiable(defaultImpl)) {
                createProxyMethod(methodName, "addNewDslElementToMap")
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .namedParams("values")
                        .constantParam(fieldName)
                        .constantClassParam(defaultImpl)
                        .constantPrimitveParam(false)
                        .optionalStringParam("key", elementKeyField != null)
                        .delegatingClosureParam(elementRwType)
                        .addTo(rwClass);
            }

            if (!isFinal(elementType)) {
                createProxyMethod(methodName, "addNewDslElementToMap")
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .namedParams("values")
                        .constantParam(fieldName)
                        .delegationTargetClassParam("typeToCreate", dslBaseType)
                        .constantPrimitveParam(true)
                        .optionalStringParam("key", elementKeyField != null)
                        .delegatingClosureParam()
                        .addTo(rwClass);
            }

            createProxyMethod(fieldName, KlumInstanceProxy.ADD_ELEMENTS_FROM_SCRIPTS_TO_MAP)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .constantParam(fieldName)
                    .arrayParam(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(ClassHelper.SCRIPT_TYPE)), "scripts")
                    .addTo(rwClass);
        }

        createProxyMethod(fieldName, "addElementsToMap")
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .constantParam(fieldName)
                .param(makeClassSafeWithGenerics(CommonAstHelper.COLLECTION_TYPE, new GenericsType(elementType)), "values")
                .addTo(rwClass);
        createProxyMethod(fieldName, "addElementsToMap")
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .constantParam(fieldName)
                .arrayParam(elementType, "values")
                .addTo(rwClass);

        createProxyMethod(methodName, "addElementToMap")
                .optional()
                .mod(visibility)
                .returning(elementType)
                .linkToField(fieldNode)
                .constantParam(fieldName)
                .constantParam(null)
                .param(elementType, elementToAddVarName)
                .addTo(rwClass);

        createAlternativesClassFor(fieldNode);
        createConverterMethods(fieldNode, methodName, false);
    }

    private ClosureExpression getTypedKeyMappingClosure(FieldNode fieldNode, ClassNode elementType) {
        AnnotationNode fieldAnnotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);

        if (fieldAnnotation == null)
            return null;

        ClosureExpression keyMappingClosure = getCodeClosureFor(fieldNode, fieldAnnotation, "keyMapping");
        if (keyMappingClosure != null) {
            keyMappingClosure = toStronglyTypedClosure(keyMappingClosure, elementType);
            // replace closure with strongly typed one
            fieldAnnotation.setMember("keyMapping", keyMappingClosure);
        }
        return keyMappingClosure;
    }

    private void createAlternativesClassFor(FieldNode fieldNode) {
        new AlternativesClassBuilder(this, fieldNode).invoke();
    }

    private void createSingleDSLObjectFieldCreationMethods(FieldNode fieldNode, String fieldName) {
        if (getFieldType(fieldNode) == FieldType.LINK) return;

        ClassNode targetFieldType = getTypeOfFieldOrMethod(fieldNode);
        ClassNode defaultImpl = getDefaultImplOfFieldOrMethod(fieldNode, targetFieldType);
        ClassNode dslBaseType = getDslBaseType(targetFieldType, defaultImpl);

        FieldNode targetTypeKeyField = getKeyField(dslBaseType);
        String targetKeyFieldName = targetTypeKeyField != null ? targetTypeKeyField.getName() : null;
        ClassNode targetRwType = DslAstHelper.getRwClassOf(defaultImpl);

        Expression keyProvider = getStaticKeyExpression(fieldNode);
        boolean needKeyParameter = targetTypeKeyField != null && keyProvider == null;

        int visibility = DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (isInstantiable(defaultImpl)) {
            createProxyMethod(fieldName, "createSingleChild")
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .constantParam(fieldName)
                    .constantClassParam(defaultImpl)
                    .constantPrimitveParam(false)
                    .optionalStringParam(targetKeyFieldName, needKeyParameter)
                    .delegatingClosureParam(targetRwType)
                    .addTo(rwClass);
        }

        if (!isFinal(targetFieldType)) {
            createProxyMethod(fieldName, "createSingleChild")
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .constantParam(fieldName)
                    .delegationTargetClassParam("typeToCreate", dslBaseType)
                    .constantPrimitveParam(true)
                    .optionalStringParam(targetKeyFieldName, needKeyParameter)
                    .delegatingClosureParam()
                    .addTo(rwClass);
        }
    }

    private ClassNode getDslBaseType(ClassNode targetFieldType, ClassNode defaultImpl) {
        if (targetFieldType.equals(defaultImpl))
            return targetFieldType;
        if (isDSLObject(targetFieldType))
            return targetFieldType;
        return defaultImpl;
    }

    private ClassNode getTypeOfFieldOrMethod(AnnotatedNode fieldNode) {
        if (fieldNode instanceof FieldNode)
            return ((FieldNode) fieldNode).getType();
        else if (fieldNode instanceof MethodNode)
            return ((MethodNode) fieldNode).getParameters()[0].getType();
        else
            throw new IllegalArgumentException("fieldNode must be either FieldNode or MethodNode");
    }

    private ClassNode getDefaultImplOfFieldOrMethod(AnnotatedNode fieldNode, ClassNode fieldType) {
        AnnotationNode fieldAnno = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);
        ClassNode defaultImpl = getNullSafeClassMember(fieldAnno, "defaultImpl", null);
        if (defaultImpl != null)
            return defaultImpl;
       return getNullSafeClassMember(getAnnotation(fieldType, DSL_CONFIG_ANNOTATION), "defaultImpl", fieldType);
    }

    private Expression getStaticKeyExpression(FieldNode fieldNode) {

        FieldNode targetKeyField = getKeyField(fieldNode.getType());
        if (targetKeyField == null)
            return null;

        ClassNode targetFieldType = fieldNode.getType();

        AnnotationNode fieldAnnotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);

        if (fieldAnnotation == null)
            return null;

        Expression keyMember = fieldAnnotation.getMember("key");

        if (keyMember instanceof ClassExpression) {
            ClassNode memberType = keyMember.getType();
            if (memberType.equals(ClassHelper.make(Field.FieldName.class)))
                return constX(fieldNode.getName());
            else
                addError("Field.key must contain either Field.FieldName or a Closure returning a " + targetFieldType.getNameWithoutPackage(), keyMember);
        } else if (keyMember instanceof ClosureExpression) {
            ClosureExpression keyProviderClosure = toStronglyTypedClosure((ClosureExpression) keyMember, annotatedClass);
            // replace closure with strongly typed one
            fieldAnnotation.setMember("key", keyProviderClosure);
            String keyGetterName = "$getStaticKeyFor$" + fieldNode.getName();
            createMethodFromClosure(
                    keyGetterName,
                    targetKeyField.getOriginType(),
                    keyProviderClosure,
                    propX(varX("this"), KlumInstanceProxy.NAME_OF_MODEL_FIELD_IN_RW_CLASS),
                    propX(varX("this"), KlumInstanceProxy.NAME_OF_MODEL_FIELD_IN_RW_CLASS)
            ).addTo(rwClass);

            return callX(varX("this"), keyGetterName);
        }

        return null;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isFinal(ClassNode classNode) {
        return (classNode.getModifiers() & ACC_FINAL) != 0;
    }

    private void createApplyMethods() {
        createProxyMethod("apply")
                .mod(ACC_PUBLIC)
                .returning(newClass(annotatedClass), null)
                .namedParams("values", null)
                .delegatingClosureParam(rwClass, null)
                .addTo(annotatedClass);

        createProxyMethod("applyLater")
                .mod(ACC_PUBLIC)
                .delegatingClosureParam(rwClass, null, null)
                .addTo(rwClass);

        createProxyMethod("applyLater")
                .mod(ACC_PUBLIC)
                .param(Integer_TYPE, "phase")
                .delegatingClosureParam(rwClass, null, null)
                .addTo(rwClass);

        createProxyMethod("applyLater")
                .mod(ACC_PUBLIC)
                .param(make(DefaultKlumPhase.class), "phase")
                .delegatingClosureParam(rwClass, null, null)
                .addTo(rwClass);
    }

    private void createFactoryField() {
        ClassNode defaultImpl = getNullSafeClassMember(getAnnotation(annotatedClass, DSL_CONFIG_ANNOTATION), "defaultImpl", annotatedClass);
        ClassNode factoryType = getFactoryBase(defaultImpl);

        boolean factoryIsGeneric = factoryType.redirect().getGenericsTypes() != null;

        InnerClassNode factoryClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + "$_Factory",
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                factoryIsGeneric ? makeClassSafeWithGenerics(factoryType, new GenericsType(defaultImpl)) : newClass(factoryType)
        );
        AnnoDocUtil.addDocumentation(factoryClass, "Factory for creating instances of " + annotatedClass.getName());

        DslAstHelper.registerAsVerbProvider(factoryClass);

        if (factoryIsGeneric)
            factoryClass.addConstructor(0, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY,
                    ctorSuperS(classX(annotatedClass)));

        overrideClosureMethods(factoryClass, defaultImpl);

        annotatedClass.getModule().addClass(factoryClass);

        FieldNode factoryField = new FieldNode(
                FACTORY_FIELD_NAME,
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                newClass(factoryClass),
                annotatedClass,
                ctorX(factoryClass)
        );

        AnnoDocUtil.addDocumentation(factoryField, "The factory for creating instances of " + annotatedClass.getName());
        factoryField.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class));
        factoryClass.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class));
        annotatedClass.addField(factoryField);
    }

    private ClassNode getFactoryBase(ClassNode defaultImpl) {
        ClassNode factoryBase = getMemberClassValue(dslAnnotation, "factory");
        if (factoryBase == null) factoryBase = getInnerClass(annotatedClass, "Factory");

        if (!isInstantiable(defaultImpl)) {
            if (factoryBase == null) return KLUM_FACTORY;
            if (!isAssignableTo(factoryBase, KLUM_FACTORY))
                addError("factory must be a KlumFactory", dslAnnotation);
            return factoryBase;
        }

        if (factoryBase == null) return keyField == null ? UNKEYED_FACTORY : KEYED_FACTORY;

        if (keyField != null && !isAssignableTo(factoryBase, KEYED_FACTORY))
            addError("keyed factory must extend " + KEYED_FACTORY.getName(), dslAnnotation);
        else if (keyField == null && !isAssignableTo(factoryBase, UNKEYED_FACTORY))
            addError("unkeyed factory must extend " + UNKEYED_FACTORY.getName(), dslAnnotation);
        return factoryBase;
    }

    private void overrideClosureMethods(InnerClassNode factoryClass, ClassNode defaultImpl) {
        ClassNode currentLevel = factoryClass.getSuperClass();

        while (currentLevel != null && factoryClass.isDerivedFrom(KLUM_FACTORY)) {
            for (MethodNode methodNode : currentLevel.getMethods()) {
                overrideUndelegatedClosureMethod(factoryClass, defaultImpl, methodNode);
            }
            currentLevel = currentLevel.getSuperClass();
        }
    }

    private void overrideUndelegatedClosureMethod(InnerClassNode factoryClass, ClassNode defaultImpl, MethodNode methodNode) {
        if (methodNode.getParameters().length == 0)
            return;
        Parameter lastParam = methodNode.getParameters()[methodNode.getParameters().length - 1];
        if (!lastParam.getType().equals(CLOSURE_TYPE))
            return;
        if (getAnnotation(lastParam, DELEGATES_TO_ANNOTATION) != null)
            return;

        AnnotationNode delegatesToRwAnnotation = getAnnotation(lastParam, DELEGATES_TO_RW_TYPE);
        if (delegatesToRwAnnotation != null) {
            ClassNode delegationTarget = getNullSafeClassMember(delegatesToRwAnnotation, "value", annotatedClass);
            if (!isDSLObject(delegationTarget))
                addError("delegatesToRw.value must be a DSL object", delegatesToRwAnnotation);

            DelegatesToRWTransformation.addDelegatesToAnnotation(delegationTarget, lastParam);
            return;
        }

        Parameter[] parameters = AstReflectionBridge.cloneParamsWithAdjustedNames(methodNode);
        Parameter closureParam = parameters[parameters.length - 1];

        AnnotationNode delegatesTo = new AnnotationNode(DELEGATES_TO_ANNOTATION);
        delegatesTo.setMember("value", classX(rwClass));
        delegatesTo.setMember("strategy", constX(Closure.DELEGATE_ONLY));
        closureParam.addAnnotation(delegatesTo);

        MethodNode newMethod = new MethodNode(
                methodNode.getName(),
                methodNode.getModifiers(),
                newClass(defaultImpl),
                parameters,
                methodNode.getExceptions(),
                returnS(callSuperX(methodNode.getName(), args(parameters)))
        );
        String originalDocumentation = ASTExtractor.extractDocumentation(methodNode, null);
        AnnoDocUtil.addDocumentation(newMethod, originalDocumentation);
        MethodNode existing = factoryClass.getDeclaredMethod(methodNode.getName(), parameters);
        if (existing == null)
            factoryClass.addMethod(newMethod);
    }

    private void createFactoryMethods() {
        if (!isInstantiable(annotatedClass))
            return;

        createFactoryMethod(CREATE_METHOD_NAME, annotatedClass)
                .forRemoval("Use Create.With() instead")
                .namedParams("values")
                .optionalStringParam("name", keyField != null)
                .delegatingClosureParam(rwClass)
                .addTo(annotatedClass);
    }

    @Deprecated(forRemoval = true)
    private void createConvenienceFactories() {
        String deprecationMessage = "Use Create.From(...) instead";
        createFactoryMethod(CREATE_FROM, annotatedClass)
                .forRemoval(deprecationMessage)
                .simpleClassParam("configType", ClassHelper.SCRIPT_TYPE, "The script to create the instance from")
                .addTo(annotatedClass);

        createFactoryMethod(CREATE_FROM, annotatedClass)
                .forRemoval(deprecationMessage)
                .optionalStringParam("name", keyField != null, "The key to use for the new instance")
                .stringParam("text", "The text to create the instance from")
                .optionalClassLoaderParam()
                .addTo(annotatedClass);

        createFactoryMethod(CREATE_FROM, annotatedClass)
                .forRemoval(deprecationMessage)
                .param(make(File.class), "src", "The file to create the instance from")
                .optionalClassLoaderParam()
                .addTo(annotatedClass);

        createFactoryMethod(CREATE_FROM, annotatedClass)
                .forRemoval(deprecationMessage)
                .param(make(URL.class), "src", "The URL to create the instance from")
                .optionalClassLoaderParam()
                .addTo(annotatedClass);

        createFactoryMethod(CREATE_FROM_CLASSPATH, annotatedClass)
                .forRemoval(deprecationMessage)
                .optionalClassLoaderParam()
                .addTo(annotatedClass);
    }

    private void createTemplateField() {
        FieldNode templateField = new FieldNode(
                TEMPLATE_FIELD_NAME,
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                newClass(TEMPLATE_TYPE),
                annotatedClass,
                ctorX(TEMPLATE_TYPE, args(classX(annotatedClass)))
        );

        AnnoDocUtil.addDocumentation(templateField, "Assign templates to new objects.");
        templateField.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class));
        annotatedClass.addField(templateField);
    }


    @SuppressWarnings({"unchecked", "java:S1872"})
    public <T extends Enum<?>> T getEnumMemberValue(AnnotationNode node, String name, Class<T> type, T defaultValue) {
        if (node == null) return defaultValue;

        final PropertyExpression member = (PropertyExpression) node.getMember(name);
        if (member == null)
            return defaultValue;

        if (!type.getName().equals(member.getObjectExpression().getType().getTypeClass().getName()))
            return defaultValue;

        try {
            String value = member.getPropertyAsString();
            Method fromString = type.getMethod("valueOf", String.class);
            return (T) fromString.invoke(null, value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public ClassNode getAnnotatedClass() {
        return annotatedClass;
    }
}
