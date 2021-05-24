package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.klum.common.CommonAstHelper;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.tools.GeneralUtils;
import org.codehaus.groovy.classgen.Verifier;

import java.util.ArrayList;
import java.util.List;

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
    private List<PropertyNode> newNodes;

    public PropertyAccessors(DSLASTTransformation dslastTransformation) {
        this.dslastTransformation = dslastTransformation;
    }

    public void invoke() {
        newNodes = new ArrayList<>();
        for (PropertyNode pNode : getInstanceProperties(dslastTransformation.annotatedClass)) {
            adjustPropertyAccessorsForSingleField(pNode);
        }

        setAccessorsForOwnerFields();

        if (dslastTransformation.keyField != null)
            setAccessorsForKeyField();

        replaceProperties(dslastTransformation.annotatedClass, newNodes);
    }

    private void adjustPropertyAccessorsForSingleField(PropertyNode pNode) {
        if (dslastTransformation.shouldFieldBeIgnored(pNode.getField()))
            return;

        String capitalizedFieldName = Verifier.capitalize(pNode.getName());
        String getterName = "get" + capitalizedFieldName;
        String setterName = "set" + capitalizedFieldName;
        String rwGetterName;
        String rwSetterName = setterName + "$rw";

        if (CommonAstHelper.isCollectionOrMap(pNode.getType())) {
            rwGetterName = getterName + "$rw";

            pNode.setGetterBlock(stmt(callX(attrX(varX("this"), constX(pNode.getName())), "asImmutable")));

            createProtectedMethod(rwGetterName)
                    .mod(Opcodes.ACC_SYNTHETIC)
                    .returning(pNode.getType())
                    .doReturn(attrX(varX("this"), constX(pNode.getName())))
                    .addTo(dslastTransformation.annotatedClass);
        } else {
            rwGetterName = "get" + capitalizedFieldName;
            pNode.setGetterBlock(stmt(attrX(varX("this"), constX(pNode.getName()))));
        }

        // TODO what about protected methods?
        createPublicMethod(getterName)
                .returning(pNode.getType())
                .doReturn(callX(GeneralUtils.varX(DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS), rwGetterName))
                .addTo(dslastTransformation.rwClass);

        createProtectedMethod(rwSetterName)
                .mod(Opcodes.ACC_SYNTHETIC)
                .returning(ClassHelper.VOID_TYPE)
                .param(pNode.getType(), "value")
                .statement(assignS(attrX(varX("this"), constX(pNode.getName())), varX("value")))
                .addTo(dslastTransformation.annotatedClass);

        createMethod(setterName)
                .mod(DSLASTTransformation.isProtected(pNode.getField()) ? Opcodes.ACC_PROTECTED : Opcodes.ACC_PUBLIC)
                .returning(ClassHelper.VOID_TYPE)
                .param(pNode.getType(), "value")
                .statement(callX(GeneralUtils.varX(DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS), rwSetterName, args("value")))
                .addTo(dslastTransformation.rwClass);

        pNode.setSetterBlock(null);
        newNodes.add(pNode);
    }

    private void setAccessorsForOwnerFields() {
        for (FieldNode ownerField : dslastTransformation.ownerFields)
            if (ownerField.getOwner() == dslastTransformation.annotatedClass)
                newNodes.add(setAccessorsForOwnerField(ownerField));
    }

    private void setAccessorsForKeyField() {
        String keyGetter = "get" + Verifier.capitalize(dslastTransformation.keyField.getName());
        createPublicMethod(keyGetter)
                .returning(dslastTransformation.keyField.getType())
                .doReturn(callX(GeneralUtils.varX(DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS), keyGetter))
                .addTo(dslastTransformation.rwClass);
    }

    private PropertyNode setAccessorsForOwnerField(FieldNode ownerField) {
        String ownerFieldName = ownerField.getName();
        PropertyNode ownerProperty = dslastTransformation.annotatedClass.getProperty(ownerFieldName);
        ownerProperty.setSetterBlock(null);
        ownerProperty.setGetterBlock(stmt(attrX(varX("this"), constX(ownerFieldName))));

        String ownerGetter = "get" + Verifier.capitalize(ownerFieldName);
        createPublicMethod(ownerGetter)
                .returning(ownerField.getType())
                .doReturn(callX(GeneralUtils.varX(DSLASTTransformation.NAME_OF_MODEL_FIELD_IN_RW_CLASS), ownerGetter))
                .addTo(dslastTransformation.rwClass);

        return ownerProperty;
    }

}
