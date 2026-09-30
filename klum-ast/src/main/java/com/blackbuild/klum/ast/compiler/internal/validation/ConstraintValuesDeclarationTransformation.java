/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast.compiler.internal.validation;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.expr.BooleanExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.stmt.AssertStatement;
import org.codehaus.groovy.ast.stmt.BlockStatement;
import org.codehaus.groovy.ast.stmt.ExpressionStatement;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/** Validates a marked annotation declaration and gives single truth expressions assertion semantics. */
@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class ConstraintValuesDeclarationTransformation extends AbstractASTTransformation {

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);
        if (nodes.length < 2 || !(nodes[0] instanceof AnnotationNode marker)) return;
        if (!(nodes[1] instanceof ClassNode domain) || !domain.isAnnotationDefinition()) {
            addError("@ConstraintValues can only mark an annotation declaration", nodes[1]);
            return;
        }
        ConstraintValuesFieldCheck.checkDeclaration(domain, marker, marker, source);

        if (!(marker.getMember("value") instanceof ClosureExpression closure)) return;
        if (!(closure.getCode() instanceof BlockStatement block) || block.getStatements().size() != 1) return;
        Statement statement = block.getStatements().get(0);
        if (!(statement instanceof ExpressionStatement expressionStatement)) return;

        AssertStatement assertion = new AssertStatement(
                new BooleanExpression(expressionStatement.getExpression()),
                new ConstantExpression("Constraint expression evaluated false: " + expressionStatement.getExpression().getText()));
        assertion.setSourcePosition(statement);
        block.getStatements().set(0, assertion);
    }
}
