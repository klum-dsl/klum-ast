/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.PluginManager;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;

@NonNullApi
public abstract class KlumAstSchemaPlugin implements Plugin<Project> {

    private Project project;
    private String version;

    @Override
    public void apply(Project project) {
        this.project = project;
        version = PluginHelper.determineOwnVersion();

        addDependentPlugins();
        activateSourcesAndJavadocs();
        addDependencies();
        configurePublishing();
    }

    private void configurePublishing() {
        project.getPlugins().withType(MavenPublishPlugin.class, mavenPublishPlugin ->
                project.getExtensions().configure(PublishingExtension.class, publishingExtension ->
                        publishingExtension.getPublications().create("mavenJava", MavenPublication.class, configuration ->
                                configuration.from(project.getComponents().findByName("java")))));
    }

    private void addDependentPlugins() {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(JavaLibraryPlugin.class);
        pluginManager.apply(GroovyPlugin.class);
        pluginManager.apply(AnnoDocimalPlugin.class);
        pluginManager.apply(KlumAstSchemaPlugin.class);
    }

    private void activateSourcesAndJavadocs() {
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.withSourcesJar();
        java.withJavadocJar();
    }

    private void addDependencies() {
        project.getDependencies().add("compileOnly", "com.blackbuild.klum.ast:klum-ast:" + version);
        project.getDependencies().add("implementation", "com.blackbuild.klum.ast:klum-ast-runtime:" + version);
    }

}
