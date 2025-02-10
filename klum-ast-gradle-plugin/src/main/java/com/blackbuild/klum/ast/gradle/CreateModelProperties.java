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
            propertiesToWrite.put("model-class", script);
            PropertiesUtils.store(propertiesToWrite, file);
        } catch (IOException e) {
            throw new GradleScriptException("Failed to create model properties file for " + type, e);
        }
    }
}
