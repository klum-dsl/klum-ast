/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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

import com.blackbuild.klum.ast.util.layer3.annotations.Cluster;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.ArgumentListExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.ReturnStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.messages.WarningMessage;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.Collection;

import static com.blackbuild.klum.common.CommonAstHelper.isAssignableTo;
import static java.lang.String.format;
import static org.codehaus.groovy.ast.ClassHelper.MAP_TYPE;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;

@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class ClusterTransformation extends AbstractASTTransformation {

    private static final ClassNode CLUSTER_ANNOTATION_TYPE = ClassHelper.make(Cluster.class);
    private static final ClassNode CLUSTER_MODEL_TYPE = ClassHelper.make(ClusterModel.class);
    public static final ClassNode COLLECTION_TYPE = ClassHelper.make(Collection.class);

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);
        AnnotatedNode parent = (AnnotatedNode) nodes[1];
        AnnotationNode anno = (AnnotationNode) nodes[0];
        if (!CLUSTER_ANNOTATION_TYPE.equals(anno.getClassNode())) return;
        if (!(parent instanceof MethodNode)) return;

        MethodNode method = (MethodNode) parent;
        
        assertIsAbstractOrEmpty(method);
        assertIsParameterless(method);
        assertReturnsMapOfStrings(method);



        method.setModifiers(method.getModifiers() & 0x7);
        method.setCode(createMethodBody(method, anno));
    }

    private void assertReturnsMapOfStrings(MethodNode method) {
        if (!method.getReturnType().equals(MAP_TYPE))
            addError(format("Method %s must return Map", method), method);
        GenericsType[] genericsTypes = method.getReturnType().getGenericsTypes();
        if (genericsTypes == null || genericsTypes.length != 2)
            addError(format("Method %s must return fully generic Map", method), method);
        else if (genericsTypes[0].isWildcard() || genericsTypes[1].isWildcard())
            addError(format("Method %s must not return wildcard generics", method), method);
        else if (!genericsTypes[0].getType().equals(ClassHelper.STRING_TYPE))
            addError(format("Method %s must return Map<String, x>", method), method);
    }

    private void assertIsParameterless(MethodNode method) {
        if (method.getParameters().length != 0)
            addError(format("Method %s must be parameterless", method), method);
    }

    private void assertIsAbstractOrEmpty(MethodNode method) {
        if (method.isAbstract()) return;
        if (method.getCode().isEmpty()) return;
        Statement code = method.getCode();
        if (code instanceof BlockStatement && ((BlockStatement) code).getStatements().size() == 1)
            code = ((BlockStatement) code).getStatements().get(0);
        Expression expression = null;
        if (code instanceof ReturnStatement)
            expression = ((ReturnStatement) code).getExpression();
        else if (code instanceof ExpressionStatement)
            expression = ((ExpressionStatement) code).getExpression();
        if (expression instanceof ConstantExpression && ((ConstantExpression) expression).isNullExpression())
            return;
        if (expression instanceof MapExpression && ((MapExpression) expression).getMapEntryExpressions().isEmpty())
            return;
        addError(format("Method %s must be abstract, empty or return either null or [:]", method), method);
    }

    private Statement createMethodBody(MethodNode method, AnnotationNode anno) {
        ClassNode filterAnnotation = getMemberClassValue(anno,"value");
        boolean includeNulls = !memberHasValue(anno, "includeNulls", false);

        ClassNode elementType = method.getReturnType().getGenericsTypes()[1].getType();
        String targetMethod = includeNulls ? "getPropertiesOfType" : "getNonEmptyPropertiesOfType";

        if (isAssignableTo(elementType, COLLECTION_TYPE)) {
            if (elementType.isUsingGenerics()) {
                elementType = elementType.getGenericsTypes()[0].getType();
                targetMethod = "getCollectionsOfType";
            } else {
                sourceUnit.getErrorCollector().addWarning(
                        WarningMessage.LIKELY_ERRORS,
                        format("method %s uses raw collection. If this is really what you want, use Collection<Object>.", method),
                        null,
                        sourceUnit
                );
            }
        }

        ArgumentListExpression args = new ArgumentListExpression(varX("this"), classX(elementType));

        if (filterAnnotation != null)
            args.addExpression(classX(filterAnnotation));

       return returnS(callX(CLUSTER_MODEL_TYPE, targetMethod, args));
    }

}
