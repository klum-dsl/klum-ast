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

import org.gradle.api.NonNullApi;
import org.gradle.api.file.Directory;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.jvm.tasks.ProcessResources;

@NonNullApi
public class KlumAstModelPlugin extends AbstractKlumPlugin<KlumModelExtension> {

    @Override
    protected void registerExtension() {
        extension = project.getExtensions().create("klumModel", KlumModelExtension.class);
        project.getConfigurations().dependencyScope("schemas", conf ->
                conf.fromDependencyCollector(extension.getSchemas().getSchema()));
    }

    @Override
    protected void additionalConfig() {
        project.getConfigurations().getByName("api").extendsFrom(project.getConfigurations().getByName("schemas"));
        Provider<Directory> descriptorDir = project.getLayout().getBuildDirectory().dir("modelDescriptors");
        TaskProvider<CreateModelProperties> createModelDescriptors = project.getTasks().register("createModelDescriptors", CreateModelProperties.class, task -> {
            task.getOutputDirectory().convention(descriptorDir);
            task.getModelProperties().convention(extension.getTopLevelScripts());
        });
        project.getTasks().named("processResources", ProcessResources.class, task ->
                task.from(createModelDescriptors, copySpec -> copySpec.into("META-INF/klum-model")));
    }

    @Override
    protected void addDependencies() {
        // nothing, all dependencies are transitive for now
    }

    protected void addDependentPlugins() {
        // no additional dependencies
    }
}
