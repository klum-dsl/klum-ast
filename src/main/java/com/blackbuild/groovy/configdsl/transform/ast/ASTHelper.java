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

import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.AbstractASTTransformation;

import java.util.*;

import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static org.codehaus.groovy.ast.ClassHelper.makeWithoutCaching;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;

/**
 * Created by stephan on 05.12.2016.
 */
public class ASTHelper {

    public static final ClassNode[] NO_EXCEPTIONS = new ClassNode[0];
    private static final String KEY_FIELD_METADATA_KEY = DSLASTTransformation.class.getName() + ".keyfield";
    private static final String OWNER_FIELD_METADATA_KEY = DSLASTTransformation.class.getName() + ".ownerfield";
    private static final FieldNode NO_SUCH_FIELD = new FieldNode(null, 0, null, null, null);
    public static ClassNode COLLECTION_TYPE = makeWithoutCaching(Collection.class);
    public static ClassNode SORTED_MAP_TYPE = makeWithoutCaching(SortedMap.class);

    public static boolean isDSLObject(ClassNode classNode) {
        return getAnnotation(classNode, DSLASTTransformation.DSL_CONFIG_ANNOTATION) != null;
    }

    public static ClassNode getHighestAncestorDSLObject(ClassNode target) {
        return getHierarchyOfDSLObjectAncestors(target).getFirst();
    }

    public static Deque<ClassNode> getHierarchyOfDSLObjectAncestors(ClassNode target) {
        return getHierarchyOfDSLObjectAncestors(new LinkedList<ClassNode>(), target);
    }

    private static Deque<ClassNode> getHierarchyOfDSLObjectAncestors(Deque<ClassNode> hierarchy, ClassNode target) {
        if (!isDSLObject(target)) return hierarchy;

        hierarchy.addFirst(target);
        return getHierarchyOfDSLObjectAncestors(hierarchy, target.getSuperClass());
    }

    public static AnnotationNode getAnnotation(AnnotatedNode field, ClassNode type) {
        List<AnnotationNode> annotation = field.getAnnotations(type);
        return annotation.isEmpty() ? null : annotation.get(0);
    }

    static boolean isCollectionOrMap(ClassNode type) {
        return isCollection(type) || isMap(type);
    }

    static boolean isCollection(ClassNode type) {
        return type.equals(COLLECTION_TYPE) || type.implementsInterface(COLLECTION_TYPE);
    }

    static boolean isMap(ClassNode type) {
        return type.equals(ClassHelper.MAP_TYPE) || type.implementsInterface(ClassHelper.MAP_TYPE);
    }

    static boolean isAbstract(ClassNode classNode) {
        return (classNode.getModifiers() & ACC_ABSTRACT) != 0;
    }

    static ClosureExpression createClosureExpression(Parameter[] parameters, Statement... code) {
        ClosureExpression result = new ClosureExpression(parameters, new BlockStatement(code, new VariableScope()));
        result.setVariableScope(new VariableScope());
        return result;
    }

    static ClosureExpression createClosureExpression(Statement... code) {
        return createClosureExpression(Parameter.EMPTY_ARRAY, code);
    }

    static ListExpression listExpression(Expression... expressions) {
        return new ListExpression(Arrays.asList(expressions));
    }

    static ArgumentListExpression argsWithOptionalKey(FieldNode keyField, String... otherArgs) {
        if (keyField == null)
            return args(otherArgs);

        ArgumentListExpression result = new ArgumentListExpression(varX("key"));

        for (String next : otherArgs)
            result.addExpression(varX(next));

        return result;
    }

    static ArgumentListExpression argsWithEmptyMapAndOptionalKey(AnnotatedNode keyField, String... otherArgs) {
        ArgumentListExpression result = new ArgumentListExpression(new MapExpression());

        if (keyField != null)
            result.addExpression(varX("key"));

        for (String next : otherArgs)
            result.addExpression(varX(next));

        return result;
    }

    static ArgumentListExpression argsWithEmptyMapClassAndOptionalKey(AnnotatedNode keyField, String... otherArgs) {
        ArgumentListExpression result = new ArgumentListExpression(new MapExpression());

        result.addExpression(varX("typeToCreate"));

        if (keyField != null)
            result.addExpression(varX("key"));

        for (String next : otherArgs)
            result.addExpression(varX(next));

        return result;
    }

