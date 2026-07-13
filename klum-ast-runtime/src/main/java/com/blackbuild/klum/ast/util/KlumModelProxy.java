/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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

import com.blackbuild.groovy.configdsl.transform.NoClosure;
import com.blackbuild.groovy.configdsl.transform.Owner;
import com.blackbuild.klum.ast.KlumModelObject;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.String.format;

/**
 * Serializable technical state belonging to a completed DSL Object.
 *
 * <p>This companion deliberately contains no construction-time mutation API and
 * never retains the Builder that created the model.</p>
 */
public final class KlumModelProxy implements Serializable {

    public static final String NAME_IN_MODEL = "$proxy";

    private final GroovyObject model;
    private final String breadcrumbPath;
    private String modelPath;
    private final Map<String, Object> metadata;
    private final Map<Integer, List<Closure<?>>> applyLaterClosures;
    private final Set<Class<?>> executedValidators = new HashSet<>();

    public KlumModelProxy(GroovyObject model, KlumBuilder.ModelState state) {
        this.model = model;
        this.breadcrumbPath = state.getBreadcrumbPath();
        this.modelPath = state.getModelPath();
        this.metadata = new HashMap<>(state.getMetadata());
        this.applyLaterClosures = new HashMap<>(state.getApplyLaterClosures());
    }

    /**
     * Returns the companion for a completed DSL Object.
     */
    public static KlumModelProxy getProxyFor(Object target) {
        if (target instanceof KlumModelProxy)
            return (KlumModelProxy) target;
        if (!(target instanceof KlumModelObject))
            throw new KlumException(format("Object of type %s is not a completed DSL Object", target.getClass().getName()));
        KlumModelProxy proxy = DslHelper.getFieldValue(target, NAME_IN_MODEL);
        if (proxy == null)
            throw new KlumException(format("Completed DSL Object %s has no model companion", target.getClass().getName()));
        return proxy;
    }

    public GroovyObject getModel() {
        return model;
    }

    public String getBreadcrumbPath() {
        return breadcrumbPath;
    }

    public String getModelPath() {
        return modelPath;
    }

    public void setModelPathIfAbsent(String path) {
        if (modelPath == null)
            modelPath = path;
    }

    public boolean hasMetaData(String key) {
        return metadata.containsKey(key);
    }

    public <T> T getMetaData(String key, Class<T> type) {
        Object value = metadata.get(key);
        if (value == null)
            return null;
        if (!type.isInstance(value))
            throw new KlumException(format("Metadata value for key '%s' is not of type %s", key, type.getName()));
        return type.cast(value);
    }

    public void setMetaData(String key, Object value) {
        metadata.put(key, value);
    }

    Map<Integer, List<Closure<?>>> getApplyLaterClosures() {
        return applyLaterClosures;
    }

    /**
     * Marks a validator as executed for this completed model.
     *
     * @return true only for the first execution of the validator type
     */
    public boolean markValidatorExecuted(Class<?> validatorType) {
        return executedValidators.add(validatorType);
    }

    public Object getSingleOwner() {
        Set<Object> owners = getOwners();
        if (owners.size() > 1)
            throw new KlumModelException("Object has more than one distinct owner");
        return owners.stream().findFirst().orElse(null);
    }

    public Set<Object> getOwners() {
        return DslHelper.getFieldsAnnotatedWith(model.getClass(), Owner.class)
                .filter(field -> field.getAnnotation(Owner.class).converter() == NoClosure.class)
                .filter(field -> !field.getAnnotation(Owner.class).transitive())
                .filter(field -> !field.getAnnotation(Owner.class).root())
                .map(Field::getName)
                .map(name -> DslHelper.getFieldValue(model, name))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
