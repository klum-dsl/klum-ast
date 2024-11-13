package com.blackbuild.klum.ast.gradle;

import org.gradle.api.NonNullApi;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.jvm.tasks.ProcessResources;

@NonNullApi
public class KlumAstModelPlugin extends AbstractKlumPlugin {

    private KlumModelExtension extension;

    @Override
    protected void registerExtension() {
        extension = project.getExtensions().create("klumModel", KlumModelExtension.class);
        project.getConfigurations().dependencyScope("schemas", conf ->
                conf.fromDependencyCollector(extension.getSchemas().getSchema()));
    }

    @Override
    protected void doApply() {
        project.getConfigurations().getByName("api").extendsFrom(project.getConfigurations().getByName("schemas"));
        Provider<Directory> descriptorDir = project.getLayout().getBuildDirectory().dir("modelDescriptors");
        TaskProvider<CreateModelProperties> createModelDescriptors = project.getTasks().register("createModelDescriptors", CreateModelProperties.class, task -> {
            task.getOutputDirectory().convention(descriptorDir);
            task.getModelProperties().convention(extension.getTopLevelScripts());
        });
        project.getTasks().named("processResources", ProcessResources.class, task -> {
            task.from(createModelDescriptors, copySpec -> copySpec.into("META-INF/klum-model"));
        });
    }

    @Override
    protected void addDependencies() {
        // nothing, all dependencies are transitive for now
    }

    protected void addDependentPlugins() {
        // no additional dependencies
    }
}
