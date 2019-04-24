/**
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

import com.blackbuild.klum.common.GenericsMethodBuilder;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.expr.Expression;

import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.notX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;

public final class DslMethodBuilder extends GenericsMethodBuilder<DslMethodBuilder> {

    public static final ClassNode CLASSLOADER_TYPE = ClassHelper.make(ClassLoader.class);
    public static final ClassNode THREAD_TYPE = ClassHelper.make(Thread.class);

    private DslMethodBuilder(String name) {
        super(name);
    }

    public static DslMethodBuilder createMethod(String name) {
        return new DslMethodBuilder(name);
    }

    public static DslMethodBuilder createPublicMethod(String name) {
        return new DslMethodBuilder(name).mod(Opcodes.ACC_PUBLIC);
    }

    public static DslMethodBuilder createOptionalPublicMethod(String name) {
        return new DslMethodBuilder(name).mod(Opcodes.ACC_PUBLIC).optional();
    }

    public static DslMethodBuilder createProtectedMethod(String name) {
        return new DslMethodBuilder(name).mod(Opcodes.ACC_PROTECTED);
    }

    public static DslMethodBuilder createPrivateMethod(String name) {
        return new DslMethodBuilder(name).mod(Opcodes.ACC_PRIVATE);
    }


    public DslMethodBuilder withoutMutatorCheck() {
        metadata.put(DSLASTTransformation.NO_MUTATION_CHECK_METADATA_KEY, Boolean.TRUE);
        return this;
    }

    public DslMethodBuilder callValidationOn(String target) {
        return callValidationMethodOn(varX(target));
    }

    private DslMethodBuilder callValidationMethodOn(Expression targetX) {
        return statement(ifS(notX(propX(targetX,"$manualValidation")), callX(targetX, DSLASTTransformation.VALIDATE_METHOD)));
    }

    public DslMethodBuilder optionallySetOwnerOnS(String target, boolean targetHasOwner) {
        if (!targetHasOwner)
            return this;
        return callMethod(varX(target), "set$owner", varX(NAME_OF_MODEL_FIELD_IN_RW_CLASS));
    }

    public DslMethodBuilder params(Parameter... params) {
        for (Parameter param : params) {
            param(param);
        }
        return this;
    }

    public DslMethodBuilder params(List<Parameter>params) {
        for (Parameter param : params) {
            param(param);
        }
        return this;
    }

    public DslMethodBuilder optionalClassLoaderParam() {
        return param(CLASSLOADER_TYPE, "loader", callX(callX(THREAD_TYPE, "currentThread"), "getContextClassLoader"));
    }

}
