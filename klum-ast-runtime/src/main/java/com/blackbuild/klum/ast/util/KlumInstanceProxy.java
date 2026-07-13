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

import com.blackbuild.klum.ast.KlumModelObject;
import com.blackbuild.klum.ast.process.KlumPhase;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.Script;
import org.codehaus.groovy.reflection.CachedField;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builder-only compatibility adapter for code written against the former RW proxy.
 *
 * <p>New runtime code belongs on {@link KlumBuilder} or {@link KlumModelProxy}.
 * Looking up this adapter for a completed model is intentionally rejected.</p>
 */
@Deprecated
@SuppressWarnings("unused")
public final class KlumInstanceProxy {

    /** Legacy generated-layout constants retained while #394 is unresolved. */
    public static final String NAME_OF_RW_FIELD_IN_MODEL_CLASS = "$rw";
    public static final String NAME_OF_PROXY_FIELD_IN_MODEL_CLASS = "$proxy";
    public static final String NAME_OF_MODEL_FIELD_IN_RW_CLASS = "this$0";

    public static final String ADD_NEW_DSL_ELEMENT_TO_COLLECTION = KlumBuilder.ADD_NEW_DSL_ELEMENT_TO_COLLECTION;
    public static final String ADD_ELEMENTS_FROM_SCRIPTS_TO_COLLECTION = KlumBuilder.ADD_ELEMENTS_FROM_SCRIPTS_TO_COLLECTION;
    public static final String ADD_ELEMENTS_FROM_SCRIPTS_TO_MAP = KlumBuilder.ADD_ELEMENTS_FROM_SCRIPTS_TO_MAP;

    private final KlumBuilder<?> builder;

    public KlumInstanceProxy(GroovyObject target) {
        if (!(target instanceof KlumBuilder))
            throw completedModelLookupFailure(target);
        this.builder = (KlumBuilder<?>) target;
    }

    private KlumInstanceProxy(KlumBuilder<?> builder) {
        this.builder = builder;
    }

    KlumBuilder<?> getBuilder() {
        return builder;
    }

    public static KlumInstanceProxy getProxyFor(Object target) {
        if (target instanceof KlumInstanceProxy)
            return (KlumInstanceProxy) target;
        if (target instanceof KlumBuilder)
            return new KlumInstanceProxy((KlumBuilder<?>) target);
        if (target instanceof KlumModelObject)
            throw completedModelLookupFailure(target);
        throw new KlumException("Object of type " + target.getClass().getName() + " is neither a Builder nor a DSL Object");
    }

    private static KlumException completedModelLookupFailure(Object target) {
        return new KlumException("KlumInstanceProxy is Builder-only; completed DSL Object "
                + target.getClass().getName()
                + " must use KlumModelProxy for model metadata or a generated factory for construction");
    }

    protected GroovyObject getRwInstance() {
        return builder;
    }

    public Object getDSLInstance() {
        return builder;
    }

    public <T> T getInstanceAttribute(String name) {
        return builder.getInstanceAttribute(name);
    }

    public <T> T getInstanceAttributeOrGetter(String name) {
        return builder.getInstanceAttributeOrGetter(name);
    }

    public Object getInstanceProperty(String name) {
        return builder.getInstanceProperty(name);
    }

    void setInstanceAttribute(String name, Object value) {
        builder.setInstanceAttribute(name, value);
    }

    Field getField(String name) {
        return builder.getField(name);
    }

    CachedField getCachedField(String name) {
        return DslHelper.getCachedField(builder.getClass(), name).orElseThrow();
    }

    public Object apply(Map<String, ?> values, Closure<?> body) {
        return builder.apply(values, body);
    }

    void applyOnly(Map<String, ?> values, Closure<?> body) {
        builder.applyOnly(values, body);
    }

    public void copyFrom(Object template) {
        builder.copyFrom(template);
    }

    public <T> T cloneInstance() {
        throw new KlumException("Builder cloning was removed; templates rehydrate completed recipes into fresh Builders");
    }

    Object getKey() {
        return builder.getKey();
    }

    Object getNullableKey() {
        return builder.getNullableKey();
    }

    public Object getSingleOwner() {
        return builder.getSingleOwner();
    }

    public Set<Object> getOwners() {
        return builder.getOwners();
    }

