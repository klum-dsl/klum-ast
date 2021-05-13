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

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.Field;
import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.Key;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.groovy.configdsl.transform.Validation;
import com.blackbuild.klum.ast.util.FactoryHelper;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.common.CommonAstHelper;
import com.blackbuild.klum.common.GenericsMethodBuilder.ClosureDefaultValue;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import groovy.util.DelegatingScript;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.MixinNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.VariableScope;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.GStringExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.classgen.VariableScopeVisitor;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.callMethodViaInvoke;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getCodeClosureFor;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getElementNameForCollectionField;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getKeyField;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getOwnerFieldNames;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getOwnerFields;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isInstantiable;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createMethodFromClosure;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createProtectedMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createPublicMethod;
import static com.blackbuild.klum.common.CommonAstHelper.COLLECTION_TYPE;
import static com.blackbuild.klum.common.CommonAstHelper.NO_EXCEPTIONS;
import static com.blackbuild.klum.common.CommonAstHelper.addCompileError;
import static com.blackbuild.klum.common.CommonAstHelper.addCompileWarning;
import static com.blackbuild.klum.common.CommonAstHelper.argsWithEmptyMapAndOptionalKey;
import static com.blackbuild.klum.common.CommonAstHelper.argsWithEmptyMapClassAndOptionalKey;
import static com.blackbuild.klum.common.CommonAstHelper.assertMethodIsParameterless;
import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static com.blackbuild.klum.common.CommonAstHelper.getElementType;
import static com.blackbuild.klum.common.CommonAstHelper.getGenericsTypes;
import static com.blackbuild.klum.common.CommonAstHelper.initializeCollectionOrMap;
import static com.blackbuild.klum.common.CommonAstHelper.isCollection;
import static com.blackbuild.klum.common.CommonAstHelper.isMap;
import static com.blackbuild.klum.common.CommonAstHelper.replaceProperties;
import static com.blackbuild.klum.common.CommonAstHelper.toStronglyTypedClosure;
import static com.blackbuild.klum.common.GenericsMethodBuilder.DEPRECATED_NODE;
import static org.codehaus.groovy.ast.ClassHelper.Boolean_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type;
import static org.codehaus.groovy.ast.ClassHelper.MAP_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.expr.MethodCallExpression.NO_ARGUMENTS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.andX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.attrX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callSuperX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callThisX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorSuperS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.eqX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.getInstanceProperties;
import static org.codehaus.groovy.ast.tools.GeneralUtils.hasDeclaredMethod;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.isInstanceOfX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.notX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.param;
import static org.codehaus.groovy.ast.tools.GeneralUtils.params;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt;
import static org.codehaus.groovy.ast.tools.GeneralUtils.throwS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.buildWildcardType;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafe;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics;
import static org.codehaus.groovy.ast.tools.GenericsUtils.newClass;
import static org.codehaus.groovy.transform.EqualsAndHashCodeASTTransformation.createEquals;
import static org.codehaus.groovy.transform.ToStringASTTransformation.createToString;

/**
 * Transformation class for the @DSL annotation.
 *
 * @author Stephan Pauxberger
 */
