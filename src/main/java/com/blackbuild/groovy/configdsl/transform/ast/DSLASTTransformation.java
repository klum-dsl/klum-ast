package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.groovy.configdsl.transform.*;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import groovy.util.DelegatingScript;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.ast.tools.GenericsUtils;
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
    ClassNode annotatedClass;
    ClassNode dslParent;
    FieldNode keyField;
    FieldNode ownerField;
    AnnotationNode dslAnnotation;

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

        if (annotedClassIsTopOfDSLHierarchy())
            preventOwnerOverride();
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

        annotatedClass.addField("$manualValidation", ACC_PRIVATE, ClassHelper.Boolean_TYPE, new ConstantExpression(mode == Validation.Mode.MANUAL));
        MethodBuilder.createPublicMethod("manualValidation")
                .param(Boolean_TYPE, "validation")
                .assignS(varX("$manualValidation"), varX("validation"))
                .addTo(annotatedClass);

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

            assertMethodIsParameterless(method);
            assertAnnotationHasNoValueOrMessage(validateAnnotation);

            block.addStatement(stmt(callX(varX("this"), method.getName())));
        }
    }

    private void assertAnnotationHasNoValueOrMessage(AnnotationNode annotation) {
        if (annotation.getMember("value") != null || annotation.getMember("message") != null)
            ASTHelper.addCompileError(sourceUnit, "@Validate annotation on method must not have parameters!", annotation);
    }

    private void assertMethodIsParameterless(MethodNode method) {
        if (method.getParameters().length > 0)
            ASTHelper.addCompileError(sourceUnit, "Lifecycle/Validate methods must be parameterless!", method);
    }

    private void assertMethodIsNotPrivate(MethodNode method) {
        if (method.isPrivate())
            ASTHelper.addCompileError(sourceUnit, "Lifecycle methods must not be private!", method);
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
        for (FieldNode fieldNode : annotatedClass.getFields()) {
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
                        addError("value of Validate must be either Validate.GroovyTruth, Validate.Ignore or a closure.", fieldNode);
                    }
                } else if (member instanceof ClosureExpression){
                    validationClosure = (ClosureExpression) member;
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
        ClosureExpression result = new ClosureExpression(params(param(OBJECT_TYPE, "it")), returnS(varX("it")));
        result.setVariableScope(scope.copy());
        return result;
    }

    private boolean annotedClassIsTopOfDSLHierarchy() {
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

        MethodBuilder.createPublicMethod(setterName(ownerField))
                .param(OBJECT_TYPE, "value")
                .statement(
                        ifS(
                                andX(
                                        isInstanceOfX(varX("value"), ownerField.getType()),
                                        notX(propX(varX("this"), ownerField.getName()))),
                                assignX(propX(varX("this"), ownerField.getName()), varX("value"))
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
        createOptionalPublicMethod(
                fieldNode.getName())
                .inheritDeprecationFrom(fieldNode)
                .param(fieldNode.getType(), "value")
                .assignToProperty(fieldNode.getName(), varX("value"))
                .addTo(annotatedClass);

        if (fieldNode.getType().equals(ClassHelper.boolean_TYPE)) {
            createOptionalPublicMethod(
                    fieldNode.getName())
                    .inheritDeprecationFrom(fieldNode)
                    .callThis(fieldNode.getName(), constX(true))
                    .addTo(annotatedClass);
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
                .inheritDeprecationFrom(fieldNode)
                .arrayParam(elementType, "values")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "addAll", varX("values")))
                .addTo(annotatedClass);

        createOptionalPublicMethod(fieldNode.getName())
                .inheritDeprecationFrom(fieldNode)
                .param(GenericsUtils.makeClassSafeWithGenerics(Iterable.class, elementType), "values")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "addAll", varX("values")))
                .addTo(annotatedClass);

        createOptionalPublicMethod(getElementNameForCollectionField(fieldNode))
                .inheritDeprecationFrom(fieldNode)
                .param(elementType, "value")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "add", varX("value")))
                .addTo(annotatedClass);
    }

    @NotNull
    private Statement[] delegateToClosure() {
        return new Statement[]{
                assignS(propX(varX("closure"), "delegate"), varX("context")),
                assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(CLOSURE_TYPE), "DELEGATE_FIRST")
                ),
                stmt(callX(varX("closure"), "call"))
        };
    }

    private void createCollectionOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        String methodName = getElementNameForCollectionField(fieldNode);

        FieldNode fieldKey = getKeyField(elementType);
        String targetOwner = getOwnerFieldName(elementType);

        warnIfSetWithoutKeyedElements(fieldNode, elementType, fieldKey);

        createOptionalPublicMethod(fieldNode.getName())
                .inheritDeprecationFrom(fieldNode)
                .closureParam("closure")
                .assignS(propX(varX("closure"), "delegate"), varX("this"))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .callMethod("closure", "call")
                .addTo(annotatedClass);

        if (!ASTHelper.isAbstract(elementType)) {
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementType)
                    .declareVariable("created", callX(classX(elementType), "newInstance", optionalKeyArg(fieldKey)))
                    .callMethod("created", "copyFromTemplate")
                    .optionalAssignThisToPropertyS("created", targetOwner)
                    .callMethod(fieldNode.getName(), "add", varX("created"))
                    .callMethod("created", POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(annotatedClass);
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(elementType)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementType)
                    .doReturn(callThisX(methodName, argsWithEmptyMapAndOptionalKey(fieldKey, "closure")))
                    .addTo(annotatedClass);
        }

        if (!isFinal(elementType)) {
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .classParam("typeToCreate", elementType)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementType)
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(fieldKey)))
                    .callMethod("created", "copyFromTemplate")
                    .optionalAssignThisToPropertyS("created", targetOwner)
                    .callMethod(fieldNode.getName(), "add", varX("created"))
                    .callMethod("created", POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(annotatedClass);
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(elementType)
                    .classParam("typeToCreate", elementType)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementType)
                    .doReturn(callThisX(methodName, argsWithEmptyMapClassAndOptionalKey(fieldKey, "closure")))
                    .addTo(annotatedClass);
        }

        createOptionalPublicMethod(methodName)
                .inheritDeprecationFrom(fieldNode)
                .param(elementType, "value")
                .callMethod(fieldNode.getName(), "add", varX("value"))
                .optionalAssignThisToPropertyS("value", targetOwner)
                .addTo(annotatedClass);

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

    private String setterName(FieldNode node) {
        char[] name = node.getName().toCharArray();
        name[0] = Character.toUpperCase(name[0]);
        return "set" + new String(name);
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
                .inheritDeprecationFrom(fieldNode)
                .param(makeClassSafeWithGenerics(MAP_TYPE, new GenericsType(keyType), new GenericsType(valueType)), "values")
                .callMethod(propX(varX("this"), fieldNode.getName()), "putAll", varX("values"))
                .addTo(annotatedClass);

        String singleElementMethod = getElementNameForCollectionField(fieldNode);

        createOptionalPublicMethod(singleElementMethod)
                .inheritDeprecationFrom(fieldNode)
                .param(keyType, "key")
                .param(valueType, "value")
                .callMethod(propX(varX("this"), fieldNode.getName()), "put", args("key", "value"))
                .addTo(annotatedClass);
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
                .inheritDeprecationFrom(fieldNode)
                .closureParam("closure")
                .assignS(propX(varX("closure"), "delegate"), varX("this"))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .callMethod("closure", "call")
                .addTo(annotatedClass);


        String methodName = getElementNameForCollectionField(fieldNode);
        String targetOwner = getOwnerFieldName(elementType);

        if (!ASTHelper.isAbstract(elementType)) {
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .param(keyType, "key")
                    .delegatingClosureParam(elementType)
                    .declareVariable("created", callX(classX(elementType), "newInstance", args("key")))
                    .callMethod("created", "copyFromTemplate")
                    .optionalAssignThisToPropertyS("created", targetOwner)
                    .callMethod(fieldNode.getName(), "put", args(varX("key"), varX("created")))
                    .callMethod("created", POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(annotatedClass);
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(elementType)
                    .param(keyType, "key")
                    .delegatingClosureParam(elementType)
                    .doReturn(callThisX(methodName, argsWithEmptyMapAndOptionalKey(keyType, "closure")))
                    .addTo(annotatedClass);
        }

        if (!isFinal(elementType)) {
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(elementType)
                    .namedParams("values")
                    .classParam("typeToCreate", elementType)
                    .param(keyType, "key")
                    .delegatingClosureParam(elementType)
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", args("key")))
                    .callMethod("created", "copyFromTemplate")
                    .optionalAssignThisToPropertyS("created", targetOwner)
                    .callMethod(fieldNode.getName(), "put", args(varX("key"), varX("created")))
                    .callMethod("created", POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod("created", "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(annotatedClass);
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(elementType)
                    .classParam("typeToCreate", elementType)
                    .param(keyType, "key")
                    .delegatingClosureParam(elementType)
                    .doReturn(callThisX(methodName, argsWithEmptyMapClassAndOptionalKey(keyType, "closure")))
                    .addTo(annotatedClass);
        }

        //noinspection ConstantConditions
        createOptionalPublicMethod(methodName)
                .inheritDeprecationFrom(fieldNode)
                .param(elementType, "value")
                .callMethod(fieldNode.getName(), "put", args(propX(varX("value"), getKeyField(elementType).getName()), varX("value")))
                .optionalAssignThisToPropertyS("value", targetOwner)
                .addTo(annotatedClass);
    }

    private void createSingleDSLObjectClosureMethod(FieldNode fieldNode) {
        String methodName = fieldNode.getName();

        ClassNode targetFieldType = fieldNode.getType();
        FieldNode targetTypeKeyField = getKeyField(targetFieldType);
        String targetOwnerFieldName = getOwnerFieldName(targetFieldType);

        if (!ASTHelper.isAbstract(targetFieldType)) {
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam(targetFieldType)
                    .declareVariable("created", callX(classX(targetFieldType), "newInstance", optionalKeyArg(targetTypeKeyField)))
                    .callMethod("created", "copyFromTemplate")
                    .optionalAssignThisToPropertyS("created", targetOwnerFieldName)
                    .assignToProperty(fieldNode.getName(), varX("created"))
                    .callMethod("created", POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod(varX("created"), "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(annotatedClass);

            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(targetFieldType)
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam(targetFieldType)
                    .doReturn(callThisX(methodName, argsWithEmptyMapAndOptionalKey(targetTypeKeyField, "closure")))
                    .addTo(annotatedClass);
        }

        if (!isFinal(targetFieldType)) {
            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(targetFieldType)
                    .namedParams("values")
                    .classParam("typeToCreate", targetFieldType)
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam(targetFieldType)
                    .declareVariable("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(targetTypeKeyField)))
                    .callMethod("created", "copyFromTemplate")
                    .optionalAssignThisToPropertyS("created", targetOwnerFieldName)
                    .assignToProperty(fieldNode.getName(), varX("created"))
                    .callMethod("created", POSTCREATE_ANNOTATION_METHOD_NAME)
                    .callMethod(varX("created"), "apply", args("values", "closure"))
                    .callValidationOn("created")
                    .doReturn("created")
                    .addTo(annotatedClass);

            createOptionalPublicMethod(methodName)
                    .inheritDeprecationFrom(fieldNode)
                    .returning(targetFieldType)
                    .classParam("typeToCreate", targetFieldType)
                    .optionalStringParam("key", targetTypeKeyField)
                    .delegatingClosureParam(targetFieldType)
                    .doReturn(callThisX(methodName, argsWithEmptyMapClassAndOptionalKey(targetTypeKeyField, "closure")))
                    .addTo(annotatedClass);
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
                .assignS(propX(varX("closure"), "delegate"), varX("this"))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .callMethod("closure", "call")
                .callThis(POSTAPPLY_ANNOTATION_METHOD_NAME)
                .doReturn("this")
                .addTo(annotatedClass);

        MethodBuilder.createPublicMethod("apply")
                .returning(newClass(annotatedClass))
                .delegatingClosureParam(annotatedClass)
                .callThis("apply", args(new MapExpression(), varX("closure")))
                .doReturn("this")
                .addTo(annotatedClass);

        createLifecycleMethod(POSTAPPLY_ANNOTATION);
    }

    private void createLifecycleMethod(ClassNode annotationType) {

        MethodBuilder lifecycleMethod = MethodBuilder.createProtectedMethod("$" + annotationType.getNameWithoutPackage());

        for (MethodNode method : annotatedClass.getAllDeclaredMethods()) {
            AnnotationNode postApplyAnnotation = getAnnotation(method, annotationType);
            if (postApplyAnnotation == null)
                continue;

            assertMethodIsParameterless(method);
            assertMethodIsNotPrivate(method);
            lifecycleMethod.callThis(method.getName());
        }
        lifecycleMethod.addTo(annotatedClass);
    }

    private void createFactoryMethods() {
        createLifecycleMethod(POSTCREATE_ANNOTATION);

        if (isAbstract(annotatedClass)) return;

        MethodBuilder.createPublicMethod("create")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .namedParams("values")
                .optionalStringParam("name", keyField)
                .delegatingClosureParam(annotatedClass)
                .declareVariable("result", keyField != null ? ctorX(annotatedClass, args("name")) : ctorX(annotatedClass))
                .callMethod("result", "copyFromTemplate")
                .callMethod("result", POSTCREATE_ANNOTATION_METHOD_NAME)
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
                .classParam("configType", ClassHelper.SCRIPT_TYPE)
                .doReturn(callX(annotatedClass, "createFrom", args("configType")))
                .addTo(annotatedClass);


        MethodBuilder.createPublicMethod("createFrom")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .classParam("configType", ClassHelper.SCRIPT_TYPE)
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
                    .callMethod("script", "setDelegate", args("result"))
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
                    .callMethod("script", "setDelegate", args("result"))
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
                            "Found more than owner key fields, only one is allowed in hierarchy (%s, %s)",
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
