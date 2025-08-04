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

import com.blackbuild.annodocimal.annotations.InlineJavadocs;
import groovy.lang.Closure;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

/**
 * Handles templates for a given dsl-class.
 * <p>
 * The template manager is a thread-local singleton that can be used to apply templates to objects created in a given scope.
 * All important methods are static and can be used from anywhere in the code.
 * </p>
 */
@InlineJavadocs
public class TemplateManager {

    private static final ThreadLocal<TemplateManager> INSTANCE = new ThreadLocal<>();

    /**
     * Returns the current instance of the TemplateManager.
     * If no instance is active, a new one is created.
     *
     * @return the current instance
     */
    public static TemplateManager getInstance() {
        if (INSTANCE.get() == null)
            INSTANCE.set(new TemplateManager());
        return INSTANCE.get();
    }

    private TemplateManager() {
        // Thread local singleton
    }


    private void deregister() {
        if (templates.isEmpty())
            INSTANCE.remove();
    }

    private final Map<Class<?>, Object> templates = new HashMap<>();

    Map<Class<?>, Object> getCurrentTemplates() {
        return new HashMap<>(templates);
    }

    /**
     * Executes the given closure with the given template as the template for the given type.
     * This means that all objects of the given type created in the scope of the closure will use the given template,
     * which also includes objects deeper in the structure.
     * The old template is restored after the closure has been executed.
     *
     * @param type     the type for which the template will be applied
     * @param template the template
     * @param body     the closure to execute
     * @param <T>      the type of the template
     * @return the result of the closure
     */
    public static <T, C> T withTemplate(Class<C> type, C template, Closure<T> body) {
        TemplateManager manager = getInstance();
        C oldTemplate = manager.getTemplate(type);
        try {
            manager.setTemplate(type, template);
            return body.call();
        } finally {
            manager.setTemplate(type, oldTemplate);
            manager.deregister();
        }
    }

    /**
     * Executes the given closure with an anonymous template for the given type.
     * This means that all objects of the given type created in the scope of the closure will use the given template,
     * which also includes objects deeper in the structure.
     * The template will be created from the given map (using Create.AsTemplate(Map)).
     * The old template is restored after the closure has been executed.
     *
     * @param type     the type for which the template will be applied
     * @param template the Map to construct the template from
     * @param body     the closure to execute
     * @param <T>      the type of the template
     * @return the result of the closure
     */
    public static <T,C> T withTemplate(Class<C> type, Map<String, ?> template, Closure<T> body) {
        C templateInstance = FactoryHelper.createAsTemplate(type, template, null);
        return withTemplate(type, templateInstance, body);
    }

    /**
     * Executes the given closure with the given templates.
     * This means that all objects of the given types created in the scope of the closure will use the given template,
     * which also includes objects deeper in the structure.
     * The old templates are restored after the closure has been executed. Usually it
     * is better to use {@link #withTemplates(List, Closure)}, which maps the templates
     * to their respective classes.
     *
     * @param newTemplates the templates to apply, Mapping classes to their respective templates
     * @param body         the closure to execute
     * @return the result of the closure
     */
    public static <T> T doWithTemplates(Map<Class<?>, ?> newTemplates, Closure<T> body) {
        if (newTemplates.isEmpty())
            return body.call();

        TemplateManager manager = getInstance();
        Map<Class<?>, ?> oldTemplates = new HashMap<>(manager.templates);

        try {
            manager.addTemplates(newTemplates);
            return body.call();
        } finally {
            manager.setTemplates(oldTemplates);
            manager.deregister();
        }
    }

    /**
     * Executes the given closure with the given anonymous templates.
     * This is done by converting the values of the map into template objects of the type defined by the keys.
     *
     * @param newTemplates the templates to apply, Mapping classes to their respective anonymous templates
     * @param body         the closure to execute
     * @return the result of the closure
     */
    public static <T> T withTemplates(Map<Class<?>, Map<String, ?>> newTemplates, Closure<T> body) {
        if (newTemplates.isEmpty())
            return body.call();

        Map<Class<?>, Object> effectiveTemplates = newTemplates.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, TemplateManager::mapToTemplate));

        return doWithTemplates(effectiveTemplates, body);
    }

    private static Object mapToTemplate(Map.Entry<Class<?>, Map<String, ?>> entry) {
        return FactoryHelper.createAsTemplate(entry.getKey(), entry.getValue(), null);
    }

    /**
     * Executes the given closure with the given templates.
     * This means that all objects of the given types created in the scope of the closure will use the given template,
     * which also includes objects deeper in the structure.
     * The old templates are restored after the closure has been executed.
     *
     * @param newTemplates the templates to apply
     * @param body         the closure to execute
     * @return the result of the closure
     */
    public static <T> T withTemplates(List<Object> newTemplates, Closure<T> body) {
        Map<Class<?>, Object> templateMap = newTemplates.stream().collect(toMap(TemplateManager::getRealType, identity()));
        return doWithTemplates(templateMap, body);
    }

    private static Class<?> getRealType(Object target) {
        Class<?> targetType = target.getClass();
        return targetType.isMemberClass() ? targetType.getSuperclass() : targetType;
    }

    /**
     * Returns the currently active template for the given type.
     *
     * @param type The type of the template
     * @param <T>  the type of the template
     * @return the template or null if no template is active
     */
    @SuppressWarnings("unchecked")
    public <T> T getTemplate(Class<T> type) {
        return (T) templates.get(type);
    }

    /**
     * Sets the template for the given type. If the template is null, the template is removed.
     *
     * @param type     the type of the template
     * @param template the template
     * @param <T>      the type of the template
     */
    public <T> void setTemplate(Class<T> type, T template) {
        if (template != null)
            templates.put(type, template);
        else
            templates.remove(type);
    }

    /**
     * Adds the given templates to the current templates.
     *
     * @param newTemplates the templates to add
     */
    public void addTemplates(Map<Class<?>, ?> newTemplates) {
        templates.putAll(newTemplates);
    }

    /**
     * Sets the templates to the given templates. Removes all existing templates.
     *
     * @param newTemplates the templates to set
     */
    public void setTemplates(Map<Class<?>, ?> newTemplates) {
        templates.clear();
        addTemplates(newTemplates);
    }


}
