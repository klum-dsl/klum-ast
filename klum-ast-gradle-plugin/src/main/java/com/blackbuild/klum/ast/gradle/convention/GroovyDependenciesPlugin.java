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
