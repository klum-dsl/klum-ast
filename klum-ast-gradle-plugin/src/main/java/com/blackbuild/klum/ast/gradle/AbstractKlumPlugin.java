package com.blackbuild.klum.ast.gradle;

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
public abstract class AbstractKlumPlugin implements Plugin<Project> {

    protected Project project;
    protected String version;

    @Override
    public final void apply(Project project) {
        this.project = project;
        version = PluginHelper.determineOwnVersion();
        registerExtension();
        configureGroovyAndJava();
        addDependentPlugins();
        addDependencies();
        configurePublishing();
        doApply();
    }

    protected abstract void doApply();

    protected abstract void registerExtension();

    private void configureGroovyAndJava() {
        PluginManager pluginManager = project.getPluginManager();
        pluginManager.apply(JavaLibraryPlugin.class);
        pluginManager.apply(GroovyPlugin.class);
        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        java.withSourcesJar();
        java.withJavadocJar();
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
