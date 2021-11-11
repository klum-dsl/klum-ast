package com.blackbuild.klum.ast.util;

import groovy.lang.Closure;

import java.util.HashMap;
import java.util.Map;

/**
 * Handles templates for a given dsl-class.
 */
public class TemplateManager {

    private static ThreadLocal<TemplateManager> INSTANCE = new ThreadLocal<>();

    public static TemplateManager getInstance() {
        if (INSTANCE.get() == null)
            INSTANCE.set(new TemplateManager());
        return INSTANCE.get();
    }

    Map<Class<?>, Object> templates = new HashMap<>();

    public static <T> Object withTemplate(Class<T> type, T template, Closure<?> body) {
        TemplateManager manager = getInstance();
        T oldTemplate = manager.getTemplate(type);
        manager.setTemplate(type,template);
        try {
            return body.call();
        } finally {
            manager.setTemplate(type, oldTemplate);
        }
    }

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
