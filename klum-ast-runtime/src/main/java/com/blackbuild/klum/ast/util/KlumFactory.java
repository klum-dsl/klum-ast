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

import com.blackbuild.groovy.configdsl.transform.DSL;
import groovy.lang.*;
import groovy.transform.Undefined;

import java.io.File;
import java.net.URL;
import java.util.Map;

import static com.blackbuild.klum.ast.util.DslHelper.requireDslType;
import static com.blackbuild.klum.ast.util.DslHelper.requireKeyed;

/**
 * Factory to create DSL model objects.
 * @param <T> The type of the DSL model object.
 */
@SuppressWarnings({"java:S100", "unused"})
public class KlumFactory<T> {

    protected final Class<T> type;
    protected KlumFactory(Class<T> type) {
        requireDslType(type);
        this.type = getTypeOrDefaultType(type);
    }

    private Class<T> getTypeOrDefaultType(Class<T> type) {
        DSL annotation = type.getAnnotation(DSL.class);
        Class<?> defaultImpl = annotation.defaultImpl();
        //noinspection unchecked - already verified via CheckDslDefaultImpl
        return defaultImpl.equals(Undefined.class) ? type : (Class<T>) defaultImpl;
    }

    /**
     * Creates a new instance of this owner's model type by reading the script from a well-defined properties file
     * placed in '/META-INF/klum-model/"schema-classname".properties'. The properties file must contain a property name
     * "model-class" which contains the name of the compiled script to run, which must return an instance of the
     * model class.
     * @see #From(Class)
     * @return The created model object.
     */
    public T FromClasspath() {
        return FactoryHelper.createFromClasspath(type);
    }

    /**
     * Creates a new instance of this owner's model type by reading the script from a well-defined properties file
     * placed in '/META-INF/klum-model/"schema-classname".properties'. The properties file must contain a property name
     * "model-class" which contains the name of the compiled script to run, which must return an instance of the
     * model class.
     * @see #From(Class)
     * @param loader The classloader to use to load the properties file.
     * @return The created model object.
     */
    public T FromClasspath(ClassLoader loader) {
        return FactoryHelper.createFromClasspath(type, loader);
    }

