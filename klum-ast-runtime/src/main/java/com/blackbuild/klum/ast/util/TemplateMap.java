package com.blackbuild.klum.ast.util;

import java.util.HashMap;
import java.util.Map;

public enum TemplateMap {

    INSTANCE;

    private ThreadLocal<Map<Class<?>, Object>> templates = new ThreadLocal<>();

    public <T> T getTemplate(Class<T> type) {
        Map<Class<?>, Object> templateMap = templates.get();
        if (templateMap == null)
            return null;

        return (T) templateMap.get(type);
    }

    public void setTemplate(Object template) {
        Map<Class<?>, Object> templateMap = templates.get();
        if (templateMap == null) {
            templateMap = new HashMap<>();
            templates.set(templateMap);
        }
        templateMap.put(template.getClass(), template);
    }

    public void removeTemplate(Class<?> type) {
        Map<Class<?>, Object> templateMap = templates.get();
        if (templateMap == null)
            return;
        templateMap.remove(type);
        if (templateMap.isEmpty())
            templates.remove();
    }
}
