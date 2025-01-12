/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.FieldType;
import com.blackbuild.groovy.configdsl.transform.Key;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.groovy.configdsl.transform.Role;
import com.blackbuild.klum.ast.util.copy.Overwrite;
import com.blackbuild.klum.ast.util.copy.OverwriteStrategy;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static com.blackbuild.klum.ast.util.DslHelper.isDslType;
import static com.blackbuild.klum.ast.util.KlumInstanceProxy.getProxyFor;
import static groovyjarjarasm.asm.Opcodes.*;

/**
 * Handles the copying of properties from one object to another.
 * Will support different override strategies.
 */
public class CopyHandler {

    private final Object target;
    private final KlumInstanceProxy proxy;
    private final Object source;

    public static void copyToFrom(Object target, Object source) {
        new CopyHandler(target, source).doCopy();
    }

    public CopyHandler(Object target, Object source) {
        this.target = target;
        proxy = getProxyFor(target);
        this.source = source;
    }

    public void doCopy() {
        DslHelper.getDslHierarchyOf(target.getClass()).forEach(this::copyFromLayer);
    }

    private void copyFromLayer(Class<?> layer) {
        if (layer.isInstance(source))
            Arrays.stream(layer.getDeclaredFields())
                    .filter(this::isNotIgnored)
                    .forEach(this::copyFromField);
    }

    @SuppressWarnings("java:S1126")
    private boolean isIgnored(Field field) {
        if ((field.getModifiers() & (ACC_SYNTHETIC | ACC_FINAL | ACC_TRANSIENT)) != 0) return true;
        if (field.isAnnotationPresent(Key.class)) return true;
        if (field.isAnnotationPresent(Owner.class)) return true;
        if (field.isAnnotationPresent(Role.class)) return true;
        if (field.getName().startsWith("$")) return true;
        if (DslHelper.getKlumFieldType(field) == FieldType.TRANSIENT) return true;
        return false;
    }

    private boolean isNotIgnored(Field field) {
        return !isIgnored(field);
    }

    private void copyFromField(Field field) {
        Class<?> fieldType = field.getType();
        if (Collection.class.isAssignableFrom(fieldType))
            copyFromCollectionField(field);
        else if (Map.class.isAssignableFrom(fieldType))
            copyFromMapField(field);
        else
            copyFromSingleField(field);
    }

    private void copyFromSingleField(Field field) {
        String fieldName = field.getName();
        Object currentValue = proxy.getInstanceAttribute(fieldName);
        KlumInstanceProxy templateProxy = getProxyFor(source);
        Object templateValue = templateProxy.getInstanceAttribute(fieldName);

        OverwriteStrategy.Single strategy = getSingleStrategy(field);

        switch (strategy) {
            case REPLACE:
                if (templateValue != null)
                    replaceValue(fieldName, templateValue);
                break;
            case ALWAYS_REPLACE:
                replaceValue(fieldName, templateValue);
                break;
            case SET_IF_NULL:
                if (currentValue == null)
                    replaceValue(fieldName, templateValue);
                break;
            case MERGE:
                if (currentValue == null)
                    replaceValue(fieldName, templateValue);
                else
                    getProxyFor(currentValue).copyFrom(templateValue);
                break;
            case INHERIT:
            default:
                throwInvalidStrategy(strategy);
        }
    }

    private void replaceValue(String fieldName, Object templateValue) {
        proxy.setInstanceAttribute(fieldName, copyValue(templateValue));
    }

    @SuppressWarnings("unchecked")
    private static @Nullable <T> T copyValue(Object templateValue) {
        if (isDslType(templateValue.getClass()))
            return (T) getProxyFor(templateValue).cloneInstance();
        else if (templateValue instanceof Collection)
            return (T) createCopyOfCollection((Collection<Object>) templateValue);
        else if (templateValue instanceof Map)
            return (T) createCopyOfMap((Map<String, Object>) templateValue);
        else
            return (T) templateValue;
    }

    private OverwriteStrategy.Single getSingleStrategy(Field field) {
        Overwrite.Single annotation = field.getAnnotation(Overwrite.Single.class);
        if (annotation != null && annotation.value() != OverwriteStrategy.Single.INHERIT)
            return annotation.value();
        return AnnotationHelper.getMostSpecificAnnotation(field.getDeclaringClass(), Overwrite.class, o -> o.singles().value() != OverwriteStrategy.Single.INHERIT)
                .map(Overwrite::singles)
                .map(Overwrite.Single::value)
                .orElse(getDefaultSingleStrategy(field));
    }

    private OverwriteStrategy.Single getDefaultSingleStrategy(Field field) {
        return isDslType(field.getType()) ? OverwriteStrategy.Single.MERGE : OverwriteStrategy.Single.REPLACE;
    }

    private void copyFromMapField(Field field) {
        String fieldName = field.getName();
        Map<Object,Object> currentValues = proxy.getInstanceAttribute(fieldName);
        Map<Object,Object> templateValues = getProxyFor(source).getInstanceAttribute(fieldName);

        OverwriteStrategy.Map strategy = getMapStrategy(field);

        switch (strategy) {
            case FULL_REPLACE:
                if (notEmpty(templateValues)) {
                    currentValues.clear();
                    addMapValues(field, currentValues, templateValues);
                }
                break;
            case ALWAYS_REPLACE:
                currentValues.clear();
                if (notEmpty(templateValues))
                    addMapValues(field, currentValues, templateValues);
                break;
            case MERGE_KEYS:
                if (notEmpty(templateValues))
                    addMapValues(field, currentValues, templateValues);
                break;
            case MERGE_VALUES:
                if (notEmpty(templateValues)) {
                    if (isDslType(DslHelper.getElementType(field)))
                        mergeMapValues(field, currentValues, templateValues);
                    else
                        addMapValues(field, currentValues, templateValues);
                }
                break;
            case ADD_MISSING:
                if (notEmpty(templateValues))
                    addMissingMapValues(field, currentValues, templateValues);
                break;
            case INHERIT:
            default:
                throwInvalidStrategy(strategy);
        }
    }

