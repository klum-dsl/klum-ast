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
import org.gradle.api.provider.Provider;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.artifacts.result.DependencyResult;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.DocsType;
import org.gradle.api.attributes.Usage;
import org.gradle.api.artifacts.type.ArtifactTypeDefinition;
import org.gradle.plugins.ide.idea.IdeaPlugin;
import org.gradle.plugins.ide.idea.model.IdeaModel;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Set;
import java.util.Comparator;

/** Lazy binary consumer. No normal dependency scope inherits any metadata dependency. */
final class KlumModelGdslConsumer {
    private KlumModelGdslConsumer() {}

    static void configure(Project project, KlumModelGdslExtension extension) {
        Configuration declarations = project.getConfigurations().dependencyScope("klumGdsl").get();
        project.getGradle().projectsEvaluated(ignored -> {
            extension.getEnabled().finalizeValue();
            if (!extension.getEnabled().get().booleanValue()) return;
            Configuration normal = project.getConfigurations().getByName("compileClasspath");
            Configuration variantDependencies = project.getConfigurations().dependencyScope("klumGdslVariantDependencies").get();
            Configuration classifierDependencies = project.getConfigurations().dependencyScope("klumGdslClassifierDependencies").get();
            Configuration variant = resolver(project, "klumGdslClasspath");
            variant.extendsFrom(variantDependencies);
            configureMetadataAttributes(project, variant);
            variant.shouldResolveConsistentlyWith(normal);
            restoreMetadataSelection(variant, normal);
            Configuration classifier = resolver(project, "klumGdslClassifierClasspath");
            classifier.extendsFrom(classifierDependencies);
            List<String> exactClassifierOrigins = routeDependencies(declarations, variantDependencies, classifierDependencies);
            List<String> variantModules = variantDependencies.getDependencies().stream()
                    .map(dependency -> dependency.getGroup() + ":" + dependency.getName()).toList();
            registerIdeRoot(project, normal, variant, classifier, exactClassifierOrigins, variantModules);
        });
    }

