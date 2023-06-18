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

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.klum.ast.validation.AstValidator;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.jetbrains.annotations.NotNull;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.*;
import static com.blackbuild.klum.common.CommonAstHelper.isAssignableTo;
import static com.blackbuild.klum.common.CommonAstHelper.isCollectionOrMap;
import static java.lang.reflect.Modifier.isFinal;

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class FieldAstValidator extends AstValidator {

    @Override
    protected void extraValidateField() {
        FieldNode fieldNode = (FieldNode) target;
        if (isCollectionOrMap(fieldNode.getType()))
            validateFieldAnnotationOnCollection();
        else
            validateFieldAnnotationOnSingleField();
        validateBaseType(CommonAstHelper.getElementType((FieldNode) target));
    }

    @Override
    protected void extraValidateMethod() {
        if (getFieldType(target) == FieldType.LINK)
            addCompileError("BaseType is not allowed on LINK fields");
        validateBaseType(((MethodNode) target).getParameters()[0].getType());
    }

    private void validateBaseType(ClassNode fieldType) {
        if (!members.containsKey("baseType")) return;

        @NotNull ClassNode baseType = getMemberClassValue(annotation, "baseType");

        if (isFinal(fieldType.getModifiers()))
            addCompileError(
                   "annotated field %s is final and cannot be overridden.",
                    ((FieldNode) target).getName()
            );

        if (!isDSLObject(baseType))
            addCompileError(
                    "BaseType must be an DSL-Object"
            );

        if (baseType != null && !isAssignableTo(baseType, fieldType))
            addCompileError(
                "annotated basetype %s of %s is not a valid subtype of it.", baseType.getName(), fieldType.getName()
            );

        if (getFieldType(target) == FieldType.LINK)
            addCompileError("BaseType is not allowed on LINK fields");

        if (isDSLObject(fieldType) && isKeyed(baseType) && !isKeyed(fieldType))
            addCompileError("BaseType %s is keyed, but field %s is not.", baseType.getName(), ((FieldNode) target).getName());
    }

    private void validateFieldAnnotationOnSingleField() {
        FieldNode fieldNode = (FieldNode) target;
        if (members.containsKey("members"))
            addCompileError("@Field.members is only valid for List or Map fields, but field %s is of type %s", fieldNode.getName(), fieldNode.getType().getName());

        if (members.containsKey("key") && !isKeyed(fieldNode.getType()))
            addCompileError("@Field.key is only valid for keyed dsl fields");
    }

    private void validateFieldAnnotationOnCollection() {
        if (members.containsKey("key"))
            addCompileError("@Field.key is only allowed for non collection fields.");
    }

}
