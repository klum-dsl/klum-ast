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

import com.blackbuild.annodocimal.annotations.InlineJavadocs;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import com.blackbuild.klum.ast.process.PhaseDriver;
import groovy.lang.Closure;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import groovy.util.DelegatingScript;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Helper methods fro use in convenience factories. This will eventually take a lot of code from
 * the AST generated methods.
 */
@InlineJavadocs
public class FactoryHelper {

    public static final String MODEL_CLASS_KEY = "model-class";

    private FactoryHelper() {
        // static only
    }

    /**
     * Creates an instance of the given class by reading the model script class from the classpath.
     * <p>
     * The model script is determined by reading the properties file META-INF/klum-model/&lt;type&gt;.properties,
     * which must contain the key 'model-class' with the fully qualified class name of the model script.
     * </p>
     * @param type The type to create
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createFromClasspath(Class<T> type) {
        return createFromClasspath(type, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Creates an instance of the given class by reading the model script class from the classpath.
     * <p>
     * The model script is determined by reading the properties file META-INF/klum-model/&lt;type&gt;.properties,
     * which must contain the key 'model-class' with the fully qualified class name of the model script.
     * The properties and the script class is loaded using the given class loader.
 *     </p>
     * @param type The type to create
     * @param loader The class loader to load the script name and class from
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createFromClasspath(Class<T> type, ClassLoader loader) {
        String path = "META-INF/klum-model/" + type.getName() + ".properties";

        try (InputStream stream = loader.getResourceAsStream(path)) {
            assertResourceExists(path, stream);

            String configModelClassName = readModelClass(path, stream);

            return createModelFrom(type, loader, path, configModelClassName);

        } catch (IOException e) {
            throw new IllegalStateException("Error while reading marker properties.", e);
        }
    }

    /**
     * Creates a new instance of the given type using the provided values, key and config closure.
     * <p>
     * First the class is instantiated using the key as constructor argument if keyed. Then the values are applied
     * by using the keys as method names of the RW instance and the values as the single argument of that method.
     * Finally the config closure is applied to the RW instance.
     * </p>
     * @param type The type to create
     * @param values The value map to apply
     * @param key The key to use for instantiation, ignored if the type is not keyed
     * @param body The config closure to apply
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T create(Class<T> type, Map<String, ?> values, String key, Closure<?> body) {
        return doCreate(type, key, () -> createInstance(type, key), proxy -> proxy.apply(values, body));
    }

    private static <T> T doCreate(Class<T> type, String key, Supplier<T> createInstance, Consumer<KlumInstanceProxy> apply) {
        try {
            BreadcrumbCollector.getInstance().enter(type.getSimpleName(), key);
            T result = createInstance.get();
            PhaseDriver.enter(result);
            KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(result);
            proxy.copyFromTemplate();
            LifecycleHelper.executeLifecycleMethods(proxy, PostCreate.class);

            apply.accept(proxy);

            PhaseDriver.executeIfReady();

            return result;
        } finally {
            BreadcrumbCollector.getInstance().leave();
            PhaseDriver.leave();
            PhaseDriver.cleanup();
        }
    }


    static <T> T createInstance(Class<T> type, String key) {
        if (key == null && DslHelper.isKeyed(type))
            return createInstanceWithNullArg(type);
        //noinspection unchecked
        return (T) InvokerHelper.invokeConstructorOf(type, key);
    }

    static <T> T createInstanceWithArgs(Class<T> type, Object... args) {
        //noinspection unchecked
        return (T) InvokerHelper.invokeConstructorOf(type, args);
    }

    /**
     * Creates an instance of the given type by running the given script.
     * <p>
     * The script can either be a regular script or a delegating script. A regular script will be instantiated
     * and executed, the result will be returned. If the script returns the wrong type, an error will be thrown.
     * For a delegating script, a new instance of the given type will be created and the script run as a closure
     * on the RW instance.
     * </p>
     * <p>
     * Basically, a regular script must start with {@code Type.Create...}, while a delegating script should only
     * contain the body of the configuration closure.
     * </p>
     * <p>For a keyed type with a delegating script, the simple name of the script is used as the key, for a
     * regular script, the script itself is responsible to provide the key.</p>
     * @param type The type to create
     * @param scriptType The script to run
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createFrom(Class<T> type, Class<? extends Script> scriptType) {
        if (DelegatingScript.class.isAssignableFrom(scriptType))
            return createFromDelegatingScript(type, (DelegatingScript) InvokerHelper.invokeConstructorOf(scriptType, null));
        Object result = InvokerHelper.runScript(scriptType, null);
        if (!type.isInstance(result))
            throw new IllegalStateException("Script " + scriptType.getName() + " did not return an instance of " + type.getName());
        //noinspection unchecked
        return (T) result;
    }

    static <T> T createFromDelegatingScript(Class<T> type, DelegatingScript script) {
        Consumer<KlumInstanceProxy> apply = proxy -> {
            script.setDelegate(proxy.getRwInstance());
            script.run();
            LifecycleHelper.executeLifecycleMethods(proxy, PostApply.class);
        };

        if (DslHelper.isKeyed(type))
            return doCreate(type, script.getClass().getSimpleName(), () -> createInstance(type, script.getClass().getSimpleName()), apply);
        else
            return doCreate(type, null, () -> createInstance(type, null), apply);
    }

    /**
     * Creates an instance of the given type by compiling the given text into a delegating script
     * and applying it to a newly created instance.
     *
     * @param type The type to create
     * @param name The name of the script, only relevant for keyed types
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createFrom(Class<T> type, String name, String text, ClassLoader loader) {
        GroovyShell shell = createGroovyShell(loader);
        Script parse = name != null ? shell.parse(text, name) : shell.parse(text);
        return createFromDelegatingScript(type, (DelegatingScript) parse);
    }

    /**
     * Creates an instance of the given type by reading the given URL, compiling it into a delegating script
     * and applying it to a newly created instance.
     *
     * @param type The type to create
     * @param src The URL to read
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createFrom(Class<T> type, URL src, ClassLoader loader) {
        try {
            String path = Paths.get(src.getPath()).getFileName().toString();
            return createFrom(type, path, ResourceGroovyMethods.getText(src), loader);
        } catch (IOException e) {
            throw new KlumException(e);
        }
    }

    /**
     * Creates an instance of the given type by reading the given file, compiling it into a delegating script
     * and applying it to a newly created instance.
     *
     * @param type The type to create
     * @param file The file to read
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createFrom(Class<T> type, File file, ClassLoader loader) {
        try {
            return createFrom(type, file.getName(), ResourceGroovyMethods.getText(file), loader);
        } catch (IOException e) {
            throw new KlumException(e);
        }
    }

    /**
     * Creates a template of the given type by reading the given resource, compiling it into a delegating script
     * and applying it to a newly created instance.
     * <p>
     * Template instance don't run lifecycle phases/methods.
     * </p>
     *
     * @param type The type to create
     * @param scriptFile The resource to read
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createAsTemplate(Class<T> type, File scriptFile, ClassLoader loader) {
        try {
            return createAsTemplate(type, ResourceGroovyMethods.getText(scriptFile), loader);
        } catch (IOException e) {
            throw new KlumException(e);
        }
    }

    /**
     * Creates a template of the given type by reading the given resource, compiling it into a delegating script
     * and applying it to a newly created instance.
     * <p>
     * Template instance don't run lifecycle phases/methods.
     * </p>
     *
     * @param type The type to create
     * @param script The resource to read
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createAsTemplate(Class<T> type, URL script, ClassLoader loader) {
        try {
            return createAsTemplate(type, ResourceGroovyMethods.getText(script), loader);
        } catch (IOException e) {
            throw new KlumException(e);
        }
    }

    /**
     * Creates a template of the given type by reading the given resource, compiling it into a delegating script
     * and applying it to a newly created instance.
     * <p>
     * Template instance don't run lifecycle phases/methods.
     * </p>
     *
     * @param type The type to create
     * @param text The script text
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createAsTemplate(Class<T> type, String text, ClassLoader loader) {
        T result = createAsTemplate(type);
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(result);
        proxy.copyFromTemplate();

        DelegatingScript script = (DelegatingScript) createGroovyShell(loader).parse(text);
        script.setDelegate(proxy.getRwInstance());
        script.run();
        return result;
    }

    @NotNull
    private static GroovyShell createGroovyShell(ClassLoader loader) {
        GroovyClassLoader gLoader = new GroovyClassLoader(loader != null ? loader : Thread.currentThread().getContextClassLoader());
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.setScriptBaseClass(DelegatingScript.class.getName());
        return new GroovyShell(gLoader, compilerConfiguration);
    }

    /**
     * Creates a new template instance of the given type using the provided values and config closure.
     * <p>
     * The values are applied by using the keys as method names of the RW instance and the values as the single argument
     * of that method. Finally the config closure is applied to the RW instance.
     * Template instance don't run lifecycle phases/methods.
     * </p>
     * @param type The type to create
     * @param values The value map to apply
     * @param closure The config closure to apply
     * @return The created instance
     * @param <T> The type to create
     */
    public static <T> T createAsTemplate(Class<T> type, Map<String, Object> values, Closure<?> closure) {
        T result = createAsTemplate(type);

        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(result);
        proxy.copyFromTemplate();
        proxy.applyOnly(values, closure);
        return result;
    }

