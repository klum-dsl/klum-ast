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
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates the user-owned JPMS descriptor of a Schema project.
 */
public abstract class ValidateKlumSchemaModule extends DefaultTask {

    private static final String COMPILER_MODULE = "com.blackbuild.klum.ast.compiler";
    private static final Set<String> REQUIRED_MODULES = Set.of(
            "com.blackbuild.klum.ast.annotations",
            "com.blackbuild.klum.ast.runtime",
            COMPILER_MODULE,
            "org.apache.groovy");
    private static final Map<String, String> ADAPTER_OPEN_TARGETS = Map.of(
            "com.blackbuild.klum.ast.jackson", "com.fasterxml.jackson.databind",
            "com.blackbuild.klum.ast.validation.bean", "org.hibernate.validator");
    private static final Pattern REQUIRES = Pattern.compile("\\brequires\\s+((?:static\\s+transitive|transitive\\s+static|static|transitive)\\s+)?([\\w.]+)\\s*;");
    private static final Pattern OPENS = Pattern.compile("\\bopens\\s+([\\w.]+)\\s+to\\s+((?:[\\w.]+\\s*,\\s*)*[\\w.]+)\\s*;");
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*(?:;|$)", Pattern.MULTILINE);

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getDescriptorFiles();

    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getSchemaSources();

    @Input
    public abstract Property<String> getGroovyVersion();

    @Input
    public abstract SetProperty<String> getOptionalAdapterModules();

    @TaskAction
    public void validateDescriptor() {
        Set<File> descriptors = getDescriptorFiles().getFiles();
        if (descriptors.isEmpty())
            return;

        File descriptor = descriptors.iterator().next();
        if (groovyMajorVersion() == 3) {
            throw new GradleException("""
                    KlumAST schema module '%s' cannot use module-info.java with Groovy 3.
                    Groovy 3 is a supported classpath-only configuration. Remove module-info.java and compile/run this Schema on the ordinary classpath, or use Groovy 4 or 5 for a named module.
                    The Schema plugin will not rewrite module-info.java or add JVM module-path workaround flags.
                    """.formatted(getProject().getPath()).trim());
        }

        String source = read(descriptor);
        Map<String, Set<String>> required = requiredModules(source);
        Map<String, Set<String>> openedPackages = openedPackages(source);
        Set<String> expectedModules = new LinkedHashSet<>(REQUIRED_MODULES);
        Set<String> adapterModules = new LinkedHashSet<>(getOptionalAdapterModules().get());
        required.keySet().stream().filter(ADAPTER_OPEN_TARGETS::containsKey).forEach(adapterModules::add);
        expectedModules.addAll(adapterModules);

        List<String> problems = new ArrayList<>();
        expectedModules.stream()
                .filter(module -> !isRequired(required, module))
                .forEach(module -> problems.add("missing `" + requiresDirective(module) + "`"));

        Set<String> targets = new LinkedHashSet<>();
        targets.add("com.blackbuild.klum.ast.runtime");
        adapterModules.stream()
                .map(ADAPTER_OPEN_TARGETS::get)
                .forEach(targets::add);
        schemaPackages().forEach(schemaPackage -> {
            Set<String> openedTo = openedPackages.getOrDefault(schemaPackage, Set.of());
            targets.stream().filter(target -> !openedTo.contains(target)).forEach(target ->
                    problems.add("missing `opens " + schemaPackage + " to " + target + ";`"));
        });

        if (!problems.isEmpty())
            throw new GradleException(remediation(problems, targets, adapterModules));
    }

    private int groovyMajorVersion() {
        String configured = getGroovyVersion().get();
        int separator = configured.indexOf('.');
        return Integer.parseInt(separator < 0 ? configured : configured.substring(0, separator));
    }

    private Set<String> schemaPackages() {
        Set<String> packages = new LinkedHashSet<>();
        Set<File> sources = new LinkedHashSet<>(getSchemaSources().getFiles());
        sources.addAll(getProject().fileTree("src/main", tree -> {
            tree.include("**/*.java", "**/*.groovy");
            tree.exclude("**/module-info.java");
        }).getFiles());
        sources.forEach(source -> {
            Matcher matcher = PACKAGE.matcher(read(source));
            if (matcher.find())
                packages.add(matcher.group(1));
        });
        return packages;
    }

    private static Map<String, Set<String>> requiredModules(String source) {
        Map<String, Set<String>> modules = new LinkedHashMap<>();
        Matcher matcher = REQUIRES.matcher(withoutComments(source));
        while (matcher.find()) {
            Set<String> modifiers = new LinkedHashSet<>();
            String declaredModifiers = matcher.group(1);
            if (declaredModifiers != null)
                Collections.addAll(modifiers, declaredModifiers.trim().split("\\s+"));
            modules.put(matcher.group(2), modifiers);
        }
        return modules;
    }

    private static boolean isRequired(Map<String, Set<String>> modules, String module) {
        Set<String> modifiers = modules.get(module);
        return modifiers != null && (!COMPILER_MODULE.equals(module) || modifiers.contains("static"));
    }

    private static Map<String, Set<String>> openedPackages(String source) {
        Map<String, Set<String>> packages = new LinkedHashMap<>();
        Matcher matcher = OPENS.matcher(withoutComments(source));
        while (matcher.find()) {
            Set<String> targets = new LinkedHashSet<>();
            for (String target : matcher.group(2).split(","))
                targets.add(target.trim());
            packages.put(matcher.group(1), targets);
        }
        return packages;
    }

    private static String withoutComments(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//.*", "");
    }

    private static String read(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException exception) {
            throw new GradleException("Cannot read " + file, exception);
        }
    }

    private String remediation(List<String> problems, Set<String> targets, Set<String> adapterModules) {
        String required = REQUIRED_MODULES.stream()
                .map(module -> "    " + requiresDirective(module))
                .sorted()
                .reduce("", (left, right) -> left + "\n" + right);
        String adapter = adapterModules.stream()
                .sorted()
                .map(module -> "    requires " + module + ";")
                .reduce("", (left, right) -> left + "\n" + right);
        String opens = schemaPackages().stream().sorted()
                .map(schemaPackage -> "    opens " + schemaPackage + " to " + String.join(", ", targets) + ";")
                .reduce("", (left, right) -> left + "\n" + right);
        return "KlumAST schema module validation failed:\n - " + String.join("\n - ", problems) + "\n\n"
                + "module-info.java remains user-owned; the Schema plugin will not edit it or add JVM workaround flags. "
                + "For Groovy 4/5, add the applicable directives:\n" + required + adapter + opens;
    }

    private static String requiresDirective(String module) {
        return "requires " + (COMPILER_MODULE.equals(module) ? "static " : "") + module + ";";
    }
}
