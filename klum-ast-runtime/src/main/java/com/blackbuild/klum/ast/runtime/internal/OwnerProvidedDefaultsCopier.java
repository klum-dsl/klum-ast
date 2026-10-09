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
package com.blackbuild.klum.ast.runtime.internal;

import com.blackbuild.klum.ast.Default;
import com.blackbuild.klum.ast.FieldType;
import com.blackbuild.klum.ast.Key;
import com.blackbuild.klum.ast.Owner;
import com.blackbuild.klum.ast.OwnerProvidedDefaults;
import com.blackbuild.klum.ast.PostApply;
import com.blackbuild.klum.ast.PostCreate;
import com.blackbuild.klum.ast.PostTree;
import com.blackbuild.klum.ast.Role;
import com.blackbuild.klum.ast.Validate;
import com.blackbuild.klum.ast.runtime.KlumModelException;
import com.blackbuild.klum.ast.runtime.KlumSchemaSupport;
import groovy.lang.Closure;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.runtime.InvokerHelper;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.blackbuild.klum.ast.runtime.internal.DslHelper.getClassFromType;
import static com.blackbuild.klum.ast.runtime.internal.DslHelper.getElementType;
import static java.beans.Introspector.decapitalize;
import static java.lang.String.format;

/** Applies the value-only conservative copy policy of {@link OwnerProvidedDefaults}. */
final class OwnerProvidedDefaultsCopier {

    private static final Object MISSING = new Object();

    private final InternalKlumBuilder<?> target;
    private final Map<Object, InternalKlumBuilder<?>> rehydratedValues = new IdentityHashMap<>();

    private OwnerProvidedDefaultsCopier(InternalKlumBuilder<?> target) {
        this.target = target;
    }

    static void applyTo(InternalKlumBuilder<?> target) {
        new OwnerProvidedDefaultsCopier(target).applyDeclaredContracts();
    }

    private void applyDeclaredContracts() {
        Map<String, ContractProperty> properties = new LinkedHashMap<>();
        for (Class<?> layer : DslHelper.getDslHierarchyOf(target.getModelType())) {
            for (OwnerProvidedDefaults annotation : layer.getDeclaredAnnotationsByType(OwnerProvidedDefaults.class)) {
                Class<?> contract = annotation.value();
                Field donorField = declaredDonorField(layer, contract);
                Object donor = target.getInstanceAttribute(donorField.getName());
                if (!isCompatibleDonor(donor, contract)) {
                    reportMissingDonor(contract, donorField);
                    continue;
                }
                contractPropertyNames(contract).forEach(name ->
                        properties.putIfAbsent(name, new ContractProperty(name, donor)));
            }
        }
        properties.values().forEach(this::applyContractProperty);
    }

    private static Field declaredDonorField(Class<?> layer, Class<?> contract) {
        return Arrays.stream(layer.getDeclaredFields())
                .filter(field -> field.isAnnotationPresent(Owner.class))
                .filter(field -> contract.isAssignableFrom(field.getType()))
                .findFirst()
                .orElseThrow(() -> new KlumModelException(format(
                        "No declared @Owner field on %s provides @OwnerProvidedDefaults contract %s",
                        layer.getName(), contract.getName())));
    }

    private static boolean isCompatibleDonor(Object donor, Class<?> contract) {
        if (donor instanceof InternalKlumBuilder<?> builder)
            return contract.isAssignableFrom(builder.getModelType());
        return contract.isInstance(donor);
    }

    private void reportMissingDonor(Class<?> contract, Field donorField) {
        KlumSchemaSupport.klumValidationForObject(target).issueAt(
                donorField.getName(),
                format("Cannot apply @OwnerProvidedDefaults contract %s: donor field '%s' is missing or incompatible",
                        contract.getName(), donorField.getName()),
                Validate.Level.WARNING
        );
    }

    private static List<String> contractPropertyNames(Class<?> contract) {
        return Arrays.stream(contract.getMethods())
                .filter(OwnerProvidedDefaultsCopier::isJavaBeanGetter)
                .sorted(Comparator.comparing(Method::getName))
                .map(OwnerProvidedDefaultsCopier::propertyName)
                .distinct()
                .toList();
    }

