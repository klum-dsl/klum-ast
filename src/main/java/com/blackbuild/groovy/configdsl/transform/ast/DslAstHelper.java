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

import com.blackbuild.klum.ast.internal.model.DslClass;
import com.blackbuild.klum.common.CommonAstHelper;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createOptionalPublicMethod;
import static com.blackbuild.klum.common.CommonAstHelper.NO_SUCH_FIELD;
import static com.blackbuild.klum.common.CommonAstHelper.addCompileError;
import static com.blackbuild.klum.common.CommonAstHelper.getAnnotation;
import static com.blackbuild.klum.common.CommonAstHelper.getGenericsTypes;
import static com.blackbuild.klum.common.CommonAstHelper.getQualifiedName;
import static com.blackbuild.klum.common.CommonAstHelper.isAbstract;
import static com.blackbuild.klum.common.CommonAstHelper.isCollection;
import static com.blackbuild.klum.common.CommonAstHelper.isMap;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callThisX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;

/**
 * Created by stephan on 05.12.2016.
 */
public class DslAstHelper {

    private static final String KEY_FIELD_METADATA_KEY = DSLASTTransformation.class.getName() + ".keyfield";
    private static final String OWNER_FIELD_METADATA_KEY = DSLASTTransformation.class.getName() + ".ownerfield";
    private static final String ELEMENT_NAME_METADATA_KEY = DSLASTTransformation.class.getName() + ".elementName";

    private DslAstHelper() {}

