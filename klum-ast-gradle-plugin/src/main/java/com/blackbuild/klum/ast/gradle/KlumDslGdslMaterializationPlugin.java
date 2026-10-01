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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.tasks.SourceSet;

public class KlumDslGdslMaterializationPlugin implements Plugin<Project> {

    public static final String TASK_NAME = "materializeKlumDslGdsl";
    public static final String OUTPUT_DIRECTORY = "generated/klum-dsl-ide/gdsl";

    @Override
    public void apply(Project project) {
        if (project != project.getRootProject()) {
            throw new IllegalStateException("The Klum DSL GDSL materialization belongs to the root project.");
        }

        project.getPluginManager().apply(BasePlugin.class);
        Provider<Directory> outputDirectory = outputDirectory(project);
        project.getTasks().register(TASK_NAME, KlumDslGdslMaterializationTask.class, task -> {
            task.setGroup("klum");
            task.setDescription("Materializes packaged IntelliJ GDSL contributors as root-owned IDE metadata.");
            task.getOutputDirectory().convention(outputDirectory);
        });
        project.getGradle().projectsEvaluated(ignored -> registerParticipants(project));
    }

    static void registerParticipants(Project project) {
        Project root = project.getRootProject();
        var state = root.getExtensions().getExtraProperties();
        String marker = "klumGdslParticipantsRegistered";
        if (state.has(marker)) return;
        state.set(marker, true);
        root.getAllprojects().forEach(participant -> {
            if (!participant.getPlugins().hasPlugin(KlumAstSchemaPlugin.class)
                    && !participant.getPlugins().hasPlugin(KlumAstModelPlugin.class)) return;
            Configuration normal = participant.getConfigurations().getByName("compileClasspath");
            SourceSet main = participant.getExtensions().getByType(JavaPluginExtension.class)
                    .getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            materializationTask(project).configure(task -> {
                if (participant.getPlugins().hasPlugin(KlumAstSchemaPlugin.class))
                    task.getNormalSchemaCoordinates().add(participant.getGroup() + ":" + participant.getName() + ":" + participant.getVersion());
                task.getNormalSchemaCoordinates().addAll(normal.getIncoming().getResolutionResult().getRootComponent()
                        .map(component -> KlumModelGdslConsumer.normalCoordinates(component.getDependencies())));
                // The SourceDirectorySet retains dependencies on generated source/resource providers.
                task.getLegacyGdslSources().from(main.getAllSource().matching(pattern -> pattern.include("**/*.gdsl")));
                // Include .gdsl files in configured language roots even when their language filters exclude them.
                task.getLegacyGdslSources().from(main.getAllSource().getSrcDirs().stream()
                        .map(directory -> participant.fileTree(directory, pattern -> pattern.include("**/*.gdsl"))).toList());
                // Project resources are inspected in source; resolving their library artifacts would compile Schemas.
                task.getLegacyGdslClasspath().from(binaryClasspath(normal));
            });
        });
    }

    static FileCollection binaryClasspath(Configuration normal) {
        return normal.getIncoming().artifactView(view -> view.componentFilter(id -> !(id instanceof ProjectComponentIdentifier))).getFiles();
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
