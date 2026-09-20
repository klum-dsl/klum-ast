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

import com.blackbuild.annodocimal.ast.AstDocumentation;
import com.blackbuild.annodocimal.ast.Documentation;
import com.blackbuild.klum.ast.Builder;
import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.runtime.KlumBuilder;
import com.blackbuild.klum.ast.compiler.internal.ast.mutators.WriteAccessHelper;
import com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper;
import groovyjarjarasm.asm.Opcodes;
import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.CodeVisitorSupport;
import org.codehaus.groovy.ast.DynamicVariable;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.Parameter;
import org.codehaus.groovy.ast.Variable;
import org.codehaus.groovy.ast.expr.BinaryExpression;
import org.codehaus.groovy.ast.expr.ClassExpression;
import org.codehaus.groovy.ast.expr.ClosureExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.MapExpression;
import org.codehaus.groovy.ast.expr.MethodCallExpression;
import org.codehaus.groovy.ast.expr.PostfixExpression;
import org.codehaus.groovy.ast.expr.PrefixExpression;
import org.codehaus.groovy.ast.expr.PropertyExpression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.syntax.Types;

import java.beans.Introspector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.copyAnnotationsFromSourceToTarget;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.createGeneratedAnnotation;
import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.isDSLObject;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isAssignableTo;
import static org.codehaus.groovy.ast.tools.GeneralUtils.args;
import static org.codehaus.groovy.ast.tools.GeneralUtils.block;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callThisX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.castX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.ifS;
import static org.codehaus.groovy.ast.tools.GeneralUtils.notNullX;
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS;

/** Projects explicitly selected pure Model queries onto the hidden and public Builder contracts. */
final class BuilderQuerySupport {

    private static final ClassNode BUILDER_QUERY = ClassHelper.make(Builder.Query.class);
    private static final ClassNode KLUM_BUILDER = ClassHelper.make(KlumBuilder.class);
    private static final Set<String> CONSTRUCTION_FACTORY_METHODS = Set.of(
            "AsBuilder", "From", "FromMap", "One", "With");
    private static final String TWIN_METADATA_KEY = BuilderQuerySupport.class.getName() + ".twin";
    private final ClassNode model;
    private final ClassNode builder;
    private final SourceUnit sourceUnit;

    BuilderQuerySupport(ClassNode model, ClassNode builder, SourceUnit sourceUnit) {
        this.model = model;
        this.builder = builder;
        this.sourceUnit = sourceUnit;
    }

    void invoke() {
        List<MethodNode> queries = new ArrayList<>(model.getMethods()).stream()
                .filter(method -> method.getDeclaringClass().redirect().equals(model.redirect()))
                .filter(BuilderQuerySupport::isBuilderQuery)
                .toList();

        Map<MethodNode, MethodNode> twins = new IdentityHashMap<>();
        queries.forEach(query -> projectQuery(query, twins));

        twins.forEach((query, twin) -> {
            Statement projectedBody = new QueryBodyTransformer(query, twin).cloneStatement(query.getCode());
            twin.setCode(hasBuilderInput(query)
                    ? projectedBody
                    : block(
                            ifS(
                                    notNullX(callThisX("$klum$completedModelOrNull")),
                                    returnS(callX(
                                            castX(model.getPlainNodeReference(), callThisX("$klum$completedModelOrNull")),
                                            query.getName(),
                                            args(twin.getParameters())
                                    ))
                            ),
                            projectedBody
                    ));
        });
    }

    private void projectQuery(MethodNode query, Map<MethodNode, MethodNode> twins) {
        if (!isProjectableDeclaration(query) || BuilderMethodProjection.hasBuilderResult(query)) return;
        validateResult(query);
        if (query.getCode() != null) query.getCode().visit(new PurityVisitor(query));
        Parameter[] projectedParameters = BuilderMethodProjection.projectedParameters(query, model);
        if (hasBuilderCollision(query, projectedParameters)) {
            error(query, String.format(
                    "@Builder.Query %s collides with an existing Builder method; rename the query or the Builder operation",
                    signature(query)));
            return;
        }
        MethodNode twin = createTwin(query, projectedParameters);
        twins.put(query, twin);
        query.setNodeMetaData(TWIN_METADATA_KEY, twin);
        builder.addMethod(twin);
    }

    static boolean isBuilderQuery(MethodNode method) {
        return !method.getAnnotations(BUILDER_QUERY).isEmpty();
    }

    private static boolean hasBuilderInput(MethodNode method) {
        return Arrays.stream(method.getParameters()).anyMatch(BuilderMethodProjection::isBuilderInput);
    }

