package com.blackbuild.groovy.configdsl.transform.ast;

import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.Statement;

import java.util.Arrays;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by stephan on 05.12.2016.
 */
public class ASTHelper {
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

    static boolean isListOrMap(ClassNode type) {
        return isList(type) || isMap(type);
    }

    static boolean isList(ClassNode type) {
        return type.equals(ClassHelper.LIST_TYPE) || type.implementsInterface(ClassHelper.LIST_TYPE);
    }

    static boolean isMap(ClassNode type) {
        return type.equals(ClassHelper.MAP_TYPE) || type.implementsInterface(ClassHelper.MAP_TYPE);
    }

    static boolean isAbstract(ClassNode classNode) {
        return (classNode.getModifiers() & Opcodes.ACC_ABSTRACT) != 0;
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
}
