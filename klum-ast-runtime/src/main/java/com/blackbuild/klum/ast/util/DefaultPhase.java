/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
import com.blackbuild.klum.ast.process.VisitingPhaseAction;
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
import static java.util.stream.Collectors.toList;

public class DefaultPhase extends VisitingPhaseAction {

    public DefaultPhase() {
        super(DefaultKlumPhase.DEFAULT);
    }

    @Override
    public void visit(@NotNull String path, @NotNull Object element, @Nullable Object container, @Nullable String nameOfFieldInContainer) {
        setDefaultValuesFromDefaultValuesAnnotationOnOwnerField(element, container, nameOfFieldInContainer);
        setDefaultValuesFromDefaultValueAnnotationsOnType(element);
        setFieldsAnnotatedWithDefaultAnnotation(element);
        executeDefaultLifecycleMethods(element);
    }

    private void setDefaultValuesFromDefaultValuesAnnotationOnOwnerField(Object element, Object container, String nameOfFieldInContainer) {
        if (container == null) return;
        Field field = DslHelper.getField(container.getClass(), nameOfFieldInContainer).orElseThrow();
        AnnotationHelper.getMetaAnnotated(field, DefaultValues.class)
                .forEach(annotation -> setDefaultValuesFromAnnotation(element, annotation));
    }

    private void setDefaultValuesFromDefaultValueAnnotationsOnType(Object element) {
        DslHelper.getDslHierarchyOf(element.getClass()).stream()
                .flatMap(layer -> AnnotationHelper.getMetaAnnotated(layer, DefaultValues.class))
                .forEach(annotation -> setDefaultValuesFromAnnotation(element, annotation));
        }

    private void setDefaultValuesFromAnnotation(Object element, Annotation valuesAnnotation) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(element);
        Map<String, Object> nonDefaultMembers = AnnotationHelper.getNonDefaultMembers(valuesAnnotation);
        if (nonDefaultMembers.containsKey("value")) {
            String valueTarget = valuesAnnotation.annotationType().getAnnotation(DefaultValues.class).valueTarget();
            Object mapping = nonDefaultMembers.remove("value");
            nonDefaultMembers.put(valueTarget, mapping);
        }

        nonDefaultMembers.forEach((field, value) -> setFieldToDefaultValue(field, value, proxy, valuesAnnotation));
    }

    private static void setFieldToDefaultValue(String field, Object value, KlumInstanceProxy proxy, Annotation valuesAnnotation) {

        try {
            if (!isEmpty(proxy.getInstanceAttribute(field))) return;
        } catch (MissingPropertyException e) {
            // ignore
        }

        Class<?> targetType = null;
        try {
            targetType = determineTargetType(field, value, proxy, targetType);
        } catch (MissingPropertyException e) {
            if (shouldIgnoreUnknownFields(valuesAnnotation)) return;
            throw new KlumSchemaException(format("Annotation %s defines a default value for '%s', but '%s' has no such field or virtual setter method.",
                    valuesAnnotation.annotationType().getName(), field, proxy.getDSLInstance().getClass().getName()), e);
        }

        if (isClosureType(value)) {
            if (Closure.class.isAssignableFrom(targetType))
                //noinspection unchecked
                value = createClosureInstance((Class<? extends Closure<Object>>) value);
            else
                //noinspection unchecked
                value = invokeClosureWithDelegateAsArgument((Class<? extends Closure<Object>>) value, proxy.getDSLInstance());
        }

        try {
            Object castedValue = castTo(value, targetType);
            proxy.invokeRwMethod(field, castedValue);
        } catch (Exception e) {
            throw new KlumSchemaException(format("Could not convert default value from annotation %s.%s to target type %s",
                    valuesAnnotation.annotationType().getName(), field, targetType.getName()), e);
        }
    }

    private static @NotNull Class<?> determineTargetType(String field, Object value, KlumInstanceProxy proxy, Class<?> fieldType) {
        Class<?> methodArgument = isClosureType(value) ? (Class<?>) value : value.getClass();
        MetaMethod exactlyMatchingSetter = proxy.getRwInstance().getMetaClass().getMetaMethod(field, new Object[]{methodArgument});

        if (exactlyMatchingSetter != null)
            fieldType = exactlyMatchingSetter.getParameterTypes()[0].getTheClass();

        if (fieldType == null) {
                fieldType = determineTargetTypeFromSingleSetterOrField(field, proxy);
        }
        return fieldType;
    }

    private static boolean shouldIgnoreUnknownFields(Annotation valuesAnnotation) {
        return valuesAnnotation.annotationType().getAnnotation(DefaultValues.class).ignoreUnknownFields();
    }

    private static @NotNull Class<?> determineTargetTypeFromSingleSetterOrField(String field, KlumInstanceProxy proxy) {
        List<MetaMethod> matchingSetters = proxy.getRwInstance().getMetaClass().getMetaMethods().stream()
                .filter(metaMethod -> metaMethod.getName().equals(field) && metaMethod.getParameterTypes().length == 1)
                .collect(toList());

        if (matchingSetters.size() == 1)
            return matchingSetters.get(0).getParameterTypes()[0].getTheClass();
        else
            return proxy.getField(field).getType();
    }

    private static void executeDefaultLifecycleMethods(Object element) {
        LifecycleHelper.executeLifecycleMethods(KlumInstanceProxy.getProxyFor(element), Default.class);
    }

    private void setFieldsAnnotatedWithDefaultAnnotation(Object element) {
        ClusterModel.getFieldsAnnotatedWith(element, Default.class)
                .entrySet()
                .stream()
                .filter(this::isUnset)
                .forEach(entry -> applyDefaultValue(element, entry.getKey()));
    }

    private void applyDefaultValue(Object element, String fieldName) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(element);
        Object defaultValue = getDefaultValue(proxy, fieldName);
        proxy.setSingleField(fieldName, defaultValue);
    }

    @SuppressWarnings({"OptionalGetWithoutIsPresent", "java:S3655"})
    private Object getDefaultValue(KlumInstanceProxy proxy, String fieldName) {
        Field field = DslHelper.getField(proxy.getDSLInstance().getClass(), fieldName).get();
        Default defaultAnnotation = field.getAnnotation(Default.class);
        if (defaultAnnotation == null) return null;
        Class<?> fieldType = field.getType();

        if (!defaultAnnotation.field().isEmpty()) {
            Object defaultValue = proxy.getInstanceProperty(defaultAnnotation.field());
            if (defaultValue != null)
                return castTo(defaultValue, fieldType);
            return getDefaultValue(proxy, defaultAnnotation.field()); // special case: cascade defaults
        } else if (!defaultAnnotation.delegate().isEmpty()) {
            Object delegationTarget = proxy.getInstanceProperty(defaultAnnotation.delegate());
            return delegationTarget != null ? castTo(InvokerHelper.getProperty(delegationTarget, fieldName), fieldType) : null;
        } else {
            return castTo(invokeClosureWithDelegateAsArgument(defaultAnnotation.code(), proxy.getDSLInstance()), fieldType);
        }
    }

}
