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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.api.component.AdhocComponentWithVariants;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.publish.maven.tasks.GenerateMavenPom;
import org.gradle.api.publish.tasks.GenerateModuleMetadata;
import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/** Installs the producer and its local contribution; Models select the outgoing capability independently. */
final class KlumSchemaGdslProducer {
    private KlumSchemaGdslProducer() {}

    static void configure(Project project, KlumSchemaGdslExtension extension) {
        // Schema coordinates and publication identity must observe all project afterEvaluate callbacks.
        project.getGradle().projectsEvaluated(ignored -> {
            extension.getPublish().finalizeValue();
            if (!extension.getPublish().get().booleanValue()) return;
            String group = project.getGroup().toString();
            String artifact = project.getName();
            String version = project.getVersion().toString();
            if (group.isBlank() || version.isBlank() || version.equals("unspecified")
                    || group.codePoints().anyMatch(Character::isISOControl)
                    || version.codePoints().anyMatch(Character::isISOControl))
                throw new GradleException("Enabled GDSL requires an explicit Schema group and version");
            String coordinates = group + ":" + artifact + ":" + version;
            List<Map<String, String>> mappings = extension.getMappings().stream().map(mapping -> {
                mapping.getFileNameSuffix().finalizeValue();
                mapping.getModelType().finalizeValue();
                return Map.of("id", mapping.getName(),
                        "fileNameSuffix", mapping.getFileNameSuffix().getOrElse(""),
                        "modelType", mapping.getModelType().getOrElse(""));
            }).toList();
            Map<String, String> suffixes = new LinkedHashMap<>();
            Map<String, String> models = new LinkedHashMap<>();
            // Validate and normalize before attaching an outgoing contract or freezing task inputs.
            KlumGdslMetadataFormat.validate(mappings, coordinates).forEach(mapping -> {
                suffixes.put(mapping.id(), mapping.suffix());
                models.put(mapping.id(), mapping.modelType());
            });
            TaskProvider<GenerateKlumGdslMetadata> generate = project.getTasks().register(
                    "generateKlumGdslMetadata", GenerateKlumGdslMetadata.class, task -> {
                        task.setGroup("klum");
                        task.setDescription("Generates the bounded Schema-owned GDSL v1 catalog and contributors.");
                        task.getFileNameSuffixes().set(suffixes);
                        task.getModelTypes().set(models);
                        task.getSchemaCoordinates().set(coordinates);
                        task.getOutputDirectory().convention(project.getLayout().getBuildDirectory().dir("generated/klum-gdsl-metadata"));
                    });
            TaskProvider<KlumGdslJar> jar = project.getTasks().register("klumGdslJar", KlumGdslJar.class, task -> {
                task.setGroup("klum");
                task.setDescription("Packages Schema-owned editor metadata separately from library artifacts.");
                task.getArchiveClassifier().set("gdsl");
                task.setPreserveFileTimestamps(false);
                task.setReproducibleFileOrder(true);
                task.from(generate.flatMap(GenerateKlumGdslMetadata::getOutputDirectory));
                task.getManifest().attributes(Map.of("Klum-Schema-Coordinates", coordinates, "Klum-Gdsl-Format", "1"));
            });
            KlumDslGdslMaterializationPlugin.materializationTask(project).configure(task -> {
                task.dependsOn(jar);
                task.getSchemaMetadataArtifacts().add(jar.flatMap(KlumGdslJar::getArchiveFile)
                        .map(file -> new KlumGdslArtifactInput(file.getAsFile(), coordinates)));
                task.getNormalSchemaCoordinates().add(coordinates);
            });
            Configuration elements = project.getConfigurations().create("klumGdslElements", configuration -> {
                configuration.setCanBeResolved(false);
                configuration.setCanBeConsumed(true);
                configuration.setCanBeDeclared(false);
                configuration.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE,
                        project.getObjects().named(Category.class, Category.DOCUMENTATION));
                configuration.getAttributes().attribute(DocsType.DOCS_TYPE_ATTRIBUTE,
                        project.getObjects().named(DocsType.class, "klum-gdsl"));
                configuration.getAttributes().attribute(Usage.USAGE_ATTRIBUTE,
                        project.getObjects().named(Usage.class, "klum-ide-metadata"));
                configuration.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE,
                        project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
                configuration.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar");
                configuration.getOutgoing().capability(group + ":" + artifact + "-gdsl:" + version);
                configuration.getOutgoing().artifact(jar);
            });
            AdhocComponentWithVariants java = (AdhocComponentWithVariants) project.getComponents().getByName("java");
            java.addVariantsFromConfiguration(elements, details -> {
                details.mapToMavenScope("runtime");
                details.mapToOptional();
            });
            project.getPluginManager().withPlugin("maven-publish", applied -> {
                MavenPublication publication = (MavenPublication) project.getExtensions().getByType(PublishingExtension.class)
                        .getPublications().getByName("mavenJava");
                TaskProvider<ValidateKlumGdslPublication> validate = project.getTasks().register(
                        "validateKlumGdslPublication", ValidateKlumGdslPublication.class, task -> {
                            task.setGroup("verification");
                            task.getSchemaCoordinates().set(coordinates);
                            task.getPublicationCoordinates().set(project.provider(() -> publication.getGroupId() + ":"
                                    + publication.getArtifactId() + ":" + publication.getVersion()));
                        });
                project.getTasks().withType(GenerateMavenPom.class).configureEach(task -> task.dependsOn(validate));
                project.getTasks().withType(GenerateModuleMetadata.class).configureEach(task -> task.dependsOn(validate));
                project.getTasks().withType(AbstractPublishToMaven.class).configureEach(task -> task.dependsOn(validate));
            });
        });
    }
}
