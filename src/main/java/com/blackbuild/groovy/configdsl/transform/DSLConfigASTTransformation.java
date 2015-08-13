package com.blackbuild.groovy.configdsl.transform;

import groovy.lang.DelegatesTo;
import groovy.transform.EqualsAndHashCode;
import groovy.transform.ToString;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.ExceptionMessage;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;

import java.util.*;

import static org.codehaus.groovy.ast.ClassHelper.CLASS_Type;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.expr.MethodCallExpression.NO_ARGUMENTS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.ast.tools.GenericsUtils.buildWildcardType;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafeWithGenerics;
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

    private static final ClassNode DSL_CONFIG_ANNOTATION = make(DSLConfig.class);
    private static final ClassNode DSL_FIELD_ANNOTATION = make(DSLField.class);
    private static final ClassNode DELEGATES_TO_ANNOTATION = make(DelegatesTo.class);
    private static final String REUSE_METHOD_NAME = "reuse";

    private static final ClassNode EQUALS_HASHCODE_ANNOT = make(EqualsAndHashCode.class);
    private static final ClassNode TOSTRING_ANNOT = make(ToString.class);
    public static final ClassNode[] NO_EXCEPTIONS = new ClassNode[0];

    private ClassNode annotatedClass;
    private FieldNode keyField;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {

        init(nodes, source);

        annotatedClass = (ClassNode) nodes[1];
        keyField = getKeyField(annotatedClass);

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
                        isDSLObject(annotatedClass.getSuperClass()) ? ctorSuperS() : ctorSuperS(args("key")),
                        assignS(propX(varX("this"), keyField.getName()), varX("key"))
                )
        );
    }

    private boolean isDSLObject(ClassNode classNode) {
        return getAnnotation(classNode, DSL_CONFIG_ANNOTATION) == null;
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

        String methodName = getMethodNameForField(fieldNode);

        if (hasAnnotation(fieldNode.getType(), DSL_CONFIG_ANNOTATION)) {
            createSingleDSLObjectClosureMethod(fieldNode);
            createSingleFieldSetterMethod(fieldNode);
        }
        else if (Map.class.isAssignableFrom(fieldNode.getType().getTypeClass()))
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
        String methodName = getMethodNameForField(fieldNode);

        annotatedClass.addMethod(
                methodName,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(elementType.makeArray(), "values")),
                NO_EXCEPTIONS,
                block(
                        stmt(callX(propX(varX("this"), fieldNode.getName()), "addAll", varX("values")))
                )
        );

        String singleElementMethod = getElementNameForCollectionField(fieldNode);

        annotatedClass.addMethod(
                singleElementMethod,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(elementType, "value")),
                NO_EXCEPTIONS,
                block(
                        stmt(callX(propX(varX("this"), fieldNode.getName()), "add", varX("value")))
                )
        );
    }

    private void createListOfDSLObjectMethods(FieldNode fieldNode, ClassNode elementType) {
        InnerClassNode contextClass = createInnerContextClassForListMembers(fieldNode, elementType);
        createContextClosure(fieldNode, contextClass);
    }

    private void createContextClosure(FieldNode fieldNode, InnerClassNode contextClass) {
        annotatedClass.addMethod(
                getMethodNameForField(fieldNode),
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(createAnnotatedClosureParameter(contextClass)),
                NO_EXCEPTIONS,
                block(
                        declS(varX("context"), ctorX(contextClass, varX("this"))),
                        assignS(propX(varX("closure"), "delegate"), varX("context")),
                        assignS(
                                propX(varX("closure"), "resolveStrategy"),
                                propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                        ),
                        stmt(callX(varX("closure"), "call"))
                )
        );
    }

    private InnerClassNode createInnerContextClassForListMembers(FieldNode fieldNode, ClassNode elementType) {
        InnerClassNode contextClass = createInnerContextClass(fieldNode);

        String methodName = getElementNameForCollectionField(fieldNode);

        FieldNode fieldKey = getKeyField(elementType);

        contextClass.addMethod(
                methodName,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                fieldKey != null ?
                        params(param(ClassHelper.STRING_TYPE, "key"), createAnnotatedClosureParameter(elementType))
                        : params(createAnnotatedClosureParameter(elementType)),
                NO_EXCEPTIONS,
                block(
                        stmt(callX(getOuterInstanceXforField(fieldNode), "add",
                                callX(
                                        elementType,
                                        "create",
                                        fieldKey != null ? args("key", "closure") : args("closure")
                                )
                        ))
                )
        );

        if (!isFinal(elementType)) {
            if (fieldKey != null) {

                contextClass.addMethod(
                        methodName,
                        Opcodes.ACC_PUBLIC,
                        ClassHelper.VOID_TYPE,
                        params(createSubclassClassParameter(annotatedClass), param(ClassHelper.STRING_TYPE, "key"), createAnnotatedClosureParameter(elementType)),
                        NO_EXCEPTIONS,
                        block(
                                declS(varX("created"), callX(varX("typeToCreate"), "newInstance", args("key"))),
                                stmt(callX(getOuterInstanceXforField(fieldNode), "add", callX(varX("created"), "apply", varX("closure"))))
                        )
                );
            } else {
                contextClass.addMethod(
                        methodName,
                        Opcodes.ACC_PUBLIC,
                        ClassHelper.VOID_TYPE,
                        params(createSubclassClassParameter(annotatedClass), createAnnotatedClosureParameter(elementType)),
                        NO_EXCEPTIONS,
                        block(
                                declS(varX("created"), callX(varX("typeToCreate"), "newInstance")),
                                stmt(callX(getOuterInstanceXforField(fieldNode), "add", callX(varX("created"), "apply", varX("closure"))))
                        )
                );
            }
        }

        contextClass.addMethod(
                REUSE_METHOD_NAME,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(elementType, "value")),
                NO_EXCEPTIONS,
                block(
                        stmt(callX(getOuterInstanceXforField(fieldNode), "add",
                                varX("value")
                        ))
                )
        );

        if (fieldKey != null) {
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


        annotatedClass.getModule().addClass(contextClass);

        return contextClass;
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
            addCompileError(String.format("Value type of map %s (%s) has now key field", fieldNode, elementType), fieldNode);
        }

        InnerClassNode contextClass = createInnerContextClassForMapMembers(fieldNode, keyType, elementType);
        createContextClosure(fieldNode, contextClass);
    }

    private InnerClassNode createInnerContextClassForMapMembers(FieldNode fieldNode, ClassNode keyType, ClassNode elementType) {
        InnerClassNode contextClass = createInnerContextClass(fieldNode);

        String methodName = getElementNameForCollectionField(fieldNode);
        FieldNode fieldKey = getKeyField(elementType);

        contextClass.addMethod(
                methodName,
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(ClassHelper.STRING_TYPE, "key"), createAnnotatedClosureParameter(elementType)),
                NO_EXCEPTIONS,
                block(
                        stmt(callX(getOuterInstanceXforField(fieldNode), "put",
                                args(
                                        varX("key"),
                                        callX(elementType, "create", args("key", "closure"))
                                )
                        ))
                )
        );

        if (!isFinal(elementType)) {
                contextClass.addMethod(
                        methodName,
                        Opcodes.ACC_PUBLIC,
                        ClassHelper.VOID_TYPE,
                        params(createSubclassClassParameter(annotatedClass), param(ClassHelper.STRING_TYPE, "key"), createAnnotatedClosureParameter(elementType)),
                        NO_EXCEPTIONS,
                        block(
                                declS(varX("created"), callX(varX("typeToCreate"), "newInstance", args("key"))),
                                stmt(callX(getOuterInstanceXforField(fieldNode), "put",
                                         args(varX("key"), callX(varX("created"), "apply", varX("closure"))))
                                )
                        )
                );
        }

        //noinspection ConstantConditions
        contextClass.addMethod(
                "reuse",
                Opcodes.ACC_PUBLIC,
                ClassHelper.VOID_TYPE,
                params(param(elementType, "value")),
                NO_EXCEPTIONS,
                block(
                        stmt(callX(getOuterInstanceXforField(fieldNode), "put",
                                args(propX(varX("value"), getKeyField(elementType).getName()), varX("value"))
                        ))
                )
        );

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


        annotatedClass.getModule().addClass(contextClass);

        return contextClass;
    }

    private void createSingleDSLObjectClosureMethod(FieldNode fieldNode) {
        String methodName = getMethodNameForField(fieldNode);

        ClassNode fieldType = fieldNode.getType();
        boolean hasKeyField = getKeyField(fieldType) != null;

        if (!isAbstract(fieldType)) {
            annotatedClass.addMethod(
                    methodName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    hasKeyField ?
                            params(param(ClassHelper.STRING_TYPE, "key"), createAnnotatedClosureParameter(fieldType))
                            : params(createAnnotatedClosureParameter(fieldType)),
                    NO_EXCEPTIONS,
                    block(
                            assignS(propX(varX("this"), fieldNode.getName()),
                                    callX(
                                            fieldType,
                                            "create",
                                            hasKeyField ? args("key", "closure") : args("closure")
                                    )
                            )
                    )
            );
        }

        if (!isFinal(fieldType)) {
            annotatedClass.addMethod(
                    methodName,
                    Opcodes.ACC_PUBLIC,
                    ClassHelper.VOID_TYPE,
                    hasKeyField ?
                            params(createSubclassClassParameter(annotatedClass), param(ClassHelper.STRING_TYPE, "key"), createAnnotatedClosureParameter(fieldType))
                            : params(createSubclassClassParameter(annotatedClass), createAnnotatedClosureParameter(fieldType)),
                    NO_EXCEPTIONS,
                    block(
                            declS(varX("created"), callX(varX("typeToCreate"), "newInstance", hasKeyField ? args("key") : NO_ARGUMENTS)),
                            assignS(propX(varX("this"), fieldNode.getName()),
                                    callX(varX("created"), "apply", varX("closure"))
                            )
                    )
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

    private NamedArgumentListExpression createKeyAssignExpression(String key, String name) {
        return new NamedArgumentListExpression(
                Collections.singletonList(
                        new MapEntryExpression(constX(key), varX(name))));
    }

    private FieldNode getKeyField(ClassNode target)
    {
        String keyFieldName = getKeyFieldName(target);

        if (keyFieldName == null) return null;

        FieldNode result = target.getField(keyFieldName);

        if (result == null) {
            addCompileError(new NoSuchFieldException(
                    String.format("Designated Key field '%s' is missing", keyFieldName)
            ));
            return null;
        }

        if (!result.getType().equals(ClassHelper.STRING_TYPE)) {
            addCompileError(new IllegalArgumentException(
                    String.format("Key field '%s' must be of type String, but is '%s' instead",
                            keyFieldName, result.getType().getName())
            ));
            return null;
        }

        return result;
    }

    private String getKeyFieldName(ClassNode target) {
        if (target == null) return null;

        Deque<ClassNode> hierarchy = getHierarchy(new LinkedList<ClassNode>(), target);

        String firstKey = getNullSafeMemberStringValue(getAnnotation(hierarchy.removeFirst(), DSL_CONFIG_ANNOTATION), "key", null);

        for (ClassNode node : hierarchy) {
            String keyOfCurrentNode = getNullSafeMemberStringValue(getAnnotation(node, DSL_CONFIG_ANNOTATION), "key", null);

            if (firstKey == null && keyOfCurrentNode == null) continue;

            if (firstKey == null) {
                addCompileError(String.format("Inconsistent hierarchy: Toplevel class %s has no key, but child class %s defines '%s'.", target, node, keyOfCurrentNode), node);
                return null;
            }

            if (keyOfCurrentNode == null) continue;

            if (!firstKey.equals(keyOfCurrentNode)) {
                addCompileError(String.format("Inconsistent hierarchy: Toplevel defines %s defines key '%s', but child class %s defines '%s'.", target, firstKey, node, keyOfCurrentNode), node);
                return null;
            }
        }

        return firstKey;
    }

    private Deque<ClassNode> getHierarchy(Deque<ClassNode> hierarchy, ClassNode target) {
        if (target == null) return hierarchy;
        if (isDSLObject(target)) return hierarchy;

        hierarchy.addFirst(target);
        return getHierarchy(hierarchy, target.getSuperClass());
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

    private boolean hasAnnotationOfType(AnnotatedNode owner, ClassNode annotation) {
        return getAnnotation(owner, annotation) != null;
    }

    AnnotationNode getAnnotation(AnnotatedNode field, ClassNode type) {
        List<AnnotationNode> annotation = field.getAnnotations(type);
        return annotation.isEmpty() ? null : annotation.get(0);
    }

    public void addCompileError(String msg, ASTNode node) {

        SyntaxException se = node != null ? new SyntaxException(msg, node.getLineNumber(), node.getColumnNumber()) : new SyntaxException(msg, -1, -1);
        sourceUnit.getErrorCollector().addFatalError(new SyntaxErrorMessage(se, sourceUnit));
    }

    public void addCompileWarning(String msg, ASTNode node) {
        // TODO Need to convert node into CST node?
        //sourceUnit.getErrorCollector().addWarning(WarningMessage.POSSIBLE_ERRORS, msg, node, sourceUnit);
    }

    public void addCompileError(Exception exception) {
        sourceUnit.getErrorCollector().addFatalError(new ExceptionMessage(exception, false, sourceUnit));
    }

}
