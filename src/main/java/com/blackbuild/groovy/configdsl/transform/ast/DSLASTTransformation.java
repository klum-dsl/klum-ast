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
import groovy.lang.Binding;
import groovy.lang.Delegate;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import groovy.util.DelegatingScript;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.ast.tools.GeneralUtils;
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

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.*;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createOptionalPublicMethod;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.expr.CastExpression.asExpression;
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
@SuppressWarnings("WeakerAccess")
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DSLASTTransformation extends AbstractASTTransformation {

    static final ClassNode[] NO_EXCEPTIONS = new ClassNode[0];
    static final ClassNode DSL_CONFIG_ANNOTATION = make(DSL.class);
    static final ClassNode DSL_FIELD_ANNOTATION = make(Field.class);
    static final ClassNode VALIDATE_ANNOTATION = make(Validate.class);
    static final ClassNode VALIDATION_ANNOTATION = make(Validation.class);
    static final ClassNode POSTAPPLY_ANNOTATION = make(PostApply.class);
    static final String POSTAPPLY_ANNOTATION_METHOD_NAME = "$" + POSTAPPLY_ANNOTATION.getNameWithoutPackage();
    static final ClassNode POSTCREATE_ANNOTATION = make(PostCreate.class);
    static final String POSTCREATE_ANNOTATION_METHOD_NAME = "$" + POSTCREATE_ANNOTATION.getNameWithoutPackage();
    static final ClassNode KEY_ANNOTATION = make(Key.class);
    static final ClassNode OWNER_ANNOTATION = make(Owner.class);
    static final ClassNode IGNORE_ANNOTATION = make(Ignore.class);

    static final ClassNode EXCEPTION_TYPE = make(Exception.class);
    static final ClassNode VALIDATION_EXCEPTION_TYPE = make(IllegalStateException.class);
    static final ClassNode ASSERTION_ERROR_TYPE = make(AssertionError.class);

    static final ClassNode EQUALS_HASHCODE_ANNOT = make(EqualsAndHashCode.class);
    static final ClassNode TOSTRING_ANNOT = make(ToString.class);
    static final String VALIDATE_METHOD = "validate";
    public static final String RW_CLASS_SUFFIX = "$_RW";
    public static final String RWCLASS_METADATA_KEY = DSLASTTransformation.class.getName() + ".rwclass";
    public static final String NO_MUTATION_CHECK_METADATA_KEY = DSLASTTransformation.class.getName() + ".nomutationcheck";
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

        if (ASTHelper.isDSLObject(annotatedClass.getSuperClass()))
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
        for (PropertyNode pNode : GeneralUtils.getInstanceProperties(annotatedClass)) {
            adjustPropertyAccessorsForSingleField(pNode, newNodes);
        }

        if (annotatedClassHoldsOwner()) {
            PropertyNode ownerProperty = annotatedClass.getProperty(ownerField.getName());
            ownerProperty.setSetterBlock(null);
            newNodes.add(ownerProperty);
        }

        replaceProperties(annotatedClass, newNodes);

    }


    private void adjustPropertyAccessorsForSingleField(PropertyNode pNode, List<PropertyNode> newNodes) {
        if (shouldFieldBeIgnored(pNode.getField()))
            return;

        String capitalizedFieldName = Verifier.capitalize(pNode.getName());
        String getterName = "get" + capitalizedFieldName;
        String setterName = "set" + capitalizedFieldName;
        String rwGetterName;
        String rwSetterName = setterName + "$rw";

        if (isCollectionOrMap(pNode.getType())) {
            rwGetterName = getterName + "$rw";

            pNode.setGetterBlock(stmt(callX(attrX(varX("this"), constX(pNode.getName())), "asImmutable")));

            MethodBuilder.createProtectedMethod(rwGetterName)
                    .mod(ACC_SYNTHETIC)
                    .returning(pNode.getType())
                    .doReturn(attrX(varX("this"), constX(pNode.getName())))
                    .addTo(annotatedClass);

        } else {
            rwGetterName = "get" + capitalizedFieldName;
            pNode.setGetterBlock(stmt(attrX(varX("this"), constX(pNode.getName()))));
        }

        MethodBuilder.createPublicMethod(getterName)
                .returning(pNode.getType())
                .doReturn(callX(varX("_model"), rwGetterName))
                .addTo(rwClass);

        MethodBuilder.createProtectedMethod(rwSetterName)
                .mod(ACC_SYNTHETIC)
                .returning(ClassHelper.VOID_TYPE)
                .param(pNode.getType(), "value")
                .statement(assignS(attrX(varX("this"), constX(pNode.getName())), varX("value")))
                .addTo(annotatedClass);

        MethodBuilder.createPublicMethod(setterName)
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
                parentRW != null ? parentRW : ClassHelper.OBJECT_TYPE);

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
                NO_EXCEPTIONS,
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
        ASTNode[] astNodes = new ASTNode[] { delegateAnnotation, rwClass.getField("_model")};

        new DelegateASTTransformation().visit(astNodes, sourceUnit);
    }

    private ClassNode getRwClassOfDslParent() {
        return dslParent != null ? (ClassNode) dslParent.getNodeMetaData(RWCLASS_METADATA_KEY) : null;
    }

    private void makeClassSerializable() {
        annotatedClass.addInterface(make(Serializable.class));
    }

    private void createDefaultMethods() {
        new DefaultMethods(this).execute();
    }

    private void createValidateMethod() {
        assertNoValidateMethodDeclared();

        Validation.Mode mode = getEnumMemberValue(getAnnotation(annotatedClass, VALIDATION_ANNOTATION), "mode", Validation.Mode.class, Validation.Mode.AUTOMATIC);

        if (dslParent == null) {
            // add manual validation only to root of hierarchy
            // TODO field could be added to rw as well
            annotatedClass.addField("$manualValidation", ACC_PROTECTED | ACC_SYNTHETIC, ClassHelper.Boolean_TYPE, new ConstantExpression(mode == Validation.Mode.MANUAL));
            MethodBuilder.createPublicMethod("manualValidation")
                    .param(Boolean_TYPE, "validation", constX(true))
                    .assignS(propX(varX("_model"), "$manualValidation"), varX("validation"))
                    .addTo(rwClass);
        }

        MethodBuilder methodBuilder = MethodBuilder.createPublicMethod(VALIDATE_METHOD);

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
            ASTHelper.addCompileError(sourceUnit, "validate() must not be declared, use @Validate methods instead.", existingValidateMethod);
    }

    private void validateCustomMethods(BlockStatement block) {
        warnIfUnannotatedDoValidateMethod();

        for (MethodNode method : annotatedClass.getMethods()) {
            AnnotationNode validateAnnotation = getAnnotation(method, VALIDATE_ANNOTATION);
            if (validateAnnotation == null) continue;

            ASTHelper.assertMethodIsParameterless(method, sourceUnit);
            assertAnnotationHasNoValueOrMessage(validateAnnotation);

            block.addStatement(stmt(callX(varX("this"), method.getName())));
        }
    }

    private void assertAnnotationHasNoValueOrMessage(AnnotationNode annotation) {
        if (annotation.getMember("value") != null || annotation.getMember("message") != null)
            ASTHelper.addCompileError(sourceUnit, "@Validate annotation on method must not have parameters!", annotation);
    }

    private void warnIfUnannotatedDoValidateMethod() {
        MethodNode doValidate = annotatedClass.getMethod("doValidate", Parameter.EMPTY_ARRAY);

        if (doValidate == null) return;

        if (getAnnotation(doValidate, VALIDATE_ANNOTATION) != null) return;

        ASTHelper.addCompileWarning(sourceUnit, "Using doValidation() is deprecated, mark validation methods with @Validate", doValidate);
        doValidate.addAnnotation(new AnnotationNode(VALIDATE_ANNOTATION));
    }

    private void validateFields(BlockStatement block) {
        Validation.Option mode = getEnumMemberValue(
                getAnnotation(annotatedClass, VALIDATION_ANNOTATION),
                "option",
                Validation.Option.class,
                Validation.Option.IGNORE_UNMARKED);
        for (final FieldNode fieldNode : annotatedClass.getFields()) {
            if (shouldFieldBeIgnoredForValidation(fieldNode)) continue;

            ClosureExpression validationClosure = createGroovyTruthClosureExpression(block.getVariableScope());
            String message = null;

            AnnotationNode validateAnnotation = getAnnotation(fieldNode, VALIDATE_ANNOTATION);
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
                    validationClosure = toStronglyTypedClosure(validationClosure, fieldNodeType);
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

            AnnotationNode annotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);

            if (annotation == null) continue;

            if (ASTHelper.isCollectionOrMap(fieldNode.getType())) return;

            if (annotation.getMember("members") != null) {
                ASTHelper.addCompileError(
                        sourceUnit, String.format("@Field.members is only valid for List or Map fields, but field %s is of type %s", fieldNode.getName(), fieldNode.getType().getName()),
                        annotation
                );
            }
        }
    }

    private void assertMembersNamesAreUnique() {
        Map<String, FieldNode> allDslCollectionFieldNodesOfHierarchy = new HashMap<String, FieldNode>();

        for (ClassNode level : ASTHelper.getHierarchyOfDSLObjectAncestors(annotatedClass)) {
            for (FieldNode field : level.getFields()) {
                if (!ASTHelper.isCollectionOrMap(field.getType())) continue;

                String memberName = getElementNameForCollectionField(field);

                FieldNode conflictingField = allDslCollectionFieldNodesOfHierarchy.get(memberName);

                if (conflictingField != null) {
                    ASTHelper.addCompileError(
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
        MethodBuilder.createPublicMethod("$set" + Verifier.capitalize(ownerField.getName()))
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
                NO_EXCEPTIONS,
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
            MethodBuilder.createPublicMethod("hashCode")
                    .returning(ClassHelper.int_TYPE)
                    .doReturn(callX(varX(keyField.getName()), "hashCode"))
                    .addTo(annotatedClass);
        } else {
            MethodBuilder.createPublicMethod("hashCode")
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

        if (hasAnnotation(fieldNode.getType(), DSL_CONFIG_ANNOTATION)) {
            createSingleDSLObjectClosureMethod(fieldNode);
            createSingleFieldSetterMethod(fieldNode);
        } else if (ASTHelper.isMap(fieldNode.getType()))
            createMapMethods(fieldNode);
        else if (ASTHelper.isCollection(fieldNode.getType()))
            createCollectionMethods(fieldNode);
        else
            createSingleFieldSetterMethod(fieldNode);
    }

    @SuppressWarnings("RedundantIfStatement")
    boolean shouldFieldBeIgnored(FieldNode fieldNode) {
        if (fieldNode == keyField) return true;
        if (fieldNode == ownerField) return true;
        if (getAnnotation(fieldNode, IGNORE_ANNOTATION) != null) return true;
        if (fieldNode.isFinal()) return true;
        if (fieldNode.getName().startsWith("$")) return true;
        if ((fieldNode.getModifiers() & ACC_TRANSIENT) != 0) return true;
        return false;
    }

    boolean shouldFieldBeIgnoredForValidation(FieldNode fieldNode) {
        if (getAnnotation(fieldNode, IGNORE_ANNOTATION) != null) return true;
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

    private String getElementNameForCollectionField(FieldNode fieldNode) {
        AnnotationNode fieldAnnotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);

        String result = getNullSafeMemberStringValue(fieldAnnotation, "members", null);

        if (result != null && result.length() > 0) return result;

        String collectionMethodName = fieldNode.getName();

        if (collectionMethodName.endsWith("s"))
            return collectionMethodName.substring(0, collectionMethodName.length() - 1);

        return collectionMethodName;
    }

    private String getNullSafeMemberStringValue(AnnotationNode fieldAnnotation, String value, String name) {
        return fieldAnnotation == null ? name : getMemberStringValue(fieldAnnotation, value, name);
    }

    private void createCollectionMethods(FieldNode fieldNode) {
        initializeField(fieldNode, asExpression(fieldNode.getType(), new ListExpression()));

        ClassNode elementType = getGenericsTypes(fieldNode)[0].getType();

        if (hasAnnotation(elementType, DSL_CONFIG_ANNOTATION))
            createCollectionOfDSLObjectMethods(fieldNode, elementType);
        else
            createCollectionOfSimpleElementsMethods(fieldNode, elementType);
    }

    private void initializeField(FieldNode fieldNode, Expression init) {
        if (!fieldNode.hasInitialExpression())
            fieldNode.setInitialValueExpression(init);
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

        FieldNode fieldKey = getKeyField(elementType);
        String targetOwner = getOwnerFieldName(elementType);

        warnIfSetWithoutKeyedElements(fieldNode, elementType, fieldKey);

        String fieldName = fieldNode.getName();
        String fieldRWName = fieldName + "$rw";

        createOptionalPublicMethod(fieldName)
                .linkToField(fieldNode)
                .closureParam("closure")
                .assignS(propX(varX("closure"), "delegate"), varX("this"))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .callMethod("closure", "call")
                .addTo(rwClass);

        if (!ASTHelper.isAbstract(elementType)) {
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementType)
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
                    .delegatingClosureParam(elementType)
                    .doReturn(callThisX(methodName, argsWithEmptyMapAndOptionalKey(fieldKey, "closure")))
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
                    .doReturn(callThisX(methodName, argsWithEmptyMapClassAndOptionalKey(fieldKey, "closure")))
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
            ASTHelper.addCompileWarning(sourceUnit,
                    String.format(
                            "WARNING: Field %s.%s is of type Set<%s>, but %s has no Key field. This might severely impact performance",
                            annotatedClass.getName(), fieldNode.getName(), elementType.getNameWithoutPackage(), elementType.getName()), fieldNode);
        }
    }

    private Expression optionalKeyArg(FieldNode fieldKey) {
        return fieldKey != null ? args("key") : NO_ARGUMENTS;
    }

    @SuppressWarnings("ConstantConditions")
    private GenericsType[] getGenericsTypes(FieldNode fieldNode) {
        GenericsType[] types = fieldNode.getType().getGenericsTypes();

        if (types == null)
            ASTHelper.addCompileError(sourceUnit, "Lists and Maps need to be assigned an explicit Generic Type", fieldNode);
        return types;
    }

    private void createMapMethods(FieldNode fieldNode) {
        if (fieldNode.getType().equals(ASTHelper.SORTED_MAP_TYPE)) {
            initializeField(fieldNode, ctorX(makeClassSafe(TreeMap.class)));
        } else {
            initializeField(fieldNode, asExpression(fieldNode.getType(), new MapExpression()));
        }

        ClassNode keyType = getGenericsTypes(fieldNode)[0].getType();
        ClassNode valueType = getGenericsTypes(fieldNode)[1].getType();

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
            ASTHelper.addCompileError(
                    sourceUnit, String.format("Value type of map %s (%s) has no key field", fieldNode.getName(), elementType.getName()),
                    fieldNode
            );
            return;
        }

        createOptionalPublicMethod(fieldNode.getName())
                .linkToField(fieldNode)
                .closureParam("closure")
                .assignS(propX(varX("closure"), "delegate"), varX("this"))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .callMethod("closure", "call")
                .addTo(rwClass);


        String methodName = getElementNameForCollectionField(fieldNode);
        String targetOwner = getOwnerFieldName(elementType);

        String fieldName = fieldNode.getName();
        String fieldRWName = fieldName + "$rw";

        if (!ASTHelper.isAbstract(elementType)) {
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .param(keyType, "key")
                    .delegatingClosureParam(elementType)
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
                    .delegatingClosureParam(elementType)
                    .doReturn(callThisX(methodName, argsWithEmptyMapAndOptionalKey(keyType, "closure")))
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
                    .doReturn(callThisX(methodName, argsWithEmptyMapClassAndOptionalKey(keyType, "closure")))
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

    private void createSingleDSLObjectClosureMethod(FieldNode fieldNode) {
        String methodName = fieldNode.getName();

        ClassNode targetFieldType = fieldNode.getType();
        FieldNode targetTypeKeyField = getKeyField(targetFieldType);
        String targetOwnerFieldName = getOwnerFieldName(targetFieldType);

        String fieldName = fieldNode.getName();

        if (!ASTHelper.isAbstract(targetFieldType)) {
            createOptionalPublicMethod(methodName)
                    .linkToField(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam(targetFieldType)
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
                    .delegatingClosureParam(targetFieldType)
                    .doReturn(callThisX(methodName, argsWithEmptyMapAndOptionalKey(targetTypeKeyField, "closure")))
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
                    .doReturn(callThisX(methodName, argsWithEmptyMapClassAndOptionalKey(targetTypeKeyField, "closure")))
                    .addTo(rwClass);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isFinal(ClassNode classNode) {
        return (classNode.getModifiers() & ACC_FINAL) != 0;
    }

    private void createApplyMethods() {
        MethodBuilder.createPublicMethod("apply")
                .returning(newClass(annotatedClass))
                .namedParams("values")
                .delegatingClosureParam(annotatedClass)
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

        MethodBuilder.createPublicMethod("apply")
                .returning(newClass(annotatedClass))
                .delegatingClosureParam(annotatedClass)
                .callThis("apply", args(new MapExpression(), varX("closure")))
                .doReturn("this")
                .addTo(annotatedClass);

        new LifecycleMethodBuilder(rwClass, POSTAPPLY_ANNOTATION).invoke();
    }

    private void createFactoryMethods() {
        new LifecycleMethodBuilder(rwClass, POSTCREATE_ANNOTATION).invoke();

        if (isAbstract(annotatedClass)) return;

        MethodBuilder.createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .namedParams("values")
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(annotatedClass)
                .declareVariable("result", keyField != null ? ctorX(annotatedClass, args("name")) : ctorX(annotatedClass))
                .callMethod(propX(varX("result"), "$rw"), TemplateMethods.COPY_FROM_TEMPLATE)
                .callMethod(propX(varX("result"), "$rw"), POSTCREATE_ANNOTATION_METHOD_NAME)
                .callMethod("result", "apply", args("values", "closure"))
                .callValidationOn("result")
                .doReturn("result")
                .addTo(annotatedClass);


        MethodBuilder.createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(annotatedClass)
                .doReturn(callX(annotatedClass, "create",
                        keyField != null ?
                        args(new MapExpression(), varX("name"), varX("closure"))
                        : args(new MapExpression(), varX("closure"))
                ))
                .addTo(annotatedClass);


        MethodBuilder.createPublicMethod("createFromScript")
                .deprecated()
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .simpleClassParam("configType", ClassHelper.SCRIPT_TYPE)
                .doReturn(callX(annotatedClass, "createFrom", args("configType")))
                .addTo(annotatedClass);


        MethodBuilder.createPublicMethod("createFrom")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .simpleClassParam("configType", ClassHelper.SCRIPT_TYPE)
                .doReturn(callX(callX(varX("configType"), "newInstance"), "run"))
                .addTo(annotatedClass);

        if (keyField != null) {
            MethodBuilder.createPublicMethod("createFrom")
                    .returning(newClass(annotatedClass))
                    .mod(Opcodes.ACC_STATIC)
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
                    .callMethod("script", "setDelegate", args(ctorX(rwClass, varX("result"))))
                    .callMethod("script", "run")
                    .doReturn("result")
                    .addTo(annotatedClass);

            MethodBuilder.createPublicMethod("createFromSnippet")
                    .deprecated()
                    .returning(newClass(annotatedClass))
                    .mod(Opcodes.ACC_STATIC)
                    .stringParam("name")
                    .stringParam("text")
                    .doReturn(callX(annotatedClass, "createFrom", args("name", "text")))
                    .addTo(annotatedClass);
        } else {
            MethodBuilder.createPublicMethod("createFrom")
                    .returning(newClass(annotatedClass))
                    .mod(Opcodes.ACC_STATIC)
                    .stringParam("text")
                    .declareVariable("result", callX(annotatedClass, "create"))
                    .declareVariable("loader", ctorX(ClassHelper.make(GroovyClassLoader.class), args(callX(callX(ClassHelper.make(Thread.class), "currentThread"), "getContextClassLoader"))))
                    .declareVariable("config", ctorX(ClassHelper.make(CompilerConfiguration.class)))
                    .assignS(propX(varX("config"), "scriptBaseClass"), constX(DelegatingScript.class.getName()))
                    .declareVariable("binding", ctorX(ClassHelper.make(Binding.class)))
                    .declareVariable("shell", ctorX(ClassHelper.make(GroovyShell.class), args("loader", "binding", "config")))
                    .declareVariable("script", callX(varX("shell"), "parse", args("text")))
                    .callMethod("script", "setDelegate", args(ctorX(rwClass, varX("result"))))
                    .callMethod("script", "run")
                    .doReturn("result")
                    .addTo(annotatedClass);

            MethodBuilder.createPublicMethod("createFromSnippet")
                    .deprecated()
                    .returning(newClass(annotatedClass))
                    .mod(Opcodes.ACC_STATIC)
                    .stringParam("text")
                    .doReturn(callX(annotatedClass, "createFrom", args("text")))
                    .addTo(annotatedClass);
        }

        MethodBuilder.createPublicMethod("createFrom")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .param(make(File.class), "src")
                .doReturn(callX(annotatedClass, "createFromSnippet", args(callX(callX(varX("src"), "toURI"), "toURL"))))
                .addTo(annotatedClass);

        MethodBuilder.createPublicMethod("createFromSnippet")
                .deprecated()
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .param(make(File.class), "src")
                .doReturn(callX(annotatedClass, "createFrom", args("src")))
                .addTo(annotatedClass);

        MethodBuilder.createPublicMethod("createFrom")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .param(make(URL.class), "src")
                .declareVariable("text", propX(varX("src"), "text"))
                .doReturn(callX(annotatedClass, "createFromSnippet", keyField != null ? args(propX(varX("src"), "path"), varX("text")) : args("text")))
                .addTo(annotatedClass);

        MethodBuilder.createPublicMethod("createFromSnippet")
                .deprecated()
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .param(make(URL.class), "src")
                .doReturn(callX(annotatedClass, "createFrom", args("src")))
                .addTo(annotatedClass);
    }

    private String getQualifiedName(FieldNode node) {
        return node.getOwner().getName() + "." + node.getName();
    }

    private FieldNode getKeyField(ClassNode target) {

        List<FieldNode> annotatedFields = getAnnotatedFieldsOfHierarchy(target, KEY_ANNOTATION);

        if (annotatedFields.isEmpty()) return null;

        if (annotatedFields.size() > 1) {
            ASTHelper.addCompileError(
                    sourceUnit, String.format(
                            "Found more than one key fields, only one is allowed in hierarchy (%s, %s)",
                            getQualifiedName(annotatedFields.get(0)),
                            getQualifiedName(annotatedFields.get(1))),
                    annotatedFields.get(0)
            );
            return null;
        }

        FieldNode result = annotatedFields.get(0);

        if (!result.getType().equals(ClassHelper.STRING_TYPE)) {
            ASTHelper.addCompileError(
                    sourceUnit, String.format("Key field '%s' must be of type String, but is '%s' instead", result.getName(), result.getType().getName()),
                    result
            );
            return null;
        }

        ClassNode ancestor = ASTHelper.getHighestAncestorDSLObject(target);

        if (target.equals(ancestor)) return result;

        FieldNode firstKey = getKeyField(ancestor);

        if (firstKey == null) {
            ASTHelper.addCompileError(
                    sourceUnit, String.format("Inconsistent hierarchy: Toplevel class %s has no key, but child class %s defines '%s'.", ancestor.getName(), target.getName(), result.getName()),
                    result
            );
            return null;
        }

        return result;
    }

    private List<FieldNode> getAnnotatedFieldsOfHierarchy(ClassNode target, ClassNode annotation) {
        List<FieldNode> result = new ArrayList<FieldNode>();

        for (ClassNode level : ASTHelper.getHierarchyOfDSLObjectAncestors(target)) {
            result.addAll(getAnnotatedFieldOfClass(level, annotation));
        }

        return result;
    }

    private List<FieldNode> getAnnotatedFieldOfClass(ClassNode target, ClassNode annotation) {
        List<FieldNode> result = new ArrayList<FieldNode>();

        for (FieldNode fieldNode : target.getFields())
            if (!fieldNode.getAnnotations(annotation).isEmpty())
                result.add(fieldNode);

        return result;
    }

    private FieldNode getOwnerField(ClassNode target) {

        List<FieldNode> annotatedFields = getAnnotatedFieldsOfHierarchy(target, OWNER_ANNOTATION);

        if (annotatedFields.isEmpty()) return null;

        if (annotatedFields.size() > 1) {
            ASTHelper.addCompileError(
                    sourceUnit, String.format(
                            "Found more than one owner fields, only one is allowed in hierarchy (%s, %s)",
                            getQualifiedName(annotatedFields.get(0)),
                            getQualifiedName(annotatedFields.get(1))),
                    annotatedFields.get(0)
            );
            return null;
        }

        return annotatedFields.get(0);
    }

    private String getOwnerFieldName(ClassNode target) {
        FieldNode ownerFieldOfElement = getOwnerField(target);
        return ownerFieldOfElement != null ? ownerFieldOfElement.getName() : null;
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
