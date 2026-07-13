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
package com.blackbuild.klum.ast.util;

import com.blackbuild.groovy.configdsl.transform.Default;
import com.blackbuild.klum.ast.process.DefaultKlumPhase;
import com.blackbuild.klum.ast.process.BuilderVisitingPhaseAction;
import com.blackbuild.klum.ast.util.layer3.ClusterModel;
import com.blackbuild.klum.ast.util.layer3.annotations.DefaultValues;
import groovy.lang.Closure;
import groovy.lang.MetaMethod;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static com.blackbuild.klum.ast.util.ClosureHelper.*;
import static com.blackbuild.klum.ast.util.DslHelper.castTo;
import static java.lang.String.format;

public class DefaultPhase extends BuilderVisitingPhaseAction {

    public DefaultPhase() {
        super(DefaultKlumPhase.DEFAULT);
    }

    @Override
    protected void doVisit(@NotNull String path, @NotNull KlumBuilder<?> element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
        setDefaultValuesFromDefaultValuesAnnotationOnOwnerField(element, container, nameOfFieldInContainer);
        setDefaultValuesFromDefaultValueAnnotationsOnType(element);
        setFieldsAnnotatedWithDefaultAnnotation(element);
        executeDefaultLifecycleMethods(element);
    }

    private void setDefaultValuesFromDefaultValuesAnnotationOnOwnerField(KlumBuilder<?> element, Object container, String nameOfFieldInContainer) {
        if (container == null) return;
        Field field = DslHelper.getField(container.getClass(), nameOfFieldInContainer).orElseThrow();
        AnnotationHelper.getMetaAnnotated(field, DefaultValues.class)
                .forEach(annotation -> setDefaultValuesFromAnnotation(element, annotation));
    }

    private void setDefaultValuesFromDefaultValueAnnotationsOnType(KlumBuilder<?> element) {
        DslHelper.getDslHierarchyOf(element.getModelType()).stream()
                .flatMap(layer -> AnnotationHelper.getMetaAnnotated(layer, DefaultValues.class))
                .forEach(annotation -> setDefaultValuesFromAnnotation(element, annotation));
        }

    private void setDefaultValuesFromAnnotation(KlumBuilder<?> element, Annotation valuesAnnotation) {
        Map<String, Object> nonDefaultMembers = AnnotationHelper.getNonDefaultMembers(valuesAnnotation);
        if (nonDefaultMembers.containsKey("value")) {
            String valueTarget = valuesAnnotation.annotationType().getAnnotation(DefaultValues.class).valueTarget();
            Object mapping = nonDefaultMembers.remove("value");
            nonDefaultMembers.put(valueTarget, mapping);
        }

        nonDefaultMembers.forEach((field, value) -> setFieldToDefaultValue(field, value, element, valuesAnnotation));
    }

    private static void setFieldToDefaultValue(String field, Object value, KlumBuilder<?> builder, Annotation valuesAnnotation) {

        try {
            if (!isEmpty(builder.getInstanceAttribute(field))) return;
        } catch (MissingPropertyException e) {
            // ignore
        }

        Class<?> targetType = null;
        try {
            targetType = determineTargetType(field, value, builder, targetType);
        } catch (MissingPropertyException e) {
            if (shouldIgnoreUnknownFields(valuesAnnotation)) return;
            throw new KlumSchemaException(format("Annotation %s defines a default value for '%s', but '%s' has no such field or virtual setter method.",
                    valuesAnnotation.annotationType().getName(), field, builder.getModelType().getName()), e);
        }

        if (isClosureType(value)) {
            if (Closure.class.isAssignableFrom(targetType))
                //noinspection unchecked
                value = createClosureInstance((Class<? extends Closure<Object>>) value);
            else
                //noinspection unchecked
                value = invokeClosureWithDelegateAsArgument((Class<? extends Closure<Object>>) value, builder);
        }

        try {
            Object castedValue = castTo(value, targetType);
            builder.invokeRwMethod(field, castedValue);
        } catch (Exception e) {
            throw new KlumSchemaException(format("Could not convert default value from annotation %s.%s to target type %s",
                    valuesAnnotation.annotationType().getName(), field, targetType.getName()), e);
        }
    }

