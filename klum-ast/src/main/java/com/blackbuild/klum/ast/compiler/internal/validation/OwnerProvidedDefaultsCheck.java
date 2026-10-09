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
package com.blackbuild.klum.ast.compiler.internal.validation;

import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.Key;
import com.blackbuild.klum.ast.Owner;
import com.blackbuild.klum.ast.OwnerProvidedDefaults;
import com.blackbuild.klum.cast.spi.Check;
import com.blackbuild.klum.cast.spi.CheckContext;
import com.blackbuild.klum.cast.spi.Diagnostic;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.AnnotationConstantExpression;
import org.codehaus.groovy.ast.expr.ArrayExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.ast.tools.GenericsUtils;

import java.beans.Introspector;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.blackbuild.klum.ast.compiler.internal.ast.DslAstHelper.getFieldType;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.getNullSafeClassMember;
import static com.blackbuild.klum.ast.compiler.internal.common.CommonAstHelper.isAssignableTo;

/** Validates the schema contract declared by {@link OwnerProvidedDefaults}. */
public class OwnerProvidedDefaultsCheck implements Check {

    private static final ClassNode OWNER_ANNOTATION = ClassHelper.make(Owner.class);
    private static final ClassNode KEY_ANNOTATION = ClassHelper.make(Key.class);
    private static final String ANNOTATION_NAME = OwnerProvidedDefaults.class.getSimpleName();
    private static final String ANNOTATION_TYPE = OwnerProvidedDefaults.class.getName();
    private static final String CONTAINER_TYPE = OwnerProvidedDefaults.Container.class.getName();
    private static final String VALUE_MEMBER = "value";

    @Override
    public List<Diagnostic> check(CheckContext context) {
        AnnotatedNode target = context.getTarget();
        if (!(target instanceof ClassNode recipient))
            return violation(context.getValidatedAnnotation(), "@" + ANNOTATION_NAME + " can only be used on a type");

        List<Diagnostic> diagnostics = new ArrayList<>();
        declarations(context.getValidatedAnnotation()).forEach(declaration ->
                diagnostics.addAll(validateDeclaration(declaration, recipient)));

        if (isLastCarrier(context.getValidatedAnnotation(), recipient))
            diagnostics.addAll(validateRepeatedContracts(recipient));

        return diagnostics;
    }

    private List<Diagnostic> validateDeclaration(AnnotationNode declaration, ClassNode recipient) {
        ClassNode contract = getNullSafeClassMember(declaration, VALUE_MEMBER, null);
        if (contract == null)
            return violation(declaration, "@" + ANNOTATION_NAME + " must declare a contract type");

        List<ContractProperty> propertyDeclarations = contractPropertyDeclarations(contract);
        if (propertyDeclarations.isEmpty())
            return violation(declaration, String.format(
                    "@%s contract %s does not declare a JavaBean getter",
                    ANNOTATION_NAME, contract.getName()));

        List<Diagnostic> contractDiagnostics = incompatibleContractProperties(declaration, contract, propertyDeclarations);
        if (!contractDiagnostics.isEmpty()) return contractDiagnostics;

        Map<String, ContractProperty> properties = contractProperties(propertyDeclarations);

        if (!isAssignableTo(recipient, contract))
            return violation(declaration, String.format(
                    "@%s recipient %s must implement contract %s",
                    ANNOTATION_NAME, recipient.getName(), contract.getName()));

        List<FieldNode> compatibleOwners = declaredOwnerFields(recipient).stream()
                .filter(field -> isAssignableTo(field.getType(), contract))
                .toList();
        if (compatibleOwners.size() != 1) {
            String ownerNames = compatibleOwners.stream().map(FieldNode::getName).collect(Collectors.joining(", "));
            String detail = compatibleOwners.isEmpty()
                    ? "requires exactly one compatible declared @Owner field"
                    : String.format("found %d compatible declared @Owner fields (%s); exactly one is required",
                            compatibleOwners.size(), ownerNames);
            return violation(declaration, String.format(
                    "@%s contract %s on %s %s",
                    ANNOTATION_NAME, contract.getName(), recipient.getName(), detail));
        }

        FieldNode donor = compatibleOwners.get(0);
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (ContractProperty property : properties.values())
            diagnostics.addAll(validateProperty(declaration, contract, recipient, donor, property));
        return diagnostics;
    }

