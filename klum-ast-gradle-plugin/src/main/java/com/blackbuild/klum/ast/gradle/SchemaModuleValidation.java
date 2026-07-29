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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pure policy for validating a user-owned Schema module descriptor.
 */
final class SchemaModuleValidation {

    private static final String COMPILER_MODULE = "com.blackbuild.klum.ast.compiler";
    private static final List<String> CORE_MODULES = List.of(
            "com.blackbuild.klum.ast.annotations",
            "com.blackbuild.klum.ast.runtime",
            COMPILER_MODULE,
            "org.apache.groovy");
    private static final Map<String, String> ADAPTER_OPEN_TARGETS = Map.of(
            "com.blackbuild.klum.ast.jackson", "com.fasterxml.jackson.databind",
            "com.blackbuild.klum.ast.validation.bean", "org.hibernate.validator");
    private static final Pattern REQUIRES = Pattern.compile(
            "\\brequires\\s+((?:(?:static|transitive)\\s+)*)((?:[A-Za-z_$][\\w$]*\\.)*[A-Za-z_$][\\w$]*)\\s*;");
    private static final Pattern OPENS = Pattern.compile(
            "\\bopens\\s+((?:[A-Za-z_$][\\w$]*\\.)*[A-Za-z_$][\\w$]*)\\s+to\\s+([^;]+);");
    private static final Pattern PACKAGE = Pattern.compile(
            "(?m)^\\s*package\\s+([A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*)*)\\s*;?");

    private SchemaModuleValidation() {
        // Utility class
    }

    static Optional<String> diagnostic(Input input) {
        if (input == null || blank(input.descriptorSource()))
            return Optional.empty();

        if (input.groovyMajorVersion() == 3)
            return Optional.of("""
                    KlumAST schema module '%s' cannot use module-info.java with Groovy 3.
                    Groovy 3 is a supported classpath-only configuration. Remove module-info.java and compile/run this Schema on the ordinary classpath, or use Groovy 4 or 5 for a named module.
                    The Schema plugin will not rewrite module-info.java or add JVM module-path workaround flags.
                    """.formatted(projectPath(input)).trim());

        String descriptor = withoutComments(input.descriptorSource());
        Map<String, Set<String>> requiredModules = requiredModules(descriptor);
        Set<String> adapterModules = adapterModules(input.configuredAdapterModules(), requiredModules.keySet());
        List<String> problems = missingRequirements(requiredModules, adapterModules);
        Set<String> openTargets = openTargets(adapterModules);
        Map<String, Set<String>> openedPackages = openedPackages(descriptor);
        schemaPackages(input.schemaSourceTexts()).forEach(schemaPackage ->
                openTargets.stream()
                        .filter(target -> !openedPackages.getOrDefault(schemaPackage, Set.of()).contains(target))
                        .forEach(target -> problems.add("missing `opens " + schemaPackage + " to " + target + ";`")));

        if (problems.isEmpty())
            return Optional.empty();
        return Optional.of(remediation(input, problems, adapterModules, openTargets));
    }

    private static List<String> missingRequirements(Map<String, Set<String>> requiredModules, Set<String> adapterModules) {
        List<String> problems = new ArrayList<>();
        for (String module : CORE_MODULES) {
            if (!isRequired(requiredModules, module))
                problems.add("missing `" + requiresDirective(module) + "`");
        }
        for (String module : adapterModules) {
            if (!isRequired(requiredModules, module))
                problems.add("missing `" + requiresDirective(module) + "`");
        }
        return problems;
    }

    private static boolean isRequired(Map<String, Set<String>> requiredModules, String module) {
        Set<String> modifiers = requiredModules.get(module);
        return modifiers != null && (!COMPILER_MODULE.equals(module) || modifiers.contains("static"));
    }

    private static Map<String, Set<String>> requiredModules(String descriptor) {
        Map<String, Set<String>> modules = new LinkedHashMap<>();
        Matcher matcher = REQUIRES.matcher(descriptor);
        while (matcher.find()) {
            Set<String> modifiers = modules.computeIfAbsent(matcher.group(2), ignored -> new LinkedHashSet<>());
            for (String modifier : matcher.group(1).trim().split("\\s+")) {
                if (!modifier.isEmpty())
                    modifiers.add(modifier);
            }
        }
        return modules;
    }