    private boolean isProjectableDeclaration(MethodNode method) {
        return method.isPublic()
                && !method.isStatic()
                && !method.isAbstract()
                && !method.isVoidMethod()
                && WriteAccessHelper.getWriteAccessTypeForMethodOrField(method).isEmpty();
    }

    private void validateResult(MethodNode method) {
        if (containsDslOrBuilder(method.getReturnType()))
            error(method, String.format(
                    "@Builder.Query result must not contain a DSL Object or Builder type: %s returns %s",
                    signature(method), method.getReturnType().toString(false)));
    }

    private boolean containsDslOrBuilder(ClassNode type) {
        if (type == null) return false;
        if (isDSLObject(type) || isAssignableTo(type, KLUM_BUILDER)) return true;
        if (type.isArray()) return containsDslOrBuilder(type.getComponentType());
        GenericsType[] generics = type.getGenericsTypes();
        if (generics == null) return false;
        for (GenericsType generic : generics) {
            if (containsDslOrBuilder(generic.getLowerBound())) return true;
            ClassNode[] upperBounds = generic.getUpperBounds();
            if (upperBounds != null && Arrays.stream(upperBounds).anyMatch(this::containsDslOrBuilder)) return true;
            if (containsDslOrBuilder(generic.getType())) return true;
        }
        return false;
    }

    private boolean hasBuilderCollision(MethodNode query, Parameter[] projectedParameters) {
        if (builder.getDeclaredMethod(query.getName(), projectedParameters) != null) return true;
        return model.getMethods().stream()
                .filter(method -> method != query)
                .filter(method -> method.getDeclaringClass().redirect().equals(model.redirect()))
                .filter(method -> method.getName().equals(query.getName()))
                .filter(method -> parametersMatch(method.getParameters(), projectedParameters))
                .anyMatch(method -> WriteAccessHelper.getWriteAccessTypeForMethodOrField(method).isPresent());
    }

    private boolean parametersMatch(Parameter[] left, Parameter[] right) {
        if (left.length != right.length) return false;
        for (int index = 0; index < left.length; index++)
            if (!left[index].getType().redirect().equals(right[index].getType().redirect())) return false;
        return true;
    }

    private boolean parametersMatch(MethodNode left, MethodNode right) {
        if (left.getParameters().length != right.getParameters().length) return false;
        for (int index = 0; index < left.getParameters().length; index++)
            if (!left.getParameters()[index].getType().redirect().equals(right.getParameters()[index].getType().redirect()))
                return false;
        return true;
    }

    private MethodNode createTwin(MethodNode source, Parameter[] parameters) {
        int modifiers = (source.getModifiers() & (Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL
                | Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_STRICT)) | Opcodes.ACC_PUBLIC;
        MethodNode twin = new MethodNode(
                source.getName(),
                modifiers,
                source.getReturnType(),
                parameters,
                source.getExceptions(),
                null
        );
        twin.setGenericsTypes(source.getGenericsTypes());
        twin.setSourcePosition(source);
        copyAnnotationsFromSourceToTarget(source, twin, Collections.emptyList());
        twin.addAnnotation(createGeneratedAnnotation(BuilderQuerySupport.class));

        Documentation sourceDocumentation = AstDocumentation.extractExact(source).orElse(Documentation.empty());
        KlumDocumentation documentation = new KlumDocumentation().replace(sourceDocumentation)
                .p(hasBuilderInput(source)
                        ? "This projection evaluates the query against Builder state; explicitly marked inputs use exact generated Builder types."
                        : "This projection evaluates the query against the current Builder state before materialization.");
        AstDocumentation.attach(twin, documentation.rendered());
        return twin;
    }

    private String signature(MethodNode method) {
        return method.getName() + "(" + Arrays.stream(method.getParameters())
                .map(parameter -> parameter.getType().getName())
                .reduce((left, right) -> left + ", " + right)
                .orElse("") + ")";
    }

    private void error(ASTNode node, String message) {
        CommonAstHelper.addCompileError(sourceUnit, message, node);
    }

    private final class PurityVisitor extends CodeVisitorSupport {
        private final MethodNode query;

        private PurityVisitor(MethodNode query) {
            this.query = query;
        }

        @Override
        public void visitBinaryExpression(BinaryExpression expression) {
            if (Types.ofType(expression.getOperation().getType(), Types.ASSIGNMENT_OPERATOR)
                    && targetsDslField(expression.getLeftExpression()))
                error(expression, String.format("@Builder.Query %s must not assign DSL Object fields", shortSignature()));
            super.visitBinaryExpression(expression);
        }