    private List<Diagnostic> validateProperty(
            AnnotationNode declaration,
            ClassNode contract,
            ClassNode recipient,
            FieldNode donor,
            ContractProperty property) {
        FieldNode donorProperty = storedBuilderProperty(donor.getType(), property.name).orElse(null);
        if (donorProperty == null)
            return violation(declaration, String.format(
                    "@%s contract %s property '%s' must be a stored Builder-visible property on donor type %s; "
                            + "owner-provided defaults execute in DEFAULT against the active Builder, do not materialize Models, "
                            + "and cannot use a computed getter, so declare a field or Groovy property named '%s'",
                    ANNOTATION_NAME, contract.getName(), property.name, donor.getType().getName(), property.name));

        ClassNode donorType = resolvedFieldType(donor.getType(), donorProperty);
        if (!isAssignableType(donorType, property.type))
            return violation(declaration, String.format(
                    "@%s contract %s donor field '%s' cannot read property '%s' with compatible type %s",
                    ANNOTATION_NAME, contract.getName(), donor.getName(), property.name, typeName(property.type)));

        FieldNode recipientField = fieldInHierarchy(recipient, property.name).orElse(null);
        if (recipientField == null || !isConfigurable(recipientField))
            return violation(declaration, String.format(
                    "@%s contract %s recipient cannot configure property '%s'",
                    ANNOTATION_NAME, contract.getName(), property.name));

        if (!isAssignableType(donorType, recipientField.getType()))
            return violation(declaration, String.format(
                    "@%s contract %s recipient property '%s' has type %s, which cannot accept donor type %s from owner field '%s'",
                    ANNOTATION_NAME, contract.getName(), property.name, typeName(recipientField.getType()),
                    typeName(donorType), donor.getName()));
        return List.of();
    }

    private List<Diagnostic> validateRepeatedContracts(ClassNode recipient) {
        Map<String, List<DeclaredProperty>> byName = new LinkedHashMap<>();
        for (AnnotationNode declaration : allDeclarations(recipient)) {
            ClassNode contract = getNullSafeClassMember(declaration, VALUE_MEMBER, null);
            if (contract == null) continue;
            contractProperties(contract).values().forEach(property ->
                    byName.computeIfAbsent(property.name, ignored -> new ArrayList<>())
                            .add(new DeclaredProperty(declaration, contract, property)));
        }

        List<Diagnostic> diagnostics = new ArrayList<>();
        for (Map.Entry<String, List<DeclaredProperty>> entry : byName.entrySet()) {
            List<DeclaredProperty> declarations = entry.getValue();
            for (int leftIndex = 0; leftIndex < declarations.size(); leftIndex++) {
                for (int rightIndex = leftIndex + 1; rightIndex < declarations.size(); rightIndex++) {
                    DeclaredProperty left = declarations.get(leftIndex);
                    DeclaredProperty right = declarations.get(rightIndex);
                    if (areCompatible(left.property.type, right.property.type)) continue;
                    diagnostics.add(diagnostic(right.declaration, String.format(
                            "@%s contracts %s and %s declare incompatible types %s and %s for property '%s'",
                            ANNOTATION_NAME, left.contract.getName(), right.contract.getName(),
                            typeName(left.property.type), typeName(right.property.type), entry.getKey())));
                }
            }
        }
        return diagnostics;
    }

    private static Map<String, ContractProperty> contractProperties(ClassNode contract) {
        return contractProperties(contractPropertyDeclarations(contract));
    }

    private static List<ContractProperty> contractPropertyDeclarations(ClassNode contract) {
        Set<ClassNode> declaringTypes = new LinkedHashSet<>(contract.getAllInterfaces());
        declaringTypes.add(contract);
        return declaringTypes.stream()
                .flatMap(type -> type.getMethods().stream()
                        .filter(method -> sameType(type, method.getDeclaringClass())))
                .filter(OwnerProvidedDefaultsCheck::isJavaBeanGetter)
                .map(method -> new ContractProperty(propertyName(method), resolvedReturnType(contract, method)))
                .toList();
    }

    private static Map<String, ContractProperty> contractProperties(List<ContractProperty> declarations) {
        Map<String, ContractProperty> result = new LinkedHashMap<>();
        declarations.forEach(property -> {
            ContractProperty current = result.get(property.name);
            if (current == null || isAssignableType(property.type, current.type))
                result.put(property.name, property);
        });
        return result;
    }

