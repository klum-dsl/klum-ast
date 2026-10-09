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
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.component.ComponentSelector;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
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

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.Set;
import java.util.Comparator;

/** Lazy source/binary consumer. No normal dependency scope inherits any metadata dependency. */
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
                    .map(dependency -> dependency instanceof ProjectDependency source ? source.getPath()
                            : dependency.getGroup() + ":" + dependency.getName()).toList();
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
            ComponentSelector requested = details.getRequested();
            String key;
            if (requested instanceof ModuleComponentSelector module) key = module.getGroup() + ":" + module.getModule();
            else if (requested instanceof ProjectComponentSelector source) key = source.getProjectPath();
            else return;
            ResolvedComponentResult selected = selectedSchema(normal.getIncoming().getResolutionResult().getRootComponent().get().getDependencies(),
                    key, new HashSet<>());
            if (selected == null) throw new GradleException("No selected normal Schema for requested GDSL " + key);
            ComponentSelector target;
            if (selected.getId() instanceof ProjectComponentIdentifier source) {
                if (!source.getBuild().isCurrentBuild()) throw new GradleException("Included-build GDSL selection is not supported: " + source);
                target = substitutions.project(source.getProjectPath());
            } else target = substitutions.module(coordinates(selected));
            details.useTarget(substitutions.variant(target,
                    selection -> selection.capabilities(capabilities -> capabilities.requireFeature("gdsl"))));
        }));
    }

    private static List<String> routeDependencies(Configuration declarations, Configuration variantDependencies,
                                                   Configuration classifierDependencies) {
        List<String> exactClassifierOrigins = new ArrayList<>();
        declarations.getDependencies().forEach(dependency -> {
            if (!(dependency instanceof ModuleDependency module))
                throw new GradleException("GDSL requires an explicit Schema module or project in klumGdsl");
            ModuleDependency request = module.copy();
            if (request.getArtifacts().isEmpty()) {
                if (request instanceof ExternalModuleDependency external) {
                    var version = external.getVersionConstraint();
                    if (!version.getRequiredVersion().isEmpty() || !version.getStrictVersion().isEmpty() || !version.getPreferredVersion().isEmpty()
                            || !version.getRejectedVersions().isEmpty())
                        throw new GradleException("GMM GDSL selections must omit the version; the normal Schema dependency is authoritative");
                }
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
        Provider<Map<String, String>> selectedVariantOrigins = project.provider(() -> {
            Map<String, String> result = new LinkedHashMap<>();
            for (String module : variantModules) {
                var selected = selectedSchema(normal.getIncoming().getResolutionResult().getRootComponent().get().getDependencies(), module, new HashSet<>());
                if (selected == null) throw new GradleException("No selected normal Schema for requested GDSL " + module);
                result.put(selected.getId().getDisplayName(), coordinates(selected));
            }
            return result;
        });
        project.getPluginManager().apply(IdeaPlugin.class);
        project.getRootProject().getPluginManager().apply(KlumDslGdslMaterializationPlugin.class);
        KlumDslGdslMaterializationPlugin.registerParticipants(project);
        KlumDslGdslMaterializationPlugin.materializationTask(project).configure(task -> {
            task.dependsOn(variant.getIncoming().getArtifacts().getArtifactFiles(), classifier.getIncoming().getArtifacts().getArtifactFiles());
            task.getRuntimeClasspath().from(KlumDslGdslMaterializationPlugin.binaryClasspath(normal));
            task.getSchemaMetadataArtifacts().addAll(variant.getIncoming().getArtifacts().getResolvedArtifacts().zip(selectedVariantOrigins,
                    KlumModelGdslConsumer::singleVariantArchives));
            task.getSchemaMetadataArtifacts().addAll(classifier.getIncoming().getArtifacts().getResolvedArtifacts().map(artifacts ->
                    singleArchives(artifacts, exactClassifierOrigins, "classifier")));
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

    private static List<KlumGdslArtifactInput> singleVariantArchives(Set<ResolvedArtifactResult> artifacts, Map<String, String> expected) {
        List<KlumGdslArtifactInput> selected = artifacts.stream().map(artifact -> {
            String origin = expected.get(artifact.getId().getComponentIdentifier().getDisplayName());
            if (origin == null) throw new GradleException("GDSL metadata origin differs from normal Schema: " + artifact.getId());
            return new KlumGdslArtifactInput(artifact.getFile(), origin);
        }).sorted(Comparator.comparing(KlumGdslArtifactInput::getCoordinates)).toList();
        return validateArchiveCounts(selected, List.copyOf(expected.values()), "GMM");
    }

    private static List<KlumGdslArtifactInput> singleArchives(Set<ResolvedArtifactResult> artifacts, List<String> expectedOrigins, String mode) {
        List<KlumGdslArtifactInput> selected = artifacts.stream().map(artifact -> {
            if (!(artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier module))
                throw new GradleException("Classifier GDSL requires binary Schema metadata: " + artifact.getId());
            return new KlumGdslArtifactInput(artifact.getFile(), coordinates(module));
        }).sorted(Comparator.comparing(KlumGdslArtifactInput::getCoordinates)).toList();
        return validateArchiveCounts(selected, expectedOrigins, mode);
    }

    private static List<KlumGdslArtifactInput> validateArchiveCounts(List<KlumGdslArtifactInput> selected, List<String> expectedOrigins, String mode) {
        Set<String> expected = new TreeSet<>(expectedOrigins);
        List<String> selectedOrigins = selected.stream().map(KlumGdslArtifactInput::getCoordinates).toList();
        if (!expected.containsAll(selectedOrigins))
            throw new GradleException("GDSL " + mode + " selection changed requested Schema GAV: " + expected + " -> " + selectedOrigins);
        for (String origin : expected) {
            long count = selected.stream().filter(artifact -> artifact.getCoordinates().equals(origin)).count();
            if (count != 1)
                throw new GradleException("GDSL " + mode + " requires exactly one metadata archive for " + origin + "; found " + count);
        }
        return new ArrayList<>(selected);
    }

    static List<String> normalCoordinates(Set<? extends DependencyResult> dependencies) {
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
                if (selected.getModuleVersion() != null) result.add(coordinates(selected));
                collectCoordinates(selected.getDependencies(), result, visited);
            }
        }
    }

    private static ResolvedComponentResult selectedSchema(Set<? extends DependencyResult> dependencies, String key, Set<String> visited) {
        for (var dependency : dependencies) {
            if (!(dependency instanceof ResolvedDependencyResult resolved)) continue;
            var selected = resolved.getSelected();
            ComponentSelector requested = resolved.getRequested();
            if (requested instanceof ModuleComponentSelector module && key.equals(module.getGroup() + ":" + module.getModule())) return selected;
            if (requested instanceof ProjectComponentSelector source && key.equals(source.getProjectPath())) return selected;
            if (selected.getModuleVersion() != null && key.equals(selected.getModuleVersion().getGroup() + ":" + selected.getModuleVersion().getName())) return selected;
            if (visited.add(selected.getId().getDisplayName())) {
                var nested = selectedSchema(selected.getDependencies(), key, visited);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static String coordinates(ResolvedComponentResult component) {
        var module = component.getModuleVersion();
        if (module == null) throw new GradleException("Schema has no normal GAV: " + component.getId());
        return module.getGroup() + ":" + module.getName() + ":" + module.getVersion();
    }

    private static String coordinates(ModuleComponentIdentifier module) {
        return module.getGroup() + ":" + module.getModule() + ":" + module.getVersion();
    }
}
