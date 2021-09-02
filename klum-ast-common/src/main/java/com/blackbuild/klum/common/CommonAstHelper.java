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
package com.blackbuild.klum.common;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CompileUnit;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.expr.MapEntryExpression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.SyntaxErrorMessage;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.syntax.Token;
import org.codehaus.groovy.syntax.Types;
import org.codehaus.groovy.transform.AbstractASTTransformation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static groovyjarjarasm.asm.Opcodes.ACC_ABSTRACT;
import static groovyjarjarasm.asm.Opcodes.ACC_FINAL;
import static org.codehaus.groovy.ast.ClassHelper.STRING_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.makeWithoutCaching;
import static org.codehaus.groovy.ast.expr.CastExpression.asExpression;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.closureX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.param;
import static org.codehaus.groovy.ast.tools.GeneralUtils.params;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;
import static org.codehaus.groovy.ast.tools.GenericsUtils.makeClassSafe;

/**
 * Created by stephan on 05.12.2016.
 */
public class CommonAstHelper {

    public static final ClassNode[] NO_EXCEPTIONS = ClassNode.EMPTY_ARRAY;
    public static final FieldNode NO_SUCH_FIELD = new FieldNode(null, 0, null, null, null);
    public static ClassNode COLLECTION_TYPE = makeWithoutCaching(Collection.class);
    public static ClassNode SORTED_MAP_TYPE = makeWithoutCaching(SortedMap.class);

    public static AnnotationNode getAnnotation(AnnotatedNode field, ClassNode type) {
        List<AnnotationNode> annotation = field.getAnnotations(type);
        return annotation.isEmpty() ? null : annotation.get(0);
    }

    public static boolean isCollectionOrMap(ClassNode type) {
        return isCollection(type) || isMap(type);
    }

    public static boolean isCollection(ClassNode type) {
        return type.equals(COLLECTION_TYPE) || type.implementsInterface(COLLECTION_TYPE);
    }

    public static boolean isMap(ClassNode type) {
        return type.equals(ClassHelper.MAP_TYPE) || type.implementsInterface(ClassHelper.MAP_TYPE);
    }

    public static boolean isAbstract(ClassNode classNode) {
        return (classNode.getModifiers() & ACC_ABSTRACT) != 0;
    }

    public static ListExpression listExpression(Expression... expressions) {
        return new ListExpression(Arrays.asList(expressions));
    }

    static ArgumentListExpression argsWithOptionalKey(String keyFieldName, String... otherArgs) {
        if (keyFieldName == null)
            return args(otherArgs);

        ArgumentListExpression result = new ArgumentListExpression(varX(keyFieldName));

        for (String next : otherArgs)
            result.addExpression(varX(next));

        return result;
    }

    public static ArgumentListExpression argsWithEmptyMapAndOptionalKey(String keyFieldName, String... otherArgs) {
        ArgumentListExpression result = new ArgumentListExpression(new MapExpression());

        if (keyFieldName != null)
            result.addExpression(varX(keyFieldName));

        for (String next : otherArgs)
            result.addExpression(varX(next));

        return result;
    }

    public static ArgumentListExpression argsWithEmptyMapClassAndOptionalKey(String keyFieldName, String... otherArgs) {
        ArgumentListExpression result = new ArgumentListExpression(new MapExpression());

        result.addExpression(varX("typeToCreate"));

        if (keyFieldName != null)
            result.addExpression(varX(keyFieldName));

        for (String next : otherArgs)
            result.addExpression(varX(next));

        return result;
    }

    public static void addCompileError(SourceUnit sourceUnit, String msg, ASTNode node) {
        SyntaxException se = new SyntaxException(msg, node.getLineNumber(), node.getColumnNumber());
        sourceUnit.getErrorCollector().addFatalError(new SyntaxErrorMessage(se, sourceUnit));
    }

    public static void addCompileError(String msg, AnnotatedNode node) {
        addCompileError(msg, node, node);
    }

