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

import com.blackbuild.annodocimal.plugin.AnnoDocimalPlugin;
import org.gradle.api.NonNullApi;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.javadoc.Javadoc;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;

@NonNullApi
public class KlumAstSchemaPlugin extends AbstractKlumPlugin<KlumExtension> {

    @Override
    protected void registerExtension() {
        extension = project.getExtensions().create("klumSchema", KlumExtension.class);
    }

    protected void addDependentPlugins() {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(AnnoDocimalPlugin.class);
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
        TaskProvider<CreateKlumDslSourceMirrors> mirrors = project.getTasks().register(
                "createKlumDslSourceMirrors",
                CreateKlumDslSourceMirrors.class,
                task -> {
                    task.setGroup("klum");
                    task.setDescription("Refreshes IDE-only AnnoDocimal source mirrors for generated Foo_DSL namespaces.");
                    task.classes(main.getOutput().getClassesDirs());
                    task.getOutputDirectory().convention(
                            project.getLayout().getBuildDirectory().dir("generated/sources/klum-dsl-ide/main"));
                });

        IdeaModel moduleIdea = project.getExtensions().getByType(IdeaModel.class);
        moduleIdea.getModule().getSourceDirs().add(mirrors.get().getOutputDirectory().get().getAsFile());
        moduleIdea.getModule().getGeneratedSourceDirs().add(mirrors.get().getOutputDirectory().get().getAsFile());

        project.getTasks().named("javadoc", Javadoc.class, task -> task.exclude("**/*_DSL.java"));
    }
}
