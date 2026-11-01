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
package com.blackbuild.klum.ast.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URISyntaxException;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** Opt-in task; no contract classes or classloaders survive execution. */
@CacheableTask
public abstract class GenerateKlumJsonSchema extends DefaultTask {
    private static final String PROJECTOR = "com.blackbuild.klum.ast.runtime.internal.JsonSchemaProjection";
    private static final String MANIFEST = ".klum-json-schema-outputs";

    @Input
    public abstract SetProperty<String> getRootTypes();

    /** Explicit body purpose; independent of the self-contained root selection and cache input. */
    @Input
    public abstract SetProperty<String> getBodyRootTypes();

    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    /** The private tool bytes are fingerprinted separately from the consumer's resolved classpath. */
    @Classpath
    public abstract ConfigurableFileCollection getToolClasspath();

    @Input
    public String getToolVersion() { return PluginHelper.determineOwnVersion(); }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    // Gradle task decoration requires an accessible constructor.
    @SuppressWarnings("java:S5993")
    public GenerateKlumJsonSchema() {
        getBodyRootTypes().convention(Set.of());
        try {
            getToolClasspath().from(new File(GenerateKlumJsonSchema.class.getProtectionDomain().getCodeSource().getLocation().toURI()));
        } catch (URISyntaxException failure) {
            throw new GradleException("Cannot locate private JSON Schema tool implementation", failure);
        }
    }

    @TaskAction
    public void generate() throws IOException {
        List<String> roots = getRootTypes().get().stream().sorted().toList();
        List<String> bodyRoots = getBodyRootTypes().get().stream().sorted().toList();
        if (roots.isEmpty() && bodyRoots.isEmpty())
            throw new GradleException("Configure klumSchema.jsonSchema.rootTypes or bodyRootTypes with at least one fully qualified DSL Model binary name");
        Map<String, String> documents = project(roots, bodyRoots);
        Path output = getOutputDirectory().get().getAsFile().toPath();
        Set<String> previous = previousOutputs(output);
        Files.createDirectories(output);
        Path staging = getTemporaryDir().toPath();
        Files.createDirectories(staging);
        for (Map.Entry<String, String> document : documents.entrySet()) {
            Path staged = staging.resolve(document.getKey());
            Files.writeString(staged, document.getValue(), StandardCharsets.UTF_8);
            Files.move(staged, output.resolve(document.getKey()), StandardCopyOption.REPLACE_EXISTING);
        }
        // Delete only files named by our own last-successful manifest, never a directory tree.
        for (String name : previous) {
            if (!documents.containsKey(name)) Files.deleteIfExists(output.resolve(name));
        }
        Path manifest = staging.resolve(MANIFEST);
        Files.writeString(manifest, String.join("\n", documents.keySet()) + "\n", StandardCharsets.UTF_8);
        Files.move(manifest, output.resolve(MANIFEST), StandardCopyOption.REPLACE_EXISTING);
    }

    // Only our private plugin constructor is opened (S3011); keeping it private avoids a public generator API.
    // Consumer Schema contracts retain public-only access. Only JDK Strings/collections cross the loader boundary.
    @SuppressWarnings({"unchecked", "java:S3011"})
    private Map<String, String> project(List<String> roots, List<String> bodyRoots) throws IOException {
        Set<File> files = new LinkedHashSet<>(getToolClasspath().getFiles());
        files.addAll(getClasspath().getFiles());
        List<URL> urls = new ArrayList<>();
        for (File file : files) urls.add(file.toURI().toURL());
        try (URLClassLoader loader = new ContractLoader(urls.toArray(URL[]::new))) {
            Class<?> implementation = Class.forName(PROJECTOR, true, loader);
            Constructor<?> constructor = implementation.getDeclaredConstructor();
            // Access only our own private plugin class; Schema contracts always use public reflection through the facade.
            constructor.setAccessible(true);
            BiFunction<List<String>, List<String>, Map<String, String>> projection =
                    (BiFunction<List<String>, List<String>, Map<String, String>>) constructor.newInstance();
            return projection.apply(roots, bodyRoots);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException failure) {
            throw new GradleException("JSON Schema generation failed for explicit roots " + roots
                    + " and Builder-body roots " + bodyRoots
                    + "; check the configured Schema/API/runtime/annotation classpath: " + failure.getMessage(), failure);
        }
    }

    /** Exact annotation-resource reads must avoid the JVM-wide JAR cache when dependency bytes change. */
    private static final class ContractLoader extends URLClassLoader {
        private ContractLoader(URL[] urls) { super(urls, ClassLoader.getPlatformClassLoader()); }

        @Override
        protected URL findResource(String moduleName, String name) throws IOException {
            URL resource = super.findResource(moduleName, name);
            if (resource == null) return null;
            // Module.getResourceAsStream opens this URL directly, bypassing ClassLoader.getResourceAsStream.
            return new URL(null, resource.toExternalForm(), new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL ignored) throws IOException {
                    URLConnection connection = resource.openConnection();
                    connection.setUseCaches(false);
                    return connection;
                }
            });
        }
    }

    private static Set<String> previousOutputs(Path directory) throws IOException {
        Path manifest = directory.resolve(MANIFEST);
        if (!Files.exists(manifest)) return Set.of();
        Set<String> result = new LinkedHashSet<>(Files.readAllLines(manifest, StandardCharsets.UTF_8));
        for (String name : result) {
            // Model filenames and earlier Builder filenames share this suffix; only manifest-owned files are removed.
            if (!name.endsWith(".schema.json") || name.contains("/") || name.contains("\\") || name.startsWith("."))
                throw new IOException("Invalid JSON Schema output manifest entry; use a dedicated output directory: " + name);
        }
        return result;
    }
}
