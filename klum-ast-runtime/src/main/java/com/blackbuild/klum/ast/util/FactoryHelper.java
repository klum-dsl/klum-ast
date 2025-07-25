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
import com.blackbuild.groovy.configdsl.transform.DSL;
import com.blackbuild.groovy.configdsl.transform.PostApply;
import com.blackbuild.groovy.configdsl.transform.PostCreate;
import com.blackbuild.klum.ast.process.BreadcrumbCollector;
import com.blackbuild.klum.ast.process.PhaseDriver;
import groovy.lang.*;
import groovy.transform.Undefined;
import groovy.util.DelegatingScript;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.ResourceGroovyMethods;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Helper methods fro use in convenience factories. This will eventually take a lot of code from
 * the AST generated methods.
 */
@InlineJavadocs
public class FactoryHelper extends GroovyObjectSupport {

    public static final String MODEL_CLASS_KEY = "model-class";

    static {
        BreadCrumbVerbInterceptor.registerClass(FactoryHelper.class);
    }

    private FactoryHelper() {
        // static only
    }

    /**
     * Creates an instance of the given class by reading the model script class from the classpath.
     * <p>
     * The model script is determined by reading the properties file META-INF/klum-model/&lt;type&gt;.properties,
     * which must contain the key 'model-class' with the fully qualified class name of the model script.
     * </p>
     *
     * @param type The type to create
     * @param <T>  The type to create
     * @return The created instance
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
     * </p>
     *
     * @param type   The type to create
     * @param loader The class loader to load the script name and class from
     * @param <T>    The type to create
     * @return The created instance
     */
    public static <T> T createFromClasspath(Class<T> type, ClassLoader loader) {
        BreadcrumbCollector.getInstance().setType("classpath");
        String path = "META-INF/klum-model/" + type.getName() + ".properties";

        try (InputStream stream = loader.getResourceAsStream(path)) {
            assertResourceExists(path, stream);

            String configModelClassName = readModelClass(path, stream);

            return createModelFrom(type, loader, path, configModelClassName);

        } catch (IOException e) {
            throw new KlumModelException("Error while reading marker properties.", e);
        }
    }

    private static void assertResourceExists(String path, InputStream stream) {
        if (stream == null)
            throw new KlumModelException("File " + path + " not found in classpath.");
    }

    private static String readModelClass(String path, InputStream stream) throws IOException {
        Properties marker = new Properties();
        marker.load(stream);
        String configModelClassName = marker.getProperty(MODEL_CLASS_KEY);
        if (configModelClassName == null)
            throw new KlumModelException("No entry 'model-class' found in " + path);
        return configModelClassName;
    }