        @Override
        public void visitPrefixExpression(PrefixExpression expression) {
            rejectDslFieldMutation(expression, expression.getExpression());
            super.visitPrefixExpression(expression);
        }

        @Override
        public void visitPostfixExpression(PostfixExpression expression) {
            rejectDslFieldMutation(expression, expression.getExpression());
            super.visitPostfixExpression(expression);
        }

        @Override
        public void visitVariableExpression(VariableExpression expression) {
            rejectBuilderOnlyRead(expression, modelField(expression));
            super.visitVariableExpression(expression);
        }

        @Override
        public void visitPropertyExpression(PropertyExpression expression) {
            rejectBuilderOnlyRead(expression, modelField(expression));
            super.visitPropertyExpression(expression);
        }

        @Override
        public void visitMethodCallExpression(MethodCallExpression call) {
            validateConstructionCall(call);
            validateDslCall(
                    call,
                    receiverModel(call.getObjectExpression()),
                    call.getMethodAsString(),
                    call.getArguments(),
                    call.getMethodTarget());
            super.visitMethodCallExpression(call);
        }

        private void validateConstructionCall(MethodCallExpression call) {
            if (!CONSTRUCTION_FACTORY_METHODS.contains(call.getMethodAsString())
                    || !isDslFactoryExpression(call.getObjectExpression()))
                return;
            error(call, String.format("@Builder.Query %s must not start or attach construction", shortSignature()));
        }

        private boolean isDslFactoryExpression(Expression expression) {
            if (!(expression instanceof PropertyExpression property)) return false;
            if (DSLASTTransformation.FACTORY_FIELD_NAME.equals(property.getPropertyAsString())
                    && property.getObjectExpression() instanceof ClassExpression owner
                    && isDSLObject(owner.getType()))
                return true;
            return isDslFactoryExpression(property.getObjectExpression());
        }

        private void validateDslCall(
                ASTNode call,
                ClassNode receiver,
                String name,
                Expression arguments,
                MethodNode target) {
            if (receiver == null || name == null || !isDSLObject(receiver)) return;
            FieldNode readField = fieldForGetter(receiver, name, arguments);
            if (readField != null) {
                rejectBuilderOnlyRead(call, readField);
                return;
            }

            MethodSelection selection = target != null
                    ? new MethodSelection(target, false)
                    : selectMethod(receiver, name, arguments);
            if (selection.ambiguous) {
                error(call, String.format(
                        "@Builder.Query %s cannot resolve overloaded DSL method '%s.%s()' unambiguously",
                        shortSignature(), receiver.getNameWithoutPackage(), name));
                return;
            }
            MethodNode selected = selection.method;
            if (selected != null && WriteAccessHelper.getWriteAccessTypeForMethodOrField(selected).isPresent()) {
                error(call, String.format(
                        "@Builder.Query %s must not call construction-time method %s()",
                        shortSignature(), name));
                return;
            }
            if (selected != null && isBuilderQuery(selected)) return;
            ClassNode methodOwner = selected == null ? receiver : selected.getDeclaringClass();
            error(call, String.format(
                    "@Builder.Query %s cannot call unprojected DSL method '%s.%s()'; annotate the source query with @Builder.Query and recompile that DSL Object",
                    shortSignature(), methodOwner.getNameWithoutPackage(), name));
        }

        private MethodSelection selectMethod(ClassNode receiver, String name, Expression arguments) {
            List<MethodNode> candidates = new ArrayList<>();
            receiver.getMethods(name).forEach(method -> addCandidate(candidates, method));
            ClassNode receiverBuilder = receiver.redirect().getNodeMetaData(DSLASTTransformation.BUILDER_CLASS_METADATA_KEY);
            if (receiverBuilder != null)
                receiverBuilder.getMethods(name).forEach(method -> addCandidate(candidates, method));
            return selectMethod(candidates, arguments);
        }

        private void addCandidate(List<MethodNode> candidates, MethodNode candidate) {
            MethodNode duplicate = candidates.stream()
                    .filter(method -> parametersMatch(method, candidate))
                    .findFirst()
                    .orElse(null);
            if (duplicate == null) {
                candidates.add(candidate);
            } else if (duplicate.isSynthetic() && !candidate.isSynthetic()) {
                candidates.remove(duplicate);
                candidates.add(candidate);
            }
        }

