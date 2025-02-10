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
package com.blackbuild.klum.ast.gradle.convention;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.plugins.GroovyPlugin;
import org.gradle.api.tasks.testing.Test;

public class GroovyDependenciesPlugin implements Plugin<Project> {

    protected Configuration groovy;
    protected Configuration spock;
    protected Project project;
    protected GroovyDependenciesExtension groovyDependencies;

    @Override
    public void apply(Project project) {
        this.project = project;

        createExtension();

        project.getPlugins().withType(GroovyPlugin.class, plugin -> {
            createConfigurations(project);

            addGroovyDefaultDependencies();
            addSpockDefaultDependencies();
            configureTestTasks();
        });
    }

    private void createExtension() {
        groovyDependencies = project.getExtensions().create("groovyDependencies", GroovyDependenciesExtension.class);
        Project rootProject = project.getRootProject();
        if (rootProject != project) {
            GroovyDependenciesExtension rootExtension = rootProject.getExtensions().findByType(GroovyDependenciesExtension.class);
            if (rootExtension != null)
                groovyDependencies.copyFrom(rootExtension);
        }
    }

    private void createConfigurations(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        groovy = configurations.create("groovy", c -> {
            c.setVisible(false);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setDescription("The Groovy libraries to use for this project.");
        });

        configurations.named("compileOnly", c -> c.extendsFrom(groovy));

        spock = configurations.create("spock", c -> {
            c.setVisible(false);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.setDescription("The spock version used for testing.");
        });

        configurations.named("testImplementation", c -> c.extendsFrom(spock));
    }

    protected void addGroovyDefaultDependencies() {
        groovy.defaultDependencies(d -> {
                    if (groovyDependencies.getGroovyVersionDependency().isPresent())
                        d.add(project.getDependencies().create(groovyDependencies.getGroovyVersionDependency().get()));
                }
        );
    }

    protected void addSpockDefaultDependencies() {
        spock.defaultDependencies(d -> {
                    if (groovyDependencies.getSpockVersionDependency().isPresent())
                        d.add(project.getDependencies().create(groovyDependencies.getSpockVersionDependency().get()));
                    if (groovyDependencies.getGroovyVersionDependency().isPresent())
                        d.add(project.getDependencies().create(groovyDependencies.getGroovyVersionDependency().get()));
                }
        );
    }

    protected void configureTestTasks() {
        project.getTasks().withType(Test.class).configureEach(test -> {
            if (groovyDependencies.getGroovyVersionDependency().isPresent() && !groovyDependencies.getSpockVersionDependency().get().contains("groovy-2.4"))
                test.useJUnitPlatform();
        });
    }
}
