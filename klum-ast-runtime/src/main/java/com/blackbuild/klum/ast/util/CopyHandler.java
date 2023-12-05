/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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
import org.codehaus.groovy.runtime.InvokerHelper;

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

    private final Object instance;
    private final KlumInstanceProxy proxy;

    public CopyHandler(Object instance) {
        this.instance = instance;
        proxy = getProxyFor(instance);
    }

    public void copyFrom(Object template) {
        DslHelper.getDslHierarchyOf(instance.getClass()).forEach(it -> copyFromLayer(it, template));
    }

    private void copyFromLayer(Class<?> layer, Object template) {
        if (layer.isInstance(template))
            Arrays.stream(layer.getDeclaredFields())
                    .filter(this::isNotIgnored)
                    .forEach(field -> copyFromField(field, template));
    }

    private boolean isIgnored(Field field) {
        if ((field.getModifiers() & (ACC_SYNTHETIC | ACC_FINAL | ACC_TRANSIENT)) != 0) return true;
        if (field.isAnnotationPresent(Key.class)) return true;
        if (field.isAnnotationPresent(Owner.class)) return true;
        if (field.getName().startsWith("$")) return true;
        if (DslHelper.getKlumFieldType(field) == FieldType.TRANSIENT) return true;
        return false;
    }

    private boolean isNotIgnored(Field field) {
        return !isIgnored(field);
    }

    private void copyFromField(Field field, Object template) {
        String fieldName = field.getName();
        Object templateValue = getProxyFor(template).getInstanceAttribute(fieldName);

        if (templateValue == null) return;

        if (templateValue instanceof Collection)
            copyFromCollectionField((Collection<?>) templateValue, fieldName);
        else if (templateValue instanceof Map)
            copyFromMapField((Map<?,?>) templateValue, fieldName);
        else
            proxy.setInstanceAttribute(fieldName, getCopiedValue(templateValue));
    }

    @SuppressWarnings("unchecked")
    private <T> T getCopiedValue(T templateValue) {
        if (isDslType(templateValue.getClass()))
            return (T) getProxyFor(templateValue).cloneInstance();
        else if (templateValue instanceof Collection)
            return (T) createCopyOfCollection((Collection) templateValue);
        else if (templateValue instanceof Map)
            return (T) createCopyOfMap((Map) templateValue);
        else
            return templateValue;
    }

    private <T> Collection<T> createCopyOfCollection(Collection<T> templateValue) {
        Collection<T> result = (Collection<T>) InvokerHelper.invokeConstructorOf(templateValue.getClass(), null);
        templateValue.stream().map(this::getCopiedValue).forEach(result::add);
        return result;
    }

    private <T> Map<String, T> createCopyOfMap(Map<String, T> templateValue) {
        Map<String, T> result = (Map<String, T>) InvokerHelper.invokeConstructorOf(templateValue.getClass(), null);
        templateValue.forEach((key, value) -> result.put(key, getCopiedValue(value)));
        return result;
    }

    private <K,V> void copyFromMapField(Map<K,V> templateValue, String fieldName) {
        if (templateValue.isEmpty()) return;
        Map<K,V> instanceField = proxy.getInstanceAttribute(fieldName);
        instanceField.clear();
        templateValue.forEach((k, v) -> instanceField.put(k, getCopiedValue(v)));
    }

    private <T> void copyFromCollectionField(Collection<T> templateValue, String fieldName) {
        if (templateValue.isEmpty()) return;
        Collection<T> instanceField = proxy.getInstanceAttribute(fieldName);
        instanceField.clear();

        templateValue.stream().map(this::getCopiedValue).forEach(instanceField::add);
    }

}
