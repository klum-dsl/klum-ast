package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.klum.ast.util.KlumInstanceProxy;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.PropertyNode;

import java.util.ArrayList;
import java.util.List;

import static com.blackbuild.groovy.configdsl.transform.ast.DslAstHelper.getGetterName;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createProtectedMethod;
import static com.blackbuild.groovy.configdsl.transform.ast.DslMethodBuilder.createPublicMethod;
import static com.blackbuild.klum.common.CommonAstHelper.replaceProperties;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.assignS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.attrX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.getInstanceProperties;
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt;
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX;

class PropertyAccessors {
    private final DSLASTTransformation dslastTransformation;
    private final List<PropertyNode> propertiesToReplace = new ArrayList<>();

    public PropertyAccessors(DSLASTTransformation dslastTransformation) {
        this.dslastTransformation = dslastTransformation;
    }

    public void invoke() {
        getInstanceProperties(dslastTransformation.annotatedClass).forEach(this::adjustPropertyAccessorsForSingleField);

        setAccessorsForOwnerFields();

        if (dslastTransformation.keyField != null)
            setAccessorsForKeyField();

        replaceProperties(dslastTransformation.annotatedClass, propertiesToReplace);
    }

    private void adjustPropertyAccessorsForSingleField(PropertyNode pNode) {
        if (dslastTransformation.shouldFieldBeIgnored(pNode.getField()))
            return;

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

        // TODO what about protected methods?
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
                .statement(callX(varX(DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS), rwSetterName, args("value")))
                .addTo(dslastTransformation.rwClass);

        pNode.setSetterBlock(null);
        propertiesToReplace.add(pNode);
    }

    private void setAccessorsForOwnerFields() {
        dslastTransformation.ownerFields.forEach(this::setAccessorsForOwnerField);
    }

    private void setAccessorsForKeyField() {
        String keyGetter = getGetterName(dslastTransformation.keyField.getName());
        createPublicMethod(keyGetter)
                .returning(dslastTransformation.keyField.getType())
                .doReturn(callX(varX(DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS), keyGetter))
                .addTo(dslastTransformation.rwClass);
    }

    private void setAccessorsForOwnerField(FieldNode ownerField) {
        String ownerFieldName = ownerField.getName();
        PropertyNode ownerProperty = dslastTransformation.annotatedClass.getProperty(ownerFieldName);
        ownerProperty.setSetterBlock(null);
        ownerProperty.setGetterBlock(stmt(attrX(varX("this"), constX(ownerFieldName))));

        String ownerGetter = getGetterName(ownerFieldName);
        createPublicMethod(ownerGetter)
                .returning(ownerField.getType())
                .doReturn(callX(varX(DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS), ownerGetter))
                .addTo(dslastTransformation.rwClass);

        propertiesToReplace.add(ownerProperty);
    }

}
