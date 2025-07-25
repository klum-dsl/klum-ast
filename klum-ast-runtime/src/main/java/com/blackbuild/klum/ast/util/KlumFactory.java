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
import groovy.lang.Script;

import java.io.File;
import java.net.URL;
import java.util.Map;
import java.util.function.Function;

import static com.blackbuild.klum.ast.util.DslHelper.requireDslType;
import static com.blackbuild.klum.ast.util.DslHelper.requireKeyed;

/**
 * Factory to create DSL model objects.
 *
 * @param <T> The type of the DSL model object.
 */
@SuppressWarnings({"java:S100", "unused"})
@InlineJavadocs
public class KlumFactory<T> {

    protected final Class<T> type;

    protected KlumFactory(Class<T> type) {
        requireDslType(type);
        this.type = FactoryHelper.getTypeOrDefaultType(type);
    }

    /**
     * Creates a new instance of this owner's model type by reading the script from a well-defined properties file
     * placed in '/META-INF/klum-model/"schema-classname".properties'. The properties file must contain a property name
     * "model-class" which contains the name of the compiled script to run, which must return an instance of the
     * model class.
     *
     * @return The created model object.
     * @see #From(Class)
     */
    public T FromClasspath() {
        return FactoryHelper.createFromClasspath(type);
    }

    /**
     * Creates a new instance of this owner's model type by reading the script from a well-defined properties file
     * placed in '/META-INF/klum-model/"schema-classname".properties'. The properties file must contain a property name
     * "model-class" which contains the name of the compiled script to run, which must return an instance of the
     * model class.
     *
     * @param loader The classloader to use to load the properties file.
     * @return The created model object.
     * @see #From(Class)
     */
    public T FromClasspath(ClassLoader loader) {
        return FactoryHelper.createFromClasspath(type, loader);
    }

