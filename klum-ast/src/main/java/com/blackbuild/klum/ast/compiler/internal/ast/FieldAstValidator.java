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

import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.cast.spi.Check;
import com.blackbuild.klum.cast.spi.CheckContext;
import com.blackbuild.klum.cast.spi.Diagnostic;
import com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper;
import org.codehaus.groovy.ast.*;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.*;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.*;
import static java.lang.reflect.Modifier.isFinal;

@SuppressWarnings("unused") // see Field
public class FieldAstValidator implements Check {

    private static final String DEFAULT_IMPL_MEMBER = "defaultImpl";

    @Override
    public List<Diagnostic> check(CheckContext context) {
        AnnotationNode annotationToCheck = context.getValidatedAnnotation();
        AnnotatedNode target = context.getTarget();
        Diagnostic diagnostic = null;
        if (target instanceof FieldNode)
            diagnostic = extraValidateField(annotationToCheck, (FieldNode) target);
        else if (target instanceof MethodNode)
            diagnostic = extraValidateMethod(annotationToCheck, (MethodNode) target);
        return diagnostic == null ? List.of() : List.of(diagnostic);
    }

    protected Diagnostic extraValidateField(AnnotationNode annotationToCheck, FieldNode fieldNode) {
        Diagnostic diagnostic;
        if (isCollectionOrMap(fieldNode.getType()))
            diagnostic = validateFieldAnnotationOnCollection(annotationToCheck);
        else
            diagnostic = validateFieldAnnotationOnSingleField(annotationToCheck, fieldNode);
        return diagnostic == null ? validateDefaultImpl(annotationToCheck, CommonAstHelper.getElementType(fieldNode)) : diagnostic;
    }

    protected Diagnostic extraValidateMethod(AnnotationNode annotationToCheck, MethodNode target) {
        if (target == null)
            return null;
        if (getFieldType(target) == FieldType.LINK && annotationToCheck.getMember(DEFAULT_IMPL_MEMBER) != null)
            return violation(annotationToCheck, "Default Implementation is not allowed on LINK fields");
        if (target.getParameters().length == 0)
            return null;
        return validateDefaultImpl(annotationToCheck, target.getParameters()[0].getType());
    }

    private Diagnostic validateDefaultImpl(AnnotationNode annotationToCheck, ClassNode fieldType) {
        if (annotationToCheck.getMember(DEFAULT_IMPL_MEMBER) == null) return null;

        @NotNull ClassNode defaultImpl = getNullSafeClassMember(annotationToCheck, DEFAULT_IMPL_MEMBER, null);

        if (isFinal(fieldType.getModifiers()))
            return violation(annotationToCheck, String.format(
                    "annotated field %s is final and cannot be overridden.",
                    fieldType.getName())
            );

        if (!isDSLObject(defaultImpl))
            return violation(annotationToCheck,
                    "Default Implementation must be an DSL-Object"
            );

        if (!isAssignableTo(defaultImpl, fieldType))
            return violation(annotationToCheck, String.format(
                "Annotated Default Implementation %s of %s is not a valid subtype of it.", defaultImpl.getName(), fieldType.getName()
            ));

        if (getFieldType(fieldType) == FieldType.LINK)
            return violation(annotationToCheck, "Default Implementation is not allowed on LINK fields");

        if (isDSLObject(fieldType) && isKeyed(defaultImpl) && !isKeyed(fieldType))
            return violation(annotationToCheck,
                    String.format("Default Implementation %s is keyed, but field %s is not.",
                            defaultImpl.getName(), fieldType.getName()));

        if (!isInstantiable(defaultImpl))
            return violation(annotationToCheck,
                    String.format("Default Implementation %s is not instantiable.", defaultImpl.getName())
            );

        return null;
    }

    private Diagnostic validateFieldAnnotationOnSingleField(AnnotationNode annotationToCheck, FieldNode fieldNode) {
        if (annotationToCheck.getMembers().containsKey("members"))
            return violation(annotationToCheck, String.format("@Field.members is only valid for List or Map fields, but field %s is of type %s", fieldNode.getName(), fieldNode.getType().getName()));

        if (annotationToCheck.getMembers().containsKey("key") && !isKeyed(fieldNode.getType()))
            return violation(annotationToCheck, "@Field.key is only valid for keyed dsl fields");

        if (annotationToCheck.getMembers().containsKey("keyMapping") && !isMap(fieldNode.getType()))
            return violation(annotationToCheck, "@Field.keyMapping is only valid for Map fields");

        return null;
    }

    private Diagnostic validateFieldAnnotationOnCollection(AnnotationNode annotationToCheck) {
        if (annotationToCheck.getMembers().containsKey("key"))
            return violation(annotationToCheck, "@Field.key is only allowed for non collection fields.");
        return null;
    }

    private Diagnostic violation(AnnotationNode annotationToCheck, String message) {
        return new Diagnostic(getClass().getName(), message, annotationToCheck);
    }

}
