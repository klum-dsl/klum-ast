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

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.Field;
import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.Key;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import com.blackbuild.groovy.configdsl.transform.Validate;
import com.blackbuild.groovy.configdsl.transform.Validation;
import com.blackbuild.klum.ast.util.FactoryHelper;
import com.blackbuild.klum.common.CommonAstHelper;
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
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.CatchStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ThrowStatement;
import org.codehaus.groovy.ast.stmt.TryCatchStatement;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.classgen.VariableScopeVisitor;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getElementNameForCollectionField;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getKeyField;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getOwnerField;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getOwnerFieldName;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.ClosureDefaultValue;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createProtectedMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createPublicMethod;
import static com.blackbuild.klum.common.CommonAstHelper.addCompileWarning;
import static com.blackbuild.klum.common.CommonAstHelper.argsWithEmptyMapAndOptionalKey;
import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static com.blackbuild.klum.common.CommonAstHelper.initializeCollectionOrMap;
import static com.blackbuild.klum.common.CommonAstHelper.replaceProperties;
import static org.codehaus.groovy.ast.ClassHelper.Boolean_TYPE;
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
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
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
@SuppressWarnings("WeakerAccess")
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DSLASTTransformation extends AbstractASTTransformation {

    public static final ClassNode DSL_CONFIG_ANNOTATION = make(DSL.class);
    public static final ClassNode DSL_FIELD_ANNOTATION = make(Field.class);
    public static final ClassNode VALIDATE_ANNOTATION = make(Validate.class);
    public static final ClassNode VALIDATION_ANNOTATION = make(Validation.class);
    public static final ClassNode POSTAPPLY_ANNOTATION = make(PostApply.class);
    public static final String POSTAPPLY_ANNOTATION_METHOD_NAME = "$" + POSTAPPLY_ANNOTATION.getNameWithoutPackage();
    public static final ClassNode POSTCREATE_ANNOTATION = make(PostCreate.class);
    public static final String POSTCREATE_ANNOTATION_METHOD_NAME = "$" + POSTCREATE_ANNOTATION.getNameWithoutPackage();
    public static final ClassNode KEY_ANNOTATION = make(Key.class);
    public static final ClassNode OWNER_ANNOTATION = make(Owner.class);
    public static final ClassNode FACTORY_HELPER = make(FactoryHelper.class);


    public static final ClassNode EXCEPTION_TYPE = make(Exception.class);
    public static final ClassNode VALIDATION_EXCEPTION_TYPE = make(IllegalStateException.class);
    public static final ClassNode ASSERTION_ERROR_TYPE = make(AssertionError.class);

    public static final ClassNode EQUALS_HASHCODE_ANNOT = make(EqualsAndHashCode.class);
    public static final ClassNode TOSTRING_ANNOT = make(ToString.class);
    public static final String VALIDATE_METHOD = "validate";
    public static final String RW_CLASS_SUFFIX = "$_RW";
    public static final String RWCLASS_METADATA_KEY = DSLASTTransformation.class.getName() + ".rwclass";
    public static final String COLLECTION_FACTORY_METADATA_KEY = DSLASTTransformation.class.getName() + ".collectionFactory";
    public static final String NO_MUTATION_CHECK_METADATA_KEY = DSLASTTransformation.class.getName() + ".nomutationcheck";
    public static final ClassNode DELEGATING_SCRIPT = ClassHelper.make(DelegatingScript.class);
    public static final String NAME_OF_MODEL_FIELD_IN_RW_CLASS = "this$0";
    public static final String NAME_OF_RW_FIELD_IN_MODEL_CLASS = "$rw";
    public static final String FIELD_TYPE_METADATA = FieldType.class.getName();
    public static final String CREATE_FROM = "createFrom";
    ClassNode annotatedClass;
    ClassNode dslParent;
    FieldNode keyField;
    FieldNode ownerField;
    AnnotationNode dslAnnotation;
    InnerClassNode rwClass;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);

        annotatedClass = (ClassNode) nodes[1];

        if (annotatedClass.isInterface())
            return;

        keyField = getKeyField(annotatedClass);
        ownerField = getOwnerField(annotatedClass);
        dslAnnotation = (AnnotationNode) nodes[0];

        if (isDSLObject(annotatedClass.getSuperClass()))
            dslParent = annotatedClass.getSuperClass();

        if (keyField != null)
            createKeyConstructor();

        determineFieldTypes();

        warnIfAFieldIsNamedOwner();

        createRWClass();
        addDirectGettersForOwnerAndKeyFields();

        setPropertyAccessors();
        createCanonicalMethods();
        validateFieldAnnotations();
        assertMembersNamesAreUnique();
        makeClassSerializable();
        createApplyMethods();
        createTemplateMethods();
        createFactoryMethods();
        createFieldDSLMethods();
        createValidateMethod();
        createDefaultMethods();
        moveMutatorsToRWClass();

        if (annotatedClassHoldsOwner())
            preventOwnerOverride();

        new VariableScopeVisitor(sourceUnit, true).visitClass(annotatedClass);
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

    private void addDirectGettersForOwnerAndKeyFields() {
        createDirectGetterFor(keyField, "get$key");
        createDirectGetterFor(ownerField, "get$owner");
    }

    private void createDirectGetterFor(FieldNode targetField, String getterName) {
        if (targetField == null)
            return;
        if (annotatedClass != targetField.getOwner())
            return;
        createPublicMethod(getterName)
                .mod(ACC_FINAL)
                .returning(targetField.getType())
                .doReturn(targetField.getName())
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

        if (annotatedClassHoldsOwner())
            newNodes.add(setAccessorsForOwnerField());

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

    private PropertyNode setAccessorsForOwnerField() {
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
                0,
                parentRW != null ? parentRW : ClassHelper.OBJECT_TYPE,
                new ClassNode[] { make(Serializable.class)},
                new MixinNode[0]);

        // Need to explicitly add this field for non static inner classes (Groovy Bug?)
        rwClass.addField(NAME_OF_MODEL_FIELD_IN_RW_CLASS, ACC_FINAL | ACC_PRIVATE | ACC_SYNTHETIC, newClass(annotatedClass), null);

        annotatedClass.getModule().addClass(rwClass);
        annotatedClass.addField(NAME_OF_RW_FIELD_IN_MODEL_CLASS, ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, ctorX(rwClass, varX("this")));
        annotatedClass.setNodeMetaData(RWCLASS_METADATA_KEY, rwClass);

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

        Validation.Mode mode = getEnumMemberValue(CommonAstHelper.getAnnotation(annotatedClass, VALIDATION_ANNOTATION), "mode", Validation.Mode.class, Validation.Mode.AUTOMATIC);

        if (dslParent == null) {
            // add manual validation only to root of hierarchy
            // TODO field could be added to rw as well
            annotatedClass.addField("$manualValidation", ACC_PROTECTED | ACC_SYNTHETIC, ClassHelper.Boolean_TYPE, new ConstantExpression(mode == Validation.Mode.MANUAL));
            createPublicMethod("manualValidation")
                    .param(Boolean_TYPE, "validation", constX(true))
                    .assignS(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), "$manualValidation"), varX("validation"))
                    .addTo(rwClass);
        }

        DslMethodBuilder methodBuilder = createPublicMethod(VALIDATE_METHOD);

        if (dslParent != null) {
            methodBuilder.statement(callSuperX(VALIDATE_METHOD));
        }

        BlockStatement block = new BlockStatement();
        validateFields(block);
        validateCustomMethods(block);

        TryCatchStatement tryCatchStatement = new TryCatchStatement(block, EmptyStatement.INSTANCE);
        tryCatchStatement.addCatch(new CatchStatement(
                param(ASSERTION_ERROR_TYPE, "e"),
                new ThrowStatement(ctorX(VALIDATION_EXCEPTION_TYPE, args(propX(varX("e"), "message"), varX("e"))))
                )
        );
        tryCatchStatement.addCatch(new CatchStatement(
                param(EXCEPTION_TYPE, "e"),
                new ThrowStatement(ctorX(VALIDATION_EXCEPTION_TYPE, args(propX(varX("e"), "message"), varX("e"))))
                )
        );

        methodBuilder
                .statement(tryCatchStatement)
                .addTo(annotatedClass);
    }

    private void assertNoValidateMethodDeclared() {
        MethodNode existingValidateMethod = annotatedClass.getDeclaredMethod(VALIDATE_METHOD, Parameter.EMPTY_ARRAY);
        if (existingValidateMethod != null)
            CommonAstHelper.addCompileError(sourceUnit, "validate() must not be declared, use @Validate methods instead.", existingValidateMethod);
    }

    private void validateCustomMethods(BlockStatement block) {
        warnIfUnannotatedDoValidateMethod();

        for (MethodNode method : annotatedClass.getMethods()) {
            AnnotationNode validateAnnotation = CommonAstHelper.getAnnotation(method, VALIDATE_ANNOTATION);
            if (validateAnnotation == null) continue;

            CommonAstHelper.assertMethodIsParameterless(method, sourceUnit);
            assertAnnotationHasNoValueOrMessage(validateAnnotation);

            block.addStatement(stmt(callX(varX("this"), method.getName())));
        }
    }

    private void assertAnnotationHasNoValueOrMessage(AnnotationNode annotation) {
        if (annotation.getMember("value") != null || annotation.getMember("message") != null)
            CommonAstHelper.addCompileError(sourceUnit, "@Validate annotation on method must not have parameters!", annotation);
    }

    private void warnIfUnannotatedDoValidateMethod() {
        MethodNode doValidate = annotatedClass.getMethod("doValidate", Parameter.EMPTY_ARRAY);

        if (doValidate == null) return;

        if (CommonAstHelper.getAnnotation(doValidate, VALIDATE_ANNOTATION) != null) return;

        CommonAstHelper.addCompileWarning(sourceUnit, "Using doValidation() is deprecated, mark validation methods with @Validate", doValidate);
        doValidate.addAnnotation(new AnnotationNode(VALIDATE_ANNOTATION));
    }

    private void validateFields(BlockStatement block) {
        Validation.Option mode = getEnumMemberValue(
                CommonAstHelper.getAnnotation(annotatedClass, VALIDATION_ANNOTATION),
                "option",
                Validation.Option.class,
                Validation.Option.IGNORE_UNMARKED);
        for (final FieldNode fieldNode : annotatedClass.getFields()) {
            if (shouldFieldBeIgnoredForValidation(fieldNode)) continue;

            ClosureExpression validationClosure = createGroovyTruthClosureExpression(block.getVariableScope());
            String message = null;

            AnnotationNode validateAnnotation = CommonAstHelper.getAnnotation(fieldNode, VALIDATE_ANNOTATION);
            if (validateAnnotation != null) {
                message = getMemberStringValue(validateAnnotation, "message", "'" + fieldNode.getName() + "' must be set!");
                Expression member = validateAnnotation.getMember("value");
                if (member instanceof ClassExpression) {
                    ClassNode memberType = member.getType();
                    if (memberType.equals(ClassHelper.make(Validate.Ignore.class)))
                        continue;
                    else if (!memberType.equals(ClassHelper.make(Validate.GroovyTruth.class))) {
                        addError("value of Validate must be either Validate.GroovyTruth, Validate.Ignore or a closure.", validateAnnotation);
                    }
                } else if (member instanceof ClosureExpression){
                    validationClosure = (ClosureExpression) member;
                    ClassNode fieldNodeType = fieldNode.getType();
                    validationClosure = CommonAstHelper.toStronglyTypedClosure(validationClosure, fieldNodeType);
                    // replace closure with strongly typed one
                    validateAnnotation.setMember("value", validationClosure);
                }
            }

            if (validateAnnotation != null || mode == Validation.Option.VALIDATE_UNMARKED) {
                block.addStatement(new AssertStatement(
                        new BooleanExpression(
                                callX(validationClosure, "call", args(varX(fieldNode.getName())))
                        ), message == null ? ConstantExpression.NULL : new ConstantExpression(message)
                ));
            }
        }
    }

    @NotNull
    private ClosureExpression createGroovyTruthClosureExpression(VariableScope scope) {
        ClosureExpression result = new ClosureExpression(Parameter.EMPTY_ARRAY, returnS(varX("it")));
        result.setVariableScope(new VariableScope());
        return result;
    }

    private boolean annotatedClassHoldsOwner() {
        return ownerField != null && ownerField.getOwner() == annotatedClass;
    }

    private void validateFieldAnnotations() {
        for (FieldNode fieldNode : annotatedClass.getFields()) {
            AnnotationNode annotation = CommonAstHelper.getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);

            if (annotation == null) continue;

            if (CommonAstHelper.isCollectionOrMap(fieldNode.getType())) return;

            if (annotation.getMember("members") != null) {
                CommonAstHelper.addCompileError(
                        sourceUnit, String.format("@Field.members is only valid for List or Map fields, but field %s is of type %s", fieldNode.getName(), fieldNode.getType().getName()),
                        annotation
                );
            }
        }
    }

    private void assertMembersNamesAreUnique() {
        Map<String, FieldNode> allDslCollectionFieldNodesOfHierarchy = new HashMap<String, FieldNode>();

        for (ClassNode level : DslAstHelper.getHierarchyOfDSLObjectAncestors(annotatedClass)) {
            for (FieldNode field : level.getFields()) {
                if (!CommonAstHelper.isCollectionOrMap(field.getType())) continue;

                String memberName = getElementNameForCollectionField(field);

                FieldNode conflictingField = allDslCollectionFieldNodesOfHierarchy.get(memberName);

                if (conflictingField != null) {
                    CommonAstHelper.addCompileError(
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


    private void preventOwnerOverride() {
        // public since we owner and owned can be in different packages
        createPublicMethod("set$owner")
                .param(OBJECT_TYPE, "value")
                .mod(ACC_SYNTHETIC | ACC_FINAL)
                .statement(
                        ifS(
                                andX(
                                        isInstanceOfX(varX("value"), ownerField.getType()),
                                        // access the field directly to prevent StackOverflow
                                        notX(attrX(varX("this"), constX(ownerField.getName())))),
                                assignX(attrX(varX("this"), constX(ownerField.getName())), varX("value"))
                        )
                )
                .addTo(annotatedClass);
    }

    private void createKeyConstructor() {
        annotatedClass.addConstructor(
                ACC_PUBLIC,
                params(param(STRING_TYPE, "key")),
                CommonAstHelper.NO_EXCEPTIONS,
                block(
                        dslParent != null ? ctorSuperS(args("key")) : ctorSuperS(),
                        assignS(propX(varX("this"), keyField.getName()), varX("key"))
                )
        );
        keyField.setModifiers(keyField.getModifiers() | ACC_FINAL);
    }

    private void createCanonicalMethods() {
        if (!hasAnnotation(annotatedClass, EQUALS_HASHCODE_ANNOT)) {
            createHashCodeIfNotDefined();
            createEquals(annotatedClass, true, dslParent != null, true, getAllTransientFields(), null);
        }
        if (!hasAnnotation(annotatedClass, TOSTRING_ANNOT)) {
            if (ownerField == null)
                createToString(annotatedClass, false, true, null, null, false);
            else
                createToString(annotatedClass, false, true, Collections.singletonList(ownerField.getName()), null, false);
        }
    }

    private List<String> getAllTransientFields() {
        List<String> result = new ArrayList<>();
        for (FieldNode fieldNode : annotatedClass.getFields()) {
            if (fieldNode.getName().startsWith("$") || fieldNode.getNodeMetaData(FIELD_TYPE_METADATA) == FieldType.TRANSIENT)
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
    }

    private void createDSLMethodsForSingleField(FieldNode fieldNode) {
        if (shouldFieldBeIgnored(fieldNode)) return;

        if (hasAnnotation(fieldNode.getType(), DSL_CONFIG_ANNOTATION)) {
            createSingleDSLObjectClosureMethod(fieldNode);
            createSingleFieldSetterMethod(fieldNode);
        } else if (CommonAstHelper.isMap(fieldNode.getType()))
            createMapMethods(fieldNode);
        else if (CommonAstHelper.isCollection(fieldNode.getType()))
            createCollectionMethods(fieldNode);
        else
            createSingleFieldSetterMethod(fieldNode);
    }

    private void storeFieldType(FieldNode fieldNode) {
        FieldType type = CommonAstHelper.getNullSafeEnumMemberValue(getAnnotation(fieldNode, DSL_FIELD_ANNOTATION), "value", FieldType.DEFAULT);
        fieldNode.putNodeMetaData(FIELD_TYPE_METADATA, type);
    }

    private boolean isProtected(FieldNode fieldNode) {
        return fieldNode.getNodeMetaData(FIELD_TYPE_METADATA) == FieldType.PROTECTED;
    }

    @SuppressWarnings("RedundantIfStatement")
    boolean shouldFieldBeIgnored(FieldNode fieldNode) {
        if ((fieldNode.getModifiers() & ACC_SYNTHETIC) != 0) return true;
        if (isKeyField(fieldNode)) return true;
        if (isOwnerField(fieldNode)) return true;
        if (fieldNode.isFinal()) return true;
        if (fieldNode.getName().startsWith("$")) return true;
        if ((fieldNode.getModifiers() & ACC_TRANSIENT) != 0) return true;
        if (fieldNode.getNodeMetaData(FIELD_TYPE_METADATA) == FieldType.TRANSIENT) return true;
        return false;
    }

    private boolean isOwnerField(FieldNode fieldNode) {
        return fieldNode == ownerField;
    }

    private boolean isKeyField(FieldNode fieldNode) {
        return fieldNode == keyField;
    }

    boolean shouldFieldBeIgnoredForValidation(FieldNode fieldNode) {
        if (fieldNode.getName().startsWith("$")) return true;
        if ((fieldNode.getModifiers() & ACC_TRANSIENT) != 0) return true;
        return false;
    }

    private void createSingleFieldSetterMethod(FieldNode fieldNode) {
        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;
        String fieldName = fieldNode.getName();

        createMethod(fieldName)
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .param(fieldNode.getType(), "value")
                .assignToProperty(fieldName, varX("value"))
                .addTo(rwClass);

        if (fieldNode.getType().equals(ClassHelper.boolean_TYPE)) {
            createMethod(fieldName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .callThis(fieldName, constX(true))
                    .addTo(rwClass);
        }
    }

    private void createCollectionMethods(FieldNode fieldNode) {
        initializeCollectionOrMap(fieldNode);

        ClassNode elementType = CommonAstHelper.getGenericsTypes(fieldNode)[0].getType();

        if (hasAnnotation(elementType, DSL_CONFIG_ANNOTATION))
            createCollectionOfDSLObjectMethods(fieldNode, elementType);
        else
            createCollectionOfSimpleElementsMethods(fieldNode, elementType);
    }

    private void createCollectionOfSimpleElementsMethods(FieldNode fieldNode, ClassNode elementType) {
        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        createMethod(fieldNode.getName())
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .arrayParam(elementType, "values")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "addAll", varX("values")))
                .addTo(rwClass);

        createMethod(fieldNode.getName())
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .param(GenericsUtils.makeClassSafeWithGenerics(Iterable.class, elementType), "values")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "addAll", varX("values")))
                .addTo(rwClass);

        createMethod(getElementNameForCollectionField(fieldNode))
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .param(elementType, "value")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "add", varX("value")))
                .addTo(rwClass);
    }

    private void createCollectionOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        String methodName = getElementNameForCollectionField(fieldNode);
        ClassNode elementRwType = DslAstHelper.getRwClassOf(elementType);


        FieldNode fieldKey = getKeyField(elementType);

        boolean targetHasOwnerField = getOwnerFieldName(elementType) != null;
        warnIfSetWithoutKeyedElements(fieldNode, elementType, fieldKey);

        String fieldName = fieldNode.getName();
        String fieldRWName = fieldName + "$rw";

        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;
        if (!!DslAstHelper.isInstantiable(elementType)) {
            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementRwType, ClosureDefaultValue.EMPTY_CLOSURE)
                    .declareVariable("created", callX(classX(elementType), "newInstance", optionalKeyArg(fieldKey)))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionallySetOwnerOnS("created", targetHasOwnerField)
                    .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "add", varX("created"))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);

            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .doReturn(callThisX(methodName, argsWithEmptyMapAndOptionalKey(fieldKey, "closure")))
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
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam()
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(fieldKey)))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionallySetOwnerOnS("created", targetHasOwnerField)
                    .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "add", varX("created"))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);
            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .delegationTargetClassParam("typeToCreate", elementType)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam()
                    .doReturn(callThisX(methodName, CommonAstHelper.argsWithEmptyMapClassAndOptionalKey(fieldKey, "closure")))
                    .addTo(rwClass);
        }

        createMethod(methodName)
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .param(elementType, "value")
                .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "add", varX("value"))
                .optionallySetOwnerOnS("value", targetHasOwnerField)
                .addTo(rwClass);

        createAlternativesClassFor(fieldNode);
    }

    private void warnIfSetWithoutKeyedElements(FieldNode fieldNode, ClassNode elementType, FieldNode fieldKey) {
        if (fieldNode.getType().getNameWithoutPackage().equals("Set") && fieldKey == null) {
            CommonAstHelper.addCompileWarning(sourceUnit,
                    String.format(
                            "WARNING: Field %s.%s is of type Set<%s>, but %s has no Key field. This might severely impact performance",
                            annotatedClass.getName(), fieldNode.getName(), elementType.getNameWithoutPackage(), elementType.getName()), fieldNode);
        }
    }

    private Expression optionalKeyArg(FieldNode fieldKey) {
        return fieldKey != null ? args("key") : NO_ARGUMENTS;
    }

    private void createMapMethods(FieldNode fieldNode) {
        initializeCollectionOrMap(fieldNode);

        ClassNode keyType = CommonAstHelper.getGenericsTypes(fieldNode)[0].getType();
        ClassNode valueType = CommonAstHelper.getGenericsTypes(fieldNode)[1].getType();

        if (hasAnnotation(valueType, DSL_CONFIG_ANNOTATION))
            createMapOfDSLObjectMethods(fieldNode, keyType, valueType);
        else
            createMapOfSimpleElementsMethods(fieldNode, keyType, valueType);
    }

    private void createMapOfSimpleElementsMethods(FieldNode fieldNode, ClassNode keyType, ClassNode valueType) {
        String methodName = fieldNode.getName();

        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        createMethod(methodName)
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .param(makeClassSafeWithGenerics(MAP_TYPE, new GenericsType(keyType), new GenericsType(valueType)), "values")
                .callMethod(propX(varX("this"), fieldNode.getName()), "putAll", varX("values"))
                .addTo(rwClass);

        String singleElementMethod = getElementNameForCollectionField(fieldNode);

        createMethod(singleElementMethod)
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .param(keyType, "key")
                .param(valueType, "value")
                .callMethod(propX(varX("this"), fieldNode.getName()), "put", args("key", "value"))
                .addTo(rwClass);
    }

    private void createMapOfDSLObjectMethods(FieldNode fieldNode, ClassNode keyType, ClassNode elementType) {
        if (getKeyField(elementType) == null) {
            CommonAstHelper.addCompileError(
                    sourceUnit, String.format("Value type of map %s (%s) has no key field", fieldNode.getName(), elementType.getName()),
                    fieldNode
            );
            return;
        }

        String methodName = getElementNameForCollectionField(fieldNode);
        boolean targetHasOwnerField = getOwnerFieldName(elementType) != null;

        String fieldName = fieldNode.getName();
        String fieldRWName = fieldName + "$rw";

        ClassNode elementRwType = DslAstHelper.getRwClassOf(elementType);
        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (!!DslAstHelper.isInstantiable(elementType)) {
            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .param(keyType, "key")
                    .delegatingClosureParam(elementRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .declareVariable("created", callX(classX(elementType), "newInstance", args("key")))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionallySetOwnerOnS("created", targetHasOwnerField)
                    .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "put", args(varX("key"), varX("created")))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);
            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .param(keyType, "key")
                    .delegatingClosureParam(elementRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .doReturn(callThisX(methodName, argsWithEmptyMapAndOptionalKey(keyType, "closure")))
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
                    .param(keyType, "key")
                    .delegatingClosureParam()
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", args("key")))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionallySetOwnerOnS("created", targetHasOwnerField)
                    .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "put", args(varX("key"), varX("created")))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);
            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .delegationTargetClassParam("typeToCreate", elementType)
                    .param(keyType, "key")
                    .delegatingClosureParam()
                    .doReturn(callThisX(methodName, CommonAstHelper.argsWithEmptyMapClassAndOptionalKey(keyType, "closure")))
                    .addTo(rwClass);
        }

        //noinspection ConstantConditions
        createMethod(methodName)
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .param(elementType, "value")
                .callMethod(propX(varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS), fieldRWName), "put", args(propX(varX("value"), getKeyField(elementType).getName()), varX("value")))
                .optionallySetOwnerOnS("value", targetHasOwnerField)
                .addTo(rwClass);

        createAlternativesClassFor(fieldNode);
    }

    private void createAlternativesClassFor(FieldNode fieldNode) {
        new AlternativesClassBuilder(fieldNode).invoke();
    }

    private void createSingleDSLObjectClosureMethod(FieldNode fieldNode) {
        String methodName = fieldNode.getName();

        ClassNode targetFieldType = fieldNode.getType();
        FieldNode targetTypeKeyField = getKeyField(targetFieldType);
        boolean targetHasOwnerField = getOwnerFieldName(targetFieldType) != null;
        ClassNode targetRwType = DslAstHelper.getRwClassOf(targetFieldType);

        String fieldName = fieldNode.getName();

        int visibility = isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (DslAstHelper.isInstantiable(targetFieldType)) {
            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam(targetRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .declareVariable("created", callX(classX(targetFieldType), "newInstance", optionalKeyArg(targetTypeKeyField)))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionallySetOwnerOnS("created", targetHasOwnerField)
                    .assignToProperty(fieldName, varX("created"))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod(varX("created"), "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);

            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam(targetRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .doReturn(callThisX(methodName, argsWithEmptyMapAndOptionalKey(targetTypeKeyField, "closure")))
                    .addTo(rwClass);
        }

        if (!isFinal(targetFieldType)) {
            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .delegationTargetClassParam("typeToCreate", targetFieldType)
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam()
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(targetTypeKeyField)))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionallySetOwnerOnS("created", targetHasOwnerField)
                    .assignToProperty(fieldName, varX("created"))
                    .callMethod(propX(varX("created"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod(varX("created"), "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);

            createMethod(methodName)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .delegationTargetClassParam("typeToCreate", targetFieldType)
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam()
                    .doReturn(callThisX(methodName, CommonAstHelper.argsWithEmptyMapClassAndOptionalKey(targetTypeKeyField, "closure")))
                    .addTo(rwClass);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isFinal(ClassNode classNode) {
        return (classNode.getModifiers() & ACC_FINAL) != 0;
    }

    private void createApplyMethods() {
        createPublicMethod("apply")
                .returning(newClass(annotatedClass))
                .namedParams("values")
                .delegatingClosureParam(rwClass, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .applyNamedParams("values")
                .assignS(propX(varX("closure"), "delegate"), varX(NAME_OF_RW_FIELD_IN_MODEL_CLASS))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_ONLY")
                )
                .callMethod("closure", "call")
                .callMethod(NAME_OF_RW_FIELD_IN_MODEL_CLASS, POSTAPPLY_ANNOTATION_METHOD_NAME)
                .doReturn("this")
                .addTo(annotatedClass);

        createPublicMethod("apply")
                .returning(newClass(annotatedClass))
                .delegatingClosureParam(rwClass, DslMethodBuilder.ClosureDefaultValue.NONE)
                .callThis("apply", args(new MapExpression(), varX("closure")))
                .doReturn("this")
                .addTo(annotatedClass);

        new LifecycleMethodBuilder(rwClass, POSTAPPLY_ANNOTATION).invoke();
    }

    private void createFactoryMethods() {
        new LifecycleMethodBuilder(rwClass, POSTCREATE_ANNOTATION).invoke();

        if (!DslAstHelper.isInstantiable(annotatedClass)) return;

        createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .namedParams("values")
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(rwClass, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .declareVariable("result", keyField != null ? ctorX(annotatedClass, args("name")) : ctorX(annotatedClass))
                .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                .callMethod("result", "apply", args("values", "closure"))
                .callValidationOn("result")
                .doReturn("result")
                .addTo(annotatedClass);


        createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(rwClass, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .doReturn(callX(annotatedClass, "create",
                        keyField != null ?
                        args(new MapExpression(), varX("name"), varX("closure"))
                        : args(new MapExpression(), varX("closure"))
                ))
                .addTo(annotatedClass);

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

        if (keyField != null) {
            createPublicMethod(CREATE_FROM)
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .stringParam("name")
                    .stringParam("text")
                    .optionalClassLoaderParam()
                    .declareVariable("simpleName", callX(callX(callX(callX(varX("name"), "tokenize", args(constX("."))), "first"), "tokenize", args(constX("/"))), "last"))
                    .declareVariable("result", ctorX(annotatedClass, args("simpleName")))
                    .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .declareVariable("gloader", ctorX(ClassHelper.make(GroovyClassLoader.class), args("loader")))
                    .declareVariable("config", ctorX(ClassHelper.make(CompilerConfiguration.class)))
                    .assignS(propX(varX("config"), "scriptBaseClass"), constX(DelegatingScript.class.getName()))
                    .declareVariable("binding", ctorX(ClassHelper.make(Binding.class)))
                    .declareVariable("shell", ctorX(ClassHelper.make(GroovyShell.class), args("gloader", "binding", "config")))
                    .declareVariable("script", callX(varX("shell"), "parse", args("text")))
                    .callMethod("script", "setDelegate", propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS))
                    .callMethod("script", "run")
                    .callValidationOn("result")
                    .doReturn("result")
                    .addTo(annotatedClass);

            createPublicMethod(CREATE_FROM) // Delegating Script
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC | ACC_SYNTHETIC)
                    .param(DELEGATING_SCRIPT, "script")
                    .declareVariable("simpleName", callX(propX(varX("configType"), "class"), "simpleName"))
                    .declareVariable("result", ctorX(annotatedClass, args("simpleName")))
                    .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("script", "setDelegate", propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS))
                    .callMethod("script", "run")
                    .callValidationOn("result")
                    .doReturn("result")
                    .addTo(annotatedClass);
        } else {
            createPublicMethod(CREATE_FROM)
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .stringParam("text")
                    .optionalClassLoaderParam()
                    .declareVariable("result", ctorX(annotatedClass))
                    .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .declareVariable("gloader", ctorX(ClassHelper.make(GroovyClassLoader.class), args("loader")))
                    .declareVariable("config", ctorX(ClassHelper.make(CompilerConfiguration.class)))
                    .assignS(propX(varX("config"), "scriptBaseClass"), constX(DelegatingScript.class.getName()))
                    .declareVariable("binding", ctorX(ClassHelper.make(Binding.class)))
                    .declareVariable("shell", ctorX(ClassHelper.make(GroovyShell.class), args("gloader", "binding", "config")))
                    .declareVariable("script", callX(varX("shell"), "parse", args("text")))
                    .callMethod("script", "setDelegate", propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS))
                    .callMethod("script", "run")
                    .callValidationOn("result")
                    .doReturn("result")
                    .addTo(annotatedClass);

            createPublicMethod(CREATE_FROM) // Delegating Script
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC | ACC_SYNTHETIC)
                    .param(newClass(DELEGATING_SCRIPT), "script")
                    .declareVariable("result", ctorX(annotatedClass))
                    .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), TemplateMethods.COPY_FROM_TEMPLATE)
                    .callMethod(propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("script", "setDelegate", propX(varX("result"), NAME_OF_RW_FIELD_IN_MODEL_CLASS))
                    .callMethod("script", "run")
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

        createPublicMethod("createFromClasspath")
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

}