    private static boolean notEmpty(Map<Object, Object> templateValues) {
        return templateValues != null && !templateValues.isEmpty();
    }

    private static void throwInvalidStrategy(Object strategy) {
        throw new AssertionError(String.format("Unexpected strategy %s encountered", strategy));
    }

    private void addMissingMapValues(Field field, Map<Object, Object> currentValues, Map<Object, Object> templateValues) {
        if (templateValues == null || templateValues.isEmpty()) return;
        Class<?> valueType = DslHelper.getClassFromType(DslHelper.getElementType(field));
        for (Map.Entry<Object,Object> entry : templateValues.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            assertCorrectType(field, value, valueType);
            if (!currentValues.containsKey(key))
                currentValues.put(key, copyValue(value));
        }
    }

    private void mergeMapValues(Field field, Map<Object, Object> currentValues, Map<Object, Object> templateValues) {
        if (templateValues == null || templateValues.isEmpty()) return;
        Class<?> valueType = DslHelper.getClassFromType(DslHelper.getElementType(field));
        for (Map.Entry<Object,Object> entry : templateValues.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            assertCorrectType(field, value, valueType);
            Object currentValue = currentValues.get(key);
            if (currentValue == null)
                currentValues.put(key, copyValue(value));
            else
                getProxyFor(currentValue).copyFrom(value);
        }
    }

    private void addMapValues(Field field, Map<Object,Object> currentValues, Map<Object,Object> templateValues) {
        Class<?> valueType = DslHelper.getClassFromType(DslHelper.getElementType(field));
        for (Map.Entry<Object,Object> entry : templateValues.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            assertCorrectType(field, value, valueType);
            currentValues.put(key, copyValue(value));
        }
    }

    private static <T> Collection<T> createCopyOfCollection(Collection<T> templateValue) {
        Collection<T> result = createNewEmptyCollectionOrMapFrom(templateValue);
        for (T t : templateValue)
            result.add(copyValue(t));
        return result;
    }

    private static <T> Map<String, T> createCopyOfMap(Map<String, T> templateValue) {
        Map<String, T> result = createNewEmptyCollectionOrMapFrom(templateValue);
        for (Map.Entry<String, T> entry : templateValue.entrySet())
            result.put(entry.getKey(), copyValue(entry.getValue()));
        return result;
    }

    @SuppressWarnings("unchecked")
    private static <T> T createNewEmptyCollectionOrMapFrom(T source) {
        return (T) InvokerHelper.invokeConstructorOf(source.getClass(), null);
    }

    private OverwriteStrategy.Map getMapStrategy(Field field) {
        Overwrite.Map annotation = field.getAnnotation(Overwrite.Map.class);
        if (annotation != null && annotation.value() != OverwriteStrategy.Map.INHERIT)
            return annotation.value();
        return AnnotationHelper.getMostSpecificAnnotation(field.getDeclaringClass(), Overwrite.class, o -> o.maps().value() != OverwriteStrategy.Map.INHERIT)
                .map(Overwrite::maps)
                .map(Overwrite.Map::value)
                .orElse(OverwriteStrategy.Map.FULL_REPLACE);
    }

    private void copyFromCollectionField(Field field) {
        String fieldName = field.getName();
        Collection<Object> currentValue = proxy.getInstanceAttribute(fieldName);
        Collection<Object> templateValue = getProxyFor(source).getInstanceAttribute(fieldName);

        OverwriteStrategy.Collection strategy = getCollectionStrategy(field);

        switch (strategy) {
            case ADD:
                if (notEmpty(templateValue))
                    addCollectionValues(field, currentValue, templateValue);
                break;
            case REPLACE:
                if (notEmpty(templateValue)) {
                    currentValue.clear();
                    addCollectionValues(field, currentValue, templateValue);
                }
                break;
            case ALWAYS_REPLACE:
                currentValue.clear();
                if (notEmpty(templateValue))
                    addCollectionValues(field, currentValue, templateValue);
                break;
            case INHERIT:
            default:
                throwInvalidStrategy(strategy);
        }
    }

    private static boolean notEmpty(Collection<Object> templateValue) {
        return templateValue != null && !templateValue.isEmpty();
    }

    private void addCollectionValues(Field field, Collection<Object> currentValue, Collection<Object> templateValue) {
        Class<?> elementType = DslHelper.getClassFromType(DslHelper.getElementType(field));
        for (Object value : templateValue) {
            assertCorrectType(field, value, elementType);
            currentValue.add(copyValue(value));
        }
    }

    private static void assertCorrectType(Field field, Object value, Class<?> elementType) {
        if (!elementType.isInstance(value))
            throw new IllegalArgumentException("Element " + value + " in " + field + " is not of expected type " + elementType);
    }

    private OverwriteStrategy.Collection getCollectionStrategy(Field field) {
        Overwrite.Collection annotation = field.getAnnotation(Overwrite.Collection.class);
        if (annotation != null && annotation.value() != OverwriteStrategy.Collection.INHERIT)
            return annotation.value();
        return AnnotationHelper.getMostSpecificAnnotation(field.getDeclaringClass(), Overwrite.class, o -> o.collections().value() != OverwriteStrategy.Collection.INHERIT)
                .map(Overwrite::collections)
                .map(Overwrite.Collection::value)
                .orElse(OverwriteStrategy.Collection.REPLACE);
    }


}