    public static void addCompileError(SourceUnit sourceUnit, String msg, ASTNode node) {
        SyntaxException se = new SyntaxException(msg, node.getLineNumber(), node.getColumnNumber());
        sourceUnit.getErrorCollector().addFatalError(new SyntaxErrorMessage(se, sourceUnit));
    }

    public static void addCompileError(String msg, FieldNode node) {
        SyntaxException se = new SyntaxException(msg, node.getLineNumber(), node.getColumnNumber());
        SourceUnit sourceUnit = node.getOwner().getModule().getContext();
        sourceUnit.getErrorCollector().addFatalError(new SyntaxErrorMessage(se, sourceUnit));
    }

    public static void addCompileError(String msg, FieldNode node, ASTNode sourcePosition) {
        SyntaxException se = new SyntaxException(msg, sourcePosition.getLineNumber(), sourcePosition.getColumnNumber());
        SourceUnit sourceUnit = node.getOwner().getModule().getContext();
        sourceUnit.getErrorCollector().addFatalError(new SyntaxErrorMessage(se, sourceUnit));
    }

    static void addCompileWarning(SourceUnit sourceUnit, String msg, ASTNode node) {
        Token token = new Token(Types.UNKNOWN, node.getText(), node.getLineNumber(), node.getColumnNumber());
        sourceUnit.getErrorCollector().addWarning(WarningMessage.LIKELY_ERRORS, msg, token, sourceUnit);
    }

    static void assertMethodIsParameterless(MethodNode method, SourceUnit sourceUnit) {
        if (method.getParameters().length > 0)
            addCompileError(sourceUnit, "Lifecycle/Validate methods must be parameterless!", method);
    }

    static void assertMethodIsNotPrivate(MethodNode method, SourceUnit sourceUnit) {
        if (method.isPrivate())
            addCompileError(sourceUnit, "Lifecycle methods must not be private!", method);
    }

    static void replaceMethod(ClassNode target, MethodNode method) {
        MethodNode oldMethod = target.getDeclaredMethod(method.getName(), method.getParameters());
        if (oldMethod != null)
            target.removeMethod(oldMethod);
        target.addMethod(method);
    }

    static ClosureExpression toStronglyTypedClosure(ClosureExpression validationClosure, ClassNode fieldNodeType) {
        String closureParameterName = validationClosure.isParameterSpecified() ? validationClosure.getParameters()[0].getName() : "it";
        ClosureExpression typeValidationClosure = new ClosureExpression(params(param(fieldNodeType, closureParameterName)), validationClosure.getCode());
        typeValidationClosure.setVariableScope(new VariableScope());
        typeValidationClosure.copyNodeMetaData(validationClosure);
        typeValidationClosure.setSourcePosition(validationClosure);
        validationClosure = typeValidationClosure;

        validationClosure.setVariableScope(new VariableScope());
        validationClosure.visit(new StronglyTypingClosureParameterVisitor(closureParameterName, fieldNodeType));
        return validationClosure;
    }

    static void moveMethodFromModelToRWClass(MethodNode method) {
        ClassNode declaringClass = method.getDeclaringClass();
        declaringClass.removeMethod(method);
        InnerClassNode rwClass = declaringClass.getNodeMetaData(DSLASTTransformation.RWCLASS_METADATA_KEY);
        // if method is public, it will already have been added by delegateTo, replace it again
        replaceMethod(rwClass, method);
    }

    static ClassNode getRwClassOf(ClassNode classNode) {
        ClassNode result = classNode.redirect().getNodeMetaData(DSLASTTransformation.RWCLASS_METADATA_KEY);

        if (result == null) {
            // parent has not yet been compiled. We create an unresolved parent class
            result = ClassHelper.makeWithoutCaching(classNode.getName() + "$_RW");
            classNode.getCompileUnit().addClassNodeToCompile(result, classNode.getModule().getContext());
        }

        return result;
    }


