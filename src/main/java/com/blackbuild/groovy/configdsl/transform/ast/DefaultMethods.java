package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.groovy.configdsl.transform.Default;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.*;
import org.jetbrains.annotations.Nullable;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.getAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createPublicMethod;
import static java.lang.Character.toUpperCase;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;
import static org.codehaus.groovy.transform.AbstractASTTransformation.getMemberStringValue;


/**
 * Helper class for default values.
 */
public class DefaultMethods {
    private DSLASTTransformation transformation;

    static final ClassNode DEFAULT_ANNOTATION = make(Default.class);


    public DefaultMethods(DSLASTTransformation dslastTransformation) {
        transformation = dslastTransformation;
    }


    public void execute() {

        for (FieldNode fieldNode : transformation.annotatedClass.getFields()) {
            AnnotationNode defaultAnnotation = getAnnotation(fieldNode, DEFAULT_ANNOTATION);

            if (defaultAnnotation != null)
                createDefaultValueFor(fieldNode, defaultAnnotation);
        }
    }

    private void createDefaultValueFor(FieldNode fieldNode, AnnotationNode defaultAnnotation) {
        assertOnlyOneMemberOfAnnotationIsSet(defaultAnnotation);

        String fieldMember = getMemberStringValue(defaultAnnotation, "value");

        if (fieldMember == null)
            fieldMember = getMemberStringValue(defaultAnnotation, "field");

        if (fieldMember != null) {
            createFieldMethod(fieldNode, fieldMember);
            return;
        }

        ClosureExpression code = getCodeClosureFor(defaultAnnotation);
        if (code != null) {
            createClosureMethod(fieldNode, code);
            return;
        }

        String delegateMember = getMemberStringValue(defaultAnnotation, "delegate");
        if (delegateMember != null) {
            createDelegateMethod(fieldNode, delegateMember);
        }
    }

    private void createDelegateMethod(FieldNode fieldNode, String delegate) {
        String ownGetter = getGetterName(fieldNode.getName());
        String delegateGetter = getGetterName(delegate);

        createPublicMethod(ownGetter)
                .returning(fieldNode.getType())
                .statement(new ElvisOperatorExpression(varX(fieldNode.getName()), new PropertyExpression(callThisX(delegateGetter), new ConstantExpression(fieldNode.getName()), true)))
                .addTo(transformation.annotatedClass);
    }

    private void createClosureMethod(FieldNode fieldNode, ClosureExpression code) {
        String ownGetter = getGetterName(fieldNode.getName());

        createPublicMethod(ownGetter)
                .returning(fieldNode.getType())
                .declareVariable("closure", code)
                .assignS(propX(varX("closure"), "delegate"), varX("this"))
                .assignS(
                        propX(varX("closure"), "resolveStrategy"),
                        propX(classX(ClassHelper.CLOSURE_TYPE), "DELEGATE_FIRST")
                )
                .statement(new ElvisOperatorExpression(varX(fieldNode.getName()), callX(varX("closure"), "call")))
                .addTo(transformation.annotatedClass);
    }

    private void createFieldMethod(FieldNode fieldNode, String targetField) {
        String ownGetter = getGetterName(fieldNode.getName());
        String fieldGetter = getGetterName(targetField);

        createPublicMethod(ownGetter)
                .returning(fieldNode.getType())
                .statement(new ElvisOperatorExpression(varX(fieldNode.getName()), callThisX(fieldGetter)))
                .addTo(transformation.annotatedClass);
    }

    private String getGetterName(String property) {
        return "get" + toUpperCase(property.toCharArray()[0]) + property.substring(1);
    }


    private void assertOnlyOneMemberOfAnnotationIsSet(AnnotationNode annotationNode) {
        int numberOfMembers = annotationNode.getMembers().size();

        if (numberOfMembers == 0)
            transformation.addError("You must define either delegate, code or field for @Default annotations", annotationNode);

        if (numberOfMembers > 1)
            transformation.addError("Only one member for @Default annotation is allowed!", annotationNode);
    }

    @Nullable
    private ClosureExpression getCodeClosureFor(AnnotationNode defaultAnnotation) {
        Expression codeExpression = defaultAnnotation.getMember("code");
        if (codeExpression == null)
            return null;
        if (codeExpression instanceof ClosureExpression)
            return (ClosureExpression) codeExpression;

        transformation.addError("Illegal value for code, only None.class or a closure is allowed.", defaultAnnotation);
        return null;
    }


}
