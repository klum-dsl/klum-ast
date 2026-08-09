/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast.compiler.internal.ast;

import com.blackbuild.annodocimal.ast.AstDocumentation;
import com.blackbuild.klum.ast.*;
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessMethodsMover;
import com.blackbuild.klum.ast.runtime.KlumKeyedModelObject;
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.runtime.KlumModelObject;
import com.blackbuild.klum.ast.runtime.KlumUnkeyedModelObject;
import com.blackbuild.klum.ast.compiler.internal.doc.DocUtil;
import com.blackbuild.klum.ast.runtime.DefaultKlumPhase;
import com.blackbuild.klum.ast.runtime.internal.InternalKlumBuilder;
import com.blackbuild.klum.ast.runtime.generated.GeneratedKlumBuilder;
import com.blackbuild.klum.ast.runtime.generated.GeneratedMaterializationToken;
import com.blackbuild.klum.ast.runtime.generated.GeneratedModelSupport;
import com.blackbuild.klum.ast.runtime.generated.GeneratedObjectState;
import com.blackbuild.klum.ast.runtime.KlumFactory;
import com.blackbuild.klum.ast.runtime.KlumFactory.BuilderFactoryProvider;
import com.blackbuild.klum.ast.compiler.internal.layer3.ClusterFactoryBuilder;
import com.blackbuild.klum.ast.compiler.internal.reflect.AstReflectionBridge;
import com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper;
import groovy.lang.Closure;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.EmptyStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.classgen.VariableScopeVisitor;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.codehaus.groovy.transform.stc.StaticTypesMarker;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.*;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.*;
import static com.blackbuild.klum.ast.compiler.internal.ast.MethodBuilder.*;
import static com.blackbuild.klum.ast.compiler.internal.ast.ProxyMethodBuilder.createProxyMethod;
import static com.blackbuild.klum.ast.compiler.internal.layer3.ClusterTransformation.CLUSTER_ANNOTATION_TYPE;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.*;
import static java.util.stream.Collectors.toList;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.expr.MethodCallExpression.NO_ARGUMENTS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.*;
import static org.codehaus.groovy.transform.EqualsAndHashCodeASTTransformation.createEquals;

/**
 * Transformation class for the @DSL annotation.
 *
 * @author Stephan Pauxberger
 */
