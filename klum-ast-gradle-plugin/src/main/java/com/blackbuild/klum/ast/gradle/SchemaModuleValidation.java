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
import java.util.StringTokenizer;

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
    private static final String TOKEN_DELIMITERS = " \t\r\n{};,";

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
        statements(descriptor).forEach(statement -> addRequirement(modules, words(statement)));
        return modules;
    }

    private static Map<String, Set<String>> openedPackages(String descriptor) {
        Map<String, Set<String>> packages = new LinkedHashMap<>();
        statements(descriptor).forEach(statement -> addOpenedPackage(packages, words(statement)));
        return packages;
    }

    private static void addRequirement(Map<String, Set<String>> modules, List<String> words) {
        int requiresIndex = words.indexOf("requires");
        if (requiresIndex < 0 || requiresIndex + 1 >= words.size())
            return;

        int firstModifierIndex = requiresIndex + 1;
        String firstModifier = words.get(firstModifierIndex);
        int moduleIndex = isModifier(firstModifier) ? firstModifierIndex + 1 : firstModifierIndex;
        String secondModifier = moduleIndex < words.size() ? words.get(moduleIndex) : "";
        if (isModifier(firstModifier) && isModifier(secondModifier))
            moduleIndex++;
        if (moduleIndex >= words.size())
            return;

        Set<String> modifiers = modules.computeIfAbsent(words.get(moduleIndex), ignored -> new LinkedHashSet<>());
        addModifier(modifiers, firstModifier);
        addModifier(modifiers, secondModifier);
    }

    private static void addOpenedPackage(Map<String, Set<String>> packages, List<String> words) {
        int opensIndex = words.indexOf("opens");
        int targetIndex = words.indexOf("to");
        if (opensIndex < 0 || targetIndex <= opensIndex + 1)
            return;

        Set<String> targets = packages.computeIfAbsent(words.get(opensIndex + 1), ignored -> new LinkedHashSet<>());
        for (int index = targetIndex + 1; index < words.size(); index++)
            targets.add(words.get(index));
    }

    private static boolean isModifier(String word) {
        return "static".equals(word) || "transitive".equals(word);
    }

    private static void addModifier(Set<String> modifiers, String word) {
        if (isModifier(word))
            modifiers.add(word);
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
            if (source != null)
                schemaPackage(withoutComments(source)).ifPresent(packages::add);
        }
        return packages;
    }

    private static Optional<String> schemaPackage(String source) {
        List<String> words = words(source);
        int packageIndex = words.indexOf("package");
        if (packageIndex < 0 || packageIndex + 1 >= words.size())
            return Optional.empty();

        StringBuilder packageName = new StringBuilder(words.get(packageIndex + 1));
        for (int index = packageIndex + 2; index < words.size(); index++) {
            String part = words.get(index);
            if (part.equals(".") || part.startsWith(".") || packageName.charAt(packageName.length() - 1) == '.')
                packageName.append(part);
            else
                break;
        }
        return packageName.charAt(packageName.length() - 1) == '.'
                ? Optional.empty()
                : Optional.of(packageName.toString());
    }

    private static List<String> statements(String source) {
        List<String> statements = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(source, ";");
        while (tokenizer.hasMoreTokens())
            statements.add(tokenizer.nextToken());
        return statements;
    }

    private static List<String> words(String source) {
        List<String> words = new ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(source, TOKEN_DELIMITERS);
        while (tokenizer.hasMoreTokens())
            words.add(tokenizer.nextToken());
        return words;
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
        CommentCursor cursor = new CommentCursor(source);
        while (cursor.hasRemaining()) {
            if (cursor.startsLineComment())
                cursor.skipLineComment();
            else if (cursor.startsBlockComment())
                cursor.skipBlockComment(result);
            else
                cursor.copyCurrent(result);
        }
        return result.toString();
    }

    private static final class CommentCursor {

        private final String source;
        private int position;

        private CommentCursor(String source) {
            this.source = source;
        }

        private boolean hasRemaining() {
            return position < source.length();
        }

        private boolean startsLineComment() {
            return source.startsWith("//", position);
        }

        private boolean startsBlockComment() {
            return source.startsWith("/*", position);
        }

        private void skipLineComment() {
            position = nextLineBreak(position + 2);
        }

        private void skipBlockComment(StringBuilder result) {
            int commentEnd = source.indexOf("*/", position + 2);
            int contentEnd = commentEnd < 0 ? source.length() : commentEnd;
            appendLineBreaks(result, position + 2, contentEnd);
            position = commentEnd < 0 ? source.length() : commentEnd + 2;
        }

        private void copyCurrent(StringBuilder result) {
            result.append(source.charAt(position));
            position++;
        }

        private int nextLineBreak(int start) {
            int carriageReturn = source.indexOf('\r', start);
            int lineFeed = source.indexOf('\n', start);
            if (carriageReturn < 0)
                return lineFeed < 0 ? source.length() : lineFeed;
            if (lineFeed < 0)
                return carriageReturn;
            return Math.min(carriageReturn, lineFeed);
        }

        private void appendLineBreaks(StringBuilder result, int start, int end) {
            for (int index = start; index < end; index++) {
                char character = source.charAt(index);
                if (character == '\r' || character == '\n')
                    result.append(character);
            }
        }
    }

    record Input(
            String projectPath,
            int groovyMajorVersion,
            String descriptorSource,
            Collection<String> schemaSourceTexts,
            Collection<String> configuredAdapterModules) {
    }
}