@SuppressWarnings({"WeakerAccess", "DefaultAnnotationParam"})
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DSLASTTransformation extends AbstractASTTransformation {

    public static final ClassNode DSL_CONFIG_ANNOTATION = make(DSL.class);
    public static final ClassNode DSL_FIELD_ANNOTATION = make(Field.class);
    public static final ClassNode VALIDATE_ANNOTATION = make(Validate.class);
    public static final ClassNode VALIDATION_ANNOTATION = make(Validation.class);
    public static final ClassNode KEY_ANNOTATION = make(Key.class);
    public static final ClassNode OWNER_ANNOTATION = make(Owner.class);
    public static final ClassNode FACTORY_HELPER = make(FactoryHelper.class);
    public static final ClassNode INSTANCE_PROXY = make(KlumInstanceProxy.class);

    public static final ClassNode EXCEPTION_TYPE = make(Exception.class);
    public static final ClassNode ASSERTION_ERROR_TYPE = make(AssertionError.class);
    public static final ClassNode MAP_ENTRY_TYPE = make(Map.Entry.class);

    public static final ClassNode EQUALS_HASHCODE_ANNOT = make(EqualsAndHashCode.class);
    public static final ClassNode TOSTRING_ANNOT = make(ToString.class);
    public static final String VALIDATE_METHOD = "validate";
    public static final String RW_CLASS_SUFFIX = "$_RW";
    public static final String RWCLASS_METADATA_KEY = DSLASTTransformation.class.getName() + ".rwclass";
    public static final String COLLECTION_FACTORY_METADATA_KEY = DSLASTTransformation.class.getName() + ".collectionFactory";
    public static final String NO_MUTATION_CHECK_METADATA_KEY = DSLASTTransformation.class.getName() + ".nomutationcheck";
    public static final String SETTER_NAME_METADATA_KEY = DSLASTTransformation.class.getName() + ".settername";
    public static final ClassNode DELEGATING_SCRIPT = ClassHelper.make(DelegatingScript.class);
    public static final String NAME_OF_MODEL_FIELD_IN_RW_CLASS = "this$0";
    public static final String FIELD_TYPE_METADATA = FieldType.class.getName();
    public static final String CREATE_FROM = "createFrom";
    public static final ClassNode INVOKER_HELPER_CLASS = ClassHelper.make(InvokerHelper.class);
    public static final String CREATE_METHOD_NAME = "create";
    public static final String CREATE_FROM_CLASSPATH = "createFromClasspath";
    public static final String SET_OWNERS_METHOD = "set$owner";
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

        if (annotatedClass.isInterface()) {
            return;
        }

        keyField = getKeyField(annotatedClass);
        ownerFields = getOwnerFields(annotatedClass);
        dslAnnotation = (AnnotationNode) nodes[0];

        if (isDSLObject(annotatedClass.getSuperClass()))
            dslParent = annotatedClass.getSuperClass();

        if (keyField != null)
            createKeyConstructor();
        else
            createExplicitEmptyConstructor();

        determineFieldTypes();

        warnIfAFieldIsNamedOwner();

        createRWClass();
        addDirectGettersForKeyField();

        setPropertyAccessors();
        createCanonicalMethods();
        validateFieldAnnotations();
        assertMembersNamesAreUnique();
        makeClassSerializable();
        createApplyMethods();
        createTemplateMethods();
        createFactoryMethods();
        createConvenienceFactories();
        createFieldDSLMethods();
        createValidateMethod();
        createDefaultMethods();
        moveMutatorsToRWClass();

        createSetOwnersMethod();

        delegateRwToModel();

        new VariableScopeVisitor(sourceUnit, true).visitClass(annotatedClass);
    }

    private void createExplicitEmptyConstructor() {
        annotatedClass.addConstructor(
                ACC_PROTECTED | ACC_SYNTHETIC,
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

    private void determineFieldTypes() {
        for (FieldNode fieldNode : annotatedClass.getFields()) {
            storeFieldType(fieldNode);
            warnIfInvalid(fieldNode);
        }
    }

    private void warnIfInvalid(FieldNode fieldNode) {
        if (fieldNode.getName().startsWith("$") && (fieldNode.getModifiers() & ACC_SYNTHETIC) != 0)
            addCompileWarning(sourceUnit, "fields starting with '$' are strongly discouraged", fieldNode);
    }

    private void addDirectGettersForKeyField() {
        if (keyField == null)
            return;
        if (annotatedClass != keyField.getOwner())
            return;
        createPublicMethod("get$key")
                .mod(ACC_FINAL)
                .returning(keyField.getType())
                .callMethod(classX(KlumInstanceProxy.class), "getKey", args("this"))
                .doReturn(keyField.getName())
                .addTo(annotatedClass);
    }

    private void moveMutatorsToRWClass() {
        new MutatorsHandler(annotatedClass).invoke();
    }

    private void setPropertyAccessors() {
        List<PropertyNode> newNodes = new ArrayList<PropertyNode>();
        for (PropertyNode pNode : getInstanceProperties(annotatedClass)) {
            adjustPropertyAccessorsForSingleField(pNode, newNodes);
        }

        setAccessorsForOwnerFields(newNodes);

        if (keyField != null)
            setAccessorsForKeyField();

        replaceProperties(annotatedClass, newNodes);
    }

    private void setAccessorsForKeyField() {
        String keyGetter = "get" + Verifier.capitalize(keyField.getName());
        createPublicMethod(keyGetter)
                .returning(keyField.getType())
                .doReturn(callX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), keyGetter))
                .addTo(rwClass);
    }

    private void setAccessorsForOwnerFields(List<PropertyNode> newNodes) {
        for (FieldNode ownerField : ownerFields)
            if (ownerField.getOwner() == annotatedClass)
                newNodes.add(setAccessorsForOwnerField(ownerField));
    }

    private PropertyNode setAccessorsForOwnerField(FieldNode ownerField) {
        String ownerFieldName = ownerField.getName();
        PropertyNode ownerProperty = annotatedClass.getProperty(ownerFieldName);
        ownerProperty.setSetterBlock(null);
        ownerProperty.setGetterBlock(stmt(attrX(varX("this"), constX(ownerFieldName))));

        String ownerGetter = "get" + Verifier.capitalize(ownerFieldName);
        createPublicMethod(ownerGetter)
                .returning(ownerField.getType())
                .doReturn(callX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), ownerGetter))
                .addTo(rwClass);

        return ownerProperty;
    }

    private void adjustPropertyAccessorsForSingleField(PropertyNode pNode, List<PropertyNode> newNodes) {
        if (shouldFieldBeIgnored(pNode.getField()))
            return;

        String capitalizedFieldName = Verifier.capitalize(pNode.getName());
        String getterName = "get" + capitalizedFieldName;
        String setterName = "set" + capitalizedFieldName;
        String rwGetterName;
        String rwSetterName = setterName + "$rw";

        if (CommonAstHelper.isCollectionOrMap(pNode.getType())) {
            rwGetterName = getterName + "$rw";

            pNode.setGetterBlock(stmt(callX(attrX(varX("this"), constX(pNode.getName())), "asImmutable")));

            createProtectedMethod(rwGetterName)
                    .mod(ACC_SYNTHETIC)
                    .returning(pNode.getType())
                    .doReturn(attrX(varX("this"), constX(pNode.getName())))
                    .addTo(annotatedClass);
        } else {
            rwGetterName = "get" + capitalizedFieldName;
            pNode.setGetterBlock(stmt(attrX(varX("this"), constX(pNode.getName()))));
        }

        // TODO what about protected methods?
        createPublicMethod(getterName)
                .returning(pNode.getType())
                .doReturn(callX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), rwGetterName))
                .addTo(rwClass);

        createProtectedMethod(rwSetterName)
                .mod(ACC_SYNTHETIC)
                .returning(ClassHelper.VOID_TYPE)
                .param(pNode.getType(), "value")
                .statement(assignS(attrX(varX("this"), constX(pNode.getName())), varX("value")))
                .addTo(annotatedClass);

        createMethod(setterName)
                .mod(isProtected(pNode.getField()) ? ACC_PROTECTED : ACC_PUBLIC)
                .returning(ClassHelper.VOID_TYPE)
                .param(pNode.getType(), "value")
                .statement(callX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), rwSetterName, args("value")))
                .addTo(rwClass);

        pNode.setSetterBlock(null);
        newNodes.add(pNode);
    }

    private void createRWClass() {
        ClassNode parentRW = getRwClassOfDslParent();

        rwClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + RW_CLASS_SUFFIX,
                ACC_PROTECTED | ACC_STATIC,
                parentRW != null ? parentRW : ClassHelper.OBJECT_TYPE,
                new ClassNode[] { make(Serializable.class)},
                MixinNode.EMPTY_ARRAY);

        // Need to explicitly add this field for non static inner classes (Groovy Bug?)
        FieldNode modelFieldInRwClass = rwClass.addField(NAME_OF_MODEL_FIELD_IN_RW_CLASS, ACC_FINAL | ACC_PRIVATE | ACC_SYNTHETIC, newClass(annotatedClass), null);


        BlockStatement block = new BlockStatement();
        if (parentRW != null)
            block.addStatement(ctorSuperS(varX("model")));
        block.addStatement(assignS(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), varX("model")));

        rwClass.addConstructor(
                ACC_PROTECTED,
                params(param(newClass(annotatedClass), "model")),
                ClassNode.EMPTY_ARRAY,
                block
        );

        annotatedClass.getModule().addClass(rwClass);
        annotatedClass.addField(KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS, ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, ctorX(rwClass, varX("this")));
        if (dslParent == null)
            annotatedClass.addField(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS, ACC_PROTECTED | ACC_SYNTHETIC | ACC_FINAL, INSTANCE_PROXY, ctorX(INSTANCE_PROXY, varX("this")));

        ClassNode parentProxy = annotatedClass.getNodeMetaData(RWCLASS_METADATA_KEY);
        if (parentProxy == null)
            annotatedClass.setNodeMetaData(RWCLASS_METADATA_KEY, rwClass);
        else
            parentProxy.setRedirect(rwClass);

        createCoercionMethod();
    }

    private void createCoercionMethod() {
        createPublicMethod("asType")
                .returning(OBJECT_TYPE)
                .param(makeClassSafe(Class.class), "type")
                .statement(
                        ifS(
                                eqX(varX("type"), classX(annotatedClass)),
                                returnS(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS))
                        )
                )
                .addTo(rwClass);
    }

    private ClassNode getRwClassOfDslParent() {
        if (dslParent == null)
            return null;

        return DslAstHelper.getRwClassOf(dslParent);
    }

    private void makeClassSerializable() {
        annotatedClass.addInterface(make(Serializable.class));
    }

    private void createDefaultMethods() {
        new DefaultMethods(this).execute();
    }

    private void createValidateMethod() {
        assertNoValidateMethodDeclared();
        checkValidateAnnotationsOnMethods();
        checkValidAnnotationsOnFields();

        Validation.Mode mode = getEnumMemberValue(getAnnotation(annotatedClass, VALIDATION_ANNOTATION), "mode", Validation.Mode.class, Validation.Mode.AUTOMATIC);

        // TODO: to proxy
        if (dslParent == null) {
            // add manual validation only to root of hierarchy
            // TODO field could be added to rw as well
            annotatedClass.addField("$manualValidation", ACC_PROTECTED | ACC_SYNTHETIC, ClassHelper.Boolean_TYPE, new ConstantExpression(mode == Validation.Mode.MANUAL));
            createPublicMethod("manualValidation")
                    .param(Boolean_TYPE, "validation", constX(true))
                    .assignS(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), "$manualValidation"), varX("validation"))
                    .addTo(rwClass);
        }

        createPublicMethod(VALIDATE_METHOD)
                .callMethod(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS, VALIDATE_METHOD)
                .addTo(annotatedClass);
    }

    private void checkValidAnnotationsOnFields() {
        annotatedClass.getFields().stream()
                .filter(fieldNode -> DslAstHelper.hasAnnotation(fieldNode, VALIDATE_ANNOTATION))
                .forEach(this::checkValidateAnnotationOnSingleField);
    }

    private void checkValidateAnnotationOnSingleField(FieldNode fieldNode) {
        AnnotationNode validateAnnotation = getAnnotation(fieldNode, VALIDATE_ANNOTATION);
        String message = getMemberStringValue(validateAnnotation, "message");
        Expression validationExpression = validateAnnotation.getMember("value");

        if (validationExpression == null)
            return;

        if (validationExpression instanceof ClosureExpression) {
            ClosureExpression validationClosure = toStronglyTypedClosure((ClosureExpression) validationExpression, fieldNode.getType());
            convertClosureExpressionToAssertStatement(fieldNode.getName(), validationClosure, message);
            // replace closure with strongly typed one
            validateAnnotation.setMember("value", validationClosure);
        }

        if (validationExpression instanceof ClassExpression) {
            ClassNode memberType = validationExpression.getType();
            if (!memberType.equals(ClassHelper.make(Validate.GroovyTruth.class)) && !memberType.equals(ClassHelper.make(Validate.Ignore.class)))
                addError("value of Validate must be either Validate.GroovyTruth, Validate.Ignore or a closure.", validateAnnotation);
        }
    }

    void convertClosureExpressionToAssertStatement(String fieldName, ClosureExpression closure, String message) {
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
                            "Field '" + fieldName + "' ($" + closureParameterName + ") is invalid",
                            Arrays.asList(constX("Field '" + fieldName + "' ("), constX(") is invalid")),
                            Collections.<Expression>singletonList(
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


    private void assertNoValidateMethodDeclared() {
        MethodNode existingValidateMethod = annotatedClass.getDeclaredMethod(VALIDATE_METHOD, Parameter.EMPTY_ARRAY);
        if (existingValidateMethod != null)
            addCompileError(sourceUnit, "validate() must not be declared, use @Validate methods instead.", existingValidateMethod);
    }

    private void checkValidateAnnotationsOnMethods() {
        annotatedClass.getMethods().forEach(this::checkValidateAnnotationOnSingleMethod);
    }

    private void assertAnnotationHasNoValueOrMessage(AnnotationNode annotation) {
        if (annotation.getMember("value") != null || annotation.getMember("message") != null)
            addCompileError(sourceUnit, "@Validate annotation on method must not have parameters!", annotation);
    }


    private AssertStatement assertStmt(Expression check, String message) {
        if (message == null) return new AssertStatement(new BooleanExpression(check), ConstantExpression.NULL);
        else return new AssertStatement(new BooleanExpression(check), new ConstantExpression(message));
    }

    @NotNull
    private ClosureExpression createGroovyTruthClosureExpression(VariableScope scope) {
        ClosureExpression result = new ClosureExpression(Parameter.EMPTY_ARRAY, returnS(varX("it")));
        result.setVariableScope(new VariableScope());
        return result;
    }

    private void validateFieldAnnotations() {
        for (FieldNode fieldNode : annotatedClass.getFields())
            validateSingleFieldAnnotation(fieldNode);
    }

    private void validateSingleFieldAnnotation(FieldNode fieldNode) {
        AnnotationNode annotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);
        if (annotation == null) return;

        if (CommonAstHelper.isCollectionOrMap(fieldNode.getType())) {
            validateFieldAnnotationOnCollection(annotation);
        } else {
            validateFieldAnnotationOnSingleField(fieldNode, annotation);
        }
    }

    private void validateFieldAnnotationOnSingleField(FieldNode fieldNode, AnnotationNode annotation) {
        if (annotation.getMember("members") != null)
            addCompileError(
                sourceUnit, String.format("@Field.members is only valid for List or Map fields, but field %s is of type %s", fieldNode.getName(), fieldNode.getType().getName()),
                annotation.getMember("members")
            );
        if (annotation.getMember("key") != null
                && (!isDSLObject(fieldNode.getType()) || getKeyField(fieldNode.getType()) == null))
            addCompileError(
                    sourceUnit, "@Field.key is only valid for keyed dsl fields",
                    annotation.getMember("key")
            );
    }

    private void validateFieldAnnotationOnCollection(AnnotationNode annotation) {
        if (annotation.getMember("key") != null)
            addCompileError(
                    sourceUnit,
                    "@Field.key is only allowed for non collection fields.",
                    annotation.getMember("key")
            );
    }

    private void assertMembersNamesAreUnique() {
        Map<String, FieldNode> allDslCollectionFieldNodesOfHierarchy = new HashMap<String, FieldNode>();

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


    private void createSetOwnersMethod() {

        BlockStatement methodBody = new BlockStatement();

        if (dslParent != null)
            methodBody.addStatement(stmt(callSuperX(SET_OWNERS_METHOD, varX("value"))));

        for (FieldNode ownerField : ownerFields) {
            methodBody.addStatement(
                    ifS(
                        andX(
                                isInstanceOfX(varX("value"), ownerField.getType()),
                                // access the field directly to prevent StackOverflow
                                notX(attrX(varX("this"), constX(ownerField.getName())))),
                        assignX(attrX(varX("this"), constX(ownerField.getName())), varX("value"))
                    )
            );
        }

        for (MethodNode ownerMethod : rwClass.getMethods()) {
            if (!DslAstHelper.hasAnnotation(ownerMethod, OWNER_ANNOTATION))
                continue;

            if (ownerMethod.getParameters().length != 1)
                addCompileError(String.format("Owner methods must have exactly one parameter. %s has %d", ownerMethod, ownerMethod.getParameters().length), ownerMethod);

            if (dslParent != null) {
                MethodNode superOwnerMethod = rwClass.getSuperClass().getDeclaredMethod(ownerMethod.getName(), ownerMethod.getParameters());
                if (superOwnerMethod != null && DslAstHelper.hasAnnotation(superOwnerMethod, OWNER_ANNOTATION))
                    addCompileWarning(sourceUnit, String.format("%s overrides %s, which might lead to unexpected results", ownerMethod, superOwnerMethod), ownerMethod);
            }

            ClassNode parameterType = ownerMethod.getParameters()[0].getOriginType();

            methodBody.addStatement(
                    ifS(
                            isInstanceOfX(varX("value"), parameterType),
                            callX(propX(varX("this"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), ownerMethod.getName(), varX("value"))
                    )
            );
        }

        // public since we owner and owned can be in different packages
        createPublicMethod(SET_OWNERS_METHOD)
                .param(OBJECT_TYPE, "value")
                .mod(ACC_SYNTHETIC)
                .statement(methodBody)
                .addTo(annotatedClass);
    }

    private void createKeyConstructor() {
        boolean hasKeyedParent = !keyField.getOwner().equals(annotatedClass);

        annotatedClass.addConstructor(
                ACC_PUBLIC,
                params(param(STRING_TYPE, "key")),
                CommonAstHelper.NO_EXCEPTIONS,
                block(
                        hasKeyedParent ? ctorSuperS(args("key")) : ctorSuperS(),
                        assignS(propX(varX("this"), keyField.getName()), varX("key"))
                )
        );
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
            createEquals(annotatedClass, true, dslParent != null, true, getAllTransientFields(), null);
        }
        if (!hasAnnotation(annotatedClass, TOSTRING_ANNOT)) {
            createToString(annotatedClass, false, true, getOwnerFieldNames(annotatedClass), null, false);
        }
    }

    private List<String> getAllTransientFields() {
        List<String> result = new ArrayList<>();
        for (FieldNode fieldNode : annotatedClass.getFields()) {
            if (fieldNode.getName().startsWith("$") || getFieldType(fieldNode) == FieldType.TRANSIENT)
                result.add(fieldNode.getName());
        }
        return result;
    }

    private void createHashCodeIfNotDefined() {
        if (hasDeclaredMethod(annotatedClass, "hashCode", 0))
            return;

        if (keyField != null) {
            createPublicMethod("hashCode")
                    .returning(ClassHelper.int_TYPE)
                    .doReturn(callX(varX(keyField.getName()), "hashCode"))
                    .addTo(annotatedClass);
        } else {
            createPublicMethod("hashCode")
                    .returning(ClassHelper.int_TYPE)
                    .doReturn(constX(0))
                    .addTo(annotatedClass);
        }
    }

    private void createFieldDSLMethods() {
        for (FieldNode fieldNode : annotatedClass.getFields())
            createDSLMethodsForSingleField(fieldNode);
        for (MethodNode methodNode : annotatedClass.getMethods()) {
            if (methodNode.getAnnotations(DSL_FIELD_ANNOTATION).isEmpty())
                continue;

            createDSLMethodsForVirtualFields(methodNode);
        }
    }

    private void createDSLMethodsForVirtualFields(MethodNode methodNode) {
        if (methodNode.getParameters().length != 1)
            addCompileError("Methods annotated with @Field need to have exactly one argument.", methodNode);

        String methodName = methodNode.getName();

        int index = DslAstHelper.findFirstUpperCaseCharacter(methodName);

        String fieldName = index == -1 ? methodName : Character.toLowerCase(methodName.charAt(index)) + methodName.substring(index + 1);

        ClassNode parameterType = methodNode.getParameters()[0].getType();
        FieldNode virtualField = new FieldNode(fieldName, ACC_PUBLIC, parameterType, annotatedClass, null);
        virtualField.addAnnotations(methodNode.getAnnotations());
        virtualField.setSourcePosition(methodNode);
        virtualField.setNodeMetaData(SETTER_NAME_METADATA_KEY, methodName);

        if (isDSLObject(parameterType))
            createSingleDSLObjectFieldCreationMethods(virtualField);

        createSingleFieldSetterMethod(virtualField);
    }

    private void createDSLMethodsForSingleField(FieldNode fieldNode) {
        if (shouldFieldBeIgnored(fieldNode)) return;
        if (getFieldType(fieldNode) == FieldType.IGNORED) return;

        if (isDSLObject(fieldNode.getType())) {
            if (getFieldType(fieldNode) != FieldType.LINK)
                createSingleDSLObjectFieldCreationMethods(fieldNode);
            createSingleFieldSetterMethod(fieldNode);
        } else if (isMap(fieldNode.getType()))
            createMapMethods(fieldNode);
        else if (isCollection(fieldNode.getType()))
            createCollectionMethods(fieldNode);
        else
            createSingleFieldSetterMethod(fieldNode);
    }

    private FieldType getFieldType(FieldNode fieldNode) {
        return fieldNode.getNodeMetaData(FIELD_TYPE_METADATA);
    }

    private void storeFieldType(FieldNode fieldNode) {
        FieldType type = CommonAstHelper.getNullSafeEnumMemberValue(getAnnotation(fieldNode, DSL_FIELD_ANNOTATION), "value", FieldType.DEFAULT);
        fieldNode.putNodeMetaData(FIELD_TYPE_METADATA, type);
    }

    private boolean isProtected(FieldNode fieldNode) {
        return getFieldType(fieldNode) == FieldType.PROTECTED;
    }

    @SuppressWarnings("RedundantIfStatement")
    boolean shouldFieldBeIgnored(FieldNode fieldNode) {
        if ((fieldNode.getModifiers() & ACC_SYNTHETIC) != 0) return true;
        if (isKeyField(fieldNode)) return true;
        if (isOwnerField(fieldNode)) return true;
        if (fieldNode.isFinal()) return true;
        if (fieldNode.getName().startsWith("$")) return true;
        if ((fieldNode.getModifiers() & ACC_TRANSIENT) != 0) return true;
        if (getFieldType(fieldNode) == FieldType.TRANSIENT) return true;
        return false;
    }

    private boolean isOwnerField(FieldNode fieldNode) {
        return !fieldNode.getAnnotations(OWNER_ANNOTATION).isEmpty();
    }

    private boolean isKeyField(FieldNode fieldNode) {
        return fieldNode == keyField;
    }

    private void createSingleFieldSetterMethod(FieldNode fieldNode) {
        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;
        String fieldName = fieldNode.getName();
        String setterName = fieldNode.getNodeMetaData(SETTER_NAME_METADATA_KEY);
        if (setterName == null)
            setterName = "set" + Verifier.capitalize(fieldName);

        createMethod(fieldName)
                .optional()
                .returning(fieldNode.getType())
                .mod(visibility)
                .linkToField(fieldNode)
                .decoratedParam(fieldNode, fieldNode.getType(), "value")
                .callMethod("this", setterName, args("value"))
                .setOwnersIf("value", isDSLObject(fieldNode.getType()))
                .doReturn("value")
                .addTo(rwClass);

        if (fieldNode.getType().equals(ClassHelper.boolean_TYPE)) {
            createMethod(fieldName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .callThis(fieldName, constX(true))
                    .addTo(rwClass);
        }

        createConverterMethods(fieldNode, fieldName, false);
    }

    private void createConverterMethods(FieldNode fieldNode, String methodName, boolean withKey) {
        if (getFieldType(fieldNode) != FieldType.LINK)
            new ConverterBuilder(this, fieldNode, methodName, withKey).execute();
    }

    private void createCollectionMethods(FieldNode fieldNode) {
        initializeCollectionOrMap(fieldNode);

        ClassNode elementType = getGenericsTypes(fieldNode)[0].getType();

        if (hasAnnotation(elementType, DSL_CONFIG_ANNOTATION))
            createCollectionOfDSLObjectMethods(fieldNode, elementType);
        else
            createCollectionOfSimpleElementsMethods(fieldNode, elementType);
    }

    private void createCollectionOfSimpleElementsMethods(FieldNode fieldNode, ClassNode elementType) {
        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        String elementName = getElementNameForCollectionField(fieldNode);
        createMethod(fieldNode.getName())
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .arrayParam(elementType, "values")
                .forS(
                    param(elementType, "$value"),
                    "values",
                    stmt(callThisX(elementName, varX("$value")))
                )
                .addTo(rwClass);

        createMethod(fieldNode.getName())
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .param(GenericsUtils.makeClassSafeWithGenerics(Iterable.class, elementType), "values")
                .forS(
                    param(elementType, "$value"),
                    "values",
                    stmt(callThisX(elementName, varX("$value")))
                )
                .addTo(rwClass);

        createMethod(elementName)
                .optional()
                .mod(visibility)
                .returning(elementType)
                .linkToField(fieldNode)
                .param(elementType, "value")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "add", varX("value")))
                .doReturn("value")
                .addTo(rwClass);

        createConverterMethods(fieldNode, elementName, false);
    }

    private void createCollectionOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        String methodName = getElementNameForCollectionField(fieldNode);
        ClassNode elementRwType = DslAstHelper.getRwClassOf(elementType);


        FieldNode fieldKey = getKeyField(elementType);

        warnIfSetWithoutKeyedElements(fieldNode, elementType, fieldKey);

        String fieldName = fieldNode.getName();
        String fieldRWName = fieldName + "$rw";

        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (getFieldType(fieldNode) != FieldType.LINK) {
            String fieldKeyName = fieldKey != null ? fieldKey.getName() : null;
            if (isInstantiable(elementType)) {
                createMethod(methodName)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .namedParams("values")
                        .optionalStringParam("key", fieldKey != null)
                        .delegatingClosureParam(elementRwType, ClosureDefaultValue.EMPTY_CLOSURE)
                        .declareVariable("created", callX(classX(elementType), "newInstance", optionalKeyArg(fieldKey, "key")))
                        .callMethod(propX(varX("created"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                        .setOwners("created")
                        .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "add", varX("created"))
                        .callMethod(propX(varX("created"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTCREATE_ANNOTATION_METHOD_NAME)
                        .callMethod("created", "apply", args("values", "closure"))
                        .doReturn("created")
                        .addTo(rwClass);

                createMethod(methodName)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .optionalStringParam(fieldKeyName, fieldKey != null)
                        .delegatingClosureParam(elementRwType, ClosureDefaultValue.EMPTY_CLOSURE)
                        .doReturn(callMethodViaInvoke(methodName, argsWithEmptyMapAndOptionalKey(fieldKeyName, "closure")))
                        .addTo(rwClass);
            }

            if (!isFinal(elementType)) {
                createMethod(methodName)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .namedParams("values")
                        .delegationTargetClassParam("typeToCreate", elementType)
                        .optionalStringParam("key", fieldKey != null)
                        .delegatingClosureParam()
                        .declareVariable("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(fieldKey, "key")))
                        .callMethod(propX(varX("created"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                        .setOwners("created")
                        .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "add", varX("created"))
                        .callMethod(propX(varX("created"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTCREATE_ANNOTATION_METHOD_NAME)
                        .callMethod("created", "apply", args("values", "closure"))
                        .doReturn("created")
                        .addTo(rwClass);
                createMethod(methodName)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .delegationTargetClassParam("typeToCreate", elementType)
                        .optionalStringParam(fieldKeyName, fieldKey != null)
                        .delegatingClosureParam()
                        .doReturn(callMethodViaInvoke(methodName, argsWithEmptyMapClassAndOptionalKey(fieldKeyName, "closure")))
                        .addTo(rwClass);
            }

            createMethod(fieldName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .arrayParam(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(ClassHelper.SCRIPT_TYPE)), "scripts")
                    .forS(
                            param(CLASS_Type, "script"),
                            "scripts",
                            stmt(callThisX(methodName, callX(elementType, CREATE_FROM, varX("script"))))
                    )
                    .addTo(rwClass);

        }

        createMethod(methodName)
                .optional()
                .mod(visibility)
                .returning(elementType)
                .linkToField(fieldNode)
                .param(elementType, "value")
                .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "add", varX("value"))
                .setOwners("value")
                .doReturn("value")
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

        ClassNode valueType = getElementType(fieldNode);

        if (isDSLObject(valueType))
            createMapOfDSLObjectMethods(fieldNode, valueType);
        else
            createMapOfSimpleElementsMethods(fieldNode, valueType);
    }

    private void createMapOfSimpleElementsMethods(FieldNode fieldNode, ClassNode valueType) {
        String methodName = fieldNode.getName();
        String singleElementMethod = getElementNameForCollectionField(fieldNode);

        ClassNode keyType = getGenericsTypes(fieldNode)[0].getType();

        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        ClosureExpression keyMappingClosure = getTypedKeyMappingClosure(fieldNode, valueType);

        if (keyMappingClosure == null) {
            createMethod(methodName)
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .param(makeClassSafeWithGenerics(MAP_TYPE, new GenericsType(keyType), new GenericsType(valueType)), "values")
                .forS(
                    param(makeClassSafeWithGenerics(MAP_ENTRY_TYPE, new GenericsType(keyType), new GenericsType(valueType)), "entry"),
                    "values",
                    stmt(callThisX(singleElementMethod, args(
                            propX(varX("entry"), "key"),
                            propX(varX("entry"), "value")))
                    )
                )
                .addTo(rwClass);
        } else {
            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .param(makeClassSafeWithGenerics(COLLECTION_TYPE, new GenericsType(valueType)), "values")
                    .forS(
                        param(valueType, "element"),
                        "values",
                        stmt(callThisX(singleElementMethod, varX("element")))
                    )
                    .addTo(rwClass);
            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .arrayParam(valueType, "values")
                    .forS(
                            param(valueType, "element"),
                            "values",
                            stmt(callThisX(singleElementMethod, varX("element")))
                    )
                    .addTo(rwClass);
        }

        Expression readKeyExpression;
        Parameter[] parameters;

        if (keyMappingClosure == null) {
            readKeyExpression = varX("key");
            parameters = params(param(keyType, "key"), param(valueType, "value"));
        } else {
            readKeyExpression = callX(keyMappingClosure, "call", args("value"));
            parameters = params(param(valueType, "value"));
        }

        createMethod(singleElementMethod)
                .optional()
                .mod(visibility)
                .returning(valueType)
                .linkToField(fieldNode)
                .params(parameters)
                .callMethod(propX(varX("this"), fieldNode.getName()), "put", args(readKeyExpression, varX("value")))
                .doReturn("value")
                .addTo(rwClass);

        createConverterMethods(fieldNode, singleElementMethod, true);
    }

    private void createMapOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        FieldNode elementKeyField = getKeyField(elementType);

        ClosureExpression keyMappingClosure = getTypedKeyMappingClosure(fieldNode, elementType);

        if (keyMappingClosure == null && elementKeyField == null) {
            addCompileError(
                    String.format("Value type of map %s (%s) has no key field and no keyMapping", fieldNode.getName(), elementType.getName()),
                    fieldNode
            );
            return;
        }

        String elementToAddVarName = "elementToAdd";

        String elementKeyFieldName = elementKeyField != null ? elementKeyField.getName() : null;
        Expression readKeyExpression = keyMappingClosure != null
                ? callX(keyMappingClosure, "call", args(elementToAddVarName))
                : propX(varX(elementToAddVarName), elementKeyFieldName);

        String methodName = getElementNameForCollectionField(fieldNode);

        String fieldName = fieldNode.getName();
        String fieldRWName = fieldName + "$rw";

        ClassNode elementRwType = DslAstHelper.getRwClassOf(elementType);
        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (getFieldType(fieldNode) != FieldType.LINK) {

            if (isInstantiable(elementType)) {
                createMethod(methodName)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .namedParams("values")
                        .optionalStringParam("key", elementKeyField != null)
                        .delegatingClosureParam(elementRwType, ClosureDefaultValue.EMPTY_CLOSURE)
                        .declareVariable(elementToAddVarName, callX(classX(elementType), "newInstance", optionalKeyArg(elementKeyField, "key")))
                        .callMethod(propX(varX(elementToAddVarName), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                        .setOwners(elementToAddVarName)
                        .callMethod(propX(varX(elementToAddVarName), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTCREATE_ANNOTATION_METHOD_NAME)
                        .callMethod(elementToAddVarName, "apply", args("values", "closure"))
                        .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "put", args(readKeyExpression, varX(elementToAddVarName)))
                        .doReturn(elementToAddVarName)
                        .addTo(rwClass);
                createMethod(methodName)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .optionalStringParam(elementKeyFieldName, elementKeyField != null)
                        .delegatingClosureParam(elementRwType, ClosureDefaultValue.EMPTY_CLOSURE)
                        .doReturn(callMethodViaInvoke(methodName, argsWithEmptyMapAndOptionalKey(elementKeyFieldName, "closure")))
                        .addTo(rwClass);
            }

            if (!isFinal(elementType)) {
                createMethod(methodName)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .namedParams("values")
                        .delegationTargetClassParam("typeToCreate", elementType)
                        .optionalStringParam("key", elementKeyField)
                        .delegatingClosureParam()
                        .declareVariable(elementToAddVarName, callX(varX("typeToCreate"), "newInstance", optionalKeyArg(elementKeyField, "key")))
                        .callMethod(propX(varX(elementToAddVarName), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                        .setOwners(elementToAddVarName)
                        .callMethod(propX(varX(elementToAddVarName), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTCREATE_ANNOTATION_METHOD_NAME)
                        .callMethod(elementToAddVarName, "apply", args("values", "closure"))
                        .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "put", args(readKeyExpression, varX(elementToAddVarName)))
                        .doReturn(elementToAddVarName)
                        .addTo(rwClass);
                createMethod(methodName)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementType)
                        .delegationTargetClassParam("typeToCreate", elementType)
                        .optionalStringParam(elementKeyFieldName, elementKeyField != null)
                        .delegatingClosureParam()
                        .doReturn(callMethodViaInvoke(methodName, argsWithEmptyMapClassAndOptionalKey(elementKeyFieldName, "closure")))
                        .addTo(rwClass);
            }

            createMethod(fieldName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .arrayParam(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(ClassHelper.SCRIPT_TYPE)), "scripts")
                    .forS(
                            param(CLASS_Type, "script"),
                            "scripts",
                            stmt(callThisX(methodName, callX(elementType, CREATE_FROM, varX("script"))))
                    )
                    .addTo(rwClass);
        }

        createMethod(methodName)
                .optional()
                .mod(visibility)
                .returning(elementType)
                .linkToField(fieldNode)
                .param(elementType, elementToAddVarName)
                .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "put", args(readKeyExpression, varX(elementToAddVarName)))
                .setOwners(elementToAddVarName)
                .doReturn(elementToAddVarName)
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
        new AlternativesClassBuilder(fieldNode).invoke();
    }

    private void createSingleDSLObjectFieldCreationMethods(FieldNode fieldNode) {
        String fieldName = fieldNode.getName();
        String setterName = fieldNode.getNodeMetaData(SETTER_NAME_METADATA_KEY);
        if (setterName == null)
            setterName = "set" + Verifier.capitalize(fieldName);

        ClassNode targetFieldType = fieldNode.getType();
        FieldNode targetTypeKeyField = getKeyField(targetFieldType);
        String targetKeyFieldName = targetTypeKeyField != null ? targetTypeKeyField.getName() : null;
        ClassNode targetRwType = DslAstHelper.getRwClassOf(targetFieldType);

        Expression keyProvider = getStaticKeyExpression(fieldNode);
        boolean needKeyParameter = targetTypeKeyField != null && keyProvider == null;

        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (isInstantiable(targetFieldType)) {
            createMethod(fieldName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .optionalStringParam(targetKeyFieldName, needKeyParameter)
                    .delegatingClosureParam(targetRwType, ClosureDefaultValue.EMPTY_CLOSURE)
                    .optionalDeclareVariable(targetKeyFieldName, keyProvider, keyProvider != null)
                    .declareVariable("created", callX(classX(targetFieldType), "newInstance", optionalKeyArg(targetKeyFieldName, targetKeyFieldName)))
                    .callMethod(propX(varX("created"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .setOwners("created")
                    .callMethod(propX(varX("created"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod(varX("created"), "apply", args("values", "closure"))
                    .callMethod("this", setterName, args("created"))
                    .doReturn("created")
                    .addTo(rwClass);

            createMethod(fieldName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .optionalStringParam(targetKeyFieldName, needKeyParameter)
                    .delegatingClosureParam(targetRwType, ClosureDefaultValue.EMPTY_CLOSURE)
                    .optionalDeclareVariable(targetKeyFieldName, keyProvider, keyProvider != null)
                    .doReturn(callMethodViaInvoke(fieldName, argsWithEmptyMapAndOptionalKey(needKeyParameter ? targetKeyFieldName : null, "closure")))
                    .addTo(rwClass);
        }

        if (!isFinal(targetFieldType)) {
            createMethod(fieldName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .delegationTargetClassParam("typeToCreate", targetFieldType)
                    .optionalStringParam(targetKeyFieldName, needKeyParameter)
                    .delegatingClosureParam()
                    .optionalDeclareVariable(targetKeyFieldName, keyProvider, keyProvider != null)
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(targetKeyFieldName, targetKeyFieldName)))
                    .callMethod(propX(varX("created"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .setOwners("created")
                    .callMethod(propX(varX("created"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod(varX("created"), "apply", args("values", "closure"))
                    .callMethod("this", setterName, args("created"))
                    .doReturn("created")
                    .addTo(rwClass);

            createMethod(fieldName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .delegationTargetClassParam("typeToCreate", targetFieldType)
                    .optionalStringParam(targetKeyFieldName, needKeyParameter)
                    .delegatingClosureParam()
                    .optionalDeclareVariable(targetKeyFieldName, keyProvider, keyProvider != null)
                    .doReturn(callMethodViaInvoke(fieldName, argsWithEmptyMapClassAndOptionalKey(targetKeyFieldName, "closure")))
                    .addTo(rwClass);
        }
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
                    propX(varX("this"), NAME_OF_MODEL_FIELD_IN_RW_CLASS),
                    propX(varX("this"), NAME_OF_MODEL_FIELD_IN_RW_CLASS)
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
        createPublicMethod("apply")
                .returning(newClass(annotatedClass))
                .namedParams("values")
                .delegatingClosureParam(rwClass, ClosureDefaultValue.EMPTY_CLOSURE)
                .callMethod(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS, "apply", args("values", "closure"))
                .addTo(annotatedClass);

        createPublicMethod("apply")
                .returning(newClass(annotatedClass))
                .delegatingClosureParam(rwClass, ClosureDefaultValue.NONE)
                .callThis("apply", args(new MapExpression(), varX("closure")))
                .addTo(annotatedClass);

        new LifecycleMethodBuilder(rwClass, KlumInstanceProxy.POSTAPPLY_ANNOTATION).invoke();
    }

    private void createFactoryMethods() {
        new LifecycleMethodBuilder(rwClass, KlumInstanceProxy.POSTCREATE_ANNOTATION).invoke();

        if (isInstantiable(annotatedClass)) {
            createPublicMethod(CREATE_METHOD_NAME)
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .namedParams("values")
                    .optionalStringParam("name", keyField)
                    .delegatingClosureParam(rwClass, ClosureDefaultValue.EMPTY_CLOSURE)
                    .declareVariable("result", keyField != null ? ctorX(annotatedClass, args("name")) : ctorX(annotatedClass))
                    .callMethod(propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .callMethod(propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("result", "apply", args("values", "closure"))
                    .callValidationOn("result")
                    .doReturn("result")
                    .addTo(annotatedClass);


            createPublicMethod(CREATE_METHOD_NAME)
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .optionalStringParam("name", keyField)
                    .delegatingClosureParam(rwClass, ClosureDefaultValue.EMPTY_CLOSURE)
                    .doReturn(callX(annotatedClass, CREATE_METHOD_NAME,
                            keyField != null ?
                                    args(new MapExpression(), varX("name"), varX("closure"))
                                    : args(new MapExpression(), varX("closure"))
                    ))
                    .addTo(annotatedClass);
        }
    }

    private void createConvenienceFactories() {
        if (isInstantiable(annotatedClass)) {
            createPublicMethod(CREATE_FROM)
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .simpleClassParam("configType", ClassHelper.SCRIPT_TYPE)
                    .statement(ifS(
                            notX(callX(classX(DELEGATING_SCRIPT), "isAssignableFrom", args("configType"))),
                            returnS(callX(callX(varX("configType"), "newInstance"), "run"))
                    ))
                    .doReturn(callX(annotatedClass, CREATE_FROM, callX(varX("configType"), "newInstance")))
                    .addTo(annotatedClass);
        } else {
            createPublicMethod(CREATE_FROM)
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .simpleClassParam("configType", ClassHelper.SCRIPT_TYPE)
                    .doReturn(callX(callX(varX("configType"), "newInstance"), "run"))
                    .addTo(annotatedClass);
        }

        if (keyField != null) {
            createPublicMethod(CREATE_FROM)
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .stringParam("name")
                    .stringParam("text")
                    .optionalClassLoaderParam()
                    .declareVariable("gloader", ctorX(ClassHelper.make(GroovyClassLoader.class), args("loader")))
                    .declareVariable("config", ctorX(ClassHelper.make(CompilerConfiguration.class)))
                    .assignS(propX(varX("config"), "scriptBaseClass"), constX(DelegatingScript.class.getName()))
                    .declareVariable("binding", ctorX(ClassHelper.make(Binding.class)))
                    .declareVariable("shell", ctorX(ClassHelper.make(GroovyShell.class), args("gloader", "binding", "config")))
                    .declareVariable("script", callX(varX("shell"), "parse", args("text", "name")))
                    .doReturn(callX(annotatedClass, CREATE_FROM, args("script")))
                    .addTo(annotatedClass);

            if (isInstantiable(annotatedClass))
                createPublicMethod(CREATE_FROM) // Delegating Script
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC | ACC_SYNTHETIC)
                    .param(DELEGATING_SCRIPT, "script")
                    .declareVariable("simpleName", callX(callX(varX("script"), "getClass"), "getSimpleName"))
                    .declareVariable("result", ctorX(annotatedClass, args("simpleName")))
                    .callMethod(propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .callMethod(propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("script", "setDelegate", propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS))
                    .callMethod("script", "run")
                    .callMethod(propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTAPPLY_ANNOTATION_METHOD_NAME)
                    .callValidationOn("result")
                    .doReturn("result")
                    .addTo(annotatedClass);
        } else {
            createPublicMethod(CREATE_FROM)
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .stringParam("text")
                    .optionalClassLoaderParam()
                    .declareVariable("gloader", ctorX(ClassHelper.make(GroovyClassLoader.class), args("loader")))
                    .declareVariable("config", ctorX(ClassHelper.make(CompilerConfiguration.class)))
                    .assignS(propX(varX("config"), "scriptBaseClass"), constX(DelegatingScript.class.getName()))
                    .declareVariable("binding", ctorX(ClassHelper.make(Binding.class)))
                    .declareVariable("shell", ctorX(ClassHelper.make(GroovyShell.class), args("gloader", "binding", "config")))
                    .declareVariable("script", callX(varX("shell"), "parse", args("text")))
                    .doReturn(callX(annotatedClass, CREATE_FROM, args("script")))
                    .addTo(annotatedClass);

            if (isInstantiable(annotatedClass))
                createPublicMethod(CREATE_FROM) // Delegating Script
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC | ACC_SYNTHETIC)
                    .param(newClass(DELEGATING_SCRIPT), "script")
                    .declareVariable("result", ctorX(annotatedClass))
                    .callMethod(propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .callMethod(propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("script", "setDelegate", propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS))
                    .callMethod("script", "run")
                    .callMethod(propX(varX("result"), KlumInstanceProxy.NAME_OF_RW_FIELD_IN_MODEL_CLASS), KlumInstanceProxy.POSTAPPLY_ANNOTATION_METHOD_NAME)
                    .callValidationOn("result")
                    .doReturn("result")
                    .addTo(annotatedClass);
        }

        createPublicMethod(CREATE_FROM)
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .param(make(File.class), "src")
                .optionalClassLoaderParam()
                .doReturn(callX(annotatedClass, CREATE_FROM, args(callX(callX(varX("src"), "toURI"), "toURL"), varX("loader"))))
                .addTo(annotatedClass);

        createPublicMethod(CREATE_FROM)
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .param(make(URL.class), "src")
                .optionalClassLoaderParam()
                .declareVariable("text", propX(varX("src"), "text"))
                .doReturn(callX(annotatedClass, CREATE_FROM, keyField != null ? args(propX(varX("src"), "path"), varX("text"), varX("loader")) : args("text", "loader")))
                .addTo(annotatedClass);

        createPublicMethod(CREATE_FROM_CLASSPATH)
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .doReturn(callX(FACTORY_HELPER, "createFromClasspath", classX(annotatedClass)))
                .addTo(annotatedClass);
    }

    @SuppressWarnings("unchecked")
    public <T extends Enum> T getEnumMemberValue(AnnotationNode node, String name, Class<T> type, T defaultValue) {
        if (node == null) return defaultValue;

        final PropertyExpression member = (PropertyExpression) node.getMember(name);
        if (member == null)
            return defaultValue;

        if (!type.equals(member.getObjectExpression().getType().getTypeClass()))
            return defaultValue;

        try {
            String value = member.getPropertyAsString();
            Method fromString = type.getMethod("valueOf", String.class);
            return (T) fromString.invoke(null, value);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void checkValidateAnnotationOnSingleMethod(MethodNode method) {
        AnnotationNode validateAnnotation = getAnnotation(method, VALIDATE_ANNOTATION);
        if (validateAnnotation == null)
            return;
        assertMethodIsParameterless(method, sourceUnit);
        assertAnnotationHasNoValueOrMessage(validateAnnotation);
    }
}
