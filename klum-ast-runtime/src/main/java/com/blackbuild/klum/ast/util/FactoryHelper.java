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

import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import com.blackbuild.klum.ast.process.PhaseDriver;
import groovy.lang.*;
import groovy.util.DelegatingScript;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;

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
public class FactoryHelper {

    public static final String MODEL_CLASS_KEY = "model-class";

    private FactoryHelper() {
        // static only
    }

    public static <T extends GroovyObject> T createFromClasspath(Class<T> type) {
        return createFromClasspath(type, Thread.currentThread().getContextClassLoader());
    }

    public static <T extends GroovyObject> T createFromClasspath(Class<T> type, ClassLoader loader) {
        String path = "META-INF/klum-model/" + type.getName() + ".properties";

        try (InputStream stream = loader.getResourceAsStream(path)) {
            assertResourceExists(path, stream);

            String configModelClassName = readModelClass(path, stream);

            return createModelFrom(type, loader, path, configModelClassName);

        } catch (IOException e) {
            throw new IllegalStateException("Error while reading marker properties.", e);
        }
    }

    public static <T> T create(Class<T> type, Map<String, Object> values, String key, Closure<?> body) {
        return doCreate(type, key, () -> createInstance(type, key), proxy -> proxy.apply(values, body));
    }

    private static <T> T doCreate(Class<T> type, String key, Supplier<T> createInstance, Consumer<KlumInstanceProxy> apply) {
        try {
            BreadcrumbCollector.getInstance().enter(type.getSimpleName(), key);
            T result = createInstance.get();
            PhaseDriver.enter(result);
            KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(result);
            proxy.copyFromTemplate();
            proxy.postCreate();

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
            proxy.postApply();
        };

        if (DslHelper.isKeyed(type))
            return doCreate(type, script.getClass().getSimpleName(), () -> createInstance(type, script.getClass().getSimpleName()), apply);
        else
            return doCreate(type, null, () -> createInstance(type, null), apply);
    }

    public static <T> T createFrom(Class<T> type, String name, String text, ClassLoader loader) {
        GroovyClassLoader gLoader = new GroovyClassLoader(loader != null ? loader : Thread.currentThread().getContextClassLoader());
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.setScriptBaseClass(DelegatingScript.class.getName());
        GroovyShell shell = new GroovyShell(gLoader, compilerConfiguration);
        Script parse = name != null ? shell.parse(text, name) : shell.parse(text);
        return createFromDelegatingScript(type, (DelegatingScript) parse);
    }

    public static <T> T createFrom(Class<T> type, URL src, ClassLoader loader) {
        try {
            String path = Paths.get(src.getPath()).getFileName().toString();
            return createFrom(type, path, ResourceGroovyMethods.getText(src), loader);
        } catch (IOException e) {
            throw new KlumException(e);
        }
    }

    public static <T> T createFrom(Class<T> type, File file, ClassLoader loader) {
        try {
            return createFrom(type, file.getName(), ResourceGroovyMethods.getText(file), loader);
        } catch (IOException e) {
            throw new KlumException(e);
        }
    }

    public static <T> T createAsTemplate(Class<T> type, Map<String, Object> values, Closure<?> closure) {
        T result;
        if (!DslHelper.isInstantiable(type))
            result = createAsSyntheticTemplate(type);
        else if (DslHelper.isKeyed(type))
            result = createInstanceWithNullArg(type);
        else
            result = createInstanceWithArgs(type);

        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(result);
        proxy.copyFromTemplate();
        proxy.applyOnly(values, closure);
        return result;
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
            return (T) type.getClassLoader().loadClass(type.getName() + "$Template").newInstance();
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
            throw new IllegalArgumentException(String.format("Could new instantiate synthetic template class, is %s a KlumDSL Object?", type), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends GroovyObject> T createModelFrom(Class<T> type, ClassLoader loader, String path, String configModelClassName) {
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
