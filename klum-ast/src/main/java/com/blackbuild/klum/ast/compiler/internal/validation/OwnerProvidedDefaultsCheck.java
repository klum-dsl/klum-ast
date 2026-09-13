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
import org.codehaus.groovy.ast.PropertyNode;
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
        ClassNode contract = getNullSafeClassMember(declaration, "value", null);
        if (contract == null)
            return violation(declaration, "@" + ANNOTATION_NAME + " must declare a contract type");

        Map<String, ContractProperty> properties = contractProperties(contract);
        if (properties.isEmpty())
            return violation(declaration, String.format(
                    "@%s contract %s does not declare a JavaBean getter",
                    ANNOTATION_NAME, contract.getName()));

        if (!isAssignableTo(recipient, contract))
            return violation(declaration, String.format(
                    "@%s recipient %s must implement contract %s",
                    ANNOTATION_NAME, recipient.getName(), contract.getName()));

        List<FieldNode> compatibleOwners = declaredOwnerFields(recipient).stream()
                .filter(field -> isAssignableTo(field.getType(), contract))
                .collect(Collectors.toList());
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
        for (ContractProperty property : properties.values()) {
            ClassNode donorType = readablePropertyType(donor.getType(), property).orElse(null);
            if (donorType == null || !isAssignableType(donorType, property.type)) {
                diagnostics.add(diagnostic(declaration, String.format(
                        "@%s contract %s donor field '%s' cannot read property '%s' with compatible type %s",
                        ANNOTATION_NAME, contract.getName(), donor.getName(), property.name, typeName(property.type))));
                continue;
            }

            FieldNode recipientField = fieldInHierarchy(recipient, property.name).orElse(null);
            if (recipientField == null || !isConfigurable(recipientField)) {
                diagnostics.add(diagnostic(declaration, String.format(
                        "@%s contract %s recipient cannot configure property '%s'",
                        ANNOTATION_NAME, contract.getName(), property.name)));
                continue;
            }

            if (!isAssignableType(donorType, recipientField.getType()))
                diagnostics.add(diagnostic(declaration, String.format(
                        "@%s contract %s recipient property '%s' has type %s, which cannot accept donor type %s from owner field '%s'",
                        ANNOTATION_NAME, contract.getName(), property.name, typeName(recipientField.getType()),
                        typeName(donorType), donor.getName())));
        }
        return diagnostics;
    }

    private List<Diagnostic> validateRepeatedContracts(ClassNode recipient) {
        Map<String, List<DeclaredProperty>> byName = new LinkedHashMap<>();
        for (AnnotationNode declaration : allDeclarations(recipient)) {
            ClassNode contract = getNullSafeClassMember(declaration, "value", null);
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
        Set<ClassNode> declaringTypes = new LinkedHashSet<>(contract.getAllInterfaces());
        declaringTypes.add(contract);
        Map<String, ContractProperty> result = new LinkedHashMap<>();
        contract.getMethods().stream()
                .filter(method -> declaringTypes.stream().anyMatch(type -> sameType(type, method.getDeclaringClass())))
                .filter(OwnerProvidedDefaultsCheck::isJavaBeanGetter)
                .forEach(method -> {
                    String propertyName = propertyName(method);
                    ClassNode returnType = resolvedReturnType(contract, method);
                    ContractProperty current = result.get(propertyName);
                    if (current == null || isAssignableType(returnType, current.type))
                        result.put(propertyName, new ContractProperty(propertyName, method.getName(), returnType));
                });
        return result;
    }

    private static ClassNode resolvedReturnType(ClassNode implementation, MethodNode method) {
        ClassNode declaringType = method.getDeclaringClass();
        ClassNode parameterized = GenericsUtils.parameterizeType(implementation, declaringType);
        return GenericsUtils.correctToGenericsSpec(
                GenericsUtils.createGenericsSpec(parameterized), method.getReturnType());
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
        String stem = getter.getName().startsWith("get")
                ? getter.getName().substring(3)
                : getter.getName().substring(2);
        return Introspector.decapitalize(stem);
    }

    private static List<FieldNode> declaredOwnerFields(ClassNode recipient) {
        return recipient.getFields().stream()
                .filter(field -> sameType(field.getOwner(), recipient))
                .filter(field -> !field.getAnnotations(OWNER_ANNOTATION).isEmpty())
                .collect(Collectors.toList());
    }

    private static Optional<ClassNode> readablePropertyType(ClassNode donorType, ContractProperty property) {
        Optional<ClassNode> getterType = donorType.getMethods(property.getterName).stream()
                .filter(OwnerProvidedDefaultsCheck::isJavaBeanGetter)
                .filter(method -> !method.isAbstract() || donorType.isInterface() || Modifier.isAbstract(donorType.getModifiers()))
                .map(method -> resolvedReturnType(donorType, method))
                .filter(type -> isAssignableType(type, property.type))
                .findFirst();
        if (getterType.isPresent()) return getterType;

        return propertyInHierarchy(donorType, property.name).map(PropertyNode::getType);
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

    private static Optional<PropertyNode> propertyInHierarchy(ClassNode type, String name) {
        for (ClassNode current = type; current != null && !ClassHelper.OBJECT_TYPE.equals(current.redirect());
             current = current.getSuperClass()) {
            Optional<PropertyNode> property = current.getProperties().stream()
                    .filter(candidate -> candidate.getName().equals(name))
                    .findFirst();
            if (property.isPresent()) return property;
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
            if (!sameGenericType(sourceGenerics[index], targetGenerics[index])) return false;
        return true;
    }

    private static boolean sameGenericType(GenericsType left, GenericsType right) {
        if (left.isWildcard() || right.isWildcard()) return left.toString().equals(right.toString());
        if (left.isPlaceholder() || right.isPlaceholder()) return left.getName().equals(right.getName());
        return isAssignableType(left.getType(), right.getType()) && isAssignableType(right.getType(), left.getType());
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
                .collect(Collectors.toList());
    }

    private static List<AnnotationNode> annotationCarriers(ClassNode recipient) {
        return recipient.getAnnotations().stream()
                .filter(annotation -> ANNOTATION_TYPE.equals(annotation.getClassNode().getName())
                        || CONTAINER_TYPE.equals(annotation.getClassNode().getName()))
                .collect(Collectors.toList());
    }

    private static List<AnnotationNode> declarations(AnnotationNode carrier) {
        if (ANNOTATION_TYPE.equals(carrier.getClassNode().getName())) return List.of(carrier);
        if (!CONTAINER_TYPE.equals(carrier.getClassNode().getName())) return List.of();

        Expression value = carrier.getMember("value");
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
                .collect(Collectors.toList());
    }

    private List<Diagnostic> violation(AnnotationNode annotation, String message) {
        return List.of(diagnostic(annotation, message));
    }

    private Diagnostic diagnostic(AnnotationNode annotation, String message) {
        return new Diagnostic(getClass().getName(), message, annotation);
    }

    private static final class ContractProperty {
        private final String name;
        private final String getterName;
        private final ClassNode type;

        private ContractProperty(String name, String getterName, ClassNode type) {
            this.name = name;
            this.getterName = getterName;
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