    private static Map<String, Set<String>> openedPackages(String descriptor) {
        Map<String, Set<String>> packages = new LinkedHashMap<>();
        Matcher matcher = OPENS.matcher(descriptor);
        while (matcher.find()) {
            Set<String> targets = packages.computeIfAbsent(matcher.group(1), ignored -> new LinkedHashSet<>());
            for (String target : matcher.group(2).split(",")) {
                String trimmed = target.trim();
                if (!trimmed.isEmpty())
                    targets.add(trimmed);
            }
        }
        return packages;
    }

    private static Set<String> adapterModules(Collection<String> configuredModules, Set<String> declaredModules) {
        Set<String> modules = new LinkedHashSet<>();
        addKnownAdapters(modules, configuredModules);
        addKnownAdapters(modules, declaredModules);
        return modules;
    }

    private static void addKnownAdapters(Set<String> target, Collection<String> candidates) {
        if (candidates == null)
            return;
        for (String candidate : candidates) {
            if (candidate != null && ADAPTER_OPEN_TARGETS.containsKey(candidate))
                target.add(candidate);
        }
    }

    private static Set<String> openTargets(Set<String> adapterModules) {
        Set<String> targets = new LinkedHashSet<>();
        targets.add("com.blackbuild.klum.ast.runtime");
        adapterModules.stream().map(ADAPTER_OPEN_TARGETS::get).forEach(targets::add);
        return targets;
    }

    private static Set<String> schemaPackages(Collection<String> sources) {
        Set<String> packages = new LinkedHashSet<>();
        if (sources == null)
            return packages;
        for (String source : sources) {
            if (source == null)
                continue;
            Matcher matcher = PACKAGE.matcher(withoutComments(source));
            if (matcher.find())
                packages.add(matcher.group(1).replaceAll("\\s+", ""));
        }
        return packages;
    }

    private static String remediation(Input input, List<String> problems, Set<String> adapterModules, Set<String> openTargets) {
        StringBuilder directives = new StringBuilder();
        CORE_MODULES.forEach(module -> directives.append("\n    ").append(requiresDirective(module)));
        adapterModules.forEach(module -> directives.append("\n    ").append(requiresDirective(module)));
        schemaPackages(input.schemaSourceTexts()).forEach(schemaPackage -> directives.append("\n    opens ")
                .append(schemaPackage).append(" to ").append(String.join(", ", openTargets)).append(";"));
        return "KlumAST schema module validation failed for '" + projectPath(input) + "':\n - "
                + String.join("\n - ", problems)
                + "\n\nmodule-info.java remains user-owned; the Schema plugin will not edit it or add JVM workaround flags. "
                + "For Groovy 4/5, add the applicable directives:" + directives;
    }

    private static String requiresDirective(String module) {
        return "requires " + (COMPILER_MODULE.equals(module) ? "static " : "") + module + ";";
    }

    private static String projectPath(Input input) {
        return blank(input.projectPath()) ? ":" : input.projectPath();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String withoutComments(String source) {
        StringBuilder result = new StringBuilder();
        boolean lineComment = false;
        boolean blockComment = false;
        for (int index = 0; index < source.length(); index++) {
            char current = source.charAt(index);
            char next = index + 1 < source.length() ? source.charAt(index + 1) : '\0';
            if (lineComment) {
                if (current == '\n' || current == '\r') {
                    lineComment = false;
                    result.append(current);
                }
                continue;
            }
            if (blockComment) {
                if (current == '*' && next == '/') {
                    blockComment = false;
                    index++;
                } else if (current == '\n' || current == '\r') {
                    result.append(current);
                }
                continue;
            }
            if (current == '/' && next == '/') {
                lineComment = true;
                index++;
            } else if (current == '/' && next == '*') {
                blockComment = true;
                index++;
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    record Input(
            String projectPath,
            int groovyMajorVersion,
            String descriptorSource,
            Collection<String> schemaSourceTexts,
            Collection<String> configuredAdapterModules) {
    }
}