    private List<Diagnostic> incompatibleContractProperties(
            AnnotationNode declaration,
            ClassNode contract,
            List<ContractProperty> properties) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        for (int leftIndex = 0; leftIndex < properties.size(); leftIndex++) {
            ContractProperty left = properties.get(leftIndex);
            for (int rightIndex = leftIndex + 1; rightIndex < properties.size(); rightIndex++) {
                ContractProperty right = properties.get(rightIndex);
                if (!left.name.equals(right.name) || areCompatible(left.type, right.type)) continue;
                diagnostics.add(diagnostic(declaration, String.format(
                        "@%s contract %s inherits incompatible types %s and %s for property '%s'",
                        ANNOTATION_NAME, contract.getName(), typeName(left.type), typeName(right.type), left.name)));
            }
        }
        return diagnostics;
    }

    private static ClassNode resolvedReturnType(ClassNode implementation, MethodNode method) {
        ClassNode declaringType = method.getDeclaringClass();
        ClassNode parameterized = GenericsUtils.parameterizeType(implementation, declaringType);
        return GenericsUtils.correctToGenericsSpec(
                GenericsUtils.createGenericsSpec(parameterized), method.getReturnType());
    }

    private static ClassNode resolvedFieldType(ClassNode implementation, FieldNode field) {
        ClassNode declaringType = field.getDeclaringClass();
        ClassNode parameterized = GenericsUtils.parameterizeType(implementation, declaringType);
        return GenericsUtils.correctToGenericsSpec(
                GenericsUtils.createGenericsSpec(parameterized), field.getType());
    }

    private static boolean isJavaBeanGetter(MethodNode method) {
        if (!method.isPublic() || method.isStatic() || method.getParameters().length != 0
                || ClassHelper.VOID_TYPE.equals(method.getReturnType()))
            return false;
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3)))
            return true;
        return name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))
                && ClassHelper.boolean_TYPE.equals(method.getReturnType());
    }

    private static String propertyName(MethodNode getter) {
        return Introspector.decapitalize(getter.getName().startsWith("get")
                ? getter.getName().substring(3)
                : getter.getName().substring(2));
    }

    private static List<FieldNode> declaredOwnerFields(ClassNode recipient) {
        return recipient.getFields().stream()
                .filter(field -> sameType(field.getOwner(), recipient))
                .filter(field -> !field.getAnnotations(OWNER_ANNOTATION).isEmpty())
                .toList();
    }

    private static Optional<FieldNode> storedBuilderProperty(ClassNode donorType, String name) {
        return fieldInHierarchy(donorType, name)
                .filter(field -> !field.isStatic() && !field.getName().startsWith("$"));
    }

    private static boolean isConfigurable(FieldNode field) {
        if (field.isStatic() || field.isFinal()
                || Modifier.isTransient(field.getModifiers()) || field.getName().startsWith("$"))
            return false;
        if (!field.getAnnotations(OWNER_ANNOTATION).isEmpty() || !field.getAnnotations(KEY_ANNOTATION).isEmpty())
            return false;
        FieldType fieldType = getFieldType(field);
        return fieldType != FieldType.TRANSIENT && fieldType != FieldType.IGNORED && fieldType != FieldType.BUILDER;
    }

    private static Optional<FieldNode> fieldInHierarchy(ClassNode type, String name) {
        for (ClassNode current = type; current != null && !ClassHelper.OBJECT_TYPE.equals(current.redirect());
             current = current.getSuperClass()) {
            FieldNode field = current.getDeclaredField(name);
            if (field != null) return Optional.of(field);
        }
        return Optional.empty();
    }

    private static boolean areCompatible(ClassNode left, ClassNode right) {
        return isAssignableType(left, right) || isAssignableType(right, left);
    }

    private static boolean isAssignableType(ClassNode source, ClassNode target) {
        ClassNode boxedSource = boxed(source);
        ClassNode boxedTarget = boxed(target);
        if (!isAssignableTo(boxedSource, boxedTarget) && !sameType(boxedSource, boxedTarget)) return false;

        GenericsType[] targetGenerics = boxedTarget.getGenericsTypes();
        if (targetGenerics == null || targetGenerics.length == 0) return true;

        ClassNode comparableSource = boxedSource;
        if (!sameType(boxedSource, boxedTarget))
            comparableSource = GenericsUtils.parameterizeType(boxedSource, boxedTarget);
        GenericsType[] sourceGenerics = comparableSource.getGenericsTypes();
        if (sourceGenerics == null || sourceGenerics.length != targetGenerics.length) return false;
        for (int index = 0; index < sourceGenerics.length; index++)
            if (!isAssignableGenericType(sourceGenerics[index], targetGenerics[index])) return false;
        return true;
    }

    private static boolean isAssignableGenericType(GenericsType source, GenericsType target) {
        if (target.isWildcard()) return isWithinWildcardBounds(source, target);
        if (source.isWildcard()) return false;
        if (source.isPlaceholder() || target.isPlaceholder()) return source.getName().equals(target.getName());
        return isAssignableType(source.getType(), target.getType())
                && isAssignableType(target.getType(), source.getType());
    }

    private static boolean isWithinWildcardBounds(GenericsType source, GenericsType target) {
        ClassNode lowerBound = target.getLowerBound();
        ClassNode[] upperBounds = target.getUpperBounds();
        if (isUnboundedWildcard(lowerBound, upperBounds)) return true;

        if (source.isPlaceholder()) return false;
        if (lowerBound != null) return isWithinLowerBound(source, lowerBound);
        return isWithinUpperBounds(source, upperBounds);
    }

    private static boolean isUnboundedWildcard(ClassNode lowerBound, ClassNode[] upperBounds) {
        return lowerBound == null && (upperBounds == null || upperBounds.length == 0
                || upperBounds.length == 1 && sameType(upperBounds[0], ClassHelper.OBJECT_TYPE));
    }

    private static boolean isWithinLowerBound(GenericsType source, ClassNode targetLowerBound) {
        ClassNode sourceLowerBound = source.isWildcard() ? source.getLowerBound() : source.getType();
        return sourceLowerBound != null && isAssignableType(targetLowerBound, sourceLowerBound);
    }

    private static boolean isWithinUpperBounds(GenericsType source, ClassNode[] targetUpperBounds) {
        if (!source.isWildcard()) return satisfiesAllUpperBounds(source.getType(), targetUpperBounds);
        if (source.getLowerBound() != null) return false;

        ClassNode[] sourceUpperBounds = source.getUpperBounds();
        if (sourceUpperBounds == null || sourceUpperBounds.length == 0) return false;
        for (ClassNode targetUpperBound : targetUpperBounds)
            if (!hasAssignableUpperBound(sourceUpperBounds, targetUpperBound)) return false;
        return true;
    }

    private static boolean satisfiesAllUpperBounds(ClassNode source, ClassNode[] targetUpperBounds) {
        for (ClassNode targetUpperBound : targetUpperBounds)
            if (!isAssignableType(source, targetUpperBound)) return false;
        return true;
    }

    private static boolean hasAssignableUpperBound(ClassNode[] sourceUpperBounds, ClassNode targetUpperBound) {
        for (ClassNode sourceUpperBound : sourceUpperBounds)
            if (isAssignableType(sourceUpperBound, targetUpperBound)) return true;
        return false;
    }

    private static ClassNode boxed(ClassNode type) {
        return ClassHelper.isPrimitiveType(type) ? ClassHelper.getWrapper(type) : type;
    }

    private static boolean sameType(ClassNode left, ClassNode right) {
        return left != null && right != null && left.redirect().equals(right.redirect());
    }

    private static String typeName(ClassNode type) {
        return type.toString(false);
    }

    private static boolean isLastCarrier(AnnotationNode current, ClassNode recipient) {
        List<AnnotationNode> carriers = annotationCarriers(recipient);
        return !carriers.isEmpty() && carriers.get(carriers.size() - 1) == current;
    }

    private static List<AnnotationNode> allDeclarations(ClassNode recipient) {
        return annotationCarriers(recipient).stream()
                .flatMap(carrier -> declarations(carrier).stream())
                .toList();
    }

    private static List<AnnotationNode> annotationCarriers(ClassNode recipient) {
        return recipient.getAnnotations().stream()
                .filter(annotation -> ANNOTATION_TYPE.equals(annotation.getClassNode().getName())
                        || CONTAINER_TYPE.equals(annotation.getClassNode().getName()))
                .toList();
    }

    private static List<AnnotationNode> declarations(AnnotationNode carrier) {
        if (ANNOTATION_TYPE.equals(carrier.getClassNode().getName())) return List.of(carrier);
        if (!CONTAINER_TYPE.equals(carrier.getClassNode().getName())) return List.of();

        Expression value = carrier.getMember(VALUE_MEMBER);
        List<Expression> expressions;
        if (value instanceof ListExpression listExpression)
            expressions = listExpression.getExpressions();
        else if (value instanceof ArrayExpression arrayExpression)
            expressions = arrayExpression.getExpressions();
        else if (value instanceof AnnotationConstantExpression)
            expressions = Collections.singletonList(value);
        else
            expressions = List.of();

        return expressions.stream()
                .filter(AnnotationConstantExpression.class::isInstance)
                .map(AnnotationConstantExpression.class::cast)
                .map(AnnotationConstantExpression::getValue)
                .filter(AnnotationNode.class::isInstance)
                .map(AnnotationNode.class::cast)
                .toList();
    }

    private List<Diagnostic> violation(AnnotationNode annotation, String message) {
        return List.of(diagnostic(annotation, message));
    }

    private Diagnostic diagnostic(AnnotationNode annotation, String message) {
        return new Diagnostic(getClass().getName(), message, annotation);
    }

    private static final class ContractProperty {
        private final String name;
        private final ClassNode type;

        private ContractProperty(String name, ClassNode type) {
            this.name = name;
            this.type = type;
        }
    }

    private static final class DeclaredProperty {
        private final AnnotationNode declaration;
        private final ClassNode contract;
        private final ContractProperty property;

        private DeclaredProperty(AnnotationNode declaration, ClassNode contract, ContractProperty property) {
            this.declaration = declaration;
            this.contract = contract;
            this.property = property;
        }
    }
}
