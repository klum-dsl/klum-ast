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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;

public class KlumDslGdslMaterializationPlugin implements Plugin<Project> {

    public static final String TASK_NAME = "materializeKlumDslGdsl";
    public static final String OUTPUT_DIRECTORY = "generated/klum-dsl-ide/gdsl";

    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new IllegalStateException("The Klum DSL GDSL materialization belongs to the root project.");
        }

        Provider<Directory> outputDirectory = outputDirectory(project);
        project.getTasks().register(TASK_NAME, KlumDslGdslMaterializationTask.class, task -> {
            task.setGroup("klum");
            task.setDescription("Materializes packaged IntelliJ GDSL contributors as root-owned IDE metadata.");
            task.getOutputDirectory().convention(outputDirectory);
        });
    }

    public static Provider<Directory> outputDirectory(Project project) {
        return project.getRootProject().getLayout().getBuildDirectory().dir(OUTPUT_DIRECTORY);
    }

    public static TaskProvider<KlumDslGdslMaterializationTask> materializationTask(Project project) {
        return project.getRootProject().getTasks().named(TASK_NAME, KlumDslGdslMaterializationTask.class);
    }

    public static void addRuntimeGdslSource(Project project, FileCollection classpath) {
        materializationTask(project).configure(task -> task.getRuntimeClasspath().from(classpath));
    }
}
