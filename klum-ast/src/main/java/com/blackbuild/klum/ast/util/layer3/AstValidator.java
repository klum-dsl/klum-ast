package com.blackbuild.klum.ast.util.layer3;

import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.klum.ast.validation.AllowedMembersForClass;
import com.blackbuild.klum.ast.validation.AllowedMembersForField;
import com.blackbuild.klum.ast.validation.AllowedMembersForMethod;
import com.blackbuild.klum.ast.validation.NeedsDslClass;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.AbstractASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

@GroovyASTTransformation(phase = CompilePhase.SEMANTIC_ANALYSIS)
public class AstValidator extends AbstractASTTransformation {

    protected static final ClassNode DSL_CN = ClassHelper.make(DSL.class);

    protected AnnotationNode annotation;
    protected AnnotatedNode target;

    protected ClassNode targetClass;

    protected Class<? extends Annotation> annotationClass;

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        init(nodes, source);
        annotation = (AnnotationNode) nodes[0];
        //noinspection unchecked
        annotationClass = annotation.getClassNode().getTypeClass();
        target = (AnnotatedNode) nodes[1];
        targetClass = target instanceof ClassNode ? (ClassNode) target : target.getDeclaringClass();

        validateAnnotation();
    }

    protected void validateAnnotation() {
        validateDslNeeded();
        if (target instanceof MethodNode) validateMethodMembers();
        else if (target instanceof ClassNode) validateClassMembers();
        else if (target instanceof FieldNode) validateFieldMembers();
        extraValidation();
    }

    protected void extraValidation() {
        // hook method
    }

    private void validateFieldMembers() {
        AllowedMembersForField allowedMembers = annotationClass.getAnnotation(AllowedMembersForField.class);
        if (allowedMembers == null) return;
        if (allowedMembers.invert())
            assertAnnotationHasNoMembersFrom(allowedMembers.value());
        else
            assertAnnotationOnlyHasMembersFrom(allowedMembers.value());
    }

    private void validateClassMembers() {
        AllowedMembersForClass allowedMembers = annotationClass.getAnnotation(AllowedMembersForClass.class);
        if (allowedMembers == null) return;
        if (allowedMembers.invert())
            assertAnnotationHasNoMembersFrom(allowedMembers.value());
        else
            assertAnnotationOnlyHasMembersFrom(allowedMembers.value());
    }

    private void validateMethodMembers() {
        AllowedMembersForMethod allowedMembers = annotationClass.getAnnotation(AllowedMembersForMethod.class);
        if (allowedMembers == null) return;
        if (allowedMembers.invert())
            assertAnnotationHasNoMembersFrom(allowedMembers.value());
        else
            assertAnnotationOnlyHasMembersFrom(allowedMembers.value());
    }

    private void assertAnnotationOnlyHasMembersFrom(String[] value) {
        Set<String> allowedMembers = Arrays.stream(value).collect(Collectors.toSet());
        Set<String> existingMembers = annotation.getMembers().keySet();

        if (allowedMembers.containsAll(existingMembers)) return;

        HashSet<String> forbiddenMembers = new HashSet<>(existingMembers);
        forbiddenMembers.removeAll(allowedMembers);

        addError(format(
                "Annotation %s has members which are not allowed when placed on a %s (%s)",
                annotation.getClassNode().getNameWithoutPackage(),
                target.getClass().getSimpleName(),
                forbiddenMembers
                ),
                annotation);
    }

    private void assertAnnotationHasNoMembersFrom(String[] value) {
        Set<String> forbiddenMembers = Arrays.stream(value).collect(Collectors.toSet());
        Set<String> existingMembers = annotation.getMembers().keySet();

        forbiddenMembers.retainAll(existingMembers);
        if (forbiddenMembers.isEmpty()) return;

        addError(format(
                        "Annotation %s has members which are not allowed when placed on a %s (%s)",
                        annotation.getClassNode().getNameWithoutPackage(),
                        target.getClass().getSimpleName(),
                        forbiddenMembers
                ),
                annotation);
    }

    private void validateDslNeeded() {
        if (!annotationClass.isAnnotationPresent(NeedsDslClass.class)) return;

        if (!hasAnnotation(targetClass, DSL_CN)) {
            addError("Annotation " + annotationClass.getName() + " can only be used on classes annotated with " + DSL.class.getName(), target);
        }
    }
}
