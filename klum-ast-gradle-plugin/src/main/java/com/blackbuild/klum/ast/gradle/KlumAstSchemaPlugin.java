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

import com.blackbuild.annodocimal.plugin.AnnoDocimalGroovyPlugin;
import com.blackbuild.annodocimal.plugin.SourceProjectionTask;
import com.blackbuild.klum.ast.gradle.convention.GroovyDependenciesExtension;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.NonNullApi;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.util.Set;

@NonNullApi
public class KlumAstSchemaPlugin extends AbstractKlumPlugin<KlumExtension> {

    private static final String MODULE_INFO = "**/module-info.java";

    @Override
    protected void registerExtension() {
        extension = project.getExtensions().create("klumSchema", KlumExtension.class);
    }

    protected void addDependentPlugins() {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(AnnoDocimalGroovyPlugin.class);
        pluginManager.apply(IdeaPlugin.class);
    }

    protected void addDependencies() {
        project.getDependencies().add("compileOnly", "com.blackbuild.klum.ast:klum-ast");
        project.getDependencies().add("api", "com.blackbuild.klum.ast:klum-ast-runtime");
    }

    @Override
    protected void additionalConfig() {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.withSourcesJar();
        java.withJavadocJar();

        SourceSet main = java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        TaskProvider<ValidateKlumSchemaModule> validateModule = project.getTasks().register(
                "validateKlumSchemaModule",
                ValidateKlumSchemaModule.class,
                task -> {
                    task.setGroup("verification");
                    task.setDescription("Validates a user-owned Schema module descriptor for the configured Groovy generation.");
                    task.getDescriptorFiles().from(main.getAllSource().matching(pattern -> pattern.include(MODULE_INFO)));
                    task.getSchemaSources().from(project.fileTree("src/main", tree -> {
                        tree.include("**/*.java", "**/*.groovy");
                        tree.exclude(MODULE_INFO);
                    }), main.getAllSource().matching(pattern -> pattern.exclude(MODULE_INFO)));
                    task.getGroovyVersion().convention(project.getExtensions()
                            .getByType(GroovyDependenciesExtension.class).getGroovyVersion());
                    task.getOptionalAdapterModules().addAll(project.provider(this::optionalAdapterModules));
                });
        project.getTasks().named("check", task -> task.dependsOn(validateModule));
        project.getTasks().withType(AbstractPublishToMaven.class).configureEach(task -> task.dependsOn(validateModule));
        Provider<Directory> mirrorDirectory =
                project.getLayout().getBuildDirectory().dir("generated/sources/klum-dsl-ide/main");
        TaskProvider<SourceProjectionTask> createMirrors = project.getTasks().register(
                "createKlumDslSourceMirrors",
                SourceProjectionTask.class,
                task -> {
                    task.setGroup("klum");
                    task.setDescription("Refreshes IDE-only AnnoDocimal source mirrors for generated Foo_DSL namespaces.");
                    task.getClassesDirectories().from(main.getOutput().getClassesDirs());
                    task.getIncludes().set(Set.of("**/*_DSL.class"));
                    task.getExcludes().set(Set.of("**/*$*"));
                    task.getOutputDirectory().convention(mirrorDirectory);
                });
        project.getRootProject().getPluginManager().apply(KlumDslSourceMirrorsAggregationPlugin.class);
        project.getRootProject().getTasks()
                .named(KlumDslSourceMirrorsAggregationPlugin.TASK_NAME)
                .configure(task -> task.dependsOn(createMirrors));

        IdeaModel moduleIdea = project.getExtensions().getByType(IdeaModel.class);
        moduleIdea.getModule().getSourceDirs().add(mirrorDirectory.get().getAsFile());
        moduleIdea.getModule().getGeneratedSourceDirs().add(mirrorDirectory.get().getAsFile());

        project.getTasks().named("javadoc", Javadoc.class, task -> task.exclude("**/*_DSL.java"));
    }

    private Set<String> optionalAdapterModules() {
        Set<String> modules = new java.util.LinkedHashSet<>();
        for (String configurationName : Set.of("api", "implementation", "compileOnly")) {
            Configuration configuration = project.getConfigurations().findByName(configurationName);
            if (configuration == null)
                continue;
            for (Dependency dependency : configuration.getDependencies()) {
                if (!"com.blackbuild.klum.ast".equals(dependency.getGroup()))
                    continue;
                if ("klum-ast-jackson".equals(dependency.getName()))
                    modules.add("com.blackbuild.klum.ast.jackson");
                if ("klum-ast-bean-validation".equals(dependency.getName()))
                    modules.add("com.blackbuild.klum.ast.validation.bean");
            }
        }
        return modules;
    }
}