    public static boolean isDSLObject(ClassNode classNode) {
        return CommonAstHelper.getAnnotation(classNode, DslClass.DSL_CONFIG_ANNOTATION) != null;
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

    static void moveMethodFromModelToRWClass(MethodNode method) {
        ClassNode declaringClass = method.getDeclaringClass();
        declaringClass.removeMethod(method);
        ClassNode rwClass = declaringClass.getNodeMetaData(DSLASTTransformation.RWCLASS_METADATA_KEY);
        // if method is public, it will already have been added by delegateTo, replace it again
        CommonAstHelper.replaceMethod(rwClass, method);
    }

    public static ClassNode getRwClassOf(ClassNode classNode) {
        if (!isDSLObject(classNode))
            return null;

        ClassNode result = classNode.redirect().getNodeMetaData(DSLASTTransformation.RWCLASS_METADATA_KEY);

        if (result != null) {
            return result;
        }

        if (classNode.isResolved()) {
            // no way to easily get the inner class node?
            result = classNode.getField("$rw").getType().getPlainNodeReference();
        } else {
            // parent has not yet been compiled. We create an unresolved parent class
            result = ClassHelper.makeWithoutCaching(classNode.getName() + "$_RW");
            classNode.getCompileUnit().addClassNodeToCompile(result, classNode.getModule().getContext());
        }
        classNode.redirect().setNodeMetaData(DSLASTTransformation.RWCLASS_METADATA_KEY, result);
        return result;
    }

    public static ClassNode getModelClassFor(ClassNode classNode) {
        if (!classNode.getName().endsWith(DSLASTTransformation.RW_CLASS_SUFFIX))
            return null;

        ClassNode outerClass = classNode.getOuterClass();

        if (outerClass == null)
            return null;

        if (getRwClassOf(outerClass) == classNode)
            return outerClass;

        return null;
    }

    static <T> T storeAndReturn(ASTNode node, Object key, T value) {
        node.setNodeMetaData(key, value);
        return value;
    }


    static String getElementNameForCollectionField(FieldNode fieldNode) {
        String result = fieldNode.getNodeMetaData(ELEMENT_NAME_METADATA_KEY);
        if (result != null)
            return result;

        AnnotationNode fieldAnnotation = CommonAstHelper.getAnnotation(fieldNode, DSLASTTransformation.DSL_FIELD_ANNOTATION);
        result = CommonAstHelper.getNullSafeMemberStringValue(fieldAnnotation, "members", null);

        if (result != null && result.length() > 0)
            return storeAndReturn(fieldNode, ELEMENT_NAME_METADATA_KEY, result);

        result = fieldNode.getName();

        if (result.endsWith("s"))
            result = result.substring(0, result.length() - 1);

        return storeAndReturn(fieldNode, ELEMENT_NAME_METADATA_KEY, result);
    }

    static FieldNode getKeyField(ClassNode target) {
        FieldNode result = target.getNodeMetaData(KEY_FIELD_METADATA_KEY);

        if (result == NO_SUCH_FIELD)
            return null;

        if (result != null)
            return result;

        List<FieldNode> hierarchyKeyFields = getAnnotatedFieldsOfHierarchy(target, DSLASTTransformation.KEY_ANNOTATION);

        if (hierarchyKeyFields.isEmpty()) {
            target.setNodeMetaData(KEY_FIELD_METADATA_KEY, NO_SUCH_FIELD);
            return null;
        }

        if (hierarchyKeyFields.size() > 1) {
            addCompileError(
                    String.format(
                            "Found more than one key fields, only one is allowed in hierarchy (%s, %s)",
                            getQualifiedName(hierarchyKeyFields.get(0)),
                            getQualifiedName(hierarchyKeyFields.get(1))),
                    hierarchyKeyFields.get(0)
            );
            return null;
        }

        result = hierarchyKeyFields.get(0);

        if (!result.getType().equals(ClassHelper.STRING_TYPE)) {
            addCompileError(
                    String.format("Key field '%s' must be of type String, but is '%s' instead", result.getName(), result.getType().getName()),
                    result
            );
            return null;
        }

        Deque<ClassNode> hierarchy = DslAstHelper.getHierarchyOfDSLObjectAncestors(target);

        ClassNode ancestorOwner = result.getOwner();

        // search for first
        for (ClassNode ancestor : hierarchy) {
            if (ancestor.equals(ancestorOwner)) {
                break;
            }

            if ((ancestor.getModifiers() & Opcodes.ACC_ABSTRACT) == 0) {
                addCompileError(
                        String.format("Inconsistent hierarchy: Non abstract toplevel class %s has no key, but child class %s defines '%s'.", ancestor.getName(), ancestorOwner.getName(), result.getName()),
                        result
                );
                return null;
            }
        }

        return storeAndReturn(target, KEY_FIELD_METADATA_KEY, result);
    }

    static ClassNode getKeyType(ClassNode target) {
        FieldNode key = getKeyField(target);
        return key != null ? key.getType() : null;
    }

    static List<FieldNode> getAnnotatedFieldsOfHierarchy(ClassNode target, ClassNode annotation) {
        List<FieldNode> result = new ArrayList<FieldNode>();

        for (ClassNode level : DslAstHelper.getHierarchyOfDSLObjectAncestors(target)) {
            result.addAll(getAnnotatedFieldsOfClass(level, annotation));
        }

        return result;
    }

    private static List<FieldNode> getAnnotatedFieldsOfClass(ClassNode target, ClassNode annotation) {
        List<FieldNode> result = new ArrayList<FieldNode>();

        for (FieldNode fieldNode : target.getFields())
            if (!fieldNode.getAnnotations(annotation).isEmpty())
                result.add(fieldNode);

        return result;
    }

    public static MethodCallExpression callMethodViaInvoke(String methodName, ArgumentListExpression arguments) {
        return callThisX("invokeMethod", args(constX(methodName), new ArrayExpression(ClassHelper.OBJECT_TYPE, arguments.getExpressions())));
    }

    static List<FieldNode> getOwnerFields(ClassNode target) {
        List<FieldNode> result = target.getNodeMetaData(OWNER_FIELD_METADATA_KEY);

        if (result != null)
            return result;

        result = getAnnotatedFieldsOfClass(target, DSLASTTransformation.OWNER_ANNOTATION);

        target.setNodeMetaData(OWNER_FIELD_METADATA_KEY, result);

        return result;
    }

    static List<String> getOwnerFieldNames(ClassNode target) {
        List<FieldNode> ownerFieldsOfElement = getOwnerFields(target);
        ArrayList<String> result = new ArrayList<>(ownerFieldsOfElement.size());

        for (FieldNode fieldNode : ownerFieldsOfElement) {
            result.add(fieldNode.getName());
        }

        return result;
    }

    static MethodNode createDelegateMethod(MethodNode targetMethod, ClassNode receiver, String field) {
        return createOptionalPublicMethod(targetMethod.getName())
                .returning(targetMethod.getReturnType())
                .params(cloneParamsWithDefaultValues(targetMethod.getParameters()))
        .callMethod(field, targetMethod.getName(), args(targetMethod.getParameters()))
        .addTo(receiver);
    }

    static Parameter[] cloneParamsWithDefaultValues(Parameter[] source) {
        Parameter[] result = new Parameter[source.length];
        for (int i = 0; i < source.length; i++) {
            Parameter srcParam = source[i];
            Parameter dstParam = new Parameter(srcParam.getOriginType(), srcParam.getName(), srcParam.getInitialExpression());
            result[i] = dstParam;
        }
        return result;
    }

    static boolean isInstantiable(ClassNode classNode) {
        return !classNode.isInterface() && !isAbstract(classNode);
    }

    @Nullable
    static ClosureExpression getCodeClosureFor(AnnotatedNode target, AnnotationNode annotation, String member) {
        Expression codeExpression = annotation.getMember(member);
        if (codeExpression == null)
            return null;
        if (codeExpression instanceof ClosureExpression)
            return (ClosureExpression) codeExpression;

        addCompileError("Illegal value for code, only a closure is allowed.", target, annotation);
        return null;
    }

    @Nullable
    static ClosureExpression getCodeClosureFor(AnnotatedNode target, ClassNode annotationType, String member) {
        AnnotationNode annotation = getAnnotation(target, annotationType);

        if (annotation == null)
            return null;

        return getCodeClosureFor(target, annotation, member);
    }

    static List<ClosureExpression> getClosureMemberList(AnnotationNode anno, String name) {
        if (anno == null)
            return Collections.emptyList();

        List<ClosureExpression> list;
        Expression expr = anno.getMember(name);

        if (expr == null)
            return Collections.emptyList();

        if (expr instanceof ClosureExpression)
            return Collections.singletonList((ClosureExpression) expr);

        if (expr instanceof ListExpression) {
            list = new ArrayList<>();
            ListExpression listExpression = (ListExpression) expr;
            for (Expression itemExpr : listExpression.getExpressions())
                if (itemExpr instanceof ClosureExpression)
                    list.add((ClosureExpression) itemExpr);
                else
                    addCompileError("Only closure are allowed for " + name, itemExpr);
            return list;
        }

        addCompileError("Unknown value", expr);
        return Collections.emptyList();
    }


    static boolean hasAnnotation(AnnotatedNode node, ClassNode annotation) {
        return !node.getAnnotations(annotation).isEmpty();
    }

    static int findFirstUpperCaseCharacter(String methodName) {
        int index = 0;
        while (index < methodName.length()) {
            if (Character.isUpperCase(methodName.charAt(index)))
                return index;
            index++;
        }
        return -1;
    }

    static boolean isDslMap(FieldNode fieldNode) {
        return isMap(fieldNode.getType()) && isDSLObject(getGenericsTypes(fieldNode)[1].getType());
    }

    static boolean isDslCollection(FieldNode fieldNode) {
        return isCollection(fieldNode.getType()) && isDSLObject(getGenericsTypes(fieldNode)[0].getType());
    }
}