    @SuppressWarnings("unchecked")
    private static <T> T createModelFrom(Class<T> type, ClassLoader loader, String path, String configModelClassName) {
        try {
            Class<? extends Script> modelClass = (Class<? extends Script>) loader.loadClass(configModelClassName);
            return createFrom(type, modelClass);
        } catch (ClassNotFoundException e) {
            throw new KlumModelException("Class '" + configModelClassName + "' defined in " + path + " does not exist", e);
        } catch (Exception e) {
            throw new KlumModelException("Could not read model from " + configModelClassName, e);
        }
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
     *
     * @param type       The type to create
     * @param scriptType The script to run
     * @param <T>        The type to create
     * @return The created instance
     */
    public static <T> T createFrom(Class<T> type, Class<? extends Script> scriptType) {
        BreadcrumbCollector.getInstance().setType("script").setQualifier(DslHelper.shortNameFor(scriptType));
        if (DelegatingScript.class.isAssignableFrom(scriptType))
            return createFromDelegatingScript(type, scriptType.getSimpleName(), (DelegatingScript) InvokerHelper.invokeConstructorOf(scriptType, null));
        Object result = InvokerHelper.runScript(scriptType, null);
        if (!type.isInstance(result))
            throw new KlumModelException("Script " + scriptType.getName() + " did not return an instance of " + type.getName());
        //noinspection unchecked
        return (T) result;
    }

    private static <T> T createFromDelegatingScript(Class<T> type, String key, DelegatingScript script) {
        Consumer<KlumInstanceProxy> apply = proxy -> {
            script.setDelegate(proxy.getRwInstance());
            script.run();
            LifecycleHelper.executeLifecycleMethods(proxy, PostApply.class);
        };

        if (DslHelper.isKeyed(type))
            return doCreate(key, () -> createInstance(type, key), apply);
        else
            return doCreate(null, () -> createInstance(type, null), apply);
    }

    private static <T> T doCreate(String key, Supplier<T> createInstance, Consumer<KlumInstanceProxy> apply) {
        return BreadcrumbCollector.withBreadcrumb(null, null, key,
                () -> PhaseDriver.withPhase(createInstance, object -> postCreate(apply, object))
        );
    }

    private static <T> void postCreate(Consumer<KlumInstanceProxy> apply, T object) {
        KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(object);
        proxy.copyFromTemplate();
        LifecycleHelper.executeLifecycleMethods(proxy, PostCreate.class);

        apply.accept(proxy);
    }

    static <T> T createInstance(Class<T> type, String key) {
        return createInstance(type, key, null);
    }

    static <T> T createInstance(Class<T> type, String key, String breadCrumbPathExtension) {
        if (!DslHelper.isInstantiable(type))
            throw new KlumModelException("Cannot instantiate abstract class " + type.getName());
        if (key == null && DslHelper.isKeyed(type))
            return createInstanceWithNullArg(type);
        //noinspection unchecked
        T result = (T) InvokerHelper.invokeConstructorOf(type, key);
        KlumInstanceProxy instanceProxy = KlumInstanceProxy.getProxyFor(result);
        if (breadCrumbPathExtension != null)
            instanceProxy.setBreadcrumbPath(BreadcrumbCollector.getInstance().getFullPath() + "/" + breadCrumbPathExtension);
        else if (BreadcrumbCollector.hasInstance())
            instanceProxy.setBreadcrumbPath(BreadcrumbCollector.getInstance().getFullPath());
        instanceProxy.setCurrentTemplates(TemplateManager.getInstance().getCurrentTemplates());
        return result;
    }

    private static <T> T createInstanceWithNullArg(Class<T> type) {
        //noinspection unchecked
        return (T) InvokerHelper.invokeConstructorOf(type, new Object[]{null});
    }

    /**
     * Creates a new instance of the given type using the provided values, key and config closure.
     * <p>
     * First the class is instantiated using the key as constructor argument if keyed. Then the values are applied
     * by using the keys as method names of the RW instance and the values as the single argument of that method.
     * Finally the config closure is applied to the RW instance.
     * </p>
     *
     * @param type   The type to create
     * @param values The value map to apply
     * @param key    The key to use for instantiation, ignored if the type is not keyed
     * @param body   The config closure to apply
     * @param <T>    The type to create
     * @return The created instance
     */
    public static <T> T create(Class<T> type, Map<String, ?> values, String key, Closure<?> body) {
        return doCreate(key, () -> createInstance(type, key), proxy -> proxy.apply(values, body));
    }

    /**
     * Creates an instance of the given type by reading the given URL, compiling it into a delegating script
     * and applying it to a newly created instance.
     *
     * @param type The type to create
     * @param src  The URL to read
     * @param <T>  The type to create
     * @return The created instance
     */
    public static <T> T createFrom(Class<T> type, URL src, Function<URL, String> keyProvider, ClassLoader loader) {
        BreadcrumbCollector.getInstance().setType("url").setQualifier(src.toString());
        if (keyProvider == null)
            keyProvider = FactoryHelper::extractKeyFromUrl;
        String key = keyProvider.apply(src);
        try {
            return doCreateFromText(type, key, ResourceGroovyMethods.getText(src), loader);
        } catch (IOException e) {
            throw new KlumModelException(e);
        }
    }

    /**
     * Creates an instance of the given type by compiling the given text into a delegating script
     * and applying it to a newly created instance.
     *
     * @param type The type to create
     * @param name The name of the script, only relevant for keyed types
     * @param <T>  The type to create
     * @return The created instance
     */
    public static <T> T createFrom(Class<T> type, String name, String text, ClassLoader loader) {

        return BreadcrumbCollector.withBreadcrumb(null, "text", name, () -> doCreateFromText(type, name, text, loader));
    }

    private static <T> T doCreateFromText(Class<T> type, String name, String text, ClassLoader loader) {
        GroovyShell shell = createGroovyShell(loader);
        Script parse = name != null ? shell.parse(text, name) : shell.parse(text);
        return createFromDelegatingScript(type, name, (DelegatingScript) parse);
    }

    @NotNull
    private static GroovyShell createGroovyShell(ClassLoader loader) {
        GroovyClassLoader gLoader = new GroovyClassLoader(loader != null ? loader : Thread.currentThread().getContextClassLoader());
        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();
        compilerConfiguration.setScriptBaseClass(DelegatingScript.class.getName());
        return new GroovyShell(gLoader, compilerConfiguration);
    }

    /**
     * Creates an instance of the given type by reading the given file, compiling it into a delegating script
     * and applying it to a newly created instance.
     *
     * @param type The type to create
     * @param file The file to read
     * @param keyProvider The function to provide the key from the file, usually some kind of name extraction
     * @param loader The classloader to use for creating the script
     * @param <T>  The type to create
     * @return The created instance
     */
    public static <T> T createFrom(Class<T> type, File file, Function<File, String> keyProvider, ClassLoader loader) {
        BreadcrumbCollector.getInstance().setType("file").setQualifier(file.getPath());
        if (keyProvider == null)
            keyProvider = FactoryHelper::extractKeyFromFile;
        try {
            return doCreateFromText(type, keyProvider.apply(file), ResourceGroovyMethods.getText(file), loader);
        } catch (IOException e) {
            throw new KlumModelException(e);
        }
    }

    /**
     * Creates a template of the given type by reading the given resource, compiling it into a delegating script
     * and applying it to a newly created instance.
     * <p>
     * Template instance don't run lifecycle phases/methods.
     * </p>
     *
     * @param type       The type to create
     * @param scriptFile The resource to read
     * @param <T>        The type to create
     * @return The created instance
     */
    public static <T> T createAsTemplate(Class<T> type, File scriptFile, ClassLoader loader) {
        try {
            return createAsTemplate(type, ResourceGroovyMethods.getText(scriptFile), loader);
        } catch (IOException e) {
            throw new KlumModelException(e);
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
     * @param <T>  The type to create
     * @return The created instance
     */
    public static <T> T createAsTemplate(Class<T> type, String text, ClassLoader loader) {
        return BreadcrumbCollector.withBreadcrumb(() -> {
            T result = createTemplateInstance(type);
            KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(result);
            proxy.copyFromTemplate();

            DelegatingScript script = (DelegatingScript) createGroovyShell(loader).parse(text);
            script.setDelegate(proxy.getRwInstance());
            script.run();
            return result;
        });
    }

    private static <T> T createTemplateInstance(Class<T> type) {
        T result;
        if (!DslHelper.isInstantiable(type))
            result = createSyntheticTemplateInstance(type);
        else if (DslHelper.isKeyed(type))
            result = createInstanceWithNullArg(type);
        else
            result = createInstanceWithArgs(type);
        KlumInstanceProxy.getProxyFor(result).setBreadcrumbPath(BreadcrumbCollector.getInstance().getFullPath());
        return result;
    }

    private static <T> T createSyntheticTemplateInstance(Class<T> type) {
        try {
            //noinspection unchecked
            return (T) type.getClassLoader().loadClass(type.getName() + "$Template").getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new KlumModelException(String.format("Could new instantiate synthetic template class, is %s a KlumDSL Object?", type), e);
        }
    }

    static <T> T createInstanceWithArgs(Class<T> type, Object... args) {
        //noinspection unchecked
        return (T) InvokerHelper.invokeConstructorOf(type, args);
    }

    /**
     * Creates a template of the given type by reading the given resource, compiling it into a delegating script
     * and applying it to a newly created instance.
     * <p>
     * Template instance don't run lifecycle phases/methods.
     * </p>
     *
     * @param type   The type to create
     * @param script The resource to read
     * @param <T>    The type to create
     * @return The created instance
     */
    public static <T> T createAsTemplate(Class<T> type, URL script, ClassLoader loader) {
        try {
            return createAsTemplate(type, ResourceGroovyMethods.getText(script), loader);
        } catch (IOException e) {
            throw new KlumModelException(e);
        }
    }

    /**
     * Creates a new template instance of the given type using the provided values and config closure.
     * <p>
     * The values are applied by using the keys as method names of the RW instance and the values as the single argument
     * of that method. Finally the config closure is applied to the RW instance.
     * Template instance don't run lifecycle phases/methods.
     * </p>
     *
     * @param type    The type to create
     * @param values  The value map to apply
     * @param closure The config closure to apply
     * @param <T>     The type to create
     * @return The created instance
     */
    public static <T> T createAsTemplate(Class<T> type, Map<String, Object> values, Closure<?> closure) {
        return BreadcrumbCollector.withBreadcrumb(() -> {
            T result = createTemplateInstance(type);
            KlumInstanceProxy proxy = KlumInstanceProxy.getProxyFor(result);
            proxy.copyFromTemplate();
            proxy.applyOnly(values, closure);
            return result;
        });
    }

    public static <T> T createAsStub(Class<T> type, String key) {
        return createInstance(type, key);
    }

    private static String extractKeyFromUrl(URL url) {
        return extractKeyFromFilename(Paths.get(url.getPath()).getFileName().toString());
    }

    private static String extractKeyFromFile(File f) {
        String filename = f.getName();
        return extractKeyFromFilename(filename);
    }

    private static @NotNull String extractKeyFromFilename(String filename) {
        int endIndex = filename.lastIndexOf('.');
        return endIndex != -1 ? filename.substring(0, endIndex) : filename;
    }

    public static <T> T createFromMap(Class<T> type, Map<String, Object> configMap) {
        return BreadcrumbCollector.withBreadcrumb(() -> {
            String keyFromMap = DslHelper.getKeyField(type)
                    .map(Field::getName)
                    .map(configMap::get)
                    .map(Object::toString)
                    .orElse(null);
            String typeFromMap = (String) configMap.get("@type");
            Class<T> effectiveType = deduceClass(type, typeFromMap);

            T result = createInstance(effectiveType, keyFromMap);
            CopyHandler.copyToFrom(result, configMap);
            return result;
        });
    }

    private static <T> Class<T> deduceClass(Class<T> baseType, String typeHint) {
        if (typeHint == null) {
            Class<T> result = getTypeOrDefaultType(baseType);
            if (DslHelper.isInstantiable(result)) return result;
            throw new KlumModelException("Cannot deduce type from base type " + baseType.getName() + " without a type hint");
        }

        ClassLoader loader = baseType.getClassLoader();

        List<String> typeNameVariants = new ArrayList<>();
        typeNameVariants.add(typeHint);
        typeNameVariants.add(baseType.getPackage().getName() + "." + typeHint);

        DSL dsl = baseType.getAnnotation(DSL.class);
        if (dsl != null && !dsl.stripSuffix().isEmpty()) {
            typeNameVariants.add(dsl.stripSuffix() + typeHint);
            typeNameVariants.add(dsl.stripSuffix() + baseType.getPackage().getName() + "." + typeHint);
        }

        return typeNameVariants.stream()
                .map(name -> safeLoadClass(name, loader, baseType))
                .filter(Objects::nonNull)
                .findFirst()
                .orElseThrow(() -> new KlumModelException("Could not find class for type hint " + typeHint + ", variants tried: " + typeNameVariants));
    }

    private static <T> Class<T> safeLoadClass(String className, ClassLoader loader, Class<T> baseType) {
        try {
            Class<T> loadedClass = (Class<T>) loader.loadClass(className);
            if (!baseType.isAssignableFrom(loadedClass))
                throw new KlumModelException("Class " + loadedClass.getName() + " is not a subclass of " + baseType.getName());
            return loadedClass;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    static <T> Class<T> getTypeOrDefaultType(Class<T> type) {
        DSL annotation = type.getAnnotation(DSL.class);
        Class<?> defaultImpl = annotation.defaultImpl();
        //noinspection unchecked - already verified via CheckDslDefaultImpl
        return defaultImpl.equals(Undefined.class) ? type : (Class<T>) defaultImpl;
    }
}