@SuppressWarnings({"WeakerAccess"})
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DSLASTTransformation extends AbstractASTTransformation {

    private static final String MODEL_TYPE_PARAMETER = "modelType";
    private static final String BUILDER_PARAMETER = "builder";
    private static final String MATERIALIZATION_TOKEN_PARAMETER = "materializationToken";
    private static final String ADD_ELEMENT_TO_COLLECTION = "addElementToCollection";
    private static final String ADD_ELEMENT_TO_MAP = "addElementToMap";
    private static final String ADD_NEW_DSL_ELEMENT_TO_MAP = "addNewDslElementToMap";
    private static final String SET_SINGLE_FIELD = "setSingleField";
    private static final String CREATE_SINGLE_CHILD = "createSingleChild";
    private static final String FACTORY_NAME = "factory";
    private static final String SCHEDULE_APPLY_LATER = "scheduleApplyLater";
    private static final String OPTIONAL_PARAMETERS_DOCUMENTATION = "the optional parameters";
    private static final String CONFIGURATION_CLOSURE_DOCUMENTATION = "the closure to configure the new element";
    private static final String ELEMENTS_TO_ADD_DOCUMENTATION = "the elements to add";
    private static final String NEW_BUILDER_RETURN_DOCUMENTATION = "the newly created Builder";
    private static final String NEW_BUILDER_CONFIGURATION_DOCUMENTATION = "The newly created Builder is configured by the optional values and closure.";
    private static final String DYNAMIC_TYPE_SELECTION_DOCUMENTATION = "Selects the implementation dynamically through " +
            "a Class value, so the closure retains the relationship's declared base public Builder type. Pass the " +
            "selected type's generated Create Factory for the exact selected public Builder. Both forms create the " +
            "owned child in the current Construction session without starting a root lifecycle.";
    private static final String CREATE_NEW_ELEMENT_BUILDER_DOCUMENTATION = "Creates a new '{{singleElementName}}' Builder and adds it";
    private static final String CLOSURE_PARAMETER = "closure";
    private static final String ADDS_ONE_OR_MORE = "Adds one or more ";
    private static final String COLLECTION_DOCUMENTATION_SUFFIX = " to the Builder's '{{fieldName}}' collection.";
    private static final String MAP_DOCUMENTATION_SUFFIX = " to the Builder's '{{fieldName}}' map.";

    public static final ClassNode DSL_CONFIG_ANNOTATION = make(DSL.class);
    public static final ClassNode DSL_FIELD_ANNOTATION = make(Field.class);
    public static final ClassNode VALIDATE_ANNOTATION = make(Validate.class);
    public static final ClassNode KEY_ANNOTATION = make(Key.class);

    private static final ClassNode DELEGATES_TO_RW_TYPE = ClassHelper.make(DelegatesToRW.class);

    public static final ClassNode OWNER_ANNOTATION = make(Owner.class);
    public static final ClassNode KLUM_FACTORY = make(KlumFactory.class);
    public static final ClassNode KEYED_FACTORY = make(KlumFactory.Keyed.class);
    public static final ClassNode UNKEYED_FACTORY = make(KlumFactory.Unkeyed.class);
    public static final ClassNode BUILDER_FACTORY = make(KlumFactory.BuilderFactory.class);
    public static final ClassNode KEYED_BUILDER_FACTORY = make(KlumFactory.KeyedBuilderFactory.class);
    public static final ClassNode UNKEYED_BUILDER_FACTORY = make(KlumFactory.UnkeyedBuilderFactory.class);
    public static final ClassNode BUILDER_FACTORY_PROVIDER = make(BuilderFactoryProvider.class);
    public static final ClassNode PUBLIC_KLUM_BUILDER = make(KlumBuilder.class);
    public static final ClassNode KLUM_BUILDER = make(InternalKlumBuilder.class);
    public static final ClassNode GENERATED_KLUM_BUILDER = make(GeneratedKlumBuilder.class);
    public static final ClassNode GENERATED_OBJECT_STATE = make(GeneratedObjectState.class);
    public static final ClassNode GENERATED_MODEL_SUPPORT = make(GeneratedModelSupport.class);
    public static final ClassNode EQUALS_HASHCODE_ANNOT = make(EqualsAndHashCode.class);
    public static final ClassNode TOSTRING_ANNOT = make(ToString.class);
    public static final String RW_CLASS_SUFFIX = "$Builder";
    public static final String RWCLASS_METADATA_KEY = DSLASTTransformation.class.getName() + ".rwclass";
    public static final String BUILDER_ANNOTATION_CLOSURE_METADATA_KEY =
            DSLASTTransformation.class.getName() + ".builderAnnotationClosure";
    public static final String MODEL_RESULT_ANNOTATION_CLOSURE_METADATA_KEY =
            DSLASTTransformation.class.getName() + ".modelResultAnnotationClosure";
    public static final ClassNode INVOKER_HELPER_CLASS = ClassHelper.make(InvokerHelper.class);
    public static final String FACTORY_FIELD_NAME = "Create";
    private static final String RESERVED_KLUM_NAMESPACE = "$klum$";
    public static final ClassNode KLUM_KEYED_MODEL_OBJECT = make(KlumKeyedModelObject.class);
    public static final ClassNode KLUM_MODEL_OBJECT = make(KlumModelObject.class);
    public static final ClassNode KLUM_UNKEYED_MODEL_OBJECT = make(KlumUnkeyedModelObject.class);
    static final ClassNode MATERIALIZATION_TOKEN = make(GeneratedMaterializationToken.class);
    public static final String APPLY_LATER = "applyLater";

    ClassNode annotatedClass;
    ClassNode dslParent;
    FieldNode keyField;
    List<FieldNode> ownerFields;
    AnnotationNode dslAnnotation;
    InnerClassNode rwClass;
    ClassNode modelImplementationClass;
    final Map<FieldNode, FieldNode> builderFields = new LinkedHashMap<>();

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

        implementMarkerInterfaces();

        rejectReservedKlumNamespace(annotatedClass);
        checkFieldNames();
        rejectCompletedModelApplyMethods();
        rejectClientConstructors();
        rejectNonDslSubclasses();
        warnIfAFieldIsNamedOwner();

        createRWClass();
        moveSourceStateToBuilder();
        createFieldDSLMethods();
        diagnoseNonSetterConfiguratorOverrides();
        setPropertyAccessors();
        createApplyMethods();
        createTemplateMethods();
        createFactoryField();
        createClusterFactories();
        convertValidationClosures();
        moveMutatorsToRWClass();
        createOwnerClosureMethods();
        retargetBuilderAnnotationClosures();

        finalizeModelFields();
        createMaterializationMethods();
        createCanonicalMethods();
        assertMembersNamesAreUnique();
        makeClassSerializable();

        runDelayedActions(annotatedClass);
        OmittedProjectionCatalog.complete(rwClass);
        GeneratedDslSupport.complete(annotatedClass);

        new VariableScopeVisitor(sourceUnit, true).visitClass(annotatedClass);
        new VariableScopeVisitor(sourceUnit, true).visitClass(rwClass);
    }

    private void implementMarkerInterfaces() {
        if (keyField != null) annotatedClass.addInterface(KLUM_KEYED_MODEL_OBJECT);
        else if (isAbstract(annotatedClass)) annotatedClass.addInterface(KLUM_MODEL_OBJECT);
        else annotatedClass.addInterface(KLUM_UNKEYED_MODEL_OBJECT);
    }

    private void warnIfAFieldIsNamedOwner() {
        FieldNode ownerNamedField = annotatedClass.getDeclaredField("owner");

        if (ownerNamedField != null)
            addCompileWarning(sourceUnit, "Fields should not be named 'owner' to prevent naming clash with Closure.owner!", ownerNamedField);
    }

    private void checkFieldNames() {
        annotatedClass.getFields().forEach(this::warnIfInvalid);
    }

    private void rejectCompletedModelApplyMethods() {
        annotatedClass.getDeclaredMethods("apply").forEach(method ->
                addCompileError(
                        sourceUnit,
                        "DSL Objects cannot declare apply methods; configuration belongs to the generated Builder.",
                        method
                ));
    }

    private void rejectClientConstructors() {
        annotatedClass.getDeclaredConstructors().stream()
                .filter(constructor -> !constructor.isSynthetic())
                .forEach(constructor -> addCompileError(
                        sourceUnit,
                        "DSL Objects cannot declare constructors; use the generated factory and configure its Builder.",
                        constructor
                ));
    }

    private void rejectNonDslSubclasses() {
        sourceUnit.getAST().getClasses().stream()
                .filter(candidate -> candidate != annotatedClass)
                .filter(candidate -> candidate.getSuperClass() != null)
                .filter(candidate -> candidate.getSuperClass().redirect().equals(annotatedClass.redirect()))
                .filter(candidate -> !isDSLObject(candidate))
                .forEach(candidate -> addCompileError(
                        sourceUnit,
                        "Non-DSL subclasses cannot extend DSL Objects; annotate " + candidate.getName() + " with @DSL.",
                        candidate
                ));
    }

    private void warnIfInvalid(FieldNode fieldNode) {
        if (fieldNode.getName().startsWith("$") && (fieldNode.getModifiers() & ACC_SYNTHETIC) != 0)
            addCompileWarning(sourceUnit, "fields starting with '$' are strongly discouraged", fieldNode);
    }

    private void rejectReservedKlumNamespace(ClassNode type) {
        type.getFields().stream()
                .filter(field -> field.getOwner().redirect().equals(type.redirect()))
                .filter(field -> !field.isSynthetic())
                .filter(field -> field.getName().startsWith(RESERVED_KLUM_NAMESPACE))
                .forEach(field -> rejectReservedKlumMember(field.getName(), field));
        type.getMethods().stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(type.redirect()))
                .filter(method -> !method.isSynthetic())
                .filter(method -> method.getName().startsWith(RESERVED_KLUM_NAMESPACE))
                .forEach(method -> rejectReservedKlumMember(method.getName(), method));
    }

    private void rejectReservedKlumMember(String name, ASTNode member) {
        addCompileError(
                sourceUnit,
                "The '$klum$' namespace is reserved for generated KlumAST members: " + name,
                member
        );
    }

    private void moveMutatorsToRWClass() {
        new WriteAccessMethodsMover(annotatedClass).invoke();
    }

    private void setPropertyAccessors() {
        new PropertyAccessors(this).invoke();
    }

    private void createRWClass() {
        ClassNode parentRW = getRwClassOfDslParent();
        ClassNode builderBase;
        if (parentRW != null) {
            builderBase = parentRW.getPlainNodeReference();
        } else {
            builderBase = GENERATED_KLUM_BUILDER;
        }

        rwClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + RW_CLASS_SUFFIX,
                ACC_PUBLIC | ACC_STATIC,
                builderBase,
                new ClassNode[] { make(Serializable.class) },
                MixinNode.EMPTY_ARRAY);
        AstDocumentation.attachText(rwClass, "The generated Builder for " + annotatedClass.getName() + ".");

        DslAstHelper.registerAsVerbProvider(rwClass);
        annotatedClass.getModule().addClass(rwClass);
        if (dslParent == null)
            annotatedClass.addField("$state", ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, GENERATED_OBJECT_STATE, null);

        ClassNode parentProxy = annotatedClass.getNodeMetaData(RWCLASS_METADATA_KEY);
        if (parentProxy == null)
            annotatedClass.setNodeMetaData(RWCLASS_METADATA_KEY, rwClass);
        else
            parentProxy.setRedirect(rwClass);

        rwClass.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class));
        GeneratedDslSupport.create(annotatedClass, rwClass);
    }

    private static final Set<String> SUPPORTED_COLLECTION_TYPES = Set.of(
            List.class.getName(),
            Set.class.getName(),
            SortedSet.class.getName(),
            NavigableSet.class.getName(),
            Map.class.getName(),
            SortedMap.class.getName(),
            NavigableMap.class.getName(),
            EnumSet.class.getName()
    );

    /** Moves every source initializer and mutable field value to the generated Builder. */
    private void moveSourceStateToBuilder() {
        new ArrayList<>(annotatedClass.getFields()).stream()
                .filter(field -> field.getOwner().equals(annotatedClass))
                .filter(field -> !field.isStatic())
                .filter(field -> !field.getName().startsWith("$"))
                .forEach(this::moveSingleFieldStateToBuilder);
        builderFields.values().forEach(this::retargetAnnotationClosuresToBuilder);
    }

    private void retargetBuilderAnnotationClosures() {
        CodeVisitorSupport visitor = new CodeVisitorSupport() {
            @Override
            public void visitVariableExpression(VariableExpression expression) {
                FieldNode target = rwClass.getField(expression.getName());
                Variable accessed = expression.getAccessedVariable();
                if (target != null && (accessed instanceof FieldNode || accessed instanceof DynamicVariable)) {
                    expression.setAccessedVariable(target);
                    expression.setType(target.getType());
                }
                super.visitVariableExpression(expression);
            }

            @Override
            public void visitPropertyExpression(PropertyExpression expression) {
                super.visitPropertyExpression(expression);
                String propertyName = expression.getPropertyAsString();
                ClassNode receiverType = expression.getObjectExpression().getType();
                FieldNode target = propertyName != null && receiverType != null
                        ? receiverType.getField(propertyName)
                        : null;
                if (target != null)
                    expression.setType(target.getType());
            }

            @Override
            public void visitClosureExpression(ClosureExpression expression) {
                for (Parameter parameter : expression.getParameters()) {
                    if (isDSLObject(parameter.getType()))
                        parameter.setType(getRwClassOf(parameter.getType()).getPlainNodeReference());
                }
                super.visitClosureExpression(expression);
            }
        };

        rwClass.getFields().stream()
                .flatMap(field -> field.getAnnotations().stream())
                .flatMap(annotation -> annotation.getMembers().values().stream())
                .forEach(expression -> retargetAnnotationExpression(expression, visitor));
        rwClass.getMethods().stream()
                .flatMap(method -> method.getAnnotations().stream())
                .flatMap(annotation -> annotation.getMembers().values().stream())
                .forEach(expression -> retargetAnnotationExpression(expression, visitor));

        annotatedClass.getAnnotations().stream()
                .flatMap(annotation -> annotation.getMembers().values().stream())
                .forEach(expression -> retargetAnnotationExpression(expression, visitor));
    }

    private void retargetAnnotationExpression(Expression expression, CodeVisitorSupport visitor) {
        expression.visit(visitor);
        if (!(expression instanceof ClosureExpression closure)) return;
        closure.putNodeMetaData(BUILDER_ANNOTATION_CLOSURE_METADATA_KEY, Boolean.TRUE);

        // Depending on the active Groovy transforms, an annotation closure may already carry an inferred
        // source-model return type here. The late model verifier repeats this correction after static type
        // checking as well, preventing class generation from casting a Builder result back to that model.
        ClassNode inferredReturnType = closure.getNodeMetaData(StaticTypesMarker.INFERRED_RETURN_TYPE);
        if (isDSLObject(inferredReturnType))
            closure.putNodeMetaData(
                    StaticTypesMarker.INFERRED_RETURN_TYPE,
                    getRwClassOf(inferredReturnType).getPlainNodeReference()
            );
    }

    private void retargetAnnotationClosuresToBuilder(FieldNode builderField) {
        CodeVisitorSupport visitor = new CodeVisitorSupport() {
            @Override
            public void visitVariableExpression(VariableExpression expression) {
                FieldNode target = rwClass.getField(expression.getName());
                Variable accessed = expression.getAccessedVariable();
                if (target != null && (accessed instanceof FieldNode || accessed instanceof DynamicVariable)) {
                    expression.setAccessedVariable(target);
                    expression.setType(target.getType());
                }
                super.visitVariableExpression(expression);
            }

            @Override
            public void visitPropertyExpression(PropertyExpression expression) {
                super.visitPropertyExpression(expression);
                String propertyName = expression.getPropertyAsString();
                ClassNode receiverType = expression.getObjectExpression().getType();
                FieldNode target = propertyName != null && receiverType != null
                        ? receiverType.getField(propertyName)
                        : null;
                if (target != null)
                    expression.setType(target.getType());
            }
        };
        builderField.getAnnotations().stream()
                .flatMap(annotation -> annotation.getMembers().values().stream())
                .forEach(expression -> expression.visit(visitor));
    }

    private void moveSingleFieldStateToBuilder(FieldNode modelField) {
        if (CommonAstHelper.isCollectionOrMap(modelField.getType())) {
            validateSupportedCollectionDeclaration(modelField);
            if (SUPPORTED_COLLECTION_TYPES.contains(modelField.getType().getName()))
                initializeCollectionOrMap(modelField);
        }

        FieldNode builderField = new FieldNode(
                modelField.getName(),
                modelField.getModifiers() & ~ACC_FINAL,
                getBuilderFieldType(modelField),
                rwClass,
                modelField.getInitialExpression()
        );
        builderField.addAnnotations(modelField.getAnnotations().stream()
                .filter(AnnotationCopyExceptions::shouldCopy)
                .toList());
        builderField.setSourcePosition(modelField);
        rwClass.addField(builderField);
        builderFields.put(modelField, builderField);

        // Source code is evaluated exactly once, as part of Builder construction.
        modelField.setInitialValueExpression(null);
    }

    private void validateSupportedCollectionDeclaration(FieldNode field) {
        if (!SUPPORTED_COLLECTION_TYPES.contains(field.getType().getName())) {
            addCompileError(
                    "Unsupported collection declaration '" + field.getType().getName()
                            + "'. Use List, Set, SortedSet/NavigableSet, Map, SortedMap/NavigableMap, or EnumSet.",
                    field
            );
        }
    }

    private ClassNode getBuilderFieldType(FieldNode field) {
        ClassNode type = field.getType();
        ClassNode storageValueType = getBuilderStorageValueType(field);
        if (!isCollection(type) && !isMap(type) && isDSLObject(storageValueType))
            return getRwClassOf(storageValueType, field.getOwner()).getPlainNodeReference();
        if (isCollection(type) && isDSLObject(storageValueType))
            return makeClassSafeWithGenerics(type, new GenericsType(getRwClassOf(storageValueType, field.getOwner()).getPlainNodeReference()));
        if (isMap(type) && isDSLObject(storageValueType))
                return makeClassSafeWithGenerics(
                        type,
                        new GenericsType(getKeyTypeForMap(type)),
                        new GenericsType(getRwClassOf(storageValueType, field.getOwner()).getPlainNodeReference())
                );
        return type;
    }

    private ClassNode getBuilderStorageValueType(FieldNode field) {
        ClassNode declaredValueType = getDeclaredFieldValueType(field);
        return isDSLObject(declaredValueType) ? declaredValueType : getEffectiveFieldValueType(field);
    }

    private ClassNode getEffectiveFieldValueType(FieldNode field) {
        return getDefaultImplOfFieldOrMethod(field, getDeclaredFieldValueType(field));
    }

    private ClassNode getDeclaredFieldValueType(FieldNode field) {
        ClassNode declaredValueType;
        if (isCollection(field.getType()))
            declaredValueType = getElementTypeForCollection(field.getType());
        else if (isMap(field.getType()))
            declaredValueType = getElementTypeForMap(field.getType());
        else
            declaredValueType = field.getType();
        return declaredValueType;
    }

    FieldNode getBuilderField(FieldNode modelField) {
        return builderFields.get(modelField);
    }

    private boolean isRelationshipField(FieldNode field) {
        return isDSLObject(getEffectiveFieldValueType(field));
    }

    private void finalizeModelFields() {
        for (FieldNode field : new ArrayList<>(builderFields.keySet())) {
            if (getFieldType(field) == FieldType.BUILDER) {
                annotatedClass.getProperties().removeIf(property -> property.getField() == field);
                annotatedClass.removeField(field.getName());
                continue;
            }
            if (getFieldType(field) != FieldType.TRANSIENT
                    && (field.getModifiers() & ACC_TRANSIENT) == 0
                    && !isRelationshipField(field))
                field.setModifiers(field.getModifiers() | ACC_FINAL);
        }
    }

    private void createMaterializationMethods() {
        createBuilderConstructors();
        createModelConstructor();
        createModelAllocationHook();
        createRelationshipAssignmentHook();
    }

    private void createBuilderConstructors() {
        BlockStatement internalBody = new BlockStatement();
        if (dslParent == null)
            internalBody.addStatement(ctorSuperS(args(varX(MODEL_TYPE_PARAMETER))));
        else
            internalBody.addStatement(ctorSuperS(args(varX(MODEL_TYPE_PARAMETER), varX("key"))));

        if (keyField != null && keyField.getOwner().equals(annotatedClass))
            internalBody.addStatement(assignS(attrX(varX("this"), constX(keyField.getName())), varX("key")));

        rwClass.addConstructor(
                ACC_PROTECTED,
                params(param(makeClassSafe(Class.class), MODEL_TYPE_PARAMETER), param(STRING_TYPE, "key")),
                NO_EXCEPTIONS,
                internalBody
        );

        rwClass.addConstructor(
                ACC_PROTECTED,
                params(param(STRING_TYPE, "key")),
                NO_EXCEPTIONS,
                block(ctorThisS(args(classX(annotatedClass), varX("key"))))
        );
    }

    private void createModelConstructor() {
        BlockStatement body = new BlockStatement();
        if (dslParent != null)
            body.addStatement(ctorSuperS(args(varX(BUILDER_PARAMETER), varX(MATERIALIZATION_TOKEN_PARAMETER))));
        else {
            body.addStatement(ctorSuperS());
            body.addStatement(stmt(callX(
                    classX(GENERATED_MODEL_SUPPORT),
                    "$klum$requireMaterializationToken",
                    args(varX(MATERIALIZATION_TOKEN_PARAMETER))
            )));
        }

        builderFields.forEach((modelField, builderField) -> {
            if (getFieldType(modelField) == FieldType.BUILDER || isRelationshipField(modelField))
                return;
            body.addStatement(assignS(
                    attrX(varX("this"), constX(modelField.getName())),
                    castX(modelField.getType(), callX(
                            classX(GENERATED_MODEL_SUPPORT),
                            "$klum$snapshotField",
                            args(varX(BUILDER_PARAMETER), constX(modelField.getName()))
                    ))
            ));
        });

        if (dslParent == null)
            body.addStatement(assignS(
                    attrX(varX("this"), constX("$state")),
                    callX(classX(GENERATED_MODEL_SUPPORT), "$klum$createState", args(varX(BUILDER_PARAMETER), varX("this")))
            ));

        annotatedClass.addConstructor(
                ACC_PROTECTED | ACC_SYNTHETIC,
                params(
                        param(rwClass.getPlainNodeReference(), BUILDER_PARAMETER),
                        param(MATERIALIZATION_TOKEN, MATERIALIZATION_TOKEN_PARAMETER)
                ),
                NO_EXCEPTIONS,
                body
        );
    }

    private void createModelAllocationHook() {
        MethodBuilder method = createProtectedMethod("$modelImplementationType")
                .returning(makeClassSafe(Class.class))
                .doReturn(classX(modelImplementationClass));
        method.addTo(rwClass);
    }

    private void createRelationshipAssignmentHook() {
        MethodBuilder method = createProtectedMethod("$assignRelationships")
                .returning(VOID_TYPE);
        if (dslParent != null) {
            MethodCallExpression parentAssignment = callSuperX("$assignRelationships");
            parentAssignment.setMethodTarget(MethodAstHelper.findMatchingMethod(
                    getRwClassOfDslParent(), "$assignRelationships", List.of()));
            method.statement(stmt(parentAssignment));
        }

        builderFields.forEach((modelField, builderField) -> {
            if (getFieldType(modelField) == FieldType.BUILDER || !isRelationshipField(modelField))
                return;
            MethodCallExpression relationshipAssignment = callX(
                    varX("this"),
                    "$assignMaterializedRelationship",
                    args(constX(modelField.getName()))
            );
            relationshipAssignment.setMethodTarget(MethodAstHelper.findMatchingMethod(
                    GENERATED_KLUM_BUILDER, "$assignMaterializedRelationship", List.of(STRING_TYPE)));
            method.statement(stmt(relationshipAssignment));
        });
        method.addTo(rwClass);
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
        modelImplementationClass = new TemplateMethods(this).invoke();
    }

    private void createOwnerClosureMethods() {
        annotatedClass.getFields()
                .stream()
                .filter(fieldNode -> DslAstHelper.hasAnnotation(fieldNode, OWNER_ANNOTATION))
                .filter(fieldNode -> fieldNode.getType().equals(CLOSURE_TYPE))
                .forEach(this::createSingleFieldSetterMethod);
    }

    private void createCanonicalMethods() {
        if (!hasAnnotation(annotatedClass, EQUALS_HASHCODE_ANNOT)) {
            createHashCodeIfNotDefined();
            createEquals(annotatedClass, true, dslParent != null, true, getAllIgnoredFieldNames(), null);
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

    private void diagnoseNonSetterConfiguratorOverrides() {
        annotatedClass.getMethods().stream()
                .filter(this::isExplicitMutator)
                .forEach(method -> builderFields.forEach((field, builderField) ->
                        diagnoseNonSetterConfiguratorOverride(method, field, builderField)
                ));
    }

    private boolean isExplicitMutator(MethodNode method) {
        return !method.getAnnotations(make(Mutator.class)).isEmpty();
    }

    private void diagnoseNonSetterConfiguratorOverride(MethodNode method, FieldNode field, FieldNode builderField) {
        if (!isGeneratedSingleFieldConfiguratorOverride(method, field)) return;
        if (method.getReturnType().equals(VOID_TYPE)) return;

        String message = "Manual configurator '" + method.getName() + "' shadows map configuration for field '"
                + field.getName() + "'. Map configuration calls this method; use 'set"
                + Verifier.capitalize(field.getName()) + "' for direct field assignment.";

        if (hasSetterLikeReturnType(method, field, builderField))
            addCompileWarning(sourceUnit, message, method);
        else
            addCompileError(sourceUnit, message + " It must return void, '" + field.getType().getNameWithoutPackage()
                    + "', or its Builder type, but returns '" + method.getReturnType().getNameWithoutPackage() + "'.", method);
    }

    private boolean isGeneratedSingleFieldConfiguratorOverride(MethodNode method, FieldNode field) {
        if (shouldFieldBeIgnored(field) || getFieldType(field) == FieldType.IGNORED) return false;
        if (isMap(field.getType()) || isCollection(field.getType())) return false;
        if (!method.getName().equals(field.getName())) return false;
        if (method.getParameters().length != 1) return false;
        return sameType(method.getParameters()[0].getType(), field.getType());
    }

    private boolean hasSetterLikeReturnType(MethodNode method, FieldNode field, FieldNode builderField) {
        ClassNode returnType = method.getReturnType();
        return sameType(returnType, field.getType())
                || sameType(returnType, builderField.getType())
                || sameType(returnType, GeneratedDslSupport.builderTypeFor(field.getType()))
                || isKlumBuilderFor(returnType, field.getType());
    }

    private static boolean isKlumBuilderFor(ClassNode type, ClassNode modelType) {
        if (!sameType(type, PUBLIC_KLUM_BUILDER)) return false;
        GenericsType[] generics = type.getGenericsTypes();
        return generics != null && generics.length == 1 && sameType(generics[0].getType(), modelType);
    }

    private static boolean sameType(ClassNode left, ClassNode right) {
        return left.redirect().getName().equals(right.redirect().getName());
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

        createProxyMethod(fieldName, SET_SINGLE_FIELD)
                .optional()
                .returning(fieldNode.getType(), "The set value")
                .mod(visibility)
                .linkToField(fieldNode)
                .documentationTitle(DocUtil.getSetterText(fieldNode))
                .constantParam(fieldName)
                .decoratedParam(fieldNode, "value", "the value to set")
                .addTo(rwClass);

        if (isDSLObject(fieldNode.getType())) {
            createProxyMethod(fieldName, SET_SINGLE_FIELD)
                    .optional()
                    .returning(makeClassSafeWithGenerics(KlumBuilder.class, fieldNode.getType()), "The set Builder value")
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .constantParam(fieldName)
                    .param(makeClassSafeWithGenerics(KlumBuilder.class, fieldNode.getType()), "value", "the Builder value to set")
                    .addTo(rwClass);
        }

        if (fieldNode.getType().equals(ClassHelper.boolean_TYPE)) {
            createProxyMethod(fieldName, SET_SINGLE_FIELD)
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

        createProxyMethod(elementName, ADD_ELEMENT_TO_COLLECTION)
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
        ClassNode elementRwType = DslAstHelper.getRwClassOf(defaultImpl).getPlainNodeReference();
        FieldType relationshipType = getFieldType(fieldNode);
        boolean linkField = relationshipType == FieldType.LINK;
        boolean optionalLinkField = relationshipType == FieldType.OPTIONAL_LINK;
        ClassNode storedElementType = linkField ? elementType : elementRwType;
        String storedElementDescription = linkField
                ? "completed '{{singleElementName}}' LINK targets"
                : "'{{singleElementName}}' Builders";

        FieldNode fieldKey = getKeyField(dslBaseType);

        warnIfSetWithoutKeyedElements(fieldNode, elementType, fieldKey);

        String fieldName = fieldNode.getName();

        int visibility = DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (!linkField) {
            String fieldKeyName = fieldKey != null ? fieldKey.getName() : null;
            if (isInstantiable(defaultImpl)) {
                createProxyMethod(methodName, InternalKlumBuilder.ADD_NEW_DSL_ELEMENT_TO_COLLECTION)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementRwType, NEW_BUILDER_RETURN_DOCUMENTATION)
                        .withDocumentation(doc -> doc
                                .title(CREATE_NEW_ELEMENT_BUILDER_DOCUMENTATION + COLLECTION_DOCUMENTATION_SUFFIX)
                                .p(NEW_BUILDER_CONFIGURATION_DOCUMENTATION)
                                .param("values", OPTIONAL_PARAMETERS_DOCUMENTATION)
                                .param(CLOSURE_PARAMETER, CONFIGURATION_CLOSURE_DOCUMENTATION))
                        .namedParams("values")
                        .constantParam(fieldName)
                        .constantClassParam(defaultImpl)
                        .constantPrimitveParam(false)
                        .optionalStringParam(fieldKeyName, fieldKey != null, null)
                        .delegatingClosureParam(elementRwType, null)
                        .addTo(rwClass);
            }

            if (!isFinal(elementType)) {
                createProxyMethod(methodName, InternalKlumBuilder.ADD_NEW_DSL_ELEMENT_TO_COLLECTION)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementRwType, NEW_BUILDER_RETURN_DOCUMENTATION)
                        .withDocumentation(doc -> doc
                                .title(CREATE_NEW_ELEMENT_BUILDER_DOCUMENTATION + COLLECTION_DOCUMENTATION_SUFFIX)
                                .p(NEW_BUILDER_CONFIGURATION_DOCUMENTATION)
                                .p(DYNAMIC_TYPE_SELECTION_DOCUMENTATION)
                                .param("values", OPTIONAL_PARAMETERS_DOCUMENTATION)
                                .param(CLOSURE_PARAMETER, CONFIGURATION_CLOSURE_DOCUMENTATION))
                        .namedParams("values")
                        .constantParam(fieldName)
                        .delegationTargetClassParam("typeToCreate", dslBaseType)
                        .constantPrimitveParam(true)
                        .optionalStringParam(fieldKeyName, fieldKey != null)
                        .delegatingClosureParam()
                        .addTo(rwClass);

                createTypedFactoryProviderMethod(methodName, InternalKlumBuilder.ADD_NEW_DSL_ELEMENT_TO_COLLECTION,
                        fieldNode, dslBaseType, fieldName, fieldKeyName, COLLECTION_DOCUMENTATION_SUFFIX);

            }

            createProxyMethod(fieldName, InternalKlumBuilder.ADD_ELEMENTS_FROM_SCRIPTS_TO_COLLECTION)
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
                .documentationTitle(ADDS_ONE_OR_MORE + storedElementDescription + COLLECTION_DOCUMENTATION_SUFFIX)
                .constantParam(fieldName)
                .arrayParam(storedElementType, "values", ELEMENTS_TO_ADD_DOCUMENTATION)
                .addTo(rwClass);

        createProxyMethod(fieldName, "addElementsToCollection")
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .documentationTitle(ADDS_ONE_OR_MORE + storedElementDescription + COLLECTION_DOCUMENTATION_SUFFIX)
                .constantParam(fieldName)
                .param(GenericsUtils.makeClassSafeWithGenerics(Iterable.class, storedElementType), "values", ELEMENTS_TO_ADD_DOCUMENTATION)
                .addTo(rwClass);

        createProxyMethod(methodName, ADD_ELEMENT_TO_COLLECTION)
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .returning(storedElementType)
                .documentationTitle("Adds one " + storedElementDescription + COLLECTION_DOCUMENTATION_SUFFIX)
                .constantParam(fieldName)
                .param(storedElementType, "value")
                .addTo(rwClass);

        if (optionalLinkField) {
            createProxyMethod(methodName, ADD_ELEMENT_TO_COLLECTION)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(elementType)
                    .documentationTitle("Adds one completed '{{singleElementName}}' as an aggregation LINK target.")
                    .constantParam(fieldName)
                    .param(elementType, "value")
                    .addTo(rwClass);
        }

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

        createProxyMethod(singleElementMethod, ADD_ELEMENT_TO_MAP)
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

        ClassNode elementRwType = DslAstHelper.getRwClassOf(defaultImpl).getPlainNodeReference();
        FieldType relationshipType = getFieldType(fieldNode);
        boolean linkField = relationshipType == FieldType.LINK;
        boolean optionalLinkField = relationshipType == FieldType.OPTIONAL_LINK;
        ClassNode storedElementType = linkField ? elementType : elementRwType;
        String storedElementDescription = linkField
                ? "completed '{{singleElementName}}' LINK targets"
                : "'{{singleElementName}}' Builders";
        int visibility = DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (!linkField) {
            if (isInstantiable(defaultImpl)) {
                createProxyMethod(methodName, ADD_NEW_DSL_ELEMENT_TO_MAP)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementRwType, NEW_BUILDER_RETURN_DOCUMENTATION)
                        .withDocumentation(doc -> doc
                                .title(CREATE_NEW_ELEMENT_BUILDER_DOCUMENTATION + MAP_DOCUMENTATION_SUFFIX)
                                .p(NEW_BUILDER_CONFIGURATION_DOCUMENTATION)
                                .param("values", OPTIONAL_PARAMETERS_DOCUMENTATION)
                                .param(CLOSURE_PARAMETER, CONFIGURATION_CLOSURE_DOCUMENTATION))
                        .namedParams("values")
                        .constantParam(fieldName)
                        .constantClassParam(defaultImpl)
                        .constantPrimitveParam(false)
                        .optionalStringParam("key", elementKeyField != null)
                        .delegatingClosureParam(elementRwType)
                        .addTo(rwClass);
            }

            if (!isFinal(elementType)) {
                createProxyMethod(methodName, ADD_NEW_DSL_ELEMENT_TO_MAP)
                        .optional()
                        .mod(visibility)
                        .linkToField(fieldNode)
                        .returning(elementRwType, NEW_BUILDER_RETURN_DOCUMENTATION)
                        .withDocumentation(doc -> doc
                                .title(CREATE_NEW_ELEMENT_BUILDER_DOCUMENTATION + MAP_DOCUMENTATION_SUFFIX)
                                .p(NEW_BUILDER_CONFIGURATION_DOCUMENTATION)
                                .p(DYNAMIC_TYPE_SELECTION_DOCUMENTATION)
                                .param("values", OPTIONAL_PARAMETERS_DOCUMENTATION)
                                .param(CLOSURE_PARAMETER, CONFIGURATION_CLOSURE_DOCUMENTATION))
                        .namedParams("values")
                        .constantParam(fieldName)
                        .delegationTargetClassParam("typeToCreate", dslBaseType)
                        .constantPrimitveParam(true)
                        .optionalStringParam("key", elementKeyField != null)
                        .delegatingClosureParam()
                        .addTo(rwClass);

                createTypedFactoryProviderMethod(methodName, ADD_NEW_DSL_ELEMENT_TO_MAP,
                        fieldNode, dslBaseType, fieldName, elementKeyField != null ? "key" : null,
                        MAP_DOCUMENTATION_SUFFIX);

            }

            createProxyMethod(fieldName, InternalKlumBuilder.ADD_ELEMENTS_FROM_SCRIPTS_TO_MAP)
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
                .documentationTitle(ADDS_ONE_OR_MORE + storedElementDescription + MAP_DOCUMENTATION_SUFFIX)
                .constantParam(fieldName)
                .param(makeClassSafeWithGenerics(CommonAstHelper.COLLECTION_TYPE, new GenericsType(storedElementType)), "values", ELEMENTS_TO_ADD_DOCUMENTATION)
                .addTo(rwClass);
        createProxyMethod(fieldName, "addElementsToMap")
                .optional()
                .mod(visibility)
                .linkToField(fieldNode)
                .documentationTitle(ADDS_ONE_OR_MORE + storedElementDescription + MAP_DOCUMENTATION_SUFFIX)
                .constantParam(fieldName)
                .arrayParam(storedElementType, "values", ELEMENTS_TO_ADD_DOCUMENTATION)
                .addTo(rwClass);

        createProxyMethod(methodName, ADD_ELEMENT_TO_MAP)
                .optional()
                .mod(visibility)
                .returning(storedElementType)
                .linkToField(fieldNode)
                .documentationTitle("Adds one " + storedElementDescription + MAP_DOCUMENTATION_SUFFIX)
                .constantParam(fieldName)
                .constantParam(null)
                .param(storedElementType, elementToAddVarName)
                .addTo(rwClass);

        if (optionalLinkField) {
            createProxyMethod(methodName, ADD_ELEMENT_TO_MAP)
                    .optional()
                    .mod(visibility)
                    .returning(elementType)
                    .linkToField(fieldNode)
                    .documentationTitle("Adds one completed '{{singleElementName}}' as an aggregation LINK target.")
                    .constantParam(fieldName)
                    .constantParam(null)
                    .param(elementType, elementToAddVarName)
                    .addTo(rwClass);
        }

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
        ClassNode targetRwType = DslAstHelper.getRwClassOf(defaultImpl).getPlainNodeReference();

        Expression keyProvider = getStaticKeyExpression(fieldNode);
        boolean needKeyParameter = targetTypeKeyField != null && keyProvider == null;

        int visibility = DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC;

        if (isInstantiable(defaultImpl)) {
            createProxyMethod(fieldName, CREATE_SINGLE_CHILD)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetRwType)
                    .namedParams("values")
                    .constantParam(fieldName)
                    .constantClassParam(defaultImpl)
                    .constantPrimitveParam(false)
                    .optionalStringParam(targetKeyFieldName, needKeyParameter)
                    .delegatingClosureParam(targetRwType)
                    .addTo(rwClass);
        }

        if (!isFinal(targetFieldType)) {
            createProxyMethod(fieldName, CREATE_SINGLE_CHILD)
                    .optional()
                    .mod(visibility)
                    .linkToField(fieldNode)
                    .returning(targetRwType)
                    .withDocumentation(doc -> doc
                            .title("Creates a new '{{singleElementName}}' Builder to this Builder.")
                            .p(NEW_BUILDER_CONFIGURATION_DOCUMENTATION)
                            .p(DYNAMIC_TYPE_SELECTION_DOCUMENTATION)
                            .param("typeToCreate", "the Class selecting the concrete DSL Object type")
                            .param(CLOSURE_PARAMETER, CONFIGURATION_CLOSURE_DOCUMENTATION)
                            .param("values", OPTIONAL_PARAMETERS_DOCUMENTATION))
                    .namedParams("values")
                    .constantParam(fieldName)
                    .delegationTargetClassParam("typeToCreate", dslBaseType)
                    .constantPrimitveParam(true)
                    .optionalStringParam(targetKeyFieldName, needKeyParameter)
                    .delegatingClosureParam()
                    .addTo(rwClass);

            createTypedFactoryProviderMethod(fieldName, CREATE_SINGLE_CHILD, fieldNode, dslBaseType, fieldName,
                    targetKeyFieldName, " to this Builder.");

        }
    }

    private void createTypedFactoryProviderMethod(String methodName, String runtimeMethod, AnnotatedNode fieldNode,
                                                   ClassNode dslBaseType, String fieldName, String keyName,
                                                   String documentationSuffix) {
        GenericFactoryMethodTypes types = genericFactoryMethodTypes(dslBaseType);
        createProxyMethod(methodName, runtimeMethod)
                .optional()
                .mod(DslAstHelper.isProtected(fieldNode) ? ACC_PROTECTED : ACC_PUBLIC)
                .linkToField(fieldNode)
                .setGenericsTypes(types.methodTypeParameters())
                .returning(types.returnType(), NEW_BUILDER_RETURN_DOCUMENTATION)
                .withDocumentation(doc -> doc
                        .title("Creates a new '{{singleElementName}}' Builder" + documentationSuffix)
                        .p("Selects the implementation statically through the generated Factory. Unlike the dynamic Class " +
                                "overload, which retains the relationship's declared base Builder type, this closure " +
                                "delegates to the exact selected public Builder. Child creation belongs to this Builder's " +
                                "current Construction session and never starts a root lifecycle.")
                        .param(FACTORY_NAME, "the generated Factory selecting the concrete DSL Object type")
                        .param(CLOSURE_PARAMETER, CONFIGURATION_CLOSURE_DOCUMENTATION)
                        .param("values", OPTIONAL_PARAMETERS_DOCUMENTATION))
                .namedParams("values")
                .constantParam(fieldName)
                .delegationTargetParam(types.providerType(), FACTORY_NAME, "the generated Factory selecting the concrete DSL Object type")
                .optionalStringParam(keyName, keyName != null)
                .delegatingClosureParam(FACTORY_NAME, 1, CONFIGURATION_CLOSURE_DOCUMENTATION)
                .addTo(rwClass);
    }

    private GenericFactoryMethodTypes genericFactoryMethodTypes(ClassNode dslBaseType) {
        ClassNode modelPlaceholder = genericPlaceholder("T", dslBaseType);
        GenericsType modelParameter = genericVariable("T", modelPlaceholder, new ClassNode[] { dslBaseType });
        GenericsType modelUse = genericVariable("T", modelPlaceholder, null);

        ClassNode builderBound = makeClassSafeWithGenerics(PUBLIC_KLUM_BUILDER, modelUse);
        ClassNode builderPlaceholder = genericPlaceholder("B", PUBLIC_KLUM_BUILDER);
        GenericsType builderParameter = genericVariable("B", builderPlaceholder, new ClassNode[] { builderBound });
        GenericsType builderUse = genericVariable("B", builderPlaceholder, null);

        ClassNode returnType = genericPlaceholder("B", PUBLIC_KLUM_BUILDER);
        returnType.setGenericsTypes(new GenericsType[] { builderParameter });
        returnType.setUsingGenerics(true);

        ClassNode providerType = makeClassSafeWithGenerics(
                BUILDER_FACTORY_PROVIDER,
                modelUse,
                builderUse
        );
        return new GenericFactoryMethodTypes(
                new GenericsType[] { modelParameter, builderParameter },
                returnType,
                providerType
        );
    }

    private static ClassNode genericPlaceholder(String name, ClassNode erasure) {
        ClassNode result = ClassHelper.makeWithoutCaching(name);
        result.setGenericsPlaceHolder(true);
        result.setRedirect(erasure);
        return result;
    }

    private static GenericsType genericVariable(String name, ClassNode type, ClassNode[] upperBounds) {
        GenericsType result = new GenericsType(type, upperBounds, null);
        result.setName(name);
        result.setPlaceholder(true);
        return result;
    }

    private record GenericFactoryMethodTypes(GenericsType[] methodTypeParameters, ClassNode returnType,
                                             ClassNode providerType) {

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof GenericFactoryMethodTypes that)) return false;
            return Arrays.equals(methodTypeParameters, that.methodTypeParameters)
                    && Objects.equals(returnType, that.returnType)
                    && Objects.equals(providerType, that.providerType);
        }

        @Override
        public int hashCode() {
            int result = Arrays.hashCode(methodTypeParameters);
            result = 31 * result + Objects.hashCode(returnType);
            return 31 * result + Objects.hashCode(providerType);
        }

        @Override
        public String toString() {
            return "GenericFactoryMethodTypes[methodTypeParameters=" + Arrays.toString(methodTypeParameters)
                    + ", returnType=" + returnType + ", providerType=" + providerType + ']';
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
                    varX("this"),
                    varX("this")
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
        createProxyMethod(APPLY_LATER, SCHEDULE_APPLY_LATER)
                .mod(ACC_PUBLIC)
                .delegatingClosureParam(rwClass, null, null)
                .addTo(rwClass);

        createProxyMethod(APPLY_LATER, SCHEDULE_APPLY_LATER)
                .mod(ACC_PUBLIC)
                .param(Integer_TYPE, "phase")
                .delegatingClosureParam(rwClass, null, null)
                .addTo(rwClass);

        createProxyMethod(APPLY_LATER, SCHEDULE_APPLY_LATER)
                .mod(ACC_PUBLIC)
                .param(make(DefaultKlumPhase.class), "phase")
                .delegatingClosureParam(rwClass, null, null)
                .addTo(rwClass);
    }

    private void createFactoryField() {
        ClassNode defaultImpl = getNullSafeClassMember(getAnnotation(annotatedClass, DSL_CONFIG_ANNOTATION), "defaultImpl", annotatedClass);
        ClassNode factoryType = getFactoryBase(defaultImpl);
        rejectReservedKlumNamespace(factoryType);
        BuilderMethodProjection.ensureProjectedMethods(factoryType, defaultImpl);

        boolean factoryIsGeneric = factoryType.redirect().getGenericsTypes() != null;

        InnerClassNode factoryClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + "$_Factory",
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                factoryIsGeneric ? makeClassSafeWithGenerics(factoryType, new GenericsType(defaultImpl)) : newClass(factoryType)
        );
        AstDocumentation.attachText(factoryClass, "Factory for creating instances of " + annotatedClass.getName());

        DslAstHelper.registerAsVerbProvider(factoryClass);

        if (factoryIsGeneric)
            factoryClass.addConstructor(ACC_PUBLIC, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY,
                    ctorSuperS(classX(annotatedClass)));
        else
            factoryClass.addConstructor(ACC_PUBLIC, Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, block());

        overrideFactoryMethods(factoryClass, defaultImpl);
        createAsBuilderFactoryAccessor(defaultImpl);

        annotatedClass.getModule().addClass(factoryClass);

        FieldNode factoryField = new FieldNode(
                FACTORY_FIELD_NAME,
                ACC_PUBLIC | ACC_STATIC | ACC_FINAL,
                newClass(factoryClass),
                annotatedClass,
                ctorX(factoryClass)
        );

        AstDocumentation.attachText(factoryField, "The factory for creating instances of " + annotatedClass.getName());
        factoryField.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class));
        factoryClass.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class));
        GeneratedDslSupport.linkFactory(annotatedClass, factoryClass);
        factoryField.setType(GeneratedDslSupport.of(annotatedClass).getFactoryInterface().getPlainNodeReference());
        annotatedClass.addField(factoryField);
    }

    private ClassNode getFactoryBase(ClassNode defaultImpl) {
        ClassNode factoryBase = getMemberClassValue(dslAnnotation, FACTORY_NAME);
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

    private void overrideFactoryMethods(InnerClassNode factoryClass, ClassNode defaultImpl) {
        Map<String, ClassNode> genericsSpec = new LinkedHashMap<>();
        ClassNode currentLevel = factoryClass;

        while (currentLevel != null && (currentLevel.equals(KLUM_FACTORY) || currentLevel.isDerivedFrom(KLUM_FACTORY))) {
            genericsSpec = createGenericsSpec(currentLevel, genericsSpec);
            ClassNode declaringClass = currentLevel;
            Map<String, ClassNode> currentSpec = genericsSpec;
            currentLevel.getMethods().stream()
                    .filter(method -> method.getDeclaringClass().redirect().equals(declaringClass.redirect()))
                    .filter(MethodNode::isPublic)
                    .filter(method -> !method.isStatic())
                    .filter(method -> !method.isFinal())
                    .filter(method -> !method.isSynthetic())
                    .filter(method -> !method.getName().startsWith(RESERVED_KLUM_NAMESPACE))
                    .filter(method -> !method.getName().equals("getAsBuilder"))
                    .map(method -> correctFactoryMethod(currentSpec, method))
                    .forEach(method -> overrideFactoryMethod(factoryClass, defaultImpl, method));
            currentLevel = currentLevel.getUnresolvedSuperClass();
        }
    }

    private static MethodNode correctFactoryMethod(Map<String, ClassNode> genericsSpec, MethodNode source) {
        MethodNode corrected = correctToGenericsSpec(genericsSpec, source);
        MethodNode twin = source.getNodeMetaData(BuilderMethodProjection.TWIN_METADATA_KEY);
        if (twin != null)
            corrected.setNodeMetaData(BuilderMethodProjection.TWIN_METADATA_KEY, twin);
        AstDocumentation.extractExact(source).ifPresent(documentation -> AstDocumentation.attach(corrected, documentation));
        return corrected;
    }

    private void overrideFactoryMethod(InnerClassNode factoryClass, ClassNode defaultImpl, MethodNode methodNode) {
        Parameter[] sourceParameters = methodNode.getParameters();
        if (sourceParameters.length > 0 && sourceParameters[sourceParameters.length - 1].getType().equals(CLOSURE_TYPE)) {
            overrideUndelegatedClosureMethod(factoryClass, defaultImpl, methodNode);
            return;
        }

        Parameter[] parameters = cloneFactoryParameters(methodNode);
        if (factoryClass.getDeclaredMethod(methodNode.getName(), parameters) != null)
            return;

        Statement body = methodNode.getReturnType().equals(VOID_TYPE)
                ? stmt(callSuperX(methodNode.getName(), args(parameters)))
                : returnS(callSuperX(methodNode.getName(), args(parameters)));
        MethodNode override = new MethodNode(
                methodNode.getName(),
                methodNode.getModifiers(),
                methodNode.getReturnType(),
                parameters,
                methodNode.getExceptions(),
                body
        );
        override.setGenericsTypes(methodNode.getGenericsTypes());
        MethodNode twin = methodNode.getNodeMetaData(BuilderMethodProjection.TWIN_METADATA_KEY);
        if (twin != null)
            override.setNodeMetaData(BuilderMethodProjection.TWIN_METADATA_KEY, twin);
        AstDocumentation.extractExact(methodNode).ifPresent(documentation -> AstDocumentation.attach(override, documentation));
        factoryClass.addMethod(override);
    }

    private void createAsBuilderFactoryAccessor(ClassNode defaultImpl) {
        ClassNode factoryType;
        if (!isInstantiable(defaultImpl))
            factoryType = BUILDER_FACTORY;
        else
            factoryType = keyField == null ? UNKEYED_BUILDER_FACTORY : KEYED_BUILDER_FACTORY;

        ClassNode specialized = factoryType.getPlainNodeReference();
        specialized.setUsingGenerics(true);
        specialized.setGenericsTypes(new GenericsType[] {
                new GenericsType(defaultImpl.getPlainNodeReference()),
                new GenericsType(GeneratedDslSupport.of(annotatedClass).getBuilderInterface())
        });

        MethodNode accessor = new MethodNode(
                "getAsBuilder",
                ACC_PUBLIC | ACC_ABSTRACT,
                specialized,
                Parameter.EMPTY_ARRAY,
                ClassNode.EMPTY_ARRAY,
                EmptyStatement.INSTANCE
        );
        AstDocumentation.attachText(accessor,
                "Returns the active-session factory for creating owned " + annotatedClass.getName() + " Builders.");
        GeneratedDslSupport.of(annotatedClass).getFactoryInterface().addMethod(accessor);
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

        Parameter[] parameters = cloneFactoryParameters(methodNode);
        Parameter closureParam = parameters[parameters.length - 1];

        AnnotationNode delegatesTo = new AnnotationNode(DELEGATES_TO_ANNOTATION);
        ClassNode publicBuilder = GeneratedDslSupport.of(annotatedClass).getBuilderInterface();
        delegatesTo.setMember("value", classX(publicBuilder));
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
        AstDocumentation.extractExact(methodNode).ifPresent(documentation -> AstDocumentation.attach(newMethod, documentation));
        MethodNode twin = methodNode.getNodeMetaData(BuilderMethodProjection.TWIN_METADATA_KEY);
        if (twin != null)
            newMethod.setNodeMetaData(BuilderMethodProjection.TWIN_METADATA_KEY, twin);
        MethodNode existing = factoryClass.getDeclaredMethod(methodNode.getName(), parameters);
        if (existing == null)
            factoryClass.addMethod(newMethod);
    }

    private static Parameter[] cloneFactoryParameters(MethodNode source) {
        Parameter[] sourceParameters = source.getParameters();
        Parameter[] result = new Parameter[sourceParameters.length];
        for (int index = 0; index < sourceParameters.length; index++) {
            Parameter parameter = sourceParameters[index];
            Parameter clone = new Parameter(parameter.getType(), parameter.getName(), parameter.getInitialExpression());
            copyAnnotationsFromSourceToTarget(parameter, clone, Collections.emptyList());
            result[index] = clone;
        }
        return result;
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
