/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.ParameterAnnotation;
import com.blackbuild.klum.common.MethodBuilderException;
import groovy.lang.DelegatesTo;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.stmt.Statement;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.createGeneratedAnnotation;
import static org.codehaus.groovy.ast.ClassHelper.make;

@SuppressWarnings("unchecked")
public abstract class AbstractMethodBuilder<T extends AbstractMethodBuilder<?>> {


    protected static final ClassNode CLASSLOADER_TYPE = ClassHelper.make(ClassLoader.class);
    protected static final ClassNode THREAD_TYPE = ClassHelper.make(Thread.class);
    protected static final ClassNode PARAMETER_ANNOTATION_TYPE = ClassHelper.make(ParameterAnnotation.class);
    protected static final ClassNode DEPRECATED_NODE = ClassHelper.make(Deprecated.class);
    protected static final ClassNode[] EMPTY_EXCEPTIONS = new ClassNode[0];
    protected static final Parameter[] EMPTY_PARAMETERS = new Parameter[0];
    protected static final ClassNode DELEGATES_TO_ANNOTATION = make(DelegatesTo.class);
    protected static final ClassNode DELEGATES_TO_TARGET_ANNOTATION = make(DelegatesTo.Target.class);

    protected final String name;
    protected final List<ClassNode> exceptions = new ArrayList<>();
    protected final Set<String> tags = new HashSet<>();
    protected int modifiers;
    protected ClassNode returnType = ClassHelper.VOID_TYPE;
    protected DeprecationType deprecationType;
    protected boolean optional;
    protected ASTNode sourceLinkTo;
    protected String documentation;
    protected GenericsType[] genericsTypes;

    protected AbstractMethodBuilder(String name) {
        this.name = name;
    }

    public T returning(ClassNode returnType) {
        this.returnType = returnType;
        return (T) this;
    }

    public T linkToField(AnnotatedNode annotatedNode) {
        return (T) inheritDeprecationFrom(annotatedNode).sourceLinkTo(annotatedNode);
    }

    public T inheritDeprecationFrom(AnnotatedNode annotatedNode) {
        if (!annotatedNode.getAnnotations(DEPRECATED_NODE).isEmpty()) {
            return deprecated();
        }
        return (T) this;
    }

    public T sourceLinkTo(ASTNode sourceLinkTo) {
        this.sourceLinkTo = sourceLinkTo;
        return (T) this;
    }

    public T documentation(String documentation) {
        this.documentation = documentation;
        return (T) this;
    }

    public T tag(String tag) {
        tags.add(tag);
        return (T) this;
    }

    /**
     * Marks this method as optional. If set, {@link #addTo(ClassNode)} does not throw an error if the method already exists.
     */
    public T optional() {
        this.optional = true;
        return (T) this;
    }

    /**
     * Sets the modifiers as defined by {@link Opcodes}.
     */
    public T mod(int modifier) {
        modifiers |= modifier;
        return (T) this;
    }

    public T deprecated() {
        deprecationType = DeprecationType.DEPRECATED;
        return (T) this;
    }

    public T forRemoval() {
        deprecationType = DeprecationType.FOR_REMOVAL;
        return (T) this;
    }

    public T setGenericsTypes(GenericsType[] genericsTypes) {
        this.genericsTypes = genericsTypes;
        return (T) this;
    }

    public MethodNode addTo(ClassNode target) {
        return doAddTo(target);
    }

    protected MethodNode doAddTo(ClassNode target) {
        Parameter[] parameterArray = getMethodParameters();
        MethodNode existing = target.getDeclaredMethod(name, parameterArray);

        if (existing != null) {
            if (optional)
                return null;
            else
                throw new MethodBuilderException("Method " + existing + " is already defined.", existing);
        }

        MethodNode method = target.addMethod(
                name,
                modifiers,
                returnType,
                parameterArray,
                exceptions.toArray(EMPTY_EXCEPTIONS),
                getMethodBody()
        );

        if (deprecationType != null)
            method.addAnnotation(deprecationType.toNode());

        if (genericsTypes != null)
            method.setGenericsTypes(genericsTypes);

        if (sourceLinkTo != null)
            method.setSourcePosition(sourceLinkTo);

        method.addAnnotation(createGeneratedAnnotation(DSLASTTransformation.class, documentation, tags));
        return method;
    }

    protected abstract Parameter[] getMethodParameters();


    protected abstract Statement getMethodBody();
}
