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
package com.blackbuild.klum.ast.gradle;

import com.blackbuild.klum.ast.gradle.convention.GroovyDependenciesExtension;
import com.blackbuild.klum.ast.gradle.convention.GroovyDependenciesPlugin;
import com.blackbuild.klum.ast.gradle.convention.GroovyVersion;
import org.gradle.api.NonNullApi;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;

@NonNullApi
public abstract class AbstractKlumPlugin<T extends KlumExtension> implements Plugin<Project> {

    protected Project project;
    protected String version;
    protected T extension;

    @Override
    public final void apply(Project project) {
        this.project = project;
        version = PluginHelper.determineOwnVersion();
        registerExtension();
        extension.getGroovyVersion().convention(GroovyVersion.GROOVY_3);
        configureGroovyAndJava();
        addDependentPlugins();
        addDependencies();
        configurePublishing();
        additionalConfig();
    }

    protected abstract void additionalConfig();

    protected abstract void registerExtension();

    private void configureGroovyAndJava() {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(JavaLibraryPlugin.class);
        pluginManager.apply(GroovyPlugin.class);
        pluginManager.apply(GroovyDependenciesPlugin.class);
        Project rootProject = project.getRootProject();
        if (rootProject == project || !rootProject.getPlugins().hasPlugin(GroovyDependenciesPlugin.class)) {
            GroovyDependenciesExtension dependenciesExtension = project.getExtensions().getByType(GroovyDependenciesExtension.class);
            dependenciesExtension.getGroovyVersion().convention(extension.getGroovyVersion());
        }
    }

    protected abstract void addDependentPlugins();

    protected abstract void addDependencies();

    private void configurePublishing() {
        project.getPlugins().withType(MavenPublishPlugin.class, mavenPublishPlugin ->
                project.getExtensions().configure(PublishingExtension.class, publishingExtension ->
                        publishingExtension.getPublications().create("mavenJava", MavenPublication.class, configuration ->
                                configuration.from(project.getComponents().findByName("java")))));
    }
}