    public static void addCompileError(String msg, AnnotatedNode node, ASTNode sourcePosition) {
        if (node instanceof FieldNode)
            addCompileError(msg, (FieldNode) node);
        else if (node instanceof ClassNode)
            addCompileError(msg, (ClassNode) node);
        else
            throw new IllegalStateException(node.toString() + " must be either a ClassNode or a FieldNode");
    }

    public static void addCompileError(String msg, FieldNode node) {
        addCompileError(msg, node, node);
    }


    public static void addCompileError(String msg, FieldNode node, ASTNode sourcePosition) {
        addCompileError(node.getOwner().getModule().getContext(), msg, sourcePosition);
    }

    public static void addCompileError(String msg, ClassNode node) {
        addCompileError(msg, node, node);
    }

    public static void addCompileError(String msg, ClassNode node, ASTNode sourcePosition) {
        addCompileError(node.getModule().getContext(), msg, sourcePosition);
    }

    public static void addCompileWarning(SourceUnit sourceUnit, String msg, ASTNode node) {
        Token token = new Token(Types.UNKNOWN, node.getText(), node.getLineNumber(), node.getColumnNumber());
        sourceUnit.getErrorCollector().addWarning(WarningMessage.LIKELY_ERRORS, msg, token, sourceUnit);
    }

    public static void assertMethodIsParameterless(MethodNode method, SourceUnit sourceUnit) {
        if (method.getParameters().length > 0)
            addCompileError(sourceUnit, "Lifecycle/Validate methods must be parameterless!", method);
    }

    public static void assertMethodIsNotPrivate(MethodNode method, SourceUnit sourceUnit) {
        if (method.isPrivate())
            addCompileError(sourceUnit, "Lifecycle methods must not be private!", method);
    }

    public static void replaceMethod(ClassNode target, MethodNode method) {
        MethodNode oldMethod = target.getDeclaredMethod(method.getName(), method.getParameters());
        if (oldMethod != null)
            target.removeMethod(oldMethod);
        target.addMethod(method);
    }