    /**
     * Creates a new instance of the model type by running the given script class. The script must either return an
     * instance of the model (i.e. contain something like 'MyClass.Create.With {...}') or must be a {@link groovy.util.DelegatingScript}
     * whose contents are the same as create/apply closure for this model class.
     * <p>
     *     Note that in case of a Keyed object in combination with a DelegatingScript, the simple name of the script class
     *     is used as key.
     * @param configurationScript The script class to run.
     * @return The instantiated object.
     */
    public T From(Class<? extends Script> configurationScript) {
        return FactoryHelper.createFrom(type, configurationScript);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given url to it. In case of a keyed model, the last part of the URL is used as the key.
     * @param configurationUrl The URL where to take the configuration text from.
     * @see #From(URL, ClassLoader)
     * @return The instantiated object.
     */
    public T From(URL configurationUrl) {
        return From(configurationUrl, null);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given url to it. In case of a keyed model, the last part of the URL is used as the key.
     * @param configurationUrl The URL where to take the configuration text from.
     * @param loader The classloader to use for compiling the configuration text.
     * @return The instantiated object.
     */
    public T From(URL configurationUrl, ClassLoader loader) {
        return FactoryHelper.createFrom(type, configurationUrl, loader);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given file to it. In case of a keyed model, the filename is used as the key.
     * @param configurationFile The file where to take the configuration text from.
     * @see #From(File, ClassLoader) )
     * @return The instantiated object.
     */
    public T From(File configurationFile) {
        return From(configurationFile, null);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given file to it. In case of a keyed model, the filename is used as the key.
     * @param configurationFile The file where to take the configuration text from.
     * @param loader The classloader to use for compiling the configuration text.
     * @return The instantiated object.
     */
    public T From(File configurationFile, ClassLoader loader) {
        return FactoryHelper.createFrom(type, configurationFile, loader);
    }

    /**
     * Creates a template instance of the model type. Templates differ from regular instances in the following way:
     *
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @see #Template(Map, Closure)
     * @return a template instance of the model type.
     */
    public T Template() {
        return Template(null, null);
    }

    /**
     * Creates a template instance of the model type. Templates differ from regular instances in the following way:
     *
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @see #Template(Map, Closure)
     * @param configuration The closure to apply to the template instance.
     * @return a template instance of the model type.
     */
    public T Template(Closure<?> configuration) {
        return Template(null, configuration);
    }

    /**
     * Creates a template instance of the model type. Templates differ from regular instances in the following way:
     *
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @see #Template(Map, Closure)
     * @param configMap The values to set on the template instance.
     * @return a template instance of the model type.
     */
    public T Template(Map<String, Object> configMap) {
        return Template(configMap, null);
    }

    /**
     * Creates a template instance of the model type. Templates differ from regular instances in the following way:
     *
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @param configMap The values to set on the template instance.
     * @param configuration The closure to apply to the template instance.
     * @return a template instance of the model type.
     */
    public T Template(Map<String, Object> configMap, Closure<?> configuration) {
        return FactoryHelper.createAsTemplate(type, configMap, configuration);
    }

    /**
     * Factory for keyed models.
     * @param <T> The type of the model.
     */
    @SuppressWarnings("java:S100")
    public abstract static class Keyed<T> extends KlumFactory<T> {

        protected Keyed(Class<T> type) {
            super(requireKeyed(type));
        }

        /**
         * Creates a new instance of the model by only setting the key, but not applying any configuration (apart from
         * 'postCreate' and 'postApply' methods).
         * @param key The key to use for the model.
         * @return The instantiated object.
         */
        public T One(String key) {
            return With(null, key, null);
        }

        /**
         * Convenience methods to allow simply replacing 'X.create' with 'X.Create.With' in scripts, without
         * checking for arguments. This means that empty create calls like 'X.create("bla")' will correctly work afterward.
         * @deprecated Use {@link #One(String)} instead.
         */
        @Deprecated
        public T With(String key) {
            return With(null, key, null);
        }

        /**
         * Creates a new instance of the model with the given key and applying the given configuration closure.
         * @param key The key to use for the model.
         * @param configuration The configuration closure to apply to the model.
         * @return The instantiated object.
         */
        public T With(String key, Closure<?> configuration) {
            return With(null, key, configuration);
        }

        /**
         * Creates a new instance of the model with the given key and applying the given configuration map.
         * @param configMap The configuration map to apply to the model.
         * @param key The key to use for the model.
         * @return The instantiated object.
         */
        public T With(Map<String, ?> configMap, String key) {
            return With(configMap, key, null);
        }

        /**
         * Creates a new instance of the model with the given key and applying the given configuration map and closure.
         * @param configMap The configuration map to apply to the model.
         * @param key The key to use for the model.
         * @param configuration The configuration closure to apply to the model.
         * @return The instantiated object.
         */
        public T With(Map<String, ?> configMap, String key, Closure<?> configuration) {
            return FactoryHelper.create(type, configMap, key, configuration);
        }

        /**
         * Creates a new instance of the model with the given key and then applying the given text as configuration.
         * @param key The key of the model to create.
         * @param configuration the configuration text to apply to the model.
         * @return The instantiated object.
         */
        public T From(String key, String configuration) {
            return From(key, configuration, null);
        }

        /**
         * Creates a new instance of the model with the given key and then applying the given text as configuration.
         * @param key The key of the model to create.
         * @param configuration the configuration text to apply to the model.
         * @param loader The classloader used to compile the configuration text.
         * @return The instantiated object.
         */
        public T From(String key, String configuration, ClassLoader loader) {
            return FactoryHelper.createFrom(type, key, configuration, loader);
        }
    }

    /**
     * Factory for unkeyed models.
     * @param <T> The type of the model.
     */
    @SuppressWarnings("java:S100")
    public abstract static class Unkeyed<T> extends KlumFactory<T> {
        protected Unkeyed(Class<T> type) {
            super(DslHelper.requireNotKeyed(type));
        }

        /**
         * Creates a new instance of the model applying any configuration (apart from
         * 'postCreate' and 'postApply' methods and any active templates).
         * @return The instantiated object.
         */
        public T One() {
            return With(null, null);
        }

        /**
         * Convenience methods to allow simply replacing 'X.create' with 'X.Create.With' in scripts, without
         * checking for arguments. This means that emtpy create calls like 'X.create()' will correctly work afterwards.
         * @deprecated Use {@link #One()} instead.
         */
        @Deprecated
        public T With() {
            return One();
        }

        /**
         * Creates a new instance of the model applying the given configuration closure.
         * @param configuration The configuration closure to apply to the model.
         * @return The instantiated object.
         */
        public T With(Closure<?> configuration) {
            return With(null, configuration);
        }

        /**
         * Creates a new instance of the model applying the given configuration map.
         * @param configMap The configuration map to apply to the model.
         * @return The instantiated object.
         */
        public T With(Map<String, ?> configMap) {
            return With(configMap, null);
        }

        /**
         * Creates a new instance of the model applying the given configuration map and closure.
         * @param configMap The configuration map to apply to the model.
         * @param configuration The configuration closure to apply to the model.
         * @return The instantiated object.
         */
        public T With(Map<String, ?> configMap, Closure<?> configuration) {
            return FactoryHelper.create(type, configMap, null, configuration);
        }

        /**
         * Creates a new instance of the model and then applying the given text as configuration.
         * @param configuration the configuration text to apply to the model.
         * @return The instantiated object.
         */
        public T From(String configuration) {
            return From(configuration, null);
        }

        /**
         * Creates a new instance of the model and then applying the given text as configuration.
         * @param configuration the configuration text to apply to the model.
         * @param loader The classloader used to compile the configuration text.
         * @return The instantiated object.
         */
        public T From(String configuration, ClassLoader loader) {
            return FactoryHelper.createFrom(type, null, configuration, loader);
        }
    }
}
