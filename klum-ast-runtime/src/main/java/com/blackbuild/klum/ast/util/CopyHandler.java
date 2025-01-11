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
import groovy.lang.GroovyObject;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static com.blackbuild.klum.ast.util.KlumInstanceProxy.getProxyFor;
import static groovyjarjarasm.asm.Opcodes.*;

/**
 * Handles the copying of properties from one object to another.
 * Will support different override strategies.
 */
public class CopyHandler {

    private final Object target;
    private final KlumInstanceProxy proxy;
    private final Object template;

    public static void copyFrom(GroovyObject instance, Object template) {
        new CopyHandler(instance, template).doCopy();
    }

    public CopyHandler(Object target, Object template) {
        this.target = target;
        proxy = getProxyFor(target);
        this.template = template;
    }

    public void doCopy() {
        DslHelper.getDslHierarchyOf(target.getClass()).forEach(this::copyFromLayer);
    }

    private void copyFromLayer(Class<?> layer) {
        if (layer.isInstance(template))
            Arrays.stream(layer.getDeclaredFields())
                    .filter(this::isNotIgnored)
                    .forEach(field -> copyFromField(field));
    }

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
        KlumInstanceProxy templateProxy = getProxyFor(template);
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
                throw new AssertionError("Unexpected strategy " + strategy + " encountered");
        }
    }

    private void replaceValue(String fieldName, Object templateValue) {
        if (DslHelper.isDslObject(templateValue)) {
            KlumInstanceProxy templateProxy = getProxyFor(templateValue);
            templateValue = templateProxy.cloneInstance();
        }
        proxy.setInstanceAttribute(fieldName, templateValue);
    }

    private OverwriteStrategy.Single getSingleStrategy(Field field) {
        Overwrite.Single annotation = field.getAnnotation(Overwrite.Single.class);
        if (annotation != null && annotation.value() != OverwriteStrategy.Single.INHERIT)
            return annotation.value();
        return AnnotationHelper.getMostSpecificAnnotation(field.getDeclaringClass(), Overwrite.class, o -> o.single().value() != OverwriteStrategy.Single.INHERIT)
                .map(Overwrite::single)
                .map(Overwrite.Single::value)
                .orElse(getDefaultSingleStrategy(field));
    }

    private OverwriteStrategy.Single getDefaultSingleStrategy(Field field) {
        return DslHelper.isDslType(field.getType()) ? OverwriteStrategy.Single.MERGE : OverwriteStrategy.Single.REPLACE;
    }

    private void copyFromMapField(Field field) {
        String fieldName = field.getName();
        Map<Object,Object> currentValues = proxy.getInstanceAttribute(fieldName);
        Map<Object,Object> templateValues = getProxyFor(template).getInstanceAttribute(fieldName);

        OverwriteStrategy.Map strategy = getMapStrategy(field);

        switch (strategy) {
            case FULL_REPLACE:
                if (templateValues != null && !templateValues.isEmpty()) {
                    currentValues.clear();
                    addMapValues(field, currentValues, templateValues);
                }
                break;
            case ALWAYS_REPLACE:
                currentValues.clear();
                if (templateValues != null && !templateValues.isEmpty())
                    addMapValues(field, currentValues, templateValues);
                break;
            case MERGE_KEYS:
                if (templateValues != null && !templateValues.isEmpty())
                    addMapValues(field, currentValues, templateValues);
                break;
            case MERGE_VALUES:
                if (templateValues != null && !templateValues.isEmpty())
                    mergeMapValues(field, currentValues, templateValues);
                break;
            case ADD_MISSING:
                if (templateValues != null && !templateValues.isEmpty())
                    addMissingMapValues(field, currentValues, templateValues);
                break;
            case INHERIT:
            default:
                throw new AssertionError("Unexpected strategy " + strategy + " encountered");
        }
    }

    private void addMissingMapValues(Field field, Map<Object, Object> currentValues, Map<Object, Object> templateValues) {
        if (templateValues == null || templateValues.isEmpty()) return;
        Class<?> valueType = DslHelper.getClassFromType(DslHelper.getElementType(field));
        for (Map.Entry<Object,Object> entry : templateValues.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            assertCorrectType(field, value, valueType);
            if (currentValues.containsKey(key))
                currentValues.put(key, getProxyFor(value).cloneInstance());
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
                currentValues.put(key, getProxyFor(value).cloneInstance());
            else
                getProxyFor(currentValue).copyFrom(value);
        }
    }

    private void addMapValues(Field field, Map<Object,Object> currentValues, Map<Object,Object> templateValues) {
        Class<?> valueType = DslHelper.getClassFromType(DslHelper.getElementType(field));
        boolean isDslType = DslHelper.isDslType(valueType);
        for (Map.Entry<Object,Object> entry : templateValues.entrySet()) {
            Object key = entry.getKey();
            Object value = entry.getValue();
            assertCorrectType(field, value, valueType);
            if (isDslType)
                currentValues.put(key, getProxyFor(value).cloneInstance());
            else
                currentValues.put(key, value);
        }
    }


    private OverwriteStrategy.Map getMapStrategy(Field field) {
        Overwrite.Map annotation = field.getAnnotation(Overwrite.Map.class);
        if (annotation != null && annotation.value() != OverwriteStrategy.Map.INHERIT)
            return annotation.value();
        return AnnotationHelper.getMostSpecificAnnotation(field.getDeclaringClass(), Overwrite.class, o -> o.map().value() != OverwriteStrategy.Map.INHERIT)
                .map(Overwrite::map)
                .map(Overwrite.Map::value)
                .orElse(getDefaultMapStrategy(field));
    }

    private OverwriteStrategy.Map getDefaultMapStrategy(Field field) {
        return DslHelper.isDslType(DslHelper.getElementType(field)) ? OverwriteStrategy.Map.MERGE_VALUES : OverwriteStrategy.Map.MERGE_KEYS;
    }

    private <T> void copyFromCollectionField(Field field) {
        String fieldName = field.getName();
        Collection<Object> currentValue = proxy.getInstanceAttribute(fieldName);
        Collection<Object> templateValue = getProxyFor(template).getInstanceAttribute(fieldName);

        OverwriteStrategy.Collection strategy = getCollectionStrategy(field);

        switch (strategy) {
            case ADD:
                if (templateValue != null && !templateValue.isEmpty())
                    addCollectionValues(field, currentValue, templateValue);
                break;
            case REPLACE:
                if (templateValue != null && !templateValue.isEmpty()) {
                    currentValue.clear();
                    addCollectionValues(field, currentValue, templateValue);
                }
                break;
            case ALWAYS_REPLACE:
                currentValue.clear();
                if (templateValue != null && !templateValue.isEmpty())
                    addCollectionValues(field, currentValue, templateValue);
                break;
            case INHERIT:
            default:
                throw new AssertionError("Unexpected strategy " + strategy + " encountered");
        }
    }

    private void addCollectionValues(Field field, Collection<Object> currentValue, Collection<Object> templateValue) {
        Class<?> elementType = DslHelper.getClassFromType(DslHelper.getElementType(field));
        boolean isDslType = DslHelper.isDslType(elementType);
        for (Object value : templateValue) {
            assertCorrectType(field, value, elementType);
            if (isDslType)
                currentValue.add(getProxyFor(value).cloneInstance());
            else
                currentValue.add(value);
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
        return AnnotationHelper.getMostSpecificAnnotation(field.getDeclaringClass(), Overwrite.class, o -> o.collection().value() != OverwriteStrategy.Collection.INHERIT)
                .map(Overwrite::collection)
                .map(Overwrite.Collection::value)
                .orElse(OverwriteStrategy.Collection.REPLACE);
    }


}
