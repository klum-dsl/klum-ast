package com.blackbuild.klum.ast.util;

import groovy.lang.Closure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Handles templates for a given dsl-class.
 */
public class TemplateManager {

    private static final ThreadLocal<TemplateManager> INSTANCE = new ThreadLocal<>();

    public static TemplateManager getInstance() {
        if (INSTANCE.get() == null)
            INSTANCE.set(new TemplateManager());
        return INSTANCE.get();
    }

    Map<Class<?>, Object> templates = new HashMap<>();

    public static <T> Object withTemplate(Class<T> type, T template, Closure<?> body) {
        TemplateManager manager = getInstance();
        T oldTemplate = manager.getTemplate(type);
        try {
            manager.setTemplate(type,template);
            return body.call();
        } finally {
            manager.setTemplate(type, oldTemplate);
        }
    }

    public static <T> Object withTemplate(Class<T> type, Map<String, Object> template, Closure<?> body) {
        T templateInstance = FactoryHelper.createAsTemplate(type, template, null);
        return withTemplate(type,templateInstance, body);
    }

    public static Object withTemplates(Map<Class<?>, Object> newTemplates, Closure<?> body) {
        if (newTemplates.isEmpty())
            return body.call();

        TemplateManager manager = getInstance();

        Map<Class<?>, Object> effectiveTemplates = newTemplates.entrySet().stream().collect(toMap(Map.Entry::getKey, TemplateManager::mapToTemplate));
        Map<Class<?>, Object> oldTemplates = new HashMap<>(manager.templates);

        try {
            manager.templates.putAll(effectiveTemplates);
            return body.call();
        } finally {
            manager.templates.clear();
            manager.templates.putAll(oldTemplates);
        }
    }

    private static Object mapToTemplate(Map.Entry<Class<?>, Object> entry) {
        if (entry.getValue() instanceof Map)
            return FactoryHelper.createAsTemplate(entry.getKey(), (Map<String, Object>) entry.getValue(), null);
        else
            return entry.getValue();
    }

    public static Object withTemplates(List<Object> newTemplates, Closure<?> body) {
        Map<Class<?>, Object> templateMap = newTemplates.stream().collect(toMap(TemplateManager::getRealType, identity()));
        return withTemplates(templateMap, body);
    }

    private static Class<?> getRealType(Object target) {
        Class<?> targetType = target.getClass();
        return targetType.isMemberClass() ? targetType.getSuperclass() : targetType;
    }

    @SuppressWarnings("unchecked")
    public <T> T getTemplate(Class<T> type) {
        return (T) templates.get(type);
    }

    public <T> void setTemplate(Class<T> type, T template) {
        if (template != null)
            templates.put(type, template);
        else
            templates.remove(type);
    }


}
