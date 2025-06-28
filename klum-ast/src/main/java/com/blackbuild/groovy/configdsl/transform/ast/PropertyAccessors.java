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

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.PropertyNode;

import java.util.ArrayList;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.*;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.*;
import static com.blackbuild.klum.common.CommonAstHelper.replaceProperties;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;

class PropertyAccessors {
    private final DSLASTTransformation dslastTransformation;
    private final List<PropertyNode> propertiesToReplace = new ArrayList<>();

    public PropertyAccessors(DSLASTTransformation dslastTransformation) {
        this.dslastTransformation = dslastTransformation;
    }

    public void invoke() {
        getInstanceNonPropertyFields(dslastTransformation.annotatedClass).forEach(this::createPropertyForBuilderField);
        getInstanceProperties(dslastTransformation.annotatedClass).forEach(this::adjustPropertyAccessorsForSingleField);

        setAccessorsForOwnerFields();

        if (dslastTransformation.keyField != null)
            setAccessorsForSpecialField(dslastTransformation.keyField);

        replaceProperties(dslastTransformation.annotatedClass, propertiesToReplace);
    }

    private void createPropertyForBuilderField(FieldNode fieldNode) {
        if (getFieldType(fieldNode) != FieldType.BUILDER)
            return;
        PropertyNode pNode = new PropertyNode(fieldNode, fieldNode.getModifiers(), returnS(varX(fieldNode.getName())), null);
        propertiesToReplace.add(pNode);
    }

    private void adjustPropertyAccessorsForSingleField(PropertyNode pNode) {
        if (dslastTransformation.shouldFieldBeIgnored(pNode.getField()))
            return;

        if (getFieldType(pNode.getField()) == FieldType.BUILDER) {
            pNode.getField().setModifiers(pNode.getField().getModifiers() & Opcodes.ACC_PROTECTED & ~Opcodes.ACC_PUBLIC);
            if (pNode.isPublic()) {
                dslastTransformation.annotatedClass.getProperties().remove(pNode);
                pNode = new PropertyNode(
                        pNode.getField(),
                        pNode.getModifiers() & ~Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED,
                        pNode.getGetterBlock(),
                        pNode.getSetterBlock()
                );
            }
        }

        String fieldName = pNode.getName();
        ClassNode fieldType = pNode.getType();

        String getterName = getGetterName(fieldName);
        String setterName = DslAstHelper.getSetterName(fieldName);
        String rwSetterName = setterName + "$rw";

        pNode.setGetterBlock(stmt(
                callX(
                        varX(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS),
                        "getInstanceProperty",
                        args(constX(fieldName))
                )
        ));

        createPublicMethod(getterName)
                .returning(fieldType)
                .doReturn(callX(
                        varX(KlumInstanceProxy.NAME_OF_PROXY_FIELD_IN_MODEL_CLASS),
                        "getInstanceAttribute",
                        args(constX(fieldName)))
                )
                .addTo(dslastTransformation.rwClass);

        createProtectedMethod(rwSetterName)
                .mod(Opcodes.ACC_SYNTHETIC)
                .returning(ClassHelper.VOID_TYPE)
                .param(fieldType, "value")
                .statement(assignS(attrX(varX("this"), constX(fieldName)), varX("value")))
                .addTo(dslastTransformation.annotatedClass);

        createMethod(setterName)
                .mod(DslAstHelper.isProtected(pNode.getField()) ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC)
                .returning(ClassHelper.VOID_TYPE)
                .param(fieldType, "value")
                .statement(callX(varX(KlumInstanceProxy.NAME_OF_MODEL_FIELD_IN_RW_CLASS), rwSetterName, args("value")))
                .addTo(dslastTransformation.rwClass);

        pNode.setSetterBlock(null);

        propertiesToReplace.add(pNode);
    }

    private void setAccessorsForOwnerFields() {
        dslastTransformation.ownerFields.forEach(this::setAccessorsForSpecialField);
    }

    private void setAccessorsForSpecialField(FieldNode fieldNode) {
        String fieldName = fieldNode.getName();
        PropertyNode property = dslastTransformation.annotatedClass.getProperty(fieldName);

        if (property != null) {
            property.setSetterBlock(null);
            property.setGetterBlock(stmt(attrX(varX("this"), constX(fieldName))));
            propertiesToReplace.add(property);
        }

        String ownerGetter = getGetterName(fieldName);
        createPublicMethod(ownerGetter)
                .returning(fieldNode.getType())
                .doReturn(callX(varX(KlumInstanceProxy.NAME_OF_MODEL_FIELD_IN_RW_CLASS), ownerGetter))
                .addTo(dslastTransformation.rwClass);

    }

}
