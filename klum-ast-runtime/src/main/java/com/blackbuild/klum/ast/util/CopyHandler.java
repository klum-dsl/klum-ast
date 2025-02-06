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
import org.codehaus.groovy.runtime.StringGroovyMethods;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.blackbuild.klum.ast.util.DslHelper.getFactoryOf;
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
    private final Object donor;

    /**
     * Copies properties from the donor to the target object. Copying is done according to the annotations on the target object's class and
     * can be customized by using the {@link Overwrite} annotation, as well as its inner class annotations.
     * @param target the object to copy to
     * @param donor the object to copy from
     */
    public static void copyToFrom(Object target, Object donor) {
        new CopyHandler(target, donor).doCopy();
    }

    public CopyHandler(Object target, Object donor) {
        this.target = target;
        proxy = getProxyFor(target);
        this.donor = donor;
    }

    public void doCopy() {
        if (donor instanceof Map)
            doCopyFromMap();
        else
            doCopyFromObject();
    }

    private void doCopyFromMap() {
        for (String key : ((Map<String, Object>) donor).keySet()) {
            doCopyNamedElement(key);
        }
    }

    private void doCopyFromObject() {
        DslHelper.getDslHierarchyOf(donor.getClass()).forEach(this::copyFromLayer);
    }

    private void copyFromLayer(Class<?> layer) {
        for (Field field : layer.getDeclaredFields()) {
            if ((field.getModifiers() & (ACC_SYNTHETIC | ACC_FINAL | ACC_TRANSIENT)) != 0) continue;
            String name = field.getName();

            doCopyNamedElement(name);
        }
    }

    private void doCopyNamedElement(String name) {
        if (name.startsWith("$")) return;
        if (name.startsWith("@")) return;

        Optional<Field> optionalField = DslHelper.getField(target.getClass(), name);

        if (optionalField.isEmpty())
            handleMissingFieldInTarget(name);
        else {
            Field targetField = optionalField.get();
            if (isNotIgnored(targetField))
                copyFromField(targetField);
        }
    }

    private void handleMissingFieldInTarget(String name) {
        throw new KlumModelException("Field " + name + " is missing in target object " + target);
    }

    @SuppressWarnings("java:S1126")
    private boolean isIgnored(Field field) {
        if (field.isAnnotationPresent(Key.class)) return true;
        if (field.isAnnotationPresent(Owner.class)) return true;
        if (field.isAnnotationPresent(Role.class)) return true;
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
        Object templateValue = getTemplateValue(fieldName, field.getType());

        OverwriteStrategy.Single strategy = getSingleStrategy(field);

        switch (strategy) {
            case REPLACE:
                if (templateValue != null)
                    replaceValue(field, templateValue);
                break;
            case ALWAYS_REPLACE:
                replaceValue(field, templateValue);
                break;
            case SET_IF_NULL:
                if (currentValue == null)
                    replaceValue(field, templateValue);
                break;
            case MERGE:
                if (templateValue != null) {
                    if (currentValue == null || !isDslType(field.getType()))
                        replaceValue(field, templateValue);
                    else
                        CopyHandler.copyToFrom(currentValue, templateValue);
                }
                break;
            case INHERIT:
            default:
                throwInvalidStrategy(strategy);
        }
    }

    private <T> T getTemplateValue(String fieldName, Class<T> type) {
        Object result;
        if (donor instanceof Map)
            result = ((Map<String, Object>) donor).get(fieldName);
        else
            result = getProxyFor(donor).getInstanceAttribute(fieldName);
        if (result != null && !(result instanceof Map) && !type.isInstance(result)) {
            if (result instanceof String)
                return coerceString((String) result, type);

            throw new IllegalArgumentException("Field " + fieldName + " is not of expected type " + type);
        }
        return (T) result;
    }

    private <T> T coerceString(String result, Class<T> type) {
        try {
            return StringGroovyMethods.asType(result, type);
        } catch (Exception e) {
            // ignore
        }

        List<Executable> converterMethods = ConverterHelper.getAllMatchingConverterMethods(type, String.class);
        if (converterMethods.isEmpty())
            throw new IllegalArgumentException("Field is not of expected type " + type);

        Executable converterMethod = converterMethods.get(0);
        if (converterMethod instanceof Method)
            return (T) InvokerHelper.invokeMethod(type, converterMethod.getName(), new Object[]{ result });
        if (converterMethod instanceof Constructor)
            return (T) InvokerHelper.invokeConstructorOf(type, new Object[]{ result });

        throw new IllegalArgumentException("String cannot be coerced to " + type);
    }

    private void replaceValue(Field field, Object templateValue) {
        Object valueCopy;
        if (templateValue instanceof Map) {
            valueCopy = getFactoryOf(field.getType()).FromMap((Map<String, Object>) templateValue);
        } else {
            valueCopy = copyValue(templateValue);
        }
        proxy.setInstanceAttribute(field.getName(), valueCopy);
    }

    @SuppressWarnings("unchecked")
    private static @Nullable <T> T copyValue(Object templateValue) {
        if (templateValue == null)
            return null;
        if (isDslType(templateValue.getClass()))
            return getProxyFor(templateValue).cloneInstance();
        if (templateValue instanceof Collection)
            return (T) createCopyOfCollection((Collection<Object>) templateValue);
        if (templateValue instanceof Map)
            return (T) createCopyOfMap((Map<String, Object>) templateValue);
        return (T) templateValue;
    }

    private OverwriteStrategy.Single getSingleStrategy(Field field) {
        Overwrite.Single annotation = AnnotationHelper.getNestedAnnotation(field, Overwrite.Single.class);
        if (annotation != null && annotation.value() != OverwriteStrategy.Single.INHERIT)
            return annotation.value();
        return AnnotationHelper.getMostSpecificAnnotation(field, Overwrite.class, o -> o.singles().value() != OverwriteStrategy.Single.INHERIT)
                .map(Overwrite::singles)
                .map(Overwrite.Single::value)
                .orElse(OverwriteStrategy.Single.MERGE);
    }

    private void copyFromMapField(Field field) {
        String fieldName = field.getName();
        Map<Object,Object> currentValues = proxy.getInstanceAttribute(fieldName);
        Map<Object,Object> templateValues = (Map<Object, Object>) getTemplateValue(fieldName, field.getType());

        if (templateValues == null)
            return;

        OverwriteStrategy.Map strategy = getMapStrategy(field);

        switch (strategy) {
            case FULL_REPLACE:
                if (!templateValues.isEmpty()) {
                    currentValues.clear();
                    addMapValues(field, currentValues, templateValues);
                }
                break;
            case SET_IF_EMPTY:
                if (currentValues.isEmpty())
                    addMapValues(field, currentValues, templateValues);
                break;
            case ALWAYS_REPLACE:
                currentValues.clear();
                addMapValues(field, currentValues, templateValues);
                break;
            case MERGE_KEYS:
                addMapValues(field, currentValues, templateValues);
                break;
            case MERGE_VALUES:
                if (isDslType(DslHelper.getElementType(field)))
                    mergeMapValues(field, currentValues, templateValues);
                else
                    addMapValues(field, currentValues, templateValues);
                break;
            case ADD_MISSING:
                addMissingMapValues(field, currentValues, templateValues);
                break;
            case INHERIT:
            default:
                throwInvalidStrategy(strategy);
        }
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
                CopyHandler.copyToFrom(currentValue, value);
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
        Overwrite.Map annotation = AnnotationHelper.getNestedAnnotation(field, Overwrite.Map.class);
        if (annotation != null && annotation.value() != OverwriteStrategy.Map.INHERIT)
            return annotation.value();
        return AnnotationHelper.getMostSpecificAnnotation(field, Overwrite.class, o -> o.maps().value() != OverwriteStrategy.Map.INHERIT)
                .map(Overwrite::maps)
                .map(Overwrite.Map::value)
                .orElse(OverwriteStrategy.Map.FULL_REPLACE);
    }

    private void copyFromCollectionField(Field field) {
        String fieldName = field.getName();
        Collection<Object> currentValue = proxy.getInstanceAttribute(fieldName);
        Collection<Object> templateValue = (Collection<Object>) getTemplateValue(fieldName, field.getType());

        if (templateValue == null) return;

        OverwriteStrategy.Collection strategy = getCollectionStrategy(field);

        switch (strategy) {
            case ADD:
                addCollectionValues(field, currentValue, templateValue);
                break;
            case REPLACE:
                if (!templateValue.isEmpty()) {
                    currentValue.clear();
                    addCollectionValues(field, currentValue, templateValue);
                }
                break;
            case SET_IF_EMPTY:
                if (currentValue.isEmpty())
                    addCollectionValues(field, currentValue, templateValue);
                break;
            case ALWAYS_REPLACE:
                currentValue.clear();
                addCollectionValues(field, currentValue, templateValue);
                break;
            case INHERIT:
            default:
                throwInvalidStrategy(strategy);
        }
    }

    private void addCollectionValues(Field field, Collection<Object> currentValue, Collection<Object> templateValue) {
        Class<?> elementType = DslHelper.getClassFromType(DslHelper.getElementType(field));
        for (Object value : templateValue) {
            assertCorrectType(field, value, elementType);
            currentValue.add(copyValue(value));
        }
    }

    private static void assertCorrectType(Field field, Object value, Class<?> elementType) {
        if (value != null && !(value instanceof Map) && !elementType.isInstance(value))
            throw new IllegalArgumentException("Element " + value + " in " + field + " is not of expected type " + elementType);
    }

    private OverwriteStrategy.Collection getCollectionStrategy(Field field) {
        Overwrite.Collection annotation = AnnotationHelper.getNestedAnnotation(field, Overwrite.Collection.class);
        if (annotation != null && annotation.value() != OverwriteStrategy.Collection.INHERIT)
            return annotation.value();
        return AnnotationHelper.getMostSpecificAnnotation(field, Overwrite.class, o -> o.collections().value() != OverwriteStrategy.Collection.INHERIT)
                .map(Overwrite::collections)
                .map(Overwrite.Collection::value)
                .orElse(OverwriteStrategy.Collection.REPLACE);
    }


}
