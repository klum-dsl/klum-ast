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

import groovy.lang.Closure;

import java.util.List;
import java.util.Map;

/**
 * A typed wrapper for TemplateManager. Provides all relevant methods without the need to
 * specify the first class parameter.
 *
 * @param <T> the type of the template being handled
 */
@SuppressWarnings("java:S100")
public class BoundTemplateHandler<T> {

    private final Class<T> type;

    public BoundTemplateHandler(Class<T> type) {
        this.type = type;
    }

    /**
     * Executes the given closure with the given template as the template.
     * This means that all objects of the owner's type created in the scope of the closure will use the given template,
     * which also includes objects deeper in the structure as well as objects created in a later phase.
     * The old template is restored after the closure has been executed.
     *
     * @param template the template
     * @param body     the closure to execute
     * @param <C>      the return type of the closure
     * @return the result of the closure
     */
    public <C> C With(T template, Closure<C> body) {
        return TemplateManager.withTemplate(type, template, body);
    }

    /**
     * Executes the given closure with an anonymous template.
     * This means that all objects of the owner's type created in the scope of the closure will use the given template,
     * which also includes objects deeper in the structure as well as objects created in a later phase.
     * The template will be created from the given map (using Create.AsTemplate(Map)).
     * The old template is restored after the closure has been executed.
     *
     * @param template the Map to construct the template from
     * @param body     the closure to execute
     * @param <C>      the return type of the closure
     * @return the result of the closure
     */
    public <C> C With(Map<String, Object> template, Closure<C> body) {
        return TemplateManager.withTemplate(type, template, body);
    }

    /**
     * Executes the given closure with the given anonymous templates.
     * This is done by converting the values of the map into template objects of the type defined by the keys.
     *
     * @param newTemplates the templates to apply, Mapping classes to their respective anonymous templates
     * @param body         the closure to execute
     * @param <C>          the return type of the closure
     * @return the result of the closure
     */
    public <C> C WithMultiple(Map<Class<?>, Map<String, Object>> newTemplates, Closure<C> body) {
        return TemplateManager.withTemplates(newTemplates, body);
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
    public <C> C WithMultiple(List<Object> newTemplates, Closure<C> body) {
        return TemplateManager.withTemplates(newTemplates, body);
    }

}