    private static @NotNull Class<?> determineTargetType(String field, Object value, KlumBuilder<?> builder, Class<?> fieldType) {
        if ("apply".equals(field) && isClosureType(value))
            return Closure.class;
        Class<?> methodArgument = isClosureType(value) ? (Class<?>) value : value.getClass();
        List<MetaMethod> compatibleSetters = builder.getMetaClass().getMetaMethods().stream()
                .filter(metaMethod -> metaMethod.getName().equals(field) && metaMethod.getParameterTypes().length == 1)
                .filter(metaMethod -> metaMethod.getParameterTypes()[0].getTheClass().isAssignableFrom(methodArgument))
                .toList();

        if (compatibleSetters.size() == 1)
            fieldType = compatibleSetters.get(0).getParameterTypes()[0].getTheClass();

        if (fieldType == null) {
                fieldType = determineTargetTypeFromSingleSetterOrField(field, builder);
        }
        return fieldType;
    }

    private static boolean shouldIgnoreUnknownFields(Annotation valuesAnnotation) {
        return valuesAnnotation.annotationType().getAnnotation(DefaultValues.class).ignoreUnknownFields();
    }

    private static @NotNull Class<?> determineTargetTypeFromSingleSetterOrField(String field, KlumBuilder<?> builder) {
        List<MetaMethod> matchingSetters = builder.getMetaClass().getMetaMethods().stream()
                .filter(metaMethod -> metaMethod.getName().equals(field) && metaMethod.getParameterTypes().length == 1)
                .toList();

        if (matchingSetters.size() == 1)
            return matchingSetters.get(0).getParameterTypes()[0].getTheClass();
        else
            return builder.getField(field).getType();
    }

    private static void executeDefaultLifecycleMethods(KlumBuilder<?> element) {
        LifecycleHelper.executeLifecycleMethods(element, Default.class);
    }

    private void setFieldsAnnotatedWithDefaultAnnotation(KlumBuilder<?> element) {
        ClusterModel.getFieldsAnnotatedWith(element, Default.class)
                .entrySet()
                .stream()
                .filter(this::isUnset)
                .forEach(entry -> applyDefaultValue(element, entry.getKey()));
    }

    private void applyDefaultValue(KlumBuilder<?> element, String fieldName) {
        Object defaultValue = getDefaultValue(element, fieldName);
        element.setSingleField(fieldName, defaultValue);
    }

    @SuppressWarnings({"OptionalGetWithoutIsPresent", "java:S3655"})
    private Object getDefaultValue(KlumBuilder<?> builder, String fieldName) {
        Field field = DslHelper.getField(builder.getClass(), fieldName).get();
        Default defaultAnnotation = field.getAnnotation(Default.class);
        if (defaultAnnotation == null) return null;
        Class<?> fieldType = field.getType();

        if (!defaultAnnotation.field().isEmpty()) {
            Object defaultValue = builder.getInstanceProperty(defaultAnnotation.field());
            if (defaultValue != null)
                return castTo(defaultValue, fieldType);
            return getDefaultValue(builder, defaultAnnotation.field()); // special case: cascade defaults
        } else if (!defaultAnnotation.delegate().isEmpty()) {
            Object delegationTarget = builder.getInstanceProperty(defaultAnnotation.delegate());
            return delegationTarget != null ? castTo(InvokerHelper.getProperty(delegationTarget, fieldName), fieldType) : null;
        } else {
            return castTo(invokeClosureWithDelegateAsArgument(defaultAnnotation.code(), builder), fieldType);
        }
    }

}
