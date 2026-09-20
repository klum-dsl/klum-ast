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
package com.blackbuild.klum.ast.compiler.internal.ast;

import com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.Arrays;

/** Rejects {@code @Builder.Input} outside methods declared by a DSL Object. */
@GroovyASTTransformation(phase = CompilePhase.CANONICALIZATION)
public class BuilderInputTransformation extends AbstractASTTransformation {

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);
        if (nodes.length < 2 || !(nodes[1] instanceof Parameter parameter)) return;

        MethodNode method = source.getAST().getClasses().stream()
                .flatMap(owner -> owner.getMethods().stream())
                .filter(candidate -> Arrays.stream(candidate.getParameters()).anyMatch(value -> value == parameter))
                .findFirst()
                .orElse(null);
        if (method == null) {
            CommonAstHelper.addCompileError(
                    source,
                    "@Builder.Input can only be used on method parameters declared by a DSL Object",
                    parameter
            );
            return;
        }
        if (DslAstHelper.isDSLObject(method.getDeclaringClass())) return;
        if (DslAstHelper.isDSLObject(DslAstHelper.getModelClassFor(method.getDeclaringClass()))) return;

        CommonAstHelper.addCompileError(
                source,
                "@Builder.Input can only be used on method parameters declared by a DSL Object",
                parameter
        );
    }
}
