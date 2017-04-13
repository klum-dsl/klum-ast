package com.blackbuild.groovy.configdsl.transform.ast.mutators;

import com.blackbuild.groovy.configdsl.transform.ast.DSLASTTransformation;
import com.blackbuild.groovy.configdsl.transform.ast.MutatorsHandler;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.stc.StaticTypeCheckingVisitor;

import java.util.ArrayList;
import java.util.List;

import static org.codehaus.groovy.syntax.Types.*;

/**
 * Created by stephan on 12.04.2017.
 */
public class ModelVerificationVisitor extends StaticTypeCheckingVisitor {
    public ModelVerificationVisitor(SourceUnit unit, ClassNode node) {
        super(unit, node);
    }


    @Override
    public void visitBinaryExpression(BinaryExpression expression) {
        super.visitBinaryExpression(expression);

        if (typeCheckingContext.getEnclosingClassNode().getName().endsWith(DSLASTTransformation.RW_CLASS_SUFFIX))
            return; // don't validate RW class methods

        MethodNode currentMethod = typeCheckingContext.getEnclosingMethod();

        if (!currentMethod.isPublic())
            return; // check only public methods for now
        if (currentMethod.isStatic())
            return; // ignore factory methods
        if ((currentMethod.getModifiers() & Opcodes.ACC_SYNTHETIC) != 0)
            return;
        if (!currentMethod.getAnnotations(MutatorsHandler.MUTATOR_ANNOTATION).isEmpty())
            return;

        if (ofType(expression.getOperation().getType(), ASSIGNMENT_OPERATOR)) {
            for (VariableExpression target : getLeftMostTargets(expression.getLeftExpression())) {
                if (target.isThisExpression() || target.getAccessedVariable() instanceof FieldNode)
                    addError(String.format("Assigning a value to a an element of a model is only allowed in Mutator methods: %s. Maybe you forgot to annotate %s with @Mutator?", expression.getText(), currentMethod.toString()), expression);
            }
        }
    }

    private List<VariableExpression> getLeftMostTargets(Expression expression) {
        return addLeftMostTargetToList(expression, new ArrayList<VariableExpression>());
    }

    private List<VariableExpression> addLeftMostTargetToList(Expression target, List<VariableExpression> list) {
        if (target instanceof VariableExpression)
            list.add(((VariableExpression) target));

        else if (target instanceof MethodCallExpression)
            addLeftMostTargetToList(((MethodCallExpression) target).getObjectExpression(), list);

        else if (target instanceof PropertyExpression)
            addLeftMostTargetToList(((PropertyExpression) target).getObjectExpression(), list);

        else if (target instanceof BinaryExpression && ((BinaryExpression) target).getOperation().getType() == LEFT_SQUARE_BRACKET)
            addLeftMostTargetToList(((BinaryExpression) target).getLeftExpression(), list);

        else if (target instanceof CastExpression)
            addLeftMostTargetToList(((CastExpression) target).getExpression(), list);

        else if (target instanceof TupleExpression) {
            for (Expression value : (TupleExpression) target) {
                addLeftMostTargetToList(value, list);
            }
        }
        else {
            // Ignore ?
            //addError("Unknown expression found as left side of BinaryExpression: " + target.toString(), target);
        }

        return list;
    }

}
