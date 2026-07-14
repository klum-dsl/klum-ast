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

import com.blackbuild.annodocimal.generator.AnnoDocGenerator;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.IOException;

/**
 * Creates source mirrors for AST-generated {@code Foo_DSL} support namespaces.
 *
 * <p>The output is IDE metadata. It must never be attached to a Gradle source set or any production artifact.</p>
 */
@CacheableTask
public abstract class CreateKlumDslSourceMirrors extends DefaultTask {

    private final ConfigurableFileCollection classesDirectories = getProject().getObjects().fileCollection();

    @Classpath
    public ConfigurableFileCollection getClassesDirectories() {
        return classesDirectories;
    }

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Inject
    protected abstract FileSystemOperations getFileSystemOperations();

    public void classes(Object... classes) {
        classesDirectories.from(classes);
    }

    @TaskAction
    public void createMirrors() {
        getFileSystemOperations().delete(spec -> spec.delete(getOutputDirectory()));
        getClassesDirectories().getAsFileTree()
                .matching(patterns -> patterns.include("**/*_DSL.class").exclude("**/*$*"))
                .visit(this::createMirror);
    }

    private void createMirror(FileVisitDetails classFile) {
        if (classFile.isDirectory()) return;
        try {
            AnnoDocGenerator.generate(classFile.getFile(), getOutputDirectory().get().getAsFile());
        } catch (IOException exception) {
            throw new GradleException("Could not create IDE source mirror for " + classFile.getRelativePath(), exception);
        }
    }
}
