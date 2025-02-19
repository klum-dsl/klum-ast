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
package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;

import java.util.Arrays;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.createGeneratedAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getRwClassOf;
import static groovyjarjarasm.asm.Opcodes.*;
import static org.codehaus.groovy.ast.ClassHelper.OBJECT_TYPE;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;

public abstract class AbstractFactoryBuilder {
    protected final ClassNode rwClass;
    protected final ClassNode targetClass;
    protected InnerClassNode collectionFactory;

    protected AbstractFactoryBuilder(ClassNode targetClass) {
        this.targetClass = targetClass;
        this.rwClass = getRwClassOf(targetClass);
    }

    protected void createDelegateMethods(MethodNode targetMethod) {
        int numberOfDefaultParams = (int) Arrays.stream(targetMethod.getParameters()).filter(Parameter::hasInitialExpression).count();

        // We want to create an explicit method realized method after resolving default values
        // i.e. bla(int a, int b = 1) -> bla(int a, int b) and bla(int a), otherwise, null values might
        // cause the wrong method to be called.
        do {
            new ProxyMethodBuilder(varX("rw"), targetMethod.getName(), targetMethod.getName())
                    .targetType(rwClass)
                    .linkToMethod(targetMethod)
                    .optional()
                    .mod(targetMethod.getModifiers() & ~ACC_ABSTRACT)
                    .returning(targetMethod.getReturnType())
                    .paramsFromWithoutDefaults(targetMethod, numberOfDefaultParams)
                    .addTo(collectionFactory);
            numberOfDefaultParams--;
        } while (numberOfDefaultParams >= 0);
    }

    public abstract void invoke();

    protected void createInnerClass(String name) {
        collectionFactory = new InnerClassNode(targetClass, targetClass.getName() + "$_" + name, ACC_PUBLIC | ACC_STATIC, OBJECT_TYPE);
        collectionFactory.addField("rw", ACC_PRIVATE | ACC_SYNTHETIC | ACC_FINAL, rwClass, null);
        collectionFactory.addConstructor(ACC_PUBLIC,
                params(param(rwClass, "rw")),
                CommonAstHelper.NO_EXCEPTIONS,
                block(
                        assignS(propX(varX("this"), "rw"), varX("rw"))
                )
        );
        DslAstHelper.registerAsVerbProvider(collectionFactory);

        MethodBuilder.createProtectedMethod("get$proxy")
                .returning(make(KlumInstanceProxy.class))
                .doReturn(propX(varX("rw"), KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS))
                .addTo(collectionFactory);

        collectionFactory.addAnnotation(createGeneratedAnnotation(getClass()));
        targetClass.getModule().addClass(collectionFactory);
    }
}

