package com.blackbuild.groovy.configdsl.transform;

import groovy.lang.DelegatesTo;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.ast.tools.GenericsUtils;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.List;

import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.param;
import static org.codehaus.groovy.ast.tools.GenericsUtils.nonGeneric;

public class MethodBuilder {

    private static final ClassNode[] EMPTY_EXCEPTIONS = new ClassNode[0];
    private static final Parameter[] EMPTY_PARAMETERS = new Parameter[0];
    private static final ClassNode DELEGATES_TO_ANNOTATION = make(DelegatesTo.class);

    private int modifiers = Opcodes.ACC_PUBLIC;

    private String name;

    private ClassNode returnType = ClassHelper.VOID_TYPE;

    private List<ClassNode> exceptions = new ArrayList<ClassNode>();

    private List<Parameter> parameters = new ArrayList<Parameter>();

    private BlockStatement body;

    public static MethodBuilder createVoidMethod(String name) {
        return new MethodBuilder().named(name);
    }

    public MethodBuilder named(String name) {
        this.name = name;
        return this;
    }

    @SuppressWarnings("ToArrayCallWithZeroLengthArrayArgument")
    public MethodNode addTo(ClassNode target) {
        return target.addMethod(
                name,
                modifiers,
                returnType,
                parameters.toArray(EMPTY_PARAMETERS),
                exceptions.toArray(EMPTY_EXCEPTIONS),
                body
        );
    }

    public MethodBuilder returning(ClassNode returnType) {
        this.returnType = returnType;
        return this;
    }

    public MethodBuilder mod(int modifier) {
        modifiers |= modifier;
        return this;
    }

    public MethodBuilder param(Parameter param) {
        parameters.add(param);
        return this;
    }

    public MethodBuilder delegatingClosureParam(ClassNode delegationTarget) {
        Parameter param = GeneralUtils.param(nonGeneric(ClassHelper.CLOSURE_TYPE), "closure");
        param.addAnnotation(createDelegatesToAnnotation(delegationTarget));
        return param(param);
    }

    private AnnotationNode createDelegatesToAnnotation(ClassNode target) {
        AnnotationNode result = new AnnotationNode(DELEGATES_TO_ANNOTATION);
        result.setMember("value", classX(target));
        return result;
    }

    public MethodBuilder code(BlockStatement code) {
        this.body = code;
        return this;
    }

    public MethodBuilder statement(Statement... statements) {
        if (body == null)
            body = new BlockStatement(statements, new VariableScope());
        else
            for (Statement statement : statements)
                body.addStatement(statement);
        return this;
    }

}