    public <T> T createSingleChild(Map<String, Object> values, String name, Class<T> type, boolean explicitType, String key, Closure<T> body) {
        return builder.createSingleChild(values, name, type, explicitType, key, body);
    }

    public <T> T setSingleField(String name, T value) {
        return builder.setSingleField(name, value);
    }

    public <T> T setSingleFieldViaConverter(String name, Class<?> converterType, String converterMethod, Object... args) {
        return builder.setSingleFieldViaConverter(name, converterType, converterMethod, args);
    }

    public <T> T addElementToCollection(String name, T value) {
        return builder.addElementToCollection(name, value);
    }

    public <T> T addElementToCollectionViaConverter(String name, Class<?> converterType, String converterMethod, Object... args) {
        return builder.addElementToCollectionViaConverter(name, converterType, converterMethod, args);
    }

    public <T> T addNewDslElementToCollection(Map<String, Object> values, String name, Class<? extends T> type, boolean explicitType, String key, Closure<T> body) {
        return builder.addNewDslElementToCollection(values, name, type, explicitType, key, body);
    }

    public void addElementsToCollection(String name, Object... values) {
        builder.addElementsToCollection(name, values);
    }

    public void addElementsToCollection(String name, Iterable<?> values) {
        builder.addElementsToCollection(name, values);
    }

    public <K, V> void addElementsToMap(String name, Map<K, V> values) {
        builder.addElementsToMap(name, values);
    }

    public <V> void addElementsToMap(String name, Iterable<V> values) {
        builder.addElementsToMap(name, values);
    }

    public void addElementsToMap(String name, Object... values) {
        builder.addElementsToMap(name, values);
    }

    public <T> T addNewDslElementToMap(Map<String, Object> values, String name, Class<? extends T> type, boolean explicitType, String key, Closure<T> body) {
        return builder.addNewDslElementToMap(values, name, type, explicitType, key, body);
    }

    public <K, V> V addElementToMap(String name, K key, V value) {
        return builder.addElementToMap(name, key, value);
    }

    public <K, V> V addElementToMapViaConverter(String name, Class<?> converterType, String converterMethod, K key, Object... args) {
        return builder.addElementToMapViaConverter(name, converterType, converterMethod, key, args);
    }

    public final void addElementsFromScriptsToCollection(String name, Class<? extends Script>... scripts) {
        builder.addElementsFromScriptsToCollection(name, scripts);
    }

    public final void addElementsFromScriptsToMap(String name, Class<? extends Script>... scripts) {
        builder.addElementsFromScriptsToMap(name, scripts);
    }

    Object invokeRwMethod(String name, Object... args) {
        return builder.invokeRwMethod(name, args);
    }

    void copyFromTemplate() {
        builder.copyFromTemplate();
    }

    Optional<String> resolveKeyForFieldFromAnnotation(String name, AnnotatedElement field) {
        return builder.resolveKeyForFieldFromAnnotation(name, field);
    }

    public String getBreadcrumbPath() {
        return builder.getBreadcrumbPath();
    }

    public void setBreadcrumbPath(String path) {
        builder.setBreadcrumbPath(path);
    }

    public void increaseBreadcrumbQuantifier() {
        builder.increaseBreadcrumbQuantifier();
    }

    void setCurrentTemplates(Map<Class<?>, Object> templates) {
        builder.setCurrentTemplates(templates);
    }

    public Map<Class<?>, Object> getCurrentTemplates() {
        return builder.getCurrentTemplates();
    }

    public void applyLater(Closure<?> closure) {
        builder.applyLater(closure);
    }

    public void applyLater(KlumPhase phase, Closure<?> closure) {
        builder.applyLater(phase, closure);
    }

    public void applyLater(Integer phase, Closure<?> closure) {
        builder.applyLater(phase, closure);
    }

    public void executeApplyLaterClosures(int phase) {
        builder.executeApplyLaterClosures(phase);
    }

    public void cleanup() {
        builder.cleanup();
    }

    public boolean hasMetaData(String key) {
        return builder.hasMetaData(key);
    }

    public <T> T getMetaData(String key, Class<T> type) {
        return builder.getMetaData(key, type);
    }

    public void setMetaData(String key, Object value) {
        builder.setMetaData(key, value);
    }

    public void setModelPath(String path) {
        builder.setModelPath(path);
    }

    public String getModelPath() {
        return builder.getModelPath();
    }
}