    private static void configureMetadataAttributes(Project project, Configuration variant) {
        variant.getAttributes().attribute(Category.CATEGORY_ATTRIBUTE, project.getObjects().named(Category.class, Category.DOCUMENTATION));
        variant.getAttributes().attribute(DocsType.DOCS_TYPE_ATTRIBUTE, project.getObjects().named(DocsType.class, "klum-gdsl"));
        variant.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, "klum-ide-metadata"));
        variant.getAttributes().attribute(Bundling.BUNDLING_ATTRIBUTE, project.getObjects().named(Bundling.class, Bundling.EXTERNAL));
        variant.getAttributes().attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "jar");
    }

    private static void restoreMetadataSelection(Configuration variant, Configuration normal) {
        // A plain user substitution resets capability selectors. Restore the metadata selection last,
        // using the selected normal result, including substituted module identity, as the authority.
        variant.getResolutionStrategy().dependencySubstitution(substitutions -> substitutions.all(details -> {
            if (!(details.getRequested() instanceof ModuleComponentSelector requested)) return;
            String selected = selectedSchema(normal.getIncoming().getResolutionResult().getRootComponent().get().getDependencies(),
                    requested.getGroup(), requested.getModule(), new HashSet<>());
            if (selected == null)
                throw new GradleException("No selected normal Schema for requested GDSL " + requested.getGroup() + ":" + requested.getModule());
            details.useTarget(substitutions.variant(substitutions.module(selected),
                    selection -> selection.capabilities(capabilities -> capabilities.requireFeature("gdsl"))));
        }));
    }

    private static List<String> routeDependencies(Configuration declarations, Configuration variantDependencies,
                                                   Configuration classifierDependencies) {
        List<String> exactClassifierOrigins = new ArrayList<>();
        declarations.getDependencies().forEach(dependency -> {
            if (!(dependency instanceof ExternalModuleDependency module))
                throw new GradleException("GDSL-2 requires an explicit binary Schema module in klumGdsl");
            ExternalModuleDependency request = module.copy();
            if (request.getArtifacts().isEmpty()) {
                var version = request.getVersionConstraint();
                if (!version.getRequiredVersion().isEmpty() || !version.getStrictVersion().isEmpty() || !version.getPreferredVersion().isEmpty()
                        || !version.getRejectedVersions().isEmpty())
                    throw new GradleException("GMM GDSL selections must omit the version; the normal Schema dependency is authoritative");
                request.capabilities(capabilities -> capabilities.requireFeature("gdsl"));
                variantDependencies.getDependencies().add(request);
            } else {
                if (request.getArtifacts().size() != 1 || !"gdsl".equals(request.getArtifacts().iterator().next().getClassifier())
                        || !exactVersion(request.getVersion()))
                    throw new GradleException("GDSL classifier fallback requires one gdsl artifact and an exact Schema GAV (no dynamic/range version)");
                exactClassifierOrigins.add(request.getGroup() + ":" + request.getName() + ":" + request.getVersion());
                classifierDependencies.getDependencies().add(request);
            }
        });
        return List.copyOf(exactClassifierOrigins);
    }

    private static void registerIdeRoot(Project project, Configuration normal, Configuration variant,
                                        Configuration classifier, List<String> exactClassifierOrigins, List<String> variantModules) {
        Provider<List<String>> selectedVariantOrigins = normal.getIncoming().getResolutionResult().getRootComponent().map(root ->
                variantModules.stream().map(module -> {
                    String[] identity = module.split(":", -1);
                    String selected = selectedSchema(root.getDependencies(), identity[0], identity[1], new HashSet<>());
                    if (selected == null) throw new GradleException("No selected normal Schema for requested GDSL " + module);
                    return selected;
                }).toList());
        project.getPluginManager().apply(IdeaPlugin.class);
        project.getRootProject().getPluginManager().apply(KlumDslGdslMaterializationPlugin.class);
        KlumDslGdslMaterializationPlugin.materializationTask(project).configure(task -> {
            task.getRuntimeClasspath().from(normal);
            task.getSchemaMetadataArtifacts().addAll(variant.getIncoming().getArtifacts().getResolvedArtifacts().map(artifacts ->
                    singleArchives(artifacts, selectedVariantOrigins.get(), "GMM")));
            task.getSchemaMetadataArtifacts().addAll(classifier.getIncoming().getArtifacts().getResolvedArtifacts().map(artifacts ->
                    singleArchives(artifacts, exactClassifierOrigins, "classifier")));
            task.getNormalSchemaCoordinates().addAll(normal.getIncoming().getResolutionResult().getRootComponent().map(root ->
                    normalCoordinates(root.getDependencies())));
        });
        IdeaModel idea = project.getExtensions().getByType(IdeaModel.class);
        var output = KlumDslGdslMaterializationPlugin.outputDirectory(project).get().getAsFile();
        idea.getModule().getResourceDirs().add(output);
        idea.getModule().getGeneratedSourceDirs().add(output);
    }

    private static Configuration resolver(Project project, String name) {
        Configuration result = project.getConfigurations().resolvable(name).get();
        result.setTransitive(false);
        return result;
    }

    private static boolean exactVersion(String version) {
        return version != null && !version.isBlank() && !version.contains("+") && !version.startsWith("latest.")
                && !version.matches(".*[\\[\\](),].*");
    }

    private static List<KlumGdslArtifactInput> singleArchives(Set<ResolvedArtifactResult> artifacts, List<String> expectedOrigins, String mode) {
        List<KlumGdslArtifactInput> selected = artifacts.stream().map(artifact -> {
            if (!(artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier module))
                throw new GradleException("GDSL-2 requires binary Schema metadata: " + artifact.getId());
            return new KlumGdslArtifactInput(artifact.getFile(), coordinates(module));
        }).sorted(Comparator.comparing(KlumGdslArtifactInput::getCoordinates)).toList();
        Set<String> expected = new HashSet<>(expectedOrigins);
        List<String> selectedOrigins = selected.stream().map(KlumGdslArtifactInput::getCoordinates).toList();
        if (!expected.containsAll(selectedOrigins))
            throw new GradleException("GDSL " + mode + " selection changed requested Schema GAV: " + expected + " -> " + selectedOrigins);
        for (String origin : expected) {
            long count = selected.stream().filter(artifact -> artifact.getCoordinates().equals(origin)).count();
            if (count != 1)
                throw new GradleException("GDSL " + mode + " requires exactly one metadata archive for " + origin + "; found " + count);
        }
        return selected;
    }

    private static List<String> normalCoordinates(Set<? extends DependencyResult> dependencies) {
        // The authoritative result includes selected transitive Schemas and platform-controlled versions.
        Set<String> result = new TreeSet<>();
        collectCoordinates(dependencies, result, new HashSet<>());
        return List.copyOf(result);
    }

    private static void collectCoordinates(Set<? extends DependencyResult> dependencies,
                                           Set<String> result, Set<String> visited) {
        for (var dependency : dependencies) {
            if (dependency instanceof ResolvedDependencyResult resolved) {
                var selected = resolved.getSelected();
                if (!visited.add(selected.getId().getDisplayName())) continue;
                if (selected.getId() instanceof ModuleComponentIdentifier module) result.add(coordinates(module));
                collectCoordinates(selected.getDependencies(), result, visited);
            }
        }
    }

    private static String selectedSchema(Set<? extends DependencyResult> dependencies, String group, String name, Set<String> visited) {
        for (var dependency : dependencies) {
            if (!(dependency instanceof ResolvedDependencyResult resolved)) continue;
            var selected = resolved.getSelected();
            if (resolved.getRequested() instanceof ModuleComponentSelector requested
                    && group.equals(requested.getGroup()) && name.equals(requested.getModule())
                    && selected.getId() instanceof ModuleComponentIdentifier module) return coordinates(module);
            // Consistent resolution also contributes a constraint under the substituted identity.
            if (selected.getId() instanceof ModuleComponentIdentifier module
                    && group.equals(module.getGroup()) && name.equals(module.getModule())) return coordinates(module);
            if (visited.add(selected.getId().getDisplayName())) {
                String nested = selectedSchema(selected.getDependencies(), group, name, visited);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static String coordinates(ModuleComponentIdentifier module) {
        return module.getGroup() + ":" + module.getModule() + ":" + module.getVersion();
    }
}
