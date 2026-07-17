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
package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.annodocimal.ast.extractor.ASTExtractor;
import com.blackbuild.annodocimal.ast.formatting.AnnoDocUtil;
import com.blackbuild.annodocimal.ast.formatting.DocBuilder;
import com.blackbuild.annodocimal.ast.formatting.JavadocDocBuilder;
import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.klum.ast.doc.DocUtil;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.PropertyNode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.*;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createMethod;
import static com.blackbuild.klum.common.CommonAstHelper.replaceProperties;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;

/** Creates mutable Builder accessors and read-only completed-model accessors. */
class PropertyAccessors {
    private static final String VALUE_PARAMETER = "value";

    private final DSLASTTransformation transformation;
    private final List<PropertyNode> modelPropertiesToReplace = new ArrayList<>();

    PropertyAccessors(DSLASTTransformation transformation) {
        this.transformation = transformation;
    }

    void invoke() {
        transformation.builderFields.forEach(this::createBuilderAccessors);
        transformation.annotatedClass.getProperties().stream()
                .filter(property -> transformation.builderFields.containsKey(property.getField()))
                .forEach(this::makeModelPropertyReadOnly);
        replaceProperties(transformation.annotatedClass, modelPropertiesToReplace);
        modelPropertiesToReplace.forEach(this::documentModelGetters);
    }

    private void createBuilderAccessors(FieldNode modelField, FieldNode builderField) {
        String fieldName = modelField.getName();
        int visibility = isProtected(modelField) ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC;

        createMethod(getGetterName(fieldName))
                .mod(visibility)
                .returning(builderField.getType())
                .linkToField(modelField)
                .withDocumentation(documentation -> documentGetter(documentation, modelField))
                .doReturn(attrX(varX("this"), constX(fieldName)))
                .addTo(transformation.rwClass);

        if (ClassHelper.boolean_TYPE.equals(builderField.getType()) || ClassHelper.Boolean_TYPE.equals(builderField.getType()))
            createMethod(getBooleanGetterName(fieldName))
                    .mod(visibility)
                    .returning(builderField.getType())
                    .linkToField(modelField)
                    .withDocumentation(documentation -> documentGetter(documentation, modelField))
                    .doReturn(attrX(varX("this"), constX(fieldName)))
                    .addTo(transformation.rwClass);

        createMethod(DslAstHelper.getSetterName(fieldName))
                .mod(visibility)
                .returning(ClassHelper.VOID_TYPE)
                .param(builderField.getType(), VALUE_PARAMETER)
                .statement(callThisX("setInstanceAttribute", args(constX(fieldName), varX(VALUE_PARAMETER))))
                .addTo(transformation.rwClass);

        // Existing completed values enter through KlumBuilder, which seals LINK targets.
        if (!builderField.getType().equals(modelField.getType()))
            createMethod(DslAstHelper.getSetterName(fieldName))
                    .mod(visibility)
                    .returning(ClassHelper.VOID_TYPE)
                    .param(modelField.getType(), VALUE_PARAMETER)
                    .statement(callThisX("setInstanceAttribute", args(constX(fieldName), varX(VALUE_PARAMETER))))
                    .addTo(transformation.rwClass);
    }

    private void makeModelPropertyReadOnly(PropertyNode property) {
        FieldNode field = property.getField();
        if (getFieldType(field) == FieldType.BUILDER)
            return;
        if (getFieldType(field) == FieldType.TRANSIENT || (field.getModifiers() & Opcodes.ACC_TRANSIENT) != 0)
            return;

        if (field.getType().getName().equals(EnumSet.class.getName()))
            property.setGetterBlock(returnS(castX(field.getType(), callX(attrX(varX("this"), constX(field.getName())), "clone"))));
        else
            property.setGetterBlock(returnS(attrX(varX("this"), constX(field.getName()))));
        property.setSetterBlock(null);
        modelPropertiesToReplace.add(property);
    }

    private void documentModelGetters(PropertyNode property) {
        FieldNode field = property.getField();
        documentModelGetter(field, getGetterName(field.getName()));
        if (ClassHelper.boolean_TYPE.equals(field.getType()) || ClassHelper.Boolean_TYPE.equals(field.getType()))
            documentModelGetter(field, getBooleanGetterName(field.getName()));
    }

    private void documentModelGetter(FieldNode field, String getterName) {
        MethodNode getter = transformation.annotatedClass.getDeclaredMethod(getterName, Parameter.EMPTY_ARRAY);
        if (getter == null)
            throw new IllegalStateException("Generated model getter not found: " + transformation.annotatedClass.getName() + "#" + getterName);
        JavadocDocBuilder documentation = new JavadocDocBuilder();
        documentGetter(documentation, field);
        AnnoDocUtil.addDocumentation(getter, documentation);
    }

    private static void documentGetter(DocBuilder documentation, FieldNode field) {
        documentation.title(DocUtil.getGetterText(field));
        if (!field.getAnnotations(AbstractMethodBuilder.DEPRECATED_NODE).isEmpty())
            documentation.deprecated(ASTExtractor.extractDocText(field).getTag("deprecated").orElse(null));
    }
}
