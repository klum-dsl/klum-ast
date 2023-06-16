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

import com.blackbuild.klum.ast.validation.AstValidator;
import com.blackbuild.klum.common.CommonAstHelper;
import groovy.transform.Undefined;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.isKeyed;
import static com.blackbuild.klum.common.CommonAstHelper.*;
import static java.lang.String.format;

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class FieldAstValidator extends AstValidator {

    private static final ClassNode UNDEFINED = ClassHelper.make(Undefined.class);

    @Override
    protected void extraValidation() {
        baseTypeMustBeSubtypeOfFieldType();
        if (target instanceof FieldNode)
            validateField();
    }

    private void baseTypeMustBeSubtypeOfFieldType() {
        ClassNode baseType = getMemberClassValue(annotation, "baseType");
        if (baseType == null) return;

        ClassNode fieldType = CommonAstHelper.getElementType((FieldNode) target);
        if (fieldType.equals(UNDEFINED) || isAssignableTo(baseType, fieldType)) return;
        addCompileError(
                sourceUnit,
                format("annotated basetype %s of %s is not a valid subtype of it.", baseType.getName(), fieldType.getName()),
                annotation
        );
    }

    private void validateField() {
        FieldNode fieldNode = (FieldNode) target;
        if (isCollectionOrMap(fieldNode.getType()))
            validateFieldAnnotationOnCollection();
        else
            validateFieldAnnotationOnSingleField();
    }

    private void validateFieldAnnotationOnSingleField() {
        FieldNode fieldNode = (FieldNode) target;
        if (members.containsKey("members"))
            addCompileError(
                    sourceUnit,
                    format("@Field.members is only valid for List or Map fields, but field %s is of type %s", fieldNode.getName(), fieldNode.getType().getName()),
                    members.get("members")
            );

        if (members.containsKey("key") && !isKeyed(fieldNode.getType()))
            addCompileError(
                    sourceUnit, "@Field.key is only valid for keyed dsl fields",
                    members.get("key")
            );
    }

    private void validateFieldAnnotationOnCollection() {
        if (members.containsKey("key"))
            addCompileError(
                    sourceUnit,
                    "@Field.key is only allowed for non collection fields.",
                    members.get("key")
            );
    }

}
