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
package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.klum.cast.checks.impl.KlumCastCheck;
import com.blackbuild.klum.common.CommonAstHelper;
import org.codehaus.groovy.ast.*;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.*;
import static com.blackbuild.klum.common.CommonAstHelper.*;
import static java.lang.reflect.Modifier.isFinal;

@SuppressWarnings("unused") // see Field
public class FieldAstValidator extends KlumCastCheck<Annotation> {

    private AnnotationNode annotationToCheck;

    @Override
    protected void doCheck(AnnotationNode annotationToCheck, AnnotatedNode target) {
        this.annotationToCheck = annotationToCheck;
        // TODO move logic to klumCast
        if (target instanceof FieldNode)
            extraValidateField((FieldNode) target);
        else if (target instanceof MethodNode)
            extraValidateMethod((MethodNode) target);
    }

    protected void extraValidateField(FieldNode fieldNode) {
        if (isCollectionOrMap(fieldNode.getType()))
            validateFieldAnnotationOnCollection();
        else
            validateFieldAnnotationOnSingleField(fieldNode);
        validateDefaultImpl(CommonAstHelper.getElementType(fieldNode));
    }

    protected void extraValidateMethod(MethodNode target) {
        if (getFieldType(target) == FieldType.LINK)
            throw new IllegalStateException("Default Implementation is not allowed on LINK fields");
        validateDefaultImpl(target.getParameters()[0].getType());
    }

    private void validateDefaultImpl(ClassNode fieldType) {
        if (annotationToCheck.getMember("defaultImpl") == null) return;

        @NotNull ClassNode defaultImpl = getNullSafeClassMember(annotationToCheck, "defaultImpl", null);

        if (isFinal(fieldType.getModifiers()))
            throw new IllegalStateException(String.format(
                   "annotated field %s is final and cannot be overridden.",
                    fieldType.getName())
            );

        if (!isDSLObject(defaultImpl))
            throw new IllegalStateException(
                    "Default Implementation must be an DSL-Object"
            ) ;

        if (!isAssignableTo(defaultImpl, fieldType))
            throw new IllegalStateException(String.format(
                "Annotated Default Implementation %s of %s is not a valid subtype of it.", defaultImpl.getName(), fieldType.getName()
            ));

        if (getFieldType(fieldType) == FieldType.LINK)
            throw new IllegalStateException("Default Implementation is not allowed on LINK fields");

        if (isDSLObject(fieldType) && isKeyed(defaultImpl) && !isKeyed(fieldType))
            throw new IllegalStateException(
                    String.format("Default Implementation %s is keyed, but field %s is not.",
                            defaultImpl.getName(), fieldType.getName()));

        if (!isInstantiable(defaultImpl))
            throw new IllegalStateException(
                    String.format("Default Implementation %s is not instantiable.", defaultImpl.getName())
            );
    }

    private void validateFieldAnnotationOnSingleField(FieldNode fieldNode) {
        if (annotationToCheck.getMembers().containsKey("members"))
            throw new IllegalStateException(String.format("@Field.members is only valid for List or Map fields, but field %s is of type %s", fieldNode.getName(), fieldNode.getType().getName()));

        if (annotationToCheck.getMembers().containsKey("key") && !isKeyed(fieldNode.getType()))
            throw new IllegalStateException("@Field.key is only valid for keyed dsl fields");

        if (annotationToCheck.getMembers().containsKey("keyMapping") && !isMap(fieldNode.getType()))
            throw new IllegalStateException("@Field.keyMapping is only valid for Map fields");
    }

    private void validateFieldAnnotationOnCollection() {
        if (annotationToCheck.getMembers().containsKey("key"))
            throw new IllegalStateException("@Field.key is only allowed for non collection fields.");
    }

}