    static void addPropertyAsFieldWithAccessors(ClassNode cNode, PropertyNode pNode) {
        final FieldNode fn = pNode.getField();
        cNode.getFields().remove(fn);
        cNode.addField(fn);

        Statement getterBlock = pNode.getGetterBlock();
        Statement setterBlock = pNode.getSetterBlock();

        int modifiers = pNode.getModifiers();
        String capitalizedName = Verifier.capitalize(pNode.getName());

        if (getterBlock != null) {
            MethodNode getter =
                    new MethodNode("get" + capitalizedName, modifiers, pNode.getType(), Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, getterBlock);
            getter.setSynthetic(true);
            addPropertyMethod(cNode, getter);

            if (ClassHelper.boolean_TYPE == pNode.getType() || ClassHelper.Boolean_TYPE == pNode.getType()) {
                String secondGetterName = "is" + capitalizedName;
                MethodNode secondGetter =
                        new MethodNode(secondGetterName, modifiers, pNode.getType(), Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, getterBlock);
                secondGetter.setSynthetic(true);
                addPropertyMethod(cNode, secondGetter);
            }
        }
        if (setterBlock != null) {
            Parameter[] setterParameterTypes = {new Parameter(pNode.getType(), "value")};
            MethodNode setter =
                    new MethodNode("set" + capitalizedName, modifiers, ClassHelper.VOID_TYPE, setterParameterTypes, ClassNode.EMPTY_ARRAY, setterBlock);
            setter.setSynthetic(true);
            addPropertyMethod(cNode, setter);
        }
    }

    // from Verifier
    static void addPropertyMethod(ClassNode classNode, MethodNode method) {
        classNode.addMethod(method);
        // GROOVY-4415 / GROOVY-4645: check that there's no abstract method which corresponds to this one
        List<MethodNode> abstractMethods = classNode.getAbstractMethods();
        if (abstractMethods==null) return;
        String methodName = method.getName();
        Parameter[] parameters = method.getParameters();
        ClassNode methodReturnType = method.getReturnType();
        for (MethodNode node : abstractMethods) {
            if (!node.getDeclaringClass().equals(classNode)) continue;
            if (node.getName().equals(methodName)
                    && node.getParameters().length==parameters.length) {
                if (parameters.length==1) {
                    // setter
                    ClassNode abstractMethodParameterType = node.getParameters()[0].getType();
                    ClassNode methodParameterType = parameters[0].getType();
                    if (!methodParameterType.isDerivedFrom(abstractMethodParameterType) && !methodParameterType.implementsInterface(abstractMethodParameterType)) {
                        continue;
                    }
                }
                ClassNode nodeReturnType = node.getReturnType();
                if (!methodReturnType.isDerivedFrom(nodeReturnType) && !methodReturnType.implementsInterface(nodeReturnType)) {
                    continue;
                }
                // matching method, remove abstract status and use the same body
                node.setModifiers(node.getModifiers() ^ ACC_ABSTRACT);
                node.setCode(method.getCode());
            }
        }
    }


    static void replaceProperties(ClassNode annotatedClass, List<PropertyNode> newNodes) {
        for (PropertyNode pNode : newNodes) {
            annotatedClass.getProperties().remove(pNode);
            addPropertyAsFieldWithAccessors(annotatedClass, pNode);
        }
    }

    static List<ClassNode> findAllKnownSubclassesOf(ClassNode type) {
        List<ClassNode> result = new ArrayList<ClassNode>();

        for (ClassNode classInCU : (List<ClassNode>) type.getCompileUnit().getClasses())
            if (classInCU.isDerivedFrom(type))
                result.add(classInCU);
        return result;
    }

    @SuppressWarnings("ConstantConditions")
    static GenericsType[] getGenericsTypes(FieldNode fieldNode) {
        GenericsType[] types = fieldNode.getType().getGenericsTypes();

        if (types == null)
            ASTHelper.addCompileError(fieldNode.getOwner().getModule().getContext(), "Lists and Maps need to be assigned an explicit Generic Type", fieldNode);
        return types;
    }

    static ClassNode getElementType(FieldNode fieldNode) {
        if (isMap(fieldNode.getType()))
            return getGenericsTypes(fieldNode)[1].getType();
        else if (isCollection(fieldNode.getType()))
            return getGenericsTypes(fieldNode)[0].getType();
        else return null;
    }

    static String getElementNameForCollectionField(FieldNode fieldNode) {
        AnnotationNode fieldAnnotation = getAnnotation(fieldNode, DSLASTTransformation.DSL_FIELD_ANNOTATION);

        String result = getNullSafeMemberStringValue(fieldAnnotation, "members", null);

        if (result != null && result.length() > 0) return result;

        String collectionMethodName = fieldNode.getName();

        if (collectionMethodName.endsWith("s"))
            return collectionMethodName.substring(0, collectionMethodName.length() - 1);

        return collectionMethodName;
    }

