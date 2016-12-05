package com.blackbuild.groovy.configdsl.transform.ast;

import com.blackbuild.groovy.configdsl.transform.Default;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.ElvisOperatorExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.jetbrains.annotations.Nullable;

import static com.blackbuild.groovy.configdsl.transform.ast.ASTHelper.getAnnotation;
import static com.blackbuild.groovy.configdsl.transform.ast.MethodBuilder.createPublicMethod;
import static java.lang.Character.toUpperCase;
import static org.codehaus.groovy.ast.ClassHelper.make;
import static org.codehaus.groovy.transform.AbstractASTTransformation.getMemberStringValue;
import static org.codehaus.groovy.ast.tools.GeneralUtils.*;


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

        String delegate = getMemberStringValue(defaultAnnotation, "value", "");
        ClosureExpression code = getCodeClosureFor(fieldNode, defaultAnnotation);
        assertOnlyDelegateOrCodeClosureIsSet(fieldNode, delegate, code);

        if (!delegate.isEmpty())
            createDelegateMethod(fieldNode, delegate);
        else
            createClosureMethod(fieldNode, code);
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

    private void createDelegateMethod(FieldNode fieldNode, String delegate) {
        String ownGetter = getGetterName(fieldNode.getName());
        String delegateGetter = getGetterName(delegate);

        createPublicMethod(ownGetter)
                .returning(fieldNode.getType())
                .statement(new ElvisOperatorExpression(varX(fieldNode.getName()), callThisX(delegateGetter)))
                .addTo(transformation.annotatedClass);
    }

    private String getGetterName(String property) {
        return "get" + toUpperCase(property.toCharArray()[0]) + property.substring(1);
    }


    private void assertOnlyDelegateOrCodeClosureIsSet(FieldNode fieldNode, String delegate, ClosureExpression code) {
        if (!delegate.isEmpty() && code != null)
            transformation.addError("Only either a default property or a closure is allowed, not both!", fieldNode);
    }

    @Nullable
    private ClosureExpression getCodeClosureFor(FieldNode fieldNode, AnnotationNode defaultAnnotation) {
        Expression codeExpression = defaultAnnotation.getMember("code");
        ClosureExpression code;
        if (codeExpression == null)
            return null;
        if (codeExpression instanceof ClassExpression) {
            code = null;
        } else if (codeExpression instanceof ClosureExpression){
            code = (ClosureExpression) codeExpression;
        } else {
            transformation.addError("Illegal value for code, only None.class or a closure is allowed.", fieldNode);
            code = null;
        }
        return code;
    }


}
