package com.blackbuild.groovy.configdsl.transform;

import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static com.blackbuild.groovy.configdsl.transform.MethodBuilder.createPublicMethod;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.expr.MethodCallExpression.NO_ARGUMENTS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.newClass;
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

    private static final ClassNode[] NO_EXCEPTIONS = new ClassNode[0];
    private static final ClassNode DSL_CONFIG_ANNOTATION = make(DSLConfig.class);
    private static final ClassNode DSL_FIELD_ANNOTATION = make(DSLField.class);
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
        createPublicMethod(getMethodNameForField(fieldNode))
            .param(fieldNode.getType(), "value")
            .assignS(propX(varX("this"), fieldNode.getName()), varX("value"))
            .addTo(annotatedClass);
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

        createPublicMethod(getMethodNameForField(fieldNode))
            .arrayParam(elementType, "values")
            .statement(callX(propX(varX("this"), fieldNode.getName()), "addAll", varX("values")))
            .addTo(annotatedClass);

        createPublicMethod(getElementNameForCollectionField(fieldNode))
                .param(elementType, "value")
                .statement(callX(propX(varX("this"), fieldNode.getName()), "add", varX("value")))
                .addTo(annotatedClass);
    }

    private void createListOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        InnerClassNode contextClass = createInnerContextClassForListMembers(fieldNode, elementType);
        createContextClosure(fieldNode, contextClass);
    }

    private void createContextClosure(FieldNode fieldNode, InnerClassNode contextClass) {

        createPublicMethod(getMethodNameForField(fieldNode))
                .delegatingClosureParam(contextClass)
                .declS("context", ctorX(contextClass, varX("this")))
                .statements(delegateToClosure())
                .addTo(annotatedClass);
    }

    @NotNull
    private Statement[] delegateToClosure() {
        return new Statement[]{
                assignS(propX(varX("closure"), "delegate"), varX("context")),
                assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                ),
                stmt(callX(varX("closure"), "call"))
        };
    }

    private InnerClassNode createInnerContextClassForListMembers(FieldNode fieldNode, ClassNode elementType) {
        InnerClassNode contextClass = createInnerContextClass(fieldNode);

        String methodName = getElementNameForCollectionField(fieldNode);

        FieldNode fieldKey = getKeyField(elementType);
        FieldNode ownerFieldOfElement = getOwnerField(elementType);
        String ownerFieldOfElementName = getOwnerFieldName(elementType);

        if (!isAbstract(elementType)) {
            createPublicMethod(methodName)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementType)
                    .declS("created", callX(elementType, "create", argsWithOptionalKeyAndClosure(fieldKey)))
                    .callS(getOuterInstanceXforField(fieldNode), "add", varX("created"))
                    .optionalAssignS(propX(varX("created"), ownerFieldOfElementName), propX(varX("this"), "outerInstance"), ownerFieldOfElement)
                    .addTo(contextClass);
        }

        if (!isFinal(elementType)) {
            createPublicMethod(methodName)
                    .classParam("typeToCreate", elementType)
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementType)
                    .declS("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(fieldKey)))
                    .callS(getOuterInstanceXforField(fieldNode), "add", callX(varX("created"), "apply", varX("closure")))
                    .optionalAssignS(propX(varX("created"), ownerFieldOfElementName), propX(varX("this"), "outerInstance"), ownerFieldOfElement)
                    .addTo(contextClass);
        }

        List<ClassNode> classesList = getClassesList(fieldNode, elementType);
        for (ClassNode implementation : classesList) {
            createPublicMethod(uncapitalizedSimpleClassName(implementation))
                    .optionalStringParam("key", fieldKey)
                    .delegatingClosureParam(elementType)
                    .callS(varX("this"), methodName, argsWithClassOptionalKeyAndClosure(implementation, fieldKey))
                    .addTo(contextClass);
        }

        createPublicMethod(REUSE_METHOD_NAME)
                .param(elementType, "value")
                .callS(getOuterInstanceXforField(fieldNode), "add", varX("value"))
                .optionalAssignS(propX(varX("value"), ownerFieldOfElementName), propX(varX("this"), "outerInstance"), ownerFieldOfElement)
                .addTo(contextClass);

        if (fieldKey != null) {
            addDynamicKeyedCreatorMethod(contextClass, methodName);
        }

        annotatedClass.getModule().addClass(contextClass);

        return contextClass;
    }

    private Expression optionalKeyArg(FieldNode fieldKey) {
        return fieldKey != null ? args("key") : NO_ARGUMENTS;
    }

    private ArgumentListExpression argsWithOptionalKeyAndClosure(FieldNode fieldKey) {
        return fieldKey != null ? args("key", "closure") : args("closure");
    }

    private ArgumentListExpression argsWithClassOptionalKeyAndClosure(ClassNode type, FieldNode fieldKey) {
        return fieldKey != null ? args(classX(type), varX("key"), varX("closure")) : args(classX(type), varX("closure"));
    }

    private void addDynamicKeyedCreatorMethod(InnerClassNode contextClass, String methodName) {
        createPublicMethod("invokeMethod")
                .returning(ClassHelper.OBJECT_TYPE)
                .stringParam("name")
                .objectParam("args")
                .statement(ifElseS(andX(
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
                .addTo(contextClass);
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

        createPublicMethod(methodName)
                .param(fieldNode.getType(), "values")
                .callS(propX(varX("this"), fieldNode.getName()), "putAll", varX("values"))
                .addTo(annotatedClass);

        String singleElementMethod = getElementNameForCollectionField(fieldNode);

        createPublicMethod(singleElementMethod)
                .param(keyType, "key")
                .param(valueType, "value")
                .callS(propX(varX("this"), fieldNode.getName()), "put", args("key", "value"))
                .addTo(annotatedClass);
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
        FieldNode ownerFieldOfElement = getOwnerField(elementType);
        String ownerFieldOfElementName = getOwnerFieldName(elementType);

        if (!isAbstract(elementType)) {
            createPublicMethod(methodName)
                    .param(keyType, "key")
                    .delegatingClosureParam(elementType)
                    .declS("created", callX(elementType, "create", args("key", "closure")))
                    .callS(getOuterInstanceXforField(fieldNode), "put", args(varX("key"), varX("created")))
                    .optionalAssignS(propX(varX("created"), ownerFieldOfElementName), propX(varX("this"), "outerInstance"), ownerFieldOfElement)
                    .addTo(contextClass);
        }

        if (!isFinal(elementType)) {
            createPublicMethod(methodName)
                    .classParam("typeToCreate", elementType)
                    .param(keyType, "key")
                    .delegatingClosureParam(elementType)
                    .declS("created", callX(varX("typeToCreate"), "newInstance", args("key")))
                    .callS(varX("created"), "apply", varX("closure"))
                    .callS(getOuterInstanceXforField(fieldNode), "put", args(varX("key"), varX("created")))
                    .optionalAssignS(propX(varX("created"), ownerFieldOfElementName), propX(varX("this"), "outerInstance"), ownerFieldOfElement)
                    .addTo(contextClass);
        }

        List<ClassNode> classesList = getClassesList(fieldNode, elementType);
        for (ClassNode implementation : classesList) {
            createPublicMethod(uncapitalizedSimpleClassName(implementation))
                    .param(keyType, "key")
                    .delegatingClosureParam(elementType)
                    .callS(varX("this"), methodName, args(classX(implementation), varX("key"), varX("closure")))
                    .addTo(contextClass);
        }

        //noinspection ConstantConditions
        createPublicMethod(REUSE_METHOD_NAME)
                .param(elementType, "value")
                .callS(getOuterInstanceXforField(fieldNode), "put",
                        args(propX(varX("value"), getKeyField(elementType).getName()), varX("value"))
                )
                .optionalAssignS(propX(varX("value"), ownerFieldOfElementName), propX(varX("this"), "outerInstance"), ownerFieldOfElement)
                .addTo(contextClass);

        addDynamicKeyedCreatorMethod(contextClass, methodName);

        annotatedClass.getModule().addClass(contextClass);

        return contextClass;
    }

    private void createSingleDSLObjectClosureMethod(FieldNode fieldNode) {
        String methodName = getMethodNameForField(fieldNode);

        ClassNode fieldType = fieldNode.getType();
        FieldNode keyField = getKeyField(fieldType);
        FieldNode ownerFieldOfElement = getOwnerField(fieldType);
        String ownerFieldName = getOwnerFieldName(fieldType);

        if (!isAbstract(fieldType)) {
            createPublicMethod(methodName)
                    .optionalStringParam("key", keyField)
                    .delegatingClosureParam(fieldType)
                    .declS("created", callX(fieldType, "create", argsWithOptionalKeyAndClosure(keyField)))
                    .assignS(propX(varX("this"), fieldNode.getName()), varX("created"))
                    .optionalAssignS(propX(varX("created"), ownerFieldName), varX("this"), ownerFieldOfElement)
                    .addTo(annotatedClass);
        }

        if (!isFinal(fieldType)) {
            createPublicMethod(methodName)
                    .classParam("typeToCreate", fieldType)
                    .optionalStringParam("key", keyField)
                    .delegatingClosureParam(fieldType)
                    .declS("created", callX(varX("typeToCreate"), "newInstance", optionalKeyArg(keyField)))
                    .assignS(propX(varX("this"), fieldNode.getName()), callX(varX("created"), "apply", varX("closure")))
                    .optionalAssignS(propX(varX("created"), ownerFieldName), varX("this"), ownerFieldOfElement)
                    .addTo(annotatedClass);
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isFinal(ClassNode classNode) {
        return (classNode.getModifiers() & ACC_FINAL) != 0;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isAbstract(ClassNode classNode) {
        return (classNode.getModifiers() & ACC_ABSTRACT) != 0;
    }

    private void createApplyMethod() {
        boolean hasExistingApply = hasDeclaredMethod(annotatedClass, "apply", 1);
        if (hasExistingApply && hasDeclaredMethod(annotatedClass, "_apply", 1)) return;

        createPublicMethod(hasExistingApply ? "_apply" : "apply")
                .returning(newClass(annotatedClass))
                .delegatingClosureParam(annotatedClass)
                .assignS(propX(varX("closure"), "delegate"), varX("this"))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .callS(varX("closure"), "call")
                .statement(returnS(varX("this")))
                .addTo(annotatedClass);
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

        createPublicMethod(hasExistingFactory ? "_create" : "create")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .stringParam("name")
                .delegatingClosureParam(annotatedClass)
                .statement(returnS(callX(
                                ctorX(annotatedClass, args("name")),
                                "apply", varX("closure")
                        )
                ))
                .addTo(annotatedClass);
    }

    private void createSimpleFactoryMethod() {
        boolean hasExistingFactory = hasDeclaredMethod(annotatedClass, "create", 1);
        if (hasExistingFactory && hasDeclaredMethod(annotatedClass, "_create", 1)) return;

        createPublicMethod(hasExistingFactory ? "_create" : "create")
                .returning(newClass(annotatedClass))
                .mod(Opcodes.ACC_STATIC)
                .delegatingClosureParam(annotatedClass)
                .statement(returnS(callX(ctorX(annotatedClass), "apply", varX("closure"))))
                .addTo(annotatedClass);
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

//        if (getAnnotation(result.getType(), DSL_CONFIG_ANNOTATION) == null) {
//            addCompileError(
//                    String.format("Designated owner field '%s' must be a dsl object, but is '%s' instead", ownerFieldName, result.getType().getName()),
//                    getAnnotation(target, DSL_CONFIG_ANNOTATION)
//            );
//            return null;
//        }

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

    private AnnotationNode getAnnotation(AnnotatedNode field, ClassNode type) {
        List<AnnotationNode> annotation = field.getAnnotations(type);
        return annotation.isEmpty() ? null : annotation.get(0);
    }

    private void addCompileError(String msg, ASTNode node) {
        SyntaxException se = new SyntaxException(msg, node.getLineNumber(), node.getColumnNumber());
        sourceUnit.getErrorCollector().addFatalError(new SyntaxErrorMessage(se, sourceUnit));
    }

    public void addCompileWarning(String msg, ASTNode node) {
        // TODO Need to convert node into CST node?
        //sourceUnit.getErrorCollector().addWarning(WarningMessage.POSSIBLE_ERRORS, msg, node, sourceUnit);
    }
}