    private static boolean isJavaBeanGetter(Method method) {
        if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers())
                || method.getParameterCount() != 0 || method.getReturnType() == void.class)
            return false;
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3)))
            return true;
        return name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))
                && method.getReturnType() == boolean.class;
    }

    private static String propertyName(Method getter) {
        return decapitalize(getter.getName().startsWith("get")
                ? getter.getName().substring(3)
                : getter.getName().substring(2));
    }

    private void applyContractProperty(ContractProperty property) {
        Field targetField = target.getModelField(property.name);
        Object donorValue = readContractValue(property);
        copyField(target, targetField, donorValue, true);
    }

    private static Object readContractValue(ContractProperty property) {
        if (property.donor instanceof InternalKlumBuilder<?> builder) {
            return builder.getInstanceAttribute(property.name);
        }
        return InvokerHelper.getProperty(property.donor, property.name);
    }

    private void copyAllFields(InternalKlumBuilder<?> nestedTarget, Object donor, boolean attached) {
        rehydratedValues.putIfAbsent(donor, nestedTarget);
        for (Class<?> layer : DslHelper.getDslHierarchyOf(nestedTarget.getModelType())) {
            for (Field field : layer.getDeclaredFields()) {
                if (!isCopyableValueField(field)) continue;
                Object donorValue = readValue(donor, field.getName());
                if (donorValue != MISSING)
                    copyField(nestedTarget, field, donorValue, attached);
            }
        }
    }

    private static boolean isCopyableValueField(Field field) {
        if (field.isSynthetic() || Modifier.isStatic(field.getModifiers()) || Modifier.isTransient(field.getModifiers()))
            return false;
        if (field.getName().startsWith("$") || field.isAnnotationPresent(Key.class)
                || field.isAnnotationPresent(Owner.class) || field.isAnnotationPresent(Role.class))
            return false;
        FieldType fieldType = DslHelper.getKlumFieldType(field);
        if (fieldType == FieldType.TRANSIENT || fieldType == FieldType.IGNORED || fieldType == FieldType.BUILDER)
            return false;
        return !isLifecycleClosure(field);
    }

    private static boolean isLifecycleClosure(Field field) {
        if (!Closure.class.isAssignableFrom(field.getType())) return false;
        return hasAnyAnnotation(field, Default.class, PostCreate.class, PostApply.class, PostTree.class);
    }

    @SafeVarargs
    private static boolean hasAnyAnnotation(Field field, Class<? extends Annotation>... annotations) {
        return Arrays.stream(annotations).anyMatch(field::isAnnotationPresent);
    }

    private static Object readValue(Object donor, String name) {
        if (donor instanceof InternalKlumBuilder<?> builder)
            return builder.getInstanceAttributeOrGetter(name);
        try {
            return DslHelper.getAttributeValue(name, donor);
        } catch (MissingPropertyException ignored) {
            try {
                return InvokerHelper.getProperty(donor, name);
            } catch (MissingPropertyException alsoMissing) {
                return MISSING;
            }
        }
    }

    private void copyField(InternalKlumBuilder<?> nestedTarget, Field field, Object donorValue, boolean attached) {
        if (donorValue == null || field.getType().isPrimitive()) return;
        if (Collection.class.isAssignableFrom(field.getType())) {
            copyCollection(nestedTarget, field, (Collection<?>) donorValue, attached);
        } else if (Map.class.isAssignableFrom(field.getType())) {
            copyMap(nestedTarget, field, (Map<?, ?>) donorValue, attached);
        } else {
            copySingle(nestedTarget, field, donorValue, attached);
        }
    }

    private void copySingle(InternalKlumBuilder<?> nestedTarget, Field field, Object donorValue, boolean attached) {
        Object currentValue = nestedTarget.getInstanceAttribute(field.getName());
        if (currentValue == null) {
            Object copied = copyValueForField(field, field.getType(), donorValue);
            nestedTarget.setSingleField(field.getName(), copied);
            initializeOwnersIfNeeded(nestedTarget, copied, attached);
        } else if (canRecursivelyMix(nestedTarget, field, currentValue, donorValue)) {
            copyAllFields((InternalKlumBuilder<?>) currentValue, donorValue, attached);
        }
    }

    private void copyCollection(InternalKlumBuilder<?> nestedTarget, Field field, Collection<?> donorValues,
                                boolean attached) {
        Collection<?> currentValues = nestedTarget.getInstanceAttribute(field.getName());
        if (currentValues == null || !currentValues.isEmpty()) return;
        Class<?> elementType = getClassFromType(getElementType(field));
        for (Object donorValue : donorValues) {
            Object copied = copyValueForField(field, elementType, donorValue);
            nestedTarget.addElementToCollection(field.getName(), copied);
            initializeOwnersIfNeeded(nestedTarget, copied, attached);
        }
    }

    private void copyMap(InternalKlumBuilder<?> nestedTarget, Field field, Map<?, ?> donorValues, boolean attached) {
        Map<Object, Object> currentValues = nestedTarget.getInstanceAttribute(field.getName());
        if (currentValues == null) return;
        Class<?> valueType = getClassFromType(getElementType(field));
        for (Map.Entry<?, ?> entry : donorValues.entrySet()) {
            Object currentValue = currentValues.get(entry.getKey());
            if (!currentValues.containsKey(entry.getKey())) {
                Object copied = copyValueForField(field, valueType, entry.getValue());
                nestedTarget.addElementToMap(field.getName(), entry.getKey(), copied);
                initializeOwnersIfNeeded(nestedTarget, copied, attached);
            } else if (canRecursivelyMix(nestedTarget, field, currentValue, entry.getValue())) {
                copyAllFields((InternalKlumBuilder<?>) currentValue, entry.getValue(), attached);
            }
        }
    }

    private static boolean canRecursivelyMix(InternalKlumBuilder<?> nestedTarget, Field field,
                                             Object currentValue, Object donorValue) {
        return donorValue != null
                && currentValue instanceof InternalKlumBuilder<?> currentBuilder
                && !currentBuilder.isSealed()
                && nestedTarget.ownsRelationshipValue(field.getName(), currentBuilder);
    }

    private Object copyValueForField(Field field, Type declaredType, Object donorValue) {
        if (donorValue == null || !DslHelper.isRelationship(field)) return donorValue;
        if (isAggregationValue(field, donorValue)) return donorValue;
        Class<?> valueType = getClassFromType(declaredType);
        return rehydrateValue(valueType, donorValue);
    }

    private static boolean isAggregationValue(Field field, Object value) {
        if (TemplateManager.isTemplate(value)) return false;
        if (DslHelper.isLink(field)) return true;
        if (!DslHelper.isOptionalLink(field)) return false;
        if (value instanceof InternalKlumBuilder<?> builder)
            return builder.isSealed() && !TemplateManager.isTemplate(builder.getCompletedModel());
        return DslHelper.isDslObject(value);
    }

    private InternalKlumBuilder<?> rehydrateValue(Class<?> declaredType, Object donorValue) {
        InternalKlumBuilder<?> existing = rehydratedValues.get(donorValue);
        if (existing != null) return existing;
        InternalKlumBuilder<?> builder = FactoryHelper.createRecipeBuilder(
                declaredType, donorValue, null, target.isTemplate());
        rehydratedValues.put(donorValue, builder);
        copyAllFields(builder, donorValue, false);
        if (!target.isTemplate()) {
            LifecycleHelper.executeLifecycleMethods(builder, PostCreate.class);
            LifecycleHelper.executeLifecycleMethods(builder, PostApply.class);
        }
        return builder;
    }

    private static void initializeOwnersIfNeeded(InternalKlumBuilder<?> container, Object copied, boolean attached) {
        if (attached && copied instanceof InternalKlumBuilder<?> builder && !builder.isSealed())
            new OwnerPhase().initializeAttachedSubtree(builder, container);
    }

    private record ContractProperty(String name, Object donor) {
        private ContractProperty {
            Objects.requireNonNull(name);
            Objects.requireNonNull(donor);
        }
    }
}
