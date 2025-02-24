/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.groovy.configdsl.transform.ast.mutators.WriteAccessMethodsMover;
import com.blackbuild.klum.ast.util.layer3.annotations.Cluster;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.runtime.MetaClassHelper;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.Optional;

// Needs to run BEFORE DSLTransformation
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class ClusterFieldTransformation extends AbstractASTTransformation {

    private static final ClassNode CLUSTER_ANNOTATION_TYPE = ClassHelper.make(Cluster.class);

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);
        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        AnnotationNode anno = (AnnotationNode) nodes[0];
        if (!CLUSTER_ANNOTATION_TYPE.equals(anno.getClassNode())) return;

        if (!(parent instanceof FieldNode)) return;

        FieldNode field = (FieldNode) parent;

        if (field.isStatic()) {
            addError("Field annotated with @Cluster must not be static", field);
            return;
        }

        ClassNode owner = field.getDeclaringClass();
        Optional<PropertyNode> propertyNode = owner.getProperties().stream().filter(p -> p.getName().equals(field.getName())).findAny();

        String getterName = "get" + MetaClassHelper.capitalize(field.getName());

        int targetModifiers = propertyNode.map(PropertyNode::getModifiers).orElseGet(field::getModifiers);

        MethodNode method = new MethodNode(getterName, targetModifiers, field.getType(), Parameter.EMPTY_ARRAY, ClassNode.EMPTY_ARRAY, new BlockStatement());
        for (AnnotationNode annotation : field.getAnnotations())
            method.addAnnotation(annotation);
        owner.addMethod(method);
        owner.removeField(field.getName());
        propertyNode.ifPresent(node -> owner.getProperties().remove(node));
        WriteAccessMethodsMover.markAsNoMutator(method);
    }

}
