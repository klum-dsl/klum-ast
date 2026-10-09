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

import com.blackbuild.klum.ast.Builder;
import com.blackbuild.klum.ast.Mutator;
import com.blackbuild.klum.ast.WriteAccess;
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessHelper;
import com.blackbuild.klum.cast.spi.Check;
import com.blackbuild.klum.cast.spi.CheckContext;
import com.blackbuild.klum.cast.spi.Diagnostic;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;

import java.util.ArrayList;
import java.util.List;

/** Validates the declaration-level {@code @Builder.Result} contract. */
@SuppressWarnings("deprecation") // @Mutator remains an intentional 4.1 source-compatibility spelling.
public class BuilderResultCheck implements Check {

    private static final ClassNode BUILDER_QUERY = ClassHelper.make(Builder.Query.class);
    private static final ClassNode LEGACY_MUTATOR = ClassHelper.make(Mutator.class);

    @Override
    public List<Diagnostic> check(CheckContext context) {
        if (!(context.getTarget() instanceof MethodNode method))
            return List.of(diagnostic(context, "@Builder.Result can only be used on methods"));

        List<Diagnostic> diagnostics = new ArrayList<>();
        if (!DslAstHelper.isDSLObject(method.getDeclaringClass()))
            diagnostics.add(diagnostic(context, "@Builder.Result can only be used on methods declared by a DSL Object"));
        if (!method.isPublic())
            diagnostics.add(diagnostic(context, "@Builder.Result methods must be public"));
        if (method.isAbstract())
            diagnostics.add(diagnostic(context, "@Builder.Result methods must declare an implementation"));
        if (method.isVoidMethod())
            diagnostics.add(diagnostic(context, "@Builder.Result methods must return a value"));
        boolean builderMethod = WriteAccessHelper.isBuilderMethod(method)
                || !method.getAnnotations(LEGACY_MUTATOR).isEmpty();
        if (!method.isStatic() && !builderMethod)
            diagnostics.add(diagnostic(context,
                    method.getAnnotations(BUILDER_QUERY).isEmpty()
                            ? "@Builder.Result on an instance method requires @Builder.Method"
                            : "@Builder.Query cannot be combined with @Builder.Result"));
        if (WriteAccessHelper.getWriteAccessTypeForMethodOrField(method).orElse(null) == WriteAccess.Type.LIFECYCLE)
            diagnostics.add(diagnostic(context, "@Builder.Result cannot be combined with a lifecycle annotation"));
        return diagnostics;
    }

    private Diagnostic diagnostic(CheckContext context, String message) {
        return new Diagnostic(getClass().getName(), message, context.getValidatedAnnotation());
    }
}