        private MethodSelection selectMethod(List<MethodNode> candidates, Expression arguments) {
            int argumentCount = argumentCount(arguments);
            List<MethodNode> byArity = candidates.stream()
                    .filter(method -> acceptsArgumentCount(method, argumentCount))
                    .toList();
            if (byArity.size() == 1) return new MethodSelection(byArity.get(0), false);

            List<MethodNode> compatible = byArity.stream()
                    .filter(method -> argumentsMatch(method.getParameters(), arguments))
                    .toList();
            if (compatible.size() == 1) return new MethodSelection(compatible.get(0), false);
            if (compatible.isEmpty() && byArity.isEmpty()) return new MethodSelection(null, false);
            return new MethodSelection(null, true);
        }

        private int argumentCount(Expression arguments) {
            return arguments instanceof TupleExpression tuple ? tuple.getExpressions().size() : 1;
        }

        private boolean acceptsArgumentCount(MethodNode method, int argumentCount) {
            int required = (int) Arrays.stream(method.getParameters())
                    .filter(parameter -> !parameter.hasInitialExpression())
                    .count();
            return argumentCount >= required && argumentCount <= method.getParameters().length;
        }

        private boolean argumentsMatch(Parameter[] parameters, Expression arguments) {
            if (!(arguments instanceof TupleExpression tuple)) return parameters.length == 1;
            List<Expression> expressions = tuple.getExpressions();
            if (parameters.length != expressions.size()) return false;
            for (int index = 0; index < parameters.length; index++) {
                Expression expression = expressions.get(index);
                ClassNode expected = parameters[index].getOriginType();
                if (expression instanceof MapExpression && !isAssignableOrEqual(ClassHelper.MAP_TYPE, expected))
                    return false;
                if (expression instanceof ClosureExpression && !isAssignableOrEqual(ClassHelper.CLOSURE_TYPE, expected))
                    return false;
                ClassNode actual = expression.getType();
                if (actual != null
                        && !actual.equals(ClassHelper.OBJECT_TYPE)
                        && !isAssignableOrEqual(actual, expected))
                    return false;
            }
            return true;
        }

        private boolean isAssignableOrEqual(ClassNode actual, ClassNode expected) {
            ClassNode boxedActual = ClassHelper.getWrapper(actual.redirect());
            ClassNode boxedExpected = ClassHelper.getWrapper(expected.redirect());
            return boxedActual.equals(boxedExpected) || isAssignableTo(boxedActual, boxedExpected);
        }

        private FieldNode fieldForGetter(ClassNode receiver, String name, Expression arguments) {
            if (argumentCount(arguments) != 0) return null;
            String fieldName;
            if (name.startsWith("get") && name.length() > 3)
                fieldName = Introspector.decapitalize(name.substring(3));
            else if (name.startsWith("is") && name.length() > 2)
                fieldName = Introspector.decapitalize(name.substring(2));
            else
                return null;
            boolean hasSourceGetter = receiver.getMethods(name).stream()
                    .filter(method -> method.getParameters().length == 0)
                    .anyMatch(method -> !method.isSynthetic());
            if (hasSourceGetter) return null;
            return receiver.getField(fieldName);
        }

        private ClassNode receiverModel(Expression expression) {
            if (expression == null) return model;
            if (expression instanceof VariableExpression variable) return receiverModel(variable);
            if (expression instanceof PropertyExpression property) return receiverModel(property);
            if (expression instanceof MethodCallExpression call) return receiverModel(call);
            if (expression instanceof ClassExpression owner) return owner.getType();
            return null;
        }

        private ClassNode receiverModel(VariableExpression variable) {
            if (variable.isThisExpression() || variable.isSuperExpression()) return model;
            FieldNode field = modelField(variable);
            if (field != null) return relationshipModel(field.getType());
            return relationshipModel(variable.getOriginType());
        }

        private ClassNode receiverModel(PropertyExpression property) {
            return relationshipModel(modelField(property));
        }

        private ClassNode receiverModel(MethodCallExpression call) {
            ClassNode receiver = receiverModel(call.getObjectExpression());
            if (receiver == null) return null;
            FieldNode field = fieldForGetter(receiver, call.getMethodAsString(), call.getArguments());
            return field == null ? null : relationshipModel(field.getType());
        }

        private void rejectDslFieldMutation(ASTNode operation, Expression target) {
            if (targetsDslField(target))
                error(operation, String.format("@Builder.Query %s must not assign DSL Object fields", shortSignature()));
        }