    /**
     * Creates a new instance of the model type by running the given script class. The script must either return an
     * instance of the model (i.e. contain something like 'MyClass.Create.With {...}') or must be a {@link groovy.util.DelegatingScript}
     * whose contents are the same as create/apply closure for this model class.
     * <p>
     * Note that in case of a Keyed object in combination with a DelegatingScript, the simple name of the script class
     * is used as key.
     *
     * @param configurationScript The script class to run.
     * @return The instantiated object.
     */
    public T From(Class<? extends Script> configurationScript) {
        return FactoryHelper.createFrom(type, configurationScript);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given url to it. In case of a keyed model, the last part of the URL is used as the key.
     *
     * @param configurationUrl The URL where to take the configuration text from.
     * @return The instantiated object.
     * @see #From(URL, ClassLoader)
     */
    public T From(URL configurationUrl) {
        return FactoryHelper.createFrom(type, configurationUrl, null, null);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given url to it. In case of a keyed model, the last part of the URL is used as the key.
     *
     * @param configurationUrl The URL where to take the configuration text from.
     * @param keyProvider      A function that derives the key from the URL.
     * @return The instantiated object.
     * @see #From(URL, ClassLoader)
     */
    public T From(URL configurationUrl, Function<URL, String> keyProvider) {
        return FactoryHelper.createFrom(type, configurationUrl, keyProvider, null);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given url to it. In case of a keyed model, the last part of the URL is used as the key.
     *
     * @param configurationUrl The URL where to take the configuration text from.
     * @param loader           The classloader to use for compiling the configuration text.
     * @return The instantiated object.
     */
    public T From(URL configurationUrl, ClassLoader loader) {
        return FactoryHelper.createFrom(type, configurationUrl, null, loader);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given url to it. In case of a keyed model, the key is derived from the URL using the provided keyProvider.
     *
     * @param configurationUrl The URL where to take the configuration text from.
     * @param keyProvider      A function that derives the key from the URL.
     * @param loader           The classloader to use for compiling the configuration text.
     * @return The instantiated object.
     */
    public T From(URL configurationUrl, Function<URL, String> keyProvider, ClassLoader loader) {
        return FactoryHelper.createFrom(type, configurationUrl, keyProvider, loader);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given file to it. In case of a keyed model, the filename is used as the key.
     *
     * @param configurationFile The file where to take the configuration text from.
     * @return The instantiated object.
     * @see #From(File, ClassLoader)
     */
    public T From(File configurationFile) {
        return FactoryHelper.createFrom(type, configurationFile, null, null);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given file to it. In case of a keyed model, provided keyProvider is used to derive the key from the file object.
     *
     * @param configurationFile The file where to take the configuration text from.
     * @param keyProvider       A function that derives the key from the file.
     * @param loader            The classloader to use for compiling the configuration text.
     * @return The instantiated object.
     */
    public T From(File configurationFile, Function<File, String> keyProvider, ClassLoader loader) {
        return FactoryHelper.createFrom(type, configurationFile, keyProvider, loader);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given file to it. In case of a keyed model, the filename is used as the key.
     *
     * @param configurationFile The file where to take the configuration text from.
     * @return The instantiated object.
     * @see #From(File, ClassLoader)
     */
    public T From(File configurationFile, Function<File, String> keyProvider) {
        return FactoryHelper.createFrom(type, configurationFile, keyProvider, null);
    }

    /**
     * Creates a new instance of the model type by instantiating the class and applying the text content of the
     * given file to it. In case of a keyed model, the filename is used as the key.
     *
     * @param configurationFile The file where to take the configuration text from.
     * @param loader            The classloader to use for compiling the configuration text.
     * @return The instantiated object.
     */
    public T From(File configurationFile, ClassLoader loader) {
        return FactoryHelper.createFrom(type, configurationFile, null, loader);
    }

    /**
     * Creates a new instance of the model type by applying the given configuration map. By default, this
     * takes the key from a map entry that is named as the key field, other values of the map are set to their
     * respective fields.
     * @param configMap a map containing the values to set on the model.
     * @return The instantiated object.
     */
    public T FromMap(Map<String, Object> configMap) {
        return FactoryHelper.createFromMap(type, configMap);
    }

    /**
     * Creates a template instance of the model type.
     * <p>
     * Templates differ from regular instances in the following way:
     * </p>
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @return a template instance of the model type.
     * @see #Template(Map, Closure)
     */
    public T Template() {
        return FactoryHelper.createAsTemplate(type, null, (Closure<?>) null);
    }

    /**
     * Creates a template instance of the model type.
     * <p>
     * Templates differ from regular instances in the following way:
     * </p>
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @param configMap     The values to set on the template instance.
     * @param configuration The closure to apply to the template instance.
     * @return a template instance of the model type.
     */
    public T Template(Map<String, Object> configMap, Closure<?> configuration) {
        return FactoryHelper.createAsTemplate(type, configMap, configuration);
    }

    /**
     * Creates a template instance of the model type.
     * <p>
     * Templates differ from regular instances in the following way:
     * </p>
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @param configuration The closure to apply to the template instance.
     * @return a template instance of the model type.
     * @see #Template(Map, Closure)
     */
    public T Template(Closure<?> configuration) {
        return FactoryHelper.createAsTemplate(type, null, configuration);
    }

    /**
     * Creates a template instance of the model type.
     * <p>
     * Templates differ from regular instances in the following way:
     * </p>
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @param configMap The values to set on the template instance.
     * @return a template instance of the model type.
     * @see #Template(Map, Closure)
     */
    public T Template(Map<String, Object> configMap) {
        return FactoryHelper.createAsTemplate(type, configMap, null);
    }

    /**
     * Creates a template instance of the model type by applying the given text as configuration.
     * <p>
     * As with {@link #From(File)},
     * the file is converted into a DelegatingScript which is then executed to create the model instance.
     * Templates differ from regular instances in the following way:
     * </p>
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @param scriptFile The script to configure the template
     * @return a template instance of the model type.
     */
    public T TemplateFrom(File scriptFile) {
        return FactoryHelper.createAsTemplate(type, scriptFile, null);
    }

    /**
     * Creates a template instance of the model type by applying the given text as configuration.
     * <p>
     * As with {@link #From(File)},
     * the file is converted into a DelegatingScript which is then executed to create the model instance.
     * Templates differ from regular instances in the following way:
     * </p>
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @param scriptFile The script to configure the template
     * @param loader     The classloader to use for compiling the configuration script.
     * @return a template instance of the model type.
     */
    public T TemplateFrom(File scriptFile, ClassLoader loader) {
        return FactoryHelper.createAsTemplate(type, scriptFile, loader);
    }

    /**
     * Creates a template instance of the model type by applying the given text as configuration.
     * <p>
     * As with {@link #From(File)},
     * the file is converted into a DelegatingScript which is then executed to create the model instance.
     * Templates differ from regular instances in the following way:
     * </p>
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @param scriptUrl The script to configure the template
     * @return a template instance of the model type.
     */
    public T TemplateFrom(URL scriptUrl) {
        return FactoryHelper.createAsTemplate(type, scriptUrl, null);
    }

    /**
     * Creates a template instance of the model type by applying the given text as configuration.
     * <p>
     * As with {@link #From(File)},
     * the file is converted into a DelegatingScript which is then executed to create the model instance.
     * Templates differ from regular instances in the following way:
     * </p>
     * <ul>
     *     <li>Template instances can even be created for abstract model classes using a synthetic subclass</li>
     *     <li>the key of a template model is always null</li>
     *     <li>owner fields are not set</li>
     *     <li>post-apply methods are not called</li>
     * </ul>
     *
     * @param scriptUrl The script to configure the template
     * @param loader    The classloader to use for compiling the configuration script.
     * @return a template instance of the model type.
     */
    public T TemplateFrom(URL scriptUrl, ClassLoader loader) {
        return FactoryHelper.createAsTemplate(type, scriptUrl, loader);
    }

    /**
     * Factory for keyed models.
     *
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
         *
         * @param key The key to use for the model.
         * @return The instantiated object.
         */
        public T One(String key) {
            return FactoryHelper.create(type, null, key, null);
        }

        /**
         * Creates a new instance of the model with the given key and applying the given configuration map and closure.
         *
         * @param configMap     The configuration map to apply to the model.
         * @param key           The key to use for the model.
         * @param configuration The configuration closure to apply to the model.
         * @return The instantiated object.
         */
        public T With(Map<String, ?> configMap, String key, Closure<?> configuration) {
            return FactoryHelper.create(type, configMap, key, configuration);
        }

        /**
         * Convenience methods to allow simply replacing 'X.create' with 'X.Create.With' in scripts, without
         * checking for arguments. This means that empty create calls like 'X.create("bla")' will correctly work afterward.
         *
         * @param key The key to use for the model
         * @return The instantiated object.
         * @deprecated Use {@link #One(String)} instead.
         */
        @Deprecated(forRemoval = true)
        public T With(String key) {
            return FactoryHelper.create(type, null, key, null);
        }

        /**
         * Creates a new instance of the model with the given key and applying the given configuration closure.
         *
         * @param key           The key to use for the model.
         * @param configuration The configuration closure to apply to the model.
         * @return The instantiated object.
         */
        public T With(String key, Closure<?> configuration) {
            return FactoryHelper.create(type, null, key, configuration);
        }

        /**
         * Creates a new instance of the model with the given key and applying the given configuration map.
         *
         * @param configMap The configuration map to apply to the model.
         * @param key       The key to use for the model.
         * @return The instantiated object.
         */
        public T With(Map<String, ?> configMap, String key) {
            return FactoryHelper.create(type, configMap, key, null);
        }

        /**
         * Creates a new instance of the model with the given key and then applying the given text as configuration.
         *
         * @param key           The key of the model to create.
         * @param configuration the configuration text to apply to the model.
         * @return The instantiated object.
         */
        public T From(String key, String configuration) {
            return FactoryHelper.createFrom(type, key, configuration, null);
        }

        /**
         * Creates a new instance of the model with the given key and then applying the given text as configuration.
         *
         * @param key           The key of the model to create.
         * @param configuration the configuration text to apply to the model.
         * @param loader        The classloader used to compile the configuration text.
         * @return The instantiated object.
         */
        public T From(String key, String configuration, ClassLoader loader) {
            return FactoryHelper.createFrom(type, key, configuration, loader);
        }
    }

    /**
     * Factory for unkeyed models.
     *
     * @param <T> The type of the model.
     */
    @SuppressWarnings("java:S100")
    public abstract static class Unkeyed<T> extends KlumFactory<T> {
        protected Unkeyed(Class<T> type) {
            super(DslHelper.requireNotKeyed(type));
        }

        /**
         * Convenience methods to allow simply replacing 'X.create' with 'X.Create.With' in scripts, without
         * checking for arguments. This means that empty create calls like 'X.create()' will correctly work afterward.
         *
         * @deprecated Use {@link #One()} instead.
         */
        @Deprecated(forRemoval = true)
        public T With() {
            return FactoryHelper.create(type, null, null, null);
        }

        /**
         * Creates a new instance of the model applying any configuration (apart from
         * 'postCreate' and 'postApply' methods and any active templates).
         *
         * @return The instantiated object.
         */
        public T One() {
            return FactoryHelper.create(type, null, null, null);
        }

        /**
         * Creates a new instance of the model applying the given configuration map and closure.
         *
         * @param configMap     The configuration map to apply to the model.
         * @param configuration The configuration closure to apply to the model.
         * @return The instantiated object.
         */
        public T With(Map<String, ?> configMap, Closure<?> configuration) {
            return FactoryHelper.create(type, configMap, null, configuration);
        }

        /**
         * Creates a new instance of the model applying the given configuration closure.
         *
         * @param configuration The configuration closure to apply to the model.
         * @return The instantiated object.
         */
        public T With(Closure<?> configuration) {
            return FactoryHelper.create(type, null, null, configuration);
        }

        /**
         * Creates a new instance of the model applying the given configuration map.
         *
         * @param configMap The configuration map to apply to the model.
         * @return The instantiated object.
         */
        public T With(Map<String, ?> configMap) {
            return FactoryHelper.create(type, configMap, null, null);
        }

        /**
         * Creates a new instance of the model and then applying the given text as configuration.
         *
         * @param configuration the configuration text to apply to the model.
         * @return The instantiated object.
         */
        public T From(String configuration) {
            return FactoryHelper.createFrom(type, null, configuration, null);
        }

        /**
         * Creates a new instance of the model and then applying the given text as configuration.
         *
         * @param configuration the configuration text to apply to the model.
         * @param loader        The classloader used to compile the configuration text.
         * @return The instantiated object.
         */
        public T From(String configuration, ClassLoader loader) {
            return FactoryHelper.createFrom(type, null, configuration, loader);
        }
    }
}