    private static String getNullSafeMemberStringValue(AnnotationNode fieldAnnotation, String value, String name) {
        return fieldAnnotation == null ? name : AbstractASTTransformation.getMemberStringValue(fieldAnnotation, value, name);
    }

    static String getQualifiedName(FieldNode node) {
        return node.getOwner().getName() + "." + node.getName();
    }

    static FieldNode getKeyField(ClassNode target) {
        FieldNode result = target.getNodeMetaData(KEY_FIELD_METADATA_KEY);

        if (result == NO_SUCH_FIELD)
            return null;

        if (result != null)
            return result;

        List<FieldNode> annotatedFields = getAnnotatedFieldsOfHierarchy(target, DSLASTTransformation.KEY_ANNOTATION);

        if (annotatedFields.isEmpty()) {
            target.setNodeMetaData(KEY_FIELD_METADATA_KEY, NO_SUCH_FIELD);
            return null;
        }

        if (annotatedFields.size() > 1) {
            ASTHelper.addCompileError(
                    String.format(
                            "Found more than one key fields, only one is allowed in hierarchy (%s, %s)",
                            getQualifiedName(annotatedFields.get(0)),
                            getQualifiedName(annotatedFields.get(1))),
                    annotatedFields.get(0)
            );
            return null;
        }

        result = annotatedFields.get(0);

        if (!result.getType().equals(ClassHelper.STRING_TYPE)) {
            ASTHelper.addCompileError(
                    String.format("Key field '%s' must be of type String, but is '%s' instead", result.getName(), result.getType().getName()),
                    result
            );
            return null;
        }

        ClassNode ancestor = ASTHelper.getHighestAncestorDSLObject(target);

        if (target.equals(ancestor)) {
            target.setNodeMetaData(KEY_FIELD_METADATA_KEY, result);
            return result;
        }

        FieldNode firstKey = getKeyField(ancestor);

        if (firstKey == null) {
            ASTHelper.addCompileError(
                    String.format("Inconsistent hierarchy: Toplevel class %s has no key, but child class %s defines '%s'.", ancestor.getName(), target.getName(), result.getName()),
                    result
            );
            return null;
        }

        target.setNodeMetaData(KEY_FIELD_METADATA_KEY, result);
        return result;
    }

    static ClassNode getKeyType(ClassNode target) {
        FieldNode key = getKeyField(target);
        return key != null ? key.getType() : null;
    }

    static List<FieldNode> getAnnotatedFieldsOfHierarchy(ClassNode target, ClassNode annotation) {
        List<FieldNode> result = new ArrayList<FieldNode>();

        for (ClassNode level : ASTHelper.getHierarchyOfDSLObjectAncestors(target)) {
            result.addAll(getAnnotatedFieldOfClass(level, annotation));
        }

        return result;
    }

    private static List<FieldNode> getAnnotatedFieldOfClass(ClassNode target, ClassNode annotation) {
        List<FieldNode> result = new ArrayList<FieldNode>();

        for (FieldNode fieldNode : target.getFields())
            if (!fieldNode.getAnnotations(annotation).isEmpty())
                result.add(fieldNode);

        return result;
    }

    static FieldNode getOwnerField(ClassNode target) {
        FieldNode result = target.getNodeMetaData(OWNER_FIELD_METADATA_KEY);

        if (result == NO_SUCH_FIELD)
            return null;

        if (result != null)
            return result;

        List<FieldNode> annotatedFields = getAnnotatedFieldsOfHierarchy(target, DSLASTTransformation.OWNER_ANNOTATION);

        if (annotatedFields.isEmpty()) {
            target.setNodeMetaData(OWNER_FIELD_METADATA_KEY, NO_SUCH_FIELD);
            return null;
        }

        if (annotatedFields.size() > 1) {
            ASTHelper.addCompileError(
                    String.format(
                            "Found more than one owner fields, only one is allowed in hierarchy (%s, %s)",
                            getQualifiedName(annotatedFields.get(0)),
                            getQualifiedName(annotatedFields.get(1))),
                    annotatedFields.get(0)
            );
            return null;
        }

        result = annotatedFields.get(0);
        target.setNodeMetaData(OWNER_FIELD_METADATA_KEY, result);
        return result;
    }

    static String getOwnerFieldName(ClassNode target) {
        FieldNode ownerFieldOfElement = getOwnerField(target);
        return ownerFieldOfElement != null ? ownerFieldOfElement.getName() : null;
    }
}