    public static ClosureExpression toStronglyTypedClosure(ClosureExpression validationClosure, ClassNode parameterType) {
        String closureParameterName = validationClosure.isParameterSpecified() ? validationClosure.getParameters()[0].getName() : "it";
        ClosureExpression typeValidationClosure = closureX(params(param(parameterType.getPlainNodeReference(), closureParameterName)), validationClosure.getCode());
        typeValidationClosure.copyNodeMetaData(validationClosure);
        typeValidationClosure.setSourcePosition(validationClosure);

        typeValidationClosure.visit(new StronglyTypingClosureParameterVisitor(closureParameterName, parameterType.getPlainNodeReference()));
        return typeValidationClosure;
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

    public static void replaceProperties(ClassNode annotatedClass, List<PropertyNode> newNodes) {
        for (PropertyNode pNode : newNodes) {
            annotatedClass.getProperties().remove(pNode);
            addPropertyAsFieldWithAccessors(annotatedClass, pNode);
        }
    }

    public static List<ClassNode> findAllKnownSubclassesOf(ClassNode type) {
        return findAllKnownSubclassesOf(type, type.getCompileUnit());
    }

    public static List<ClassNode> findAllKnownSubclassesOf(ClassNode type, CompileUnit compileUnit) {
        if ((type.getModifiers() & ACC_FINAL) != 0)
            return Collections.emptyList();
        List<ClassNode> result = new ArrayList<ClassNode>();

        for (ClassNode classInCU : (List<ClassNode>) compileUnit.getClasses())
            if (classInCU.isDerivedFrom(type))
                result.add(classInCU);
        return result;
    }

    @SuppressWarnings("ConstantConditions")
    public static GenericsType[] getGenericsTypes(FieldNode fieldNode) {
        GenericsType[] types = fieldNode.getType().getGenericsTypes();

        if (types == null)
            addCompileError(fieldNode.getOwner().getModule().getContext(), "Lists and Maps need to be assigned an explicit Generic Type", fieldNode);
        return types;
    }

    public static ClassNode getElementType(FieldNode fieldNode) {
        if (isMap(fieldNode.getType()))
            return getGenericsTypes(fieldNode)[1].getType();
        else if (isCollection(fieldNode.getType()))
            return getGenericsTypes(fieldNode)[0].getType();
        else
            return fieldNode.getType();
    }

    public static String getNullSafeMemberStringValue(AnnotationNode fieldAnnotation, String name, String defaultValue) {
        return fieldAnnotation == null ? defaultValue : AbstractASTTransformation.getMemberStringValue(fieldAnnotation, name, defaultValue);
    }

    public static <T extends Enum> T getNullSafeEnumMemberValue(AnnotationNode node, String name, T defaultValue) {
        if (node == null)
            return defaultValue;

        Expression member = node.getMember(name);
        if (member == null)
            return defaultValue;

        if (!(member instanceof PropertyExpression))
            return defaultValue;

        String value = ((PropertyExpression) member).getPropertyAsString();

        return (T) Enum.valueOf(defaultValue.getClass(), value);
    }

    public static void initializeCollectionOrMap(FieldNode fieldNode) {
        ClassNode fieldType = fieldNode.getType();
        if (isCollection(fieldType))
            initializeField(fieldNode, asExpression(fieldType, new ListExpression()));
        else if (fieldType.equals(CommonAstHelper.SORTED_MAP_TYPE))
            initializeField(fieldNode, ctorX(makeClassSafe(TreeMap.class)));
        else if (isMap(fieldType))
            initializeField(fieldNode, asExpression(fieldType, new MapExpression()));
        else
            throw new IllegalStateException("FieldNode " + fieldNode + " is no collection or Map");
    }

    private static void initializeField(FieldNode fieldNode, Expression init) {
        if (!fieldNode.hasInitialExpression())
            fieldNode.setInitialValueExpression(init);
    }

    public static String getQualifiedName(FieldNode node) {
        return node.getOwner().getName() + "." + node.getName();
    }

    public static MapExpression getLiteralMapExpressionFromClosure(ClosureExpression closure) {
        BlockStatement code = (BlockStatement) closure.getCode();
        if (code.getStatements().size() != 1) return null;
        Statement statement = code.getStatements().get(0);
        if (!(statement instanceof ExpressionStatement)) return null;
        Expression expression = ((ExpressionStatement) statement).getExpression();
        if (!(expression instanceof MapExpression)) return null;
        return (MapExpression) expression;
    }

    public static Map<String, ClassNode> getStringClassMapFromClosure(ClosureExpression closureExpression, AnnotatedNode source) {
        MapExpression map = getLiteralMapExpressionFromClosure(closureExpression);
        if (map == null)
            return null;

        Map<String, ClassNode> result = new LinkedHashMap<String, ClassNode>();

        for (MapEntryExpression entry : map.getMapEntryExpressions()) {
            String key = getKeyStringFromLiteralMapEntry(entry, source);
            ClassNode value = getClassNodeValueFromLiteralMapEntry(entry, source);

            result.put(key, value);
        }

        return result;
    }

    public static String getKeyStringFromLiteralMapEntry(MapEntryExpression entryExpression, AnnotatedNode source) {
        Expression result = entryExpression.getKeyExpression();
        if (result instanceof ConstantExpression && result.getType().equals(STRING_TYPE))
            return result.getText();

        CommonAstHelper.addCompileError("Map keys may only be Strings.", source, entryExpression);
        return null;
    }

    public static ClassNode getClassNodeValueFromLiteralMapEntry(MapEntryExpression entryExpression, AnnotatedNode source) {
        Expression result = entryExpression.getValueExpression();
        if (result instanceof ClassExpression)
            return result.getType();

        CommonAstHelper.addCompileError("Map values may only be classes.", source, entryExpression);
        return null;
    }

}