        private void rejectBuilderOnlyRead(ASTNode access, FieldNode field) {
            if (field != null && DslAstHelper.getFieldType(field) == FieldType.BUILDER)
                error(access, String.format(
                        "@Builder.Query %s must not read construction-only field '%s'",
                        shortSignature(), field.getName()));
        }

        private String shortSignature() {
            return query.getName() + "()";
        }

        private boolean targetsDslField(Expression expression) {
            if (expression instanceof VariableExpression variable)
                return modelField(variable) != null;
            if (expression instanceof PropertyExpression property) {
                Expression receiver = property.getObjectExpression();
                return receiver instanceof VariableExpression variable && variable.isThisExpression()
                        || targetsDslField(receiver);
            }
            if (expression instanceof BinaryExpression binary
                    && binary.getOperation().getType() == Types.LEFT_SQUARE_BRACKET)
                return targetsDslField(binary.getLeftExpression());
            return false;
        }

        private final class MethodSelection {
            private final MethodNode method;
            private final boolean ambiguous;

            private MethodSelection(MethodNode method, boolean ambiguous) {
                this.method = method;
                this.ambiguous = ambiguous;
            }

        }
    }

    private FieldNode modelField(VariableExpression expression) {
        Variable accessed = expression.getAccessedVariable();
        if (accessed instanceof FieldNode field && isFieldInModelHierarchy(field)) return field;
        if (accessed instanceof DynamicVariable || accessed == null) return model.getField(expression.getName());
        return null;
    }

    private FieldNode modelField(PropertyExpression expression) {
        ClassNode owner = expression.getObjectExpression() instanceof VariableExpression variable
                && (variable.isThisExpression() || variable.isSuperExpression())
                ? model
                : receiverModelForProperty(expression.getObjectExpression());
        return owner == null ? null : owner.getField(expression.getPropertyAsString());
    }

    private ClassNode receiverModelForProperty(Expression expression) {
        if (expression instanceof VariableExpression variable) {
            return relationshipModel(modelField(variable));
        }
        if (expression instanceof PropertyExpression property) {
            return relationshipModel(modelField(property));
        }
        return null;
    }

    private ClassNode relationshipModel(FieldNode field) {
        return field == null ? null : relationshipModel(field.getType());
    }

    private boolean isFieldInModelHierarchy(FieldNode field) {
        return field.getOwner().redirect().equals(model.redirect()) || model.isDerivedFrom(field.getOwner());
    }

    private ClassNode relationshipModel(ClassNode type) {
        if (type == null) return null;
        if (isDSLObject(type)) return type;
        GenericsType[] generics = type.getGenericsTypes();
        if (generics == null) return null;
        return Arrays.stream(generics)
                .map(GenericsType::getType)
                .filter(DslAstHelper::isDSLObject)
                .findFirst()
                .orElse(null);
    }

    private final class QueryBodyTransformer extends BuilderMethodProjection.StatementCloner {
        private final Map<Variable, Parameter> parameters = new IdentityHashMap<>();

        private QueryBodyTransformer(MethodNode source, MethodNode twin) {
            Parameter[] sourceParameters = source.getParameters();
            Parameter[] twinParameters = twin.getParameters();
            for (int index = 0; index < Math.min(sourceParameters.length, twinParameters.length); index++)
                parameters.put(sourceParameters[index], twinParameters[index]);
        }

        @Override
        protected Statement handleUnsupportedStatement(Statement source) {
            return source;
        }

        @Override
        public Expression transform(Expression expression) {
            if (expression == null) return null;
            if (expression instanceof ClosureExpression source) return cloneClosure(source);
            if (expression instanceof VariableExpression source) {
                VariableExpression result = (VariableExpression) source.transformExpression(this);
                Parameter parameter = parameters.get(source.getAccessedVariable());
                if (parameter != null) {
                    result.setAccessedVariable(parameter);
                    result.setType(parameter.getType());
                    return result;
                }
                FieldNode field = modelField(source);
                if (field != null) {
                    FieldNode builderField = builder.getField(field.getName());
                    if (builderField != null) {
                        result.setAccessedVariable(builderField);
                        result.setType(builderField.getType());
                    }
                }
                return result;
            }
            if (expression instanceof MethodCallExpression source) {
                MethodCallExpression result = (MethodCallExpression) source.transformExpression(this);
                MethodNode target = source.getMethodTarget();
                if (target != null) {
                    MethodNode twin = target.getNodeMetaData(TWIN_METADATA_KEY);
                    if (twin != null) result.setMethodTarget(twin);
                    else result.setMethodTarget(null);
                }
                return result;
            }
            return expression.transformExpression(this);
        }
    }
}
