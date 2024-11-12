package com.blackbuild.klum.ast.gradle;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleScriptException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.util.PropertiesUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Properties;

public abstract class CreateModelProperties extends DefaultTask {

    @Input
    public abstract MapProperty<String, String> getModelProperties();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void createModelProperties() {
        getModelProperties().get().forEach(this::createModelPropertiesFile);
    }

    private void createModelPropertiesFile(String type, String script) {
        File file = getOutputDirectory().file(type + ".properties").get().getAsFile();
        try {
            Files.createDirectories(file.getParentFile().toPath());
            Properties propertiesToWrite = new Properties();
            propertiesToWrite.put("model-class", type);
            PropertiesUtils.store(propertiesToWrite, file);
        } catch (IOException e) {
            throw new GradleScriptException("Failed to create model properties file for " + type, e);
        }
    }
}
