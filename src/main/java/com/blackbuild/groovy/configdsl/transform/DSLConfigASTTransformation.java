package com.blackbuild.groovy.configdsl.transform;

import groovy.lang.DelegatesTo;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static com.blackbuild.groovy.configdsl.transform.MethodBuilder.createPublicVoidMethod;
import static org.codehaus.groovy.ast.ClassHelper.*;
import static org.codehaus.groovy.ast.expr.MethodCallExpression.NO_ARGUMENTS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.*;
import static org.codehaus.groovy.transform.EqualsAndHashCodeASTTransformation.createEquals;
import static org.codehaus.groovy.transform.EqualsAndHashCodeASTTransformation.createHashCode;
import static org.codehaus.groovy.transform.ToStringASTTransformation.createToString;

/**
 * Transformation class for the @DSLConfig annotation.
 *
 * @author Stephan Pauxberger
 */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class DSLConfigASTTransformation extends AbstractASTTransformation {

    public static final ClassNode[] NO_EXCEPTIONS = new ClassNode[0];
    private static final ClassNode DSL_CONFIG_ANNOTATION = make(DSLConfig.class);
    private static final ClassNode DSL_FIELD_ANNOTATION = make(DSLField.class);
    private static final ClassNode DELEGATES_TO_ANNOTATION = make(DelegatesTo.class);
    private static final String REUSE_METHOD_NAME = "reuse";
    private static final ClassNode EQUALS_HASHCODE_ANNOT = make(EqualsAndHashCode.class);
    private static final ClassNode TOSTRING_ANNOT = make(ToString.class);
    private ClassNode annotatedClass;
    private FieldNode keyField;
    private FieldNode ownerField;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {

        init(nodes, source);

        annotatedClass = (ClassNode) nodes[1];
        keyField = getKeyField(annotatedClass);
        ownerField = getOwnerField(annotatedClass);

        if (keyField != null)
            createKeyConstructor();

        createApplyMethod();
        createFactoryMethods();

        createFieldMethods();

        createCanonicalMethods();
    }

    private void createKeyConstructor() {
        annotatedClass.addConstructor(
                ACC_PUBLIC,
                params(param(STRING_TYPE, "key")),
                NO_EXCEPTIONS,
                block(
                        isDSLObject(annotatedClass.getSuperClass()) ? ctorSuperS(args("key")) : ctorSuperS(),
                        assignS(propX(varX("this"), keyField.getName()), varX("key"))
                )
        );
    }

    private boolean isDSLObject(ClassNode classNode) {
        return getAnnotation(classNode, DSL_CONFIG_ANNOTATION) != null;
    }

    private void createCanonicalMethods() {
        if (!hasAnnotation(annotatedClass, EQUALS_HASHCODE_ANNOT)) {
            createHashCode(annotatedClass, false, false, false, null, null);
            createEquals(annotatedClass, false, false, true, null, null);
        }
        if (!hasAnnotation(annotatedClass, TOSTRING_ANNOT)) {
            createToString(annotatedClass, false, false, null, null, false);
        }
    }

    private void createFieldMethods() {
        for (FieldNode fieldNode : annotatedClass.getFields())
            createMethodsForSingleField(fieldNode);
    }

    private void createMethodsForSingleField(FieldNode fieldNode) {
        if (fieldNode == keyField) return;
        if (fieldNode == ownerField) return;

        String methodName = getMethodNameForField(fieldNode);

        if (hasAnnotation(fieldNode.getType(), DSL_CONFIG_ANNOTATION)) {
            createSingleDSLObjectClosureMethod(fieldNode);
            createSingleFieldSetterMethod(fieldNode);
        } else if (Map.class.isAssignableFrom(fieldNode.getType().getTypeClass()))
            createMapMethod(fieldNode);
        else if (List.class.isAssignableFrom(fieldNode.getType().getTypeClass()))
            createListMethod(fieldNode);
        else
            createSingleFieldSetterMethod(fieldNode);
    }

    private void createSingleFieldSetterMethod(FieldNode fieldNode) {
        String methodName = getMethodNameForField(fieldNode);

        annotatedClass.addMethod(
                methodName,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(fieldNode.getType(), "value")),
                NO_EXCEPTIONS,
                block(
                        assignS(propX(varX("this"), fieldNode.getName()), varX("value"))
                )
        );
    }

    private String getMethodNameForField(FieldNode fieldNode) {
        AnnotationNode fieldAnnotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);

        return getNullSafeMemberStringValue(fieldAnnotation, "value", fieldNode.getName());
    }

    private String getElementNameForCollectionField(FieldNode fieldNode) {
        AnnotationNode fieldAnnotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);

        String result = getNullSafeMemberStringValue(fieldAnnotation, "element", null);

        if (result != null && result.length() > 0) return result;

        String collectionMethodName = getMethodNameForField(fieldNode);

        if (collectionMethodName.endsWith("s"))
            return collectionMethodName.substring(0, collectionMethodName.length() - 1);

        return collectionMethodName;
    }

    private String getNullSafeMemberStringValue(AnnotationNode fieldAnnotation, String value, String name) {
        return fieldAnnotation == null ? name : getMemberStringValue(fieldAnnotation, value, name);
    }

    private void createListMethod(FieldNode fieldNode) {
        if (!fieldNode.hasInitialExpression())
            fieldNode.setInitialValueExpression(new ListExpression());

        ClassNode elementType = getGenericsTypes(fieldNode)[0].getType();

        if (hasAnnotation(elementType, DSL_CONFIG_ANNOTATION))
            createListOfDSLObjectMethods(fieldNode, elementType);
        else
            createListOfSimpleElementsMethods(fieldNode, elementType);
    }

    private void createListOfSimpleElementsMethods(FieldNode fieldNode, ClassNode elementType) {

        createPublicVoidMethod(getMethodNameForField(fieldNode))
            .arrayParam(elementType, "values")
            .statement(callX(propX(varX("this"), fieldNode.getName()), "addAll", varX("values")))
            .addTo(annotatedClass);

        createPublicVoidMethod(getElementNameForCollectionField(fieldNode))
                .param(elementType, "value")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "add", varX("value")))
                .addTo(annotatedClass);
    }

    private void createListOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        InnerClassNode contextClass = createInnerContextClassForListMembers(fieldNode, elementType);
        createContextClosure(fieldNode, contextClass);
    }

    private void createContextClosure(FieldNode fieldNode, InnerClassNode contextClass) {

        createPublicVoidMethod(getMethodNameForField(fieldNode))
                .delegatingClosureParam(contextClass)
                .statement(
                        declS(varX("context"), ctorX(contextClass, varX("this"))),
                        assignS(propX(varX("closure"), "delegate"), varX("context")),
                        assignS(
                                propX(varX("closure"), "resolveStrategy"),
                                propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                        ),
                        stmt(callX(varX("closure"), "call"))
                )
                .addTo(annotatedClass);
    }

    private InnerClassNode createInnerContextClassForListMembers(FieldNode fieldNode, ClassNode elementType) {
        InnerClassNode contextClass = createInnerContextClass(fieldNode);

        String methodName = getElementNameForCollectionField(fieldNode);

        FieldNode fieldKey = getKeyField(elementType);
        FieldNode ownerFieldOfElement = getOwnerField(elementType);

        if (!isAbstract(elementType)) {
            BlockStatement methodBody = block(
                    declS(varX("created"),
                            callX(
                                    elementType,
                                    "create",
                                    fieldKey != null ? args("key", "closure") : args("closure")
                            )
                    ),
                    stmt(callX(getOuterInstanceXforField(fieldNode), "add", varX("created")))
            );

            addOuterInstanceAsOwnerStatementToMethodBody(ownerFieldOfElement, methodBody);

            contextClass.addMethod(
                    methodName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    optionalKeyAndClosureParams(elementType, fieldKey),
                    NO_EXCEPTIONS,
                    methodBody
            );
        }

        if (!isFinal(elementType)) {

            BlockStatement methodBody = fieldKey != null ?
                    block(
                            declS(varX("created"), callX(varX("typeToCreate"), "newInstance", args("key"))),
                            stmt(callX(getOuterInstanceXforField(fieldNode), "add", callX(varX("created"), "apply", varX("closure"))))
                    ) :
                    block(
                            declS(varX("created"), callX(varX("typeToCreate"), "newInstance")),
                            stmt(callX(getOuterInstanceXforField(fieldNode), "add", callX(varX("created"), "apply", varX("closure"))))
                    );

            addOuterInstanceAsOwnerStatementToMethodBody(ownerFieldOfElement, methodBody);

            contextClass.addMethod(
                    methodName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    subclassOptionalKeyAndClosureParams(elementType, fieldKey),
                    NO_EXCEPTIONS,
                    methodBody
            );
        }

        List<ClassNode> classesList = getClassesList(fieldNode, elementType);
        for (ClassNode implementation : classesList) {
            String alternativeName = uncapitalizedSimpleClassName(implementation);

            contextClass.addMethod(
                    alternativeName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    optionalKeyAndClosureParams(elementType, fieldKey),
                    NO_EXCEPTIONS,
                    block(
                            stmt(callX(getOuterInstanceXforField(fieldNode), "add",
                                    callX(
                                            implementation,
                                            "create",
                                            fieldKey != null ? args("key", "closure") : args("closure")
                                    )
                            ))
                    )
            );
        }

        BlockStatement reuseMethodBody = block(
                stmt(callX(getOuterInstanceXforField(fieldNode), "add",
                        varX("value")
                ))
        );

        addSetOwnerToOuterInstanceStatement(ownerFieldOfElement, reuseMethodBody);

        contextClass.addMethod(
                REUSE_METHOD_NAME,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(elementType, "value")),
                NO_EXCEPTIONS,
                reuseMethodBody
        );

        if (fieldKey != null) {
            addDynamicKeyedCreatorMethod(contextClass, methodName);
        }


        annotatedClass.getModule().addClass(contextClass);

        return contextClass;
    }

    private Parameter[] subclassOptionalKeyAndClosureParams(ClassNode elementType, FieldNode fieldKey) {
        return fieldKey != null
                ? classKeyAndClosureParams(elementType)
                : params(createSubclassClassParameter(annotatedClass), createAnnotatedClosureParameter(elementType));
    }

    private Parameter[] classKeyAndClosureParams(ClassNode elementType) {
        return params(createSubclassClassParameter(annotatedClass), param(ClassHelper.STRING_TYPE, "key"), createAnnotatedClosureParameter(elementType));
    }

    private Parameter[] optionalKeyAndClosureParams(ClassNode elementType, FieldNode fieldKey) {
        return fieldKey != null ?
                keyAndClosureParameter(elementType)
                : params(createAnnotatedClosureParameter(elementType));
    }

    private Parameter[] keyAndClosureParameter(ClassNode elementType) {
        return params(param(ClassHelper.STRING_TYPE, "key"), createAnnotatedClosureParameter(elementType));
    }

    private void addSetOwnerToOuterInstanceStatement(FieldNode ownerFieldOfElement, BlockStatement reuseMethodBody) {
        if (ownerFieldOfElement != null) {
            reuseMethodBody.addStatement(
                    assignS(propX(varX("value"), ownerFieldOfElement.getName()), propX(varX("this"), "outerInstance"))
            );
        }
    }

    private void addDynamicKeyedCreatorMethod(InnerClassNode contextClass, String methodName) {
        contextClass.addMethod(
                "invokeMethod",
                Opcodes.ACC_PUBLIC,
                ClassHelper.OBJECT_TYPE,
                params(param(ClassHelper.STRING_TYPE, "name"), param(ClassHelper.OBJECT_TYPE, "args")),
                NO_EXCEPTIONS,
                block(
                        ifElseS(andX(
                                        isOneX(new PropertyExpression(varX("args"), constX("length"), true)),
                                        isInstanceOfX(
                                                indexX(varX("args"), constX(0)),
                                                ClassHelper.CLOSURE_TYPE)
                                ),
                                stmt(callThisX(
                                        methodName,
                                        args(varX("name"), castX(ClassHelper.CLOSURE_TYPE, indexX(varX("args"), constX(0))))
                                )),
                                stmt(callSuperX("invokeMethod", args("name", "args")))
                        )
                )
        );
    }

    private String uncapitalizedSimpleClassName(ClassNode node) {
        char[] name = node.getNameWithoutPackage().toCharArray();
        name[0] = Character.toLowerCase(name[0]);
        return new String(name);
    }

    private List<ClassNode> getClassesList(AnnotatedNode fieldNode, ClassNode elementType) {
        AnnotationNode annotation = getAnnotation(fieldNode, DSL_FIELD_ANNOTATION);
        if (annotation == null) return Collections.emptyList();

        List<ClassNode> subclasses = getClassList(annotation, "alternatives");

        if (!subclasses.contains(elementType) && !isAbstract(elementType))
            subclasses.add(elementType);

        return subclasses;
    }

    @NotNull
    private Expression getOuterInstanceXforField(FieldNode fieldNode) {
        return propX(propX(varX("this"), "outerInstance"), fieldNode.getName());
    }

    @NotNull
    private InnerClassNode createInnerContextClass(FieldNode fieldNode) {
        InnerClassNode contextClass = new InnerClassNode(
                annotatedClass,
                annotatedClass.getName() + "$" + fieldNode.getName() + "Context",
                ACC_STATIC,
                ClassHelper.OBJECT_TYPE);

        contextClass.addField("outerInstance", 0, newClass(annotatedClass), null);
        contextClass.addConstructor(
                0,
                params(param(newClass(annotatedClass), "outerInstance")),
                NO_EXCEPTIONS,
                block(
                        assignS(
                                propX(varX("this"), "outerInstance"),
                                varX("outerInstance")
                        )
                )
        );
        return contextClass;
    }

    @SuppressWarnings("ConstantConditions")
    private GenericsType[] getGenericsTypes(FieldNode fieldNode) {
        GenericsType[] types = fieldNode.getType().getGenericsTypes();

        if (types == null)
            addCompileError("Lists and Maps need to be assigned an explicit Generic Type", fieldNode);
        return types;
    }

    private void createMapMethod(FieldNode fieldNode) {
        if (!fieldNode.hasInitialExpression())
            fieldNode.setInitialValueExpression(new MapExpression());

        ClassNode keyType = getGenericsTypes(fieldNode)[0].getType();
        ClassNode valueType = getGenericsTypes(fieldNode)[1].getType();

        if (hasAnnotation(valueType, DSL_CONFIG_ANNOTATION))
            createMapOfDSLObjectMethods(fieldNode, keyType, valueType);
        else
            createMapOfSimpleElementsMethods(fieldNode, keyType, valueType);
    }

    private void createMapOfSimpleElementsMethods(FieldNode fieldNode, ClassNode keyType, ClassNode valueType) {
        String methodName = getMethodNameForField(fieldNode);

        annotatedClass.addMethod(
                methodName,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(fieldNode.getType(), "values")),
                NO_EXCEPTIONS,
                block(
                        stmt(callX(propX(varX("this"), fieldNode.getName()), "putAll", varX("values")))
                )
        );

        String singleElementMethod = getElementNameForCollectionField(fieldNode);

        annotatedClass.addMethod(
                singleElementMethod,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(keyType, "key"), param(valueType, "value")),
                NO_EXCEPTIONS,
                block(
                        stmt(callX(propX(varX("this"), fieldNode.getName()), "put", args("key", "value")))
                )
        );
    }

    private void createMapOfDSLObjectMethods(FieldNode fieldNode, ClassNode keyType, ClassNode elementType) {
        if (getKeyField(elementType) == null) {
            addCompileError(
                    String.format("Value type of map %s (%s) has now key field", fieldNode, elementType),
                    fieldNode
            );
        }

        InnerClassNode contextClass = createInnerContextClassForMapMembers(fieldNode, keyType, elementType);
        createContextClosure(fieldNode, contextClass);
    }

    private InnerClassNode createInnerContextClassForMapMembers(FieldNode fieldNode, ClassNode keyType, ClassNode elementType) {
        InnerClassNode contextClass = createInnerContextClass(fieldNode);

        String methodName = getElementNameForCollectionField(fieldNode);
        FieldNode fieldKey = getKeyField(elementType);
        FieldNode ownerFieldOfElement = getOwnerField(elementType);

        if (!isAbstract(elementType)) {
            BlockStatement methodBody = block(
                    declS(varX("created"),
                            callX(elementType, "create", args("key", "closure"))
                    ),
                    stmt(callX(getOuterInstanceXforField(fieldNode), "put",
                            args(varX("key"), varX("created"))
                    ))
            );

            addOuterInstanceAsOwnerStatementToMethodBody(ownerFieldOfElement, methodBody);

            contextClass.addMethod(
                    methodName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    keyAndClosureParameter(elementType),
                    NO_EXCEPTIONS,
                    methodBody
            );
        }

        if (!isFinal(elementType)) {
            BlockStatement methodBody = block(
                    declS(varX("created"), callX(varX("typeToCreate"), "newInstance", args("key"))),
                    stmt(callX(getOuterInstanceXforField(fieldNode), "put",
                                    args(varX("key"), callX(varX("created"), "apply", varX("closure"))))
                    )
            );

            addOuterInstanceAsOwnerStatementToMethodBody(ownerFieldOfElement, methodBody);

            contextClass.addMethod(
                    methodName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    classKeyAndClosureParams(elementType),
                    NO_EXCEPTIONS,
                    methodBody
            );
        }

        List<ClassNode> classesList = getClassesList(fieldNode, elementType);
        for (ClassNode implementation : classesList) {
            String alternativeName = uncapitalizedSimpleClassName(implementation);

            contextClass.addMethod(
                    alternativeName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    keyAndClosureParameter(implementation),
                    NO_EXCEPTIONS,
                    block(
                            stmt(callX(getOuterInstanceXforField(fieldNode), "put",
                                    args(varX("key"),
                                            callX(
                                                    implementation,
                                                    "create",
                                                    args("key", "closure")
                                            )
                                    )
                            ))
                    )
            );
        }


        //noinspection ConstantConditions
        BlockStatement reuseMethodBody = block(
                stmt(callX(getOuterInstanceXforField(fieldNode), "put",
                        args(propX(varX("value"), getKeyField(elementType).getName()), varX("value"))
                ))
        );
        addSetOwnerToOuterInstanceStatement(ownerFieldOfElement, reuseMethodBody);
        contextClass.addMethod(
                REUSE_METHOD_NAME,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(elementType, "value")),
                NO_EXCEPTIONS,
                reuseMethodBody
        );

        addDynamicKeyedCreatorMethod(contextClass, methodName);

        annotatedClass.getModule().addClass(contextClass);

        return contextClass;
    }

    private void addOuterInstanceAsOwnerStatementToMethodBody(FieldNode ownerFieldOfElement, BlockStatement methodBody) {
        if (ownerFieldOfElement != null) {
            methodBody.addStatement(
                    assignS(propX(varX("created"), ownerFieldOfElement.getName()), propX(varX("this"), "outerInstance"))
            );
        }
    }

    private void createSingleDSLObjectClosureMethod(FieldNode fieldNode) {
        String methodName = getMethodNameForField(fieldNode);

        ClassNode fieldType = fieldNode.getType();
        FieldNode keyField = getKeyField(fieldType);
        boolean hasKeyField = keyField != null;
        FieldNode ownerFieldOfElement = getOwnerField(fieldType);

        if (!isAbstract(fieldType)) {
            BlockStatement methodBody = block(
                    declS(
                            varX("created"),
                            callX(
                                    fieldType,
                                    "create",
                                    hasKeyField ? args("key", "closure") : args("closure")
                            )
                    ),
                    assignS(propX(varX("this"), fieldNode.getName()), varX("created"))
            );

            addSetOwnerStatementToMethodBody(ownerFieldOfElement, methodBody);

            annotatedClass.addMethod(
                    methodName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    optionalKeyAndClosureParams(fieldType, keyField),
                    NO_EXCEPTIONS,
                    methodBody
            );
        }

        if (!isFinal(fieldType)) {
            BlockStatement methodBody = block(
                    declS(varX("created"), callX(varX("typeToCreate"), "newInstance", hasKeyField ? args("key") : NO_ARGUMENTS)),
                    assignS(propX(varX("this"), fieldNode.getName()),
                            callX(varX("created"), "apply", varX("closure"))
                    )
            );

            addSetOwnerStatementToMethodBody(ownerFieldOfElement, methodBody);

            annotatedClass.addMethod(
                    methodName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    subclassOptionalKeyAndClosureParams(fieldType, keyField),
                    NO_EXCEPTIONS,
                    methodBody
            );
        }
    }

    private void addSetOwnerStatementToMethodBody(FieldNode ownerFieldOfElement, BlockStatement methodBody) {
        if (ownerFieldOfElement != null) {
            methodBody.addStatement(
                    assignS(propX(varX("created"), ownerFieldOfElement.getName()), varX("this"))
            );
        }
    }

    private boolean isFinal(ClassNode classNode) {
        return (classNode.getModifiers() & ACC_FINAL) != 0;
    }

    private boolean isAbstract(ClassNode classNode) {
        return (classNode.getModifiers() & ACC_ABSTRACT) != 0;
    }

    private Parameter createSubclassClassParameter(ClassNode annotatedClass) {
        return param(makeClassSafeWithGenerics(CLASS_Type, buildWildcardType(annotatedClass)), "typeToCreate");
    }


    private void createApplyMethod() {
        boolean hasExistingApply = hasDeclaredMethod(annotatedClass, "apply", 1);
        if (hasExistingApply && hasDeclaredMethod(annotatedClass, "_apply", 1)) return;

        annotatedClass.addMethod(
                hasExistingApply ? "_apply" : "apply",
                Opcodes.ACC_PUBLIC,
                newClass(annotatedClass),
                params(createAnnotatedClosureParameter(annotatedClass)),
                NO_EXCEPTIONS,
                block(
                        assignS(propX(varX("closure"), "delegate"), varX("this")),
                        assignS(
                                propX(varX("closure"), "resolveStrategy"),
                                propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                        ),
                        stmt(callX(varX("closure"), "call")),
                        returnS(varX("this"))
                )
        );
    }

    private void createFactoryMethods() {

        if (keyField == null)
            createSimpleFactoryMethod();
        else
            createFactoryMethodWithKeyParameter();
    }

    private void createFactoryMethodWithKeyParameter() {
        boolean hasExistingFactory = hasDeclaredMethod(annotatedClass, "create", 2);
        if (hasExistingFactory && hasDeclaredMethod(annotatedClass, "_create", 2)) return;

        annotatedClass.addMethod(
                hasExistingFactory ? "_create" : "create",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                newClass(annotatedClass),
                params(param(ClassHelper.STRING_TYPE, "name"), createAnnotatedClosureParameter(annotatedClass)),
                NO_EXCEPTIONS,
                block(
                        returnS(callX(
                                        ctorX(annotatedClass, args("name")),
                                        "apply", varX("closure")
                                )
                        )
                )
        );
    }

    private void createSimpleFactoryMethod() {
        boolean hasExistingFactory = hasDeclaredMethod(annotatedClass, "create", 1);
        if (hasExistingFactory && hasDeclaredMethod(annotatedClass, "_create", 1)) return;

        annotatedClass.addMethod(
                hasExistingFactory ? "_create" : "create",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                newClass(annotatedClass),
                params(createAnnotatedClosureParameter(annotatedClass)),
                NO_EXCEPTIONS,
                block(returnS(callX(ctorX(annotatedClass), "apply", varX("closure"))))
        );
    }

    private FieldNode getKeyField(ClassNode target) {
        String keyFieldName = getKeyFieldName(target);

        if (keyFieldName == null) return null;

        FieldNode result = target.getField(keyFieldName);

        if (result == null) {
            addCompileError(
                    String.format("Designated Key field '%s' is missing", keyFieldName),
                    getAnnotation(target, DSL_CONFIG_ANNOTATION)
            );
            return null;
        }

        if (!result.getType().equals(ClassHelper.STRING_TYPE)) {
            addCompileError(
                    String.format("Key field '%s' must be of type String, but is '%s' instead", keyFieldName, result.getType().getName()),
                    getAnnotation(target, DSL_CONFIG_ANNOTATION)
            );
            return null;
        }

        return result;
    }

    private FieldNode getOwnerField(ClassNode target) {
        String ownerFieldName = getOwnerFieldName(target);

        if (ownerFieldName == null) return null;

        FieldNode result = target.getField(ownerFieldName);

        if (result == null) {
            addCompileError(
                    String.format("Designated owner field '%s' is missing", ownerFieldName),
                    getAnnotation(target, DSL_CONFIG_ANNOTATION)
            );
            return null;
        }


        if (getAnnotation(result.getType(), DSL_CONFIG_ANNOTATION) == null) {
            addCompileError(
                    String.format("Designated owner field '%s' must be a dsl object, but is '%s' instead", ownerFieldName, result.getType().getName()),
                    getAnnotation(target, DSL_CONFIG_ANNOTATION)
            );
            return null;
        }

        return result;
    }

    private String getKeyFieldName(ClassNode target) {
        Deque<ClassNode> hierarchy = getHierarchyOfDSLObjectAncestors(new LinkedList<ClassNode>(), target);

        String firstKey = getNullSafeMemberStringValue(getAnnotation(hierarchy.removeFirst(), DSL_CONFIG_ANNOTATION), "key", null);

        for (ClassNode node : hierarchy) {
            String keyOfCurrentNode = getNullSafeMemberStringValue(getAnnotation(node, DSL_CONFIG_ANNOTATION), "key", null);

            if (firstKey == null && keyOfCurrentNode == null) continue;

            if (firstKey == null) {
                addCompileError(
                        String.format("Inconsistent hierarchy: Toplevel class %s has no key, but child class %s defines '%s'.", target, node, keyOfCurrentNode),
                        node
                );
                return null;
            }

            if (keyOfCurrentNode == null) continue;

            if (!firstKey.equals(keyOfCurrentNode)) {
                addCompileError(
                        String.format("Inconsistent hierarchy: Toplevel defines %s defines key '%s', but child class %s defines '%s'.", target, firstKey, node, keyOfCurrentNode),
                        node
                );
                return null;
            }
        }

        return firstKey;
    }

    private String getOwnerFieldName(ClassNode target) {
        Deque<ClassNode> hierarchy = getHierarchyOfDSLObjectAncestors(new LinkedList<ClassNode>(), target);

        String owner = null;
        ClassNode declaringClass = null;

        for (ClassNode node : hierarchy) {
            String ownerOfCurrentNode = getNullSafeMemberStringValue(getAnnotation(node, DSL_CONFIG_ANNOTATION), "owner", null);

            if (owner == null && ownerOfCurrentNode == null) continue;

            if (owner == null) {
                owner = ownerOfCurrentNode;
                declaringClass = node;
                continue;
            }

            if (owner.equals(ownerOfCurrentNode)) continue;
            if (ownerOfCurrentNode == null) continue;

            addCompileError(
                    String.format("Inconsistent owner hierarchy: class %s defines owner '%s', but child class %s defines '%s'.", declaringClass, owner, node, ownerOfCurrentNode),
                    node
            );
            return null;
        }

        return owner;
    }

    private Deque<ClassNode> getHierarchyOfDSLObjectAncestors(Deque<ClassNode> hierarchy, ClassNode target) {
        if (!isDSLObject(target)) return hierarchy;

        hierarchy.addFirst(target);
        return getHierarchyOfDSLObjectAncestors(hierarchy, target.getSuperClass());
    }

    private Parameter createAnnotatedClosureParameter(ClassNode target) {
        Parameter result = param(GenericsUtils.nonGeneric(ClassHelper.CLOSURE_TYPE), "closure");
        result.addAnnotation(createDelegatesToAnnotation(target));
        return result;
    }

    private AnnotationNode createDelegatesToAnnotation(ClassNode target) {
        AnnotationNode result = new AnnotationNode(DELEGATES_TO_ANNOTATION);
        result.setMember("value", classX(target));
        return result;
    }

    AnnotationNode getAnnotation(AnnotatedNode field, ClassNode type) {
        List<AnnotationNode> annotation = field.getAnnotations(type);
        return annotation.isEmpty() ? null : annotation.get(0);
    }

    public void addCompileError(String msg, ASTNode node) {
        SyntaxException se = new SyntaxException(msg, node.getLineNumber(), node.getColumnNumber());
        sourceUnit.getErrorCollector().addFatalError(new SyntaxErrorMessage(se, sourceUnit));
    }

    public void addCompileWarning(String msg, ASTNode node) {
        // TODO Need to convert node into CST node?
        //sourceUnit.getErrorCollector().addWarning(WarningMessage.POSSIBLE_ERRORS, msg, node, sourceUnit);
    }
}