    private static <T> T createAsTemplate(Class<T> type) {
        if (!DslHelper.isInstantiable(type))
            return createAsSyntheticTemplate(type);
        else if (DslHelper.isKeyed(type))
            return createInstanceWithNullArg(type);
        else
            return createInstanceWithArgs(type);
    }

    public static <T> T createAsStub(Class<T> type, String key) {
        return createInstance(type, key);
    }

    private static <T> T createInstanceWithNullArg(Class<T> type) {
        //noinspection unchecked
        return (T) InvokerHelper.invokeConstructorOf(type, new Object[] {null});
    }

    private static <T> T createAsSyntheticTemplate(Class<T> type) {
        try {
            //noinspection unchecked
            return (T) type.getClassLoader().loadClass(type.getName() + "$Template").getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException(String.format("Could new instantiate synthetic template class, is %s a KlumDSL Object?", type), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T createModelFrom(Class<T> type, ClassLoader loader, String path, String configModelClassName) {
        try {
            Class<? extends Script> modelClass = (Class<? extends Script>) loader.loadClass(configModelClassName);
            return createFrom(type, modelClass);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Class '" + configModelClassName + "' defined in " + path + " does not exist", e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read model from " + configModelClassName, e);
        }
    }

    private static void assertResourceExists(String path, InputStream stream) {
        if (stream == null)
            throw new IllegalStateException("File " + path + " not found in classpath.");
    }

    private static String readModelClass(String path, InputStream stream) throws IOException {
        Properties marker = new Properties();
        marker.load(stream);
        String configModelClassName = marker.getProperty(MODEL_CLASS_KEY);
        if (configModelClassName == null)
            throw new IllegalStateException("No entry 'model-class' found in " + path);
        return configModelClassName;
    }

    private static void breadcrumb(String path) {
        BreadcrumbCollector.getInstance().enter(path);
    }

}
