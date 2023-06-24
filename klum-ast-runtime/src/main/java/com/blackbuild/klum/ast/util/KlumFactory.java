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

import com.blackbuild.groovy.configdsl.transform.DSL;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.Script;
import groovy.transform.Undefined;

import java.io.File;
import java.net.URL;
import java.util.Map;

import static com.blackbuild.klum.ast.util.DslHelper.isKeyed;
import static com.blackbuild.klum.ast.util.DslHelper.requireDslType;

/**
 * Factory to create DSL model objects. Two different subclasses handle keyed and unkeyed DSLs.
 * This allows for a cleaner API.
 * @param <T> The type of the DSL model object.
 */
@SuppressWarnings("java:S100")
public abstract class KlumFactory<T extends GroovyObject> {

    protected final Class<T> type;

    public static <C extends GroovyObject> KlumFactory<C> create(Class<C> type) {
        requireDslType(type);
        if (isKeyed(type))
            return new KlumKeyedFactory<>(type);
        else
            return new KlumUnkeyedFactory<>(type);
    }

    protected KlumFactory(Class<T> type) {
        requireDslType(type);
        this.type = getTypeOrDefaultType(type);
    }

    private Class<T> getTypeOrDefaultType(Class<T> type) {
        DSL annotation = type.getAnnotation(DSL.class);
        Class<?> defaultImpl = annotation.defaultImpl();
        return defaultImpl.equals(Undefined.class) ? type : (Class<T>) defaultImpl;
    }

    public T FromClasspath() {
        return FactoryHelper.createFromClasspath(type);
    }

    public T FromClasspath(ClassLoader loader) {
        return FactoryHelper.createFromClasspath(type, loader);
    }

    public T From(Class<? extends Script> scriptClass) {
        return FactoryHelper.createFrom(type, scriptClass);
    }

    public T From(String name, String text) {
        return From(name, text, null);
    }

    public T From(String name, String text, ClassLoader loader) {
        return FactoryHelper.createFrom(type, name, text, loader);
    }

    public T From(URL src) {
        return From(src, null);
    }

    public T From(URL src, ClassLoader loader) {
        return FactoryHelper.createFrom(type, src, loader);
    }

    public T From(File file) {
        return From(file, null);
    }

    public T From(File file, ClassLoader loader) {
        return FactoryHelper.createFrom(type, file, loader);
    }

    public T Template() {
        return Template(null, null);
    }

    public T Template(Closure<?> body) {
        return Template(null, body);
    }

    public T Template(Map<String, Object> values) {
        return Template(values, null);
    }

    public T Template(Map<String, Object> values, Closure<?> body) {
        return FactoryHelper.createAsTemplate(type, values, body);
    }
}
