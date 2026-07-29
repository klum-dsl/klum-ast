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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the user-owned JPMS descriptor of a Schema project.
 */
public abstract class ValidateKlumSchemaModule extends DefaultTask {

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

        SchemaModuleValidation.Input input = new SchemaModuleValidation.Input(
                getProject().getPath(),
                groovyMajorVersion(),
                read(descriptors.iterator().next()),
                schemaSourceTexts(),
                getOptionalAdapterModules().get());
        SchemaModuleValidation.diagnostic(input).ifPresent(diagnostic -> {
            throw new GradleException(diagnostic);
        });
    }

    private int groovyMajorVersion() {
        String configured = getGroovyVersion().get();
        int separator = configured.indexOf('.');
        return Integer.parseInt(separator < 0 ? configured : configured.substring(0, separator));
    }

    private List<String> schemaSourceTexts() {
        Set<File> sources = new LinkedHashSet<>(getSchemaSources().getFiles());
        sources.addAll(getProject().fileTree("src/main", tree -> {
            tree.include("**/*.java", "**/*.groovy");
            tree.exclude("**/module-info.java");
        }).getFiles());
        return sources.stream().map(ValidateKlumSchemaModule::read).toList();
    }

    private static String read(File file) {
        try {
            return Files.readString(file.toPath());
        } catch (IOException exception) {
            throw new GradleException("Cannot read " + file, exception);
        }
    }
}
