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

import com.blackbuild.groovy.configdsl.transform.*;
import com.blackbuild.klum.common.CommonAstHelper;
import groovy.lang.Binding;
import groovy.lang.Delegate;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import groovy.util.DelegatingScript;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.DelegateASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.*;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.*;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.*;
import static com.blackbuild.klum.common.CommonAstHelper.initializeCollectionOrMap;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.expr.MethodCallExpression.NO_ARGUMENTS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
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
    public static final ClassNode IGNORE_ANNOTATION = make(Ignore.class);

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
    public static final ClassNode READONLY_ANNOTATION = make(ReadOnly.class);
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
        keyField = getKeyField(annotatedClass);
        ownerField = getOwnerField(annotatedClass);
        dslAnnotation = (AnnotationNode) nodes[0];

        if (DslAstHelper.isDSLObject(annotatedClass.getSuperClass()))
            dslParent = annotatedClass.getSuperClass();

        if (keyField != null)
            createKeyConstructor();

        createRWClass();

        setPropertyAccessors();

        delegateFromRwToModel();

        validateFieldAnnotations();
        assertMembersNamesAreUnique();
        makeClassSerializable();
        createApplyMethods();
        createTemplateMethods();
        createFactoryMethods();
        createFieldMethods();
        createCanonicalMethods();
        createValidateMethod();
        createDefaultMethods();
        moveMutatorsToRWClass();

        if (annotatedClassHoldsOwner())
            preventOwnerOverride();
    }

    private void moveMutatorsToRWClass() {
        new MutatorsHandler(annotatedClass).invoke();
    }

    private void setPropertyAccessors() {
        List<PropertyNode> newNodes = new ArrayList<PropertyNode>();
        for (PropertyNode pNode : getInstanceProperties(annotatedClass)) {
            adjustPropertyAccessorsForSingleField(pNode, newNodes);
        }

        if (annotatedClassHoldsOwner()) {
            PropertyNode ownerProperty = annotatedClass.getProperty(ownerField.getName());
            ownerProperty.setSetterBlock(null);
            ownerProperty.setGetterBlock(stmt(attrX(varX("this"), constX(ownerField.getName()))));

            newNodes.add(ownerProperty);
        }

        CommonAstHelper.replaceProperties(annotatedClass, newNodes);

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
                .doReturn(callX(varX("_model"), rwGetterName))
                .addTo(rwClass);

        createProtectedMethod(rwSetterName)
                .mod(ACC_SYNTHETIC)
                .returning(ClassHelper.VOID_TYPE)
                .param(pNode.getType(), "value")
                .statement(assignS(attrX(varX("this"), constX(pNode.getName())), varX("value")))
                .addTo(annotatedClass);

        createMethod(setterName)
                .mod(isReadOnly(pNode.getField()) ? ACC_PROTECTED : ACC_PUBLIC)
                .returning(ClassHelper.VOID_TYPE)
                .param(pNode.getType(), "value")
                .statement(callX(varX("_model"), rwSetterName, args("value")))
                .addTo(rwClass);

        pNode.setSetterBlock(null);
        newNodes.add(pNode);
    }

    private void createRWClass() {
        ClassNode parentRW = getRwClassOfDslParent();

        rwClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + RW_CLASS_SUFFIX,
                ACC_STATIC,
                parentRW != null ? parentRW : ClassHelper.OBJECT_TYPE,
                new ClassNode[] { make(Serializable.class)},
                new MixinNode[0]);

        rwClass.addField("_model", ACC_FINAL | ACC_PRIVATE, newClass(annotatedClass), null);

        BlockStatement constructorBody = new BlockStatement();

        if (parentRW != null)
            constructorBody.addStatement(ctorSuperS(varX("_model")));

        constructorBody.addStatement(
                assignS(propX(varX("this"), "_model"), varX("_model"))
        );

        rwClass.addConstructor(
                0,
                params(param(newClass(annotatedClass), "_model")),
                CommonAstHelper.NO_EXCEPTIONS,
                constructorBody
        );
        annotatedClass.getModule().addClass(rwClass);
        annotatedClass.addField("$rw", ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, ctorX(rwClass, varX("this")));
        annotatedClass.setNodeMetaData(RWCLASS_METADATA_KEY, rwClass);
    }

    private void delegateFromRwToModel() {
        AnnotationNode delegateAnnotation = new AnnotationNode(ClassHelper.make(Delegate.class));
        delegateAnnotation.setMember("parameterAnnotations", constX(true));
        delegateAnnotation.setMember("methodAnnotations", constX(true));
        delegateAnnotation.setMember("excludes", constX("methodMissing"));
        ASTNode[] astNodes = new ASTNode[] { delegateAnnotation, rwClass.getField("_model")};

        new DelegateASTTransformation().visit(astNodes, sourceUnit);
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
                    .assignS(propX(varX("_model"), "$manualValidation"), varX("validation"))
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
        return ownerField != null && annotatedClass.getDeclaredField(ownerField.getName()) != null;
    }

    private void validateFieldAnnotations() {
        for (FieldNode fieldNode : annotatedClass.getFields()) {
            if (shouldFieldBeIgnored(fieldNode)) continue;

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
        createPublicMethod("$set" + Verifier.capitalize(ownerField.getName()))
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
            createEquals(annotatedClass, false, true, true, null, null);
        }
        if (!hasAnnotation(annotatedClass, TOSTRING_ANNOT)) {
            if (ownerField == null)
                createToString(annotatedClass, false, false, null, null, false);
            else
                createToString(annotatedClass, false, false, Collections.singletonList(ownerField.getName()), null, false);
        }
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

    private void createFieldMethods() {
        for (FieldNode fieldNode : annotatedClass.getFields())
            createMethodsForSingleField(fieldNode);
    }

    private void createMethodsForSingleField(FieldNode fieldNode) {
        if (shouldFieldBeIgnored(fieldNode)) return;
        if (isReadOnly(fieldNode)) return;

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

    private boolean isReadOnly(FieldNode fieldNode) {
        return !fieldNode.getAnnotations(READONLY_ANNOTATION).isEmpty();
    }

    @SuppressWarnings("RedundantIfStatement")
    boolean shouldFieldBeIgnored(FieldNode fieldNode) {
        if (fieldNode == keyField) return true;
        if (fieldNode == ownerField) return true;
        if (CommonAstHelper.getAnnotation(fieldNode, IGNORE_ANNOTATION) != null) return true;
        if (fieldNode.isFinal()) return true;
        if (fieldNode.getName().startsWith("$")) return true;
        if ((fieldNode.getModifiers() & ACC_TRANSIENT) != 0) return true;
        return false;
    }

    boolean shouldFieldBeIgnoredForValidation(FieldNode fieldNode) {
        if (CommonAstHelper.getAnnotation(fieldNode, IGNORE_ANNOTATION) != null) return true;
        if (fieldNode.getName().startsWith("$")) return true;
        if ((fieldNode.getModifiers() & ACC_TRANSIENT) != 0) return true;
        return false;
    }

    private void createSingleFieldSetterMethod(FieldNode fieldNode) {
        createOptionalPublicMethod(fieldNode.getName())
                .linkToField(fieldNode)
                .param(fieldNode.getType(), "value")
                .assignToProperty(fieldNode.getName(), varX("value"))
                .addTo(rwClass);

        if (fieldNode.getType().equals(ClassHelper.boolean_TYPE)) {
            createOptionalPublicMethod(fieldNode.getName())
                    .linkToField(fieldNode)
                    .callThis(fieldNode.getName(), constX(true))
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

        createOptionalPublicMethod(fieldNode.getName())
                .linkToField(fieldNode)
                .arrayParam(elementType, "values")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "addAll", varX("values")))
                .addTo(rwClass);

        createOptionalPublicMethod(fieldNode.getName())
                .linkToField(fieldNode)
                .param(GenericsUtils.makeClassSafeWithGenerics(Iterable.class, elementType), "values")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "addAll", varX("values")))
                .addTo(rwClass);

        createOptionalPublicMethod(getElementNameForCollectionField(fieldNode))
                .linkToField(fieldNode)
                .param(elementType, "value")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "add", varX("value")))
                .addTo(rwClass);
    }

    private void createCollectionOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        String methodName = getElementNameForCollectionField(fieldNode);
        ClassNode elementRwType = DslAstHelper.getRwClassOf(elementType);


        FieldNode fieldKey = getKeyField(elementType);
        String targetOwner = getOwnerFieldName(elementType);

        warnIfSetWithoutKeyedElements(fieldNode, elementType, fieldKey);

        String fieldName = fieldNode.getName();
        String fieldRWName = fieldName + "$rw";

        createAlternativesClassFor(fieldNode);

        if (!CommonAstHelper.isAbstract(elementType)) {
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .declareVariable("created", callX(classX(elementType), "newInstance", optionalKeyArg(fieldKey)))
                    .callMethod(propX(varX("created"), "$rw"), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionalAssignModelToPropertyS("created", targetOwner)
                    .callMethod(propX(varX("_model"), fieldRWName), "add", varX("created"))
                    .callMethod(propX(varX("created"), "$rw"), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .doReturn(callThisX(methodName, CommonAstHelper.argsWithEmptyMapAndOptionalKey(fieldKey, "closure")))
                    .addTo(rwClass);
        }

        if (!isFinal(elementType)) {
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .delegationTargetClassParam("typeToCreate", elementType)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam()
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(fieldKey)))
                    .callMethod(propX(varX("created"), "$rw"), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionalAssignModelToPropertyS("created", targetOwner)
                    .callMethod(propX(varX("_model"), fieldRWName), "add", varX("created"))
                    .callMethod(propX(varX("created"), "$rw"), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .delegationTargetClassParam("typeToCreate", elementType)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam()
                    .doReturn(callThisX(methodName, CommonAstHelper.argsWithEmptyMapClassAndOptionalKey(fieldKey, "closure")))
                    .addTo(rwClass);
        }

        createOptionalPublicMethod(methodName)
                .linkToField(fieldNode)
                .param(elementType, "value")
                .callMethod(propX(varX("_model"), fieldRWName), "add", varX("value"))
                .optionalAssignModelToPropertyS("value", targetOwner)
                .addTo(rwClass);

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

        createOptionalPublicMethod(methodName)
                .linkToField(fieldNode)
                .param(makeClassSafeWithGenerics(MAP_TYPE, new GenericsType(keyType), new GenericsType(valueType)), "values")
                .callMethod(propX(varX("this"), fieldNode.getName()), "putAll", varX("values"))
                .addTo(rwClass);

        String singleElementMethod = getElementNameForCollectionField(fieldNode);

        createOptionalPublicMethod(singleElementMethod)
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

        createAlternativesClassFor(fieldNode);

        String methodName = getElementNameForCollectionField(fieldNode);
        String targetOwner = getOwnerFieldName(elementType);

        String fieldName = fieldNode.getName();
        String fieldRWName = fieldName + "$rw";

        ClassNode elementRwType = DslAstHelper.getRwClassOf(elementType);

        if (!CommonAstHelper.isAbstract(elementType)) {
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .param(keyType, "key")
                    .delegatingClosureParam(elementRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .declareVariable("created", callX(classX(elementType), "newInstance", args("key")))
                    .callMethod(propX(varX("created"), "$rw"), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionalAssignModelToPropertyS("created", targetOwner)
                    .callMethod(propX(varX("_model"), fieldRWName), "put", args(varX("key"), varX("created")))
                    .callMethod(propX(varX("created"), "$rw"), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .param(keyType, "key")
                    .delegatingClosureParam(elementRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .doReturn(callThisX(methodName, CommonAstHelper.argsWithEmptyMapAndOptionalKey(keyType, "closure")))
                    .addTo(rwClass);
        }

        if (!isFinal(elementType)) {
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .delegationTargetClassParam("typeToCreate", elementType)
                    .param(keyType, "key")
                    .delegatingClosureParam()
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", args("key")))
                    .callMethod(propX(varX("created"), "$rw"), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionalAssignModelToPropertyS("created", targetOwner)
                    .callMethod(propX(varX("_model"), fieldRWName), "put", args(varX("key"), varX("created")))
                    .callMethod(propX(varX("created"), "$rw"), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .delegationTargetClassParam("typeToCreate", elementType)
                    .param(keyType, "key")
                    .delegatingClosureParam()
                    .doReturn(callThisX(methodName, CommonAstHelper.argsWithEmptyMapClassAndOptionalKey(keyType, "closure")))
                    .addTo(rwClass);
        }

        //noinspection ConstantConditions
        createOptionalPublicMethod(methodName)
                .linkToField(fieldNode)
                .param(elementType, "value")
                .callMethod(propX(varX("_model"), fieldRWName), "put", args(propX(varX("value"), getKeyField(elementType).getName()), varX("value")))
                .optionalAssignModelToPropertyS("value", targetOwner)
                .addTo(rwClass);


    }

    private void createAlternativesClassFor(FieldNode fieldNode) {
        new AlternativesClassBuilder(fieldNode).invoke();
    }

    private void createSingleDSLObjectClosureMethod(FieldNode fieldNode) {
        String methodName = fieldNode.getName();

        ClassNode targetFieldType = fieldNode.getType();
        FieldNode targetTypeKeyField = getKeyField(targetFieldType);
        String targetOwnerFieldName = getOwnerFieldName(targetFieldType);
        ClassNode targetRwType = DslAstHelper.getRwClassOf(targetFieldType);

        String fieldName = fieldNode.getName();

        if (!CommonAstHelper.isAbstract(targetFieldType)) {
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam(targetRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .declareVariable("created", callX(classX(targetFieldType), "newInstance", optionalKeyArg(targetTypeKeyField)))
                    .callMethod(propX(varX("created"), "$rw"), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionalAssignModelToPropertyS("created", targetOwnerFieldName)
                    .assignToProperty(fieldName, varX("created"))
                    .callMethod(propX(varX("created"), "$rw"), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod(varX("created"), "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);

            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam(targetRwType, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                    .doReturn(callThisX(methodName, CommonAstHelper.argsWithEmptyMapAndOptionalKey(targetTypeKeyField, "closure")))
                    .addTo(rwClass);
        }

        if (!isFinal(targetFieldType)) {
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .delegationTargetClassParam("typeToCreate", targetFieldType)
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam()
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(targetTypeKeyField)))
                    .callMethod(propX(varX("created"), "$rw"), TemplateMethods.COPY_FROM_TEMPLATE)
                    .optionalAssignModelToPropertyS("created", targetOwnerFieldName)
                    .assignToProperty(fieldName, varX("created"))
                    .callMethod(propX(varX("created"), "$rw"), POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod(varX("created"), "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(rwClass);

            createOptionalPublicMethod(methodName)
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
                .assignS(propX(varX("closure"), "delegate"), varX("$rw"))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .callMethod("closure", "call")
                .callMethod("$rw", POSTAPPLY_ANNOTATION_METHOD_NAME)
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

        if (CommonAstHelper.isAbstract(annotatedClass)) return;

        createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .namedParams("values")
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(rwClass, DslMethodBuilder.ClosureDefaultValue.EMPTY_CLOSURE)
                .declareVariable("result", keyField != null ? ctorX(annotatedClass, args("name")) : ctorX(annotatedClass))
                .callMethod(propX(varX("result"), "$rw"), TemplateMethods.COPY_FROM_TEMPLATE)
                .callMethod(propX(varX("result"), "$rw"), POSTCREATE_ANNOTATION_METHOD_NAME)
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


        createPublicMethod("createFromScript")
                .deprecated()
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .simpleClassParam("configType", ClassHelper.SCRIPT_TYPE)
                .doReturn(callX(annotatedClass, "createFrom", args("configType")))
                .addTo(annotatedClass);


        createPublicMethod("createFrom")
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .simpleClassParam("configType", ClassHelper.SCRIPT_TYPE)
                .statement(ifS(
                        notX(callX(classX(DELEGATING_SCRIPT), "isAssignableFrom", args("configType"))),
                        returnS(callX(callX(varX("configType"), "newInstance"), "run"))
                ))
                .doReturn(callX(annotatedClass, "createFrom", callX(varX("configType"), "newInstance")))
                .addTo(annotatedClass);

        if (keyField != null) {
            createPublicMethod("createFrom")
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .stringParam("name")
                    .stringParam("text")
                    .declareVariable("simpleName", callX(callX(callX(callX(varX("name"), "tokenize", args(constX("."))), "first"), "tokenize", args(constX("/"))), "last"))
                    .declareVariable("result", callX(annotatedClass, "create", args("simpleName")))
                    .declareVariable("loader", ctorX(ClassHelper.make(GroovyClassLoader.class), args(callX(callX(ClassHelper.make(Thread.class), "currentThread"), "getContextClassLoader"))))
                    .declareVariable("config", ctorX(ClassHelper.make(CompilerConfiguration.class)))
                    .assignS(propX(varX("config"), "scriptBaseClass"), constX(DelegatingScript.class.getName()))
                    .declareVariable("binding", ctorX(ClassHelper.make(Binding.class)))
                    .declareVariable("shell", ctorX(ClassHelper.make(GroovyShell.class), args("loader", "binding", "config")))
                    .declareVariable("script", callX(varX("shell"), "parse", args("text")))
                    .callMethod("script", "setDelegate", propX(varX("result"), "$rw"))
                    .callMethod("script", "run")
                    .doReturn("result")
                    .addTo(annotatedClass);

            createPublicMethod("createFrom") // Delegating Script
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC | ACC_SYNTHETIC)
                    .param(DELEGATING_SCRIPT, "script")
                    .declareVariable("simpleName", callX(propX(varX("configType"), "class"), "simpleName"))
                    .declareVariable("result", callX(annotatedClass, "create", args("simpleName")))
                    .callMethod("script", "setDelegate", propX(varX("result"), "$rw"))
                    .callMethod("script", "run")
                    .doReturn("result")
                    .addTo(annotatedClass);

            createPublicMethod("createFromSnippet")
                    .deprecated()
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .stringParam("name")
                    .stringParam("text")
                    .doReturn(callX(annotatedClass, "createFrom", args("name", "text")))
                    .addTo(annotatedClass);
        } else {
            createPublicMethod("createFrom")
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .stringParam("text")
                    .declareVariable("result", callX(annotatedClass, "create"))
                    .declareVariable("loader", ctorX(ClassHelper.make(GroovyClassLoader.class), args(callX(callX(ClassHelper.make(Thread.class), "currentThread"), "getContextClassLoader"))))
                    .declareVariable("config", ctorX(ClassHelper.make(CompilerConfiguration.class)))
                    .assignS(propX(varX("config"), "scriptBaseClass"), constX(DelegatingScript.class.getName()))
                    .declareVariable("binding", ctorX(ClassHelper.make(Binding.class)))
                    .declareVariable("shell", ctorX(ClassHelper.make(GroovyShell.class), args("loader", "binding", "config")))
                    .declareVariable("script", callX(varX("shell"), "parse", args("text")))
                    .callMethod("script", "setDelegate", propX(varX("result"), "$rw"))
                    .callMethod("script", "run")
                    .doReturn("result")
                    .addTo(annotatedClass);

            createPublicMethod("createFrom") // Delegating Script
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .param(newClass(DELEGATING_SCRIPT), "script")
                    .declareVariable("result", callX(annotatedClass, "create"))
                    .callMethod("script", "setDelegate", propX(varX("result"), "$rw"))
                    .callMethod("script", "run")
                    .doReturn("result")
                    .addTo(annotatedClass);

            createPublicMethod("createFromSnippet")
                    .deprecated()
                    .returning(newClass(annotatedClass))
                    .mod(ACC_STATIC)
                    .stringParam("text")
                    .doReturn(callX(annotatedClass, "createFrom", args("text")))
                    .addTo(annotatedClass);
        }

        createPublicMethod("createFrom")
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .param(make(File.class), "src")
                .doReturn(callX(annotatedClass, "createFromSnippet", args(callX(callX(varX("src"), "toURI"), "toURL"))))
                .addTo(annotatedClass);

        createPublicMethod("createFromSnippet")
                .deprecated()
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .param(make(File.class), "src")
                .doReturn(callX(annotatedClass, "createFrom", args("src")))
                .addTo(annotatedClass);

        createPublicMethod("createFrom")
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .param(make(URL.class), "src")
                .declareVariable("text", propX(varX("src"), "text"))
                .doReturn(callX(annotatedClass, "createFromSnippet", keyField != null ? args(propX(varX("src"), "path"), varX("text")) : args("text")))
                .addTo(annotatedClass);

        createPublicMethod("createFromSnippet")
                .deprecated()
                .returning(newClass(annotatedClass))
                .mod(ACC_STATIC)
                .param(make(URL.class), "src")
                .doReturn(callX(annotatedClass, "createFrom", args("src")))
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
