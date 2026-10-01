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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/** Recognizes exact generated copies and the bounded #809 recipe, never arbitrary executable predicates. */
final class KlumGdslLegacyResources {
    private KlumGdslLegacyResources() {}

    static void validate(List<KlumGdslArchive> archives, Set<File> sources, Set<File> classpath) {
        if (archives.isEmpty()) return;
        try {
            for (File source : sources) check(new String(Files.readAllBytes(source.toPath()), StandardCharsets.UTF_8), source.toString(), archives);
            for (File file : classpath) {
                if (file.isDirectory()) {
                    try (var paths = Files.walk(file.toPath())) {
                        for (var path : paths.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".gdsl")).toList())
                            check(new String(Files.readAllBytes(path), StandardCharsets.UTF_8), path.toString(), archives);
                    }
                } else if (file.getName().endsWith(".gdsl")) {
                    check(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), file.toString(), archives);
                } else {
                    try (ZipFile zip = new ZipFile(file)) {
                        for (var entry : zip.stream().filter(entry -> !entry.isDirectory() && entry.getName().endsWith(".gdsl")).toList()) {
                            try (var stream = zip.getInputStream(entry)) {
                                check(new String(stream.readAllBytes(), StandardCharsets.UTF_8), file + "!/" + entry.getName(), archives);
                            }
                        }
                    }
                }
            }
        } catch (IOException exception) {
            throw new GradleException("Cannot inspect participating normal GDSL resources; previous IDE output is stale", exception);
        }
    }

    private static final Pattern LEGACY = Pattern.compile(
            Pattern.quote("contributor(context(scope:scriptScope())){if(place.containingFile.name.endsWith(")
                    + "'([^'\\\\]*)'" + Pattern.quote("))delegatesTo(findClass(")
                    + "'([^'\\\\]*)_DSL\\.Builder'" + Pattern.quote("))}"));

    private static final Pattern GENERATED = generatedPattern();

    private static Pattern generatedPattern() {
        String template = KlumGdslMetadataFormat.payload(new KlumGdslMetadataFormat.Mapping("legacy", "suffix-marker", "model-marker"));
        String[] parts = template.split("suffix-marker|model-marker", -1);
        return Pattern.compile(Pattern.quote(parts[0]) + "(.*)" + Pattern.quote(parts[1]) + "(.*)" + Pattern.quote(parts[2]));
    }

    private static void check(String content, String locator, List<KlumGdslArchive> archives) {
        var generated = GENERATED.matcher(content);
        if (generated.matches()) {
            var mapping = recognizedMapping(unescape(generated.group(1)), unescape(generated.group(2)), locator);
            if (mapping != null && content.equals(KlumGdslMetadataFormat.payload(mapping)))
                throw legacy(locator, describeOrigins(archives, mapping.suffix(), mapping.modelType()));
        }
        var matcher = LEGACY.matcher(compact(content));
        if (!matcher.matches()) return;
        // Recognition is syntactic and bounded: the whole #809 contributor with valid literal suffix/Model data.
        // Additional executable code or other predicates remain arbitrary user-managed contributors.
        var mapping = recognizedMapping(matcher.group(1), matcher.group(2), locator);
        if (mapping != null) throw legacy(locator, describeOrigins(archives, mapping.suffix(), mapping.modelType()));
    }

    private static KlumGdslMetadataFormat.Mapping recognizedMapping(String suffix, String model, String locator) {
        if (suffix == null || model == null) return null;
        try {
            return KlumGdslMetadataFormat.validate(List.of(Map.of("id", "legacy", "fileNameSuffix", suffix, "modelType", model)), locator).get(0);
        } catch (GradleException ignored) {
            return null;
        }
    }

    private static String describeOrigins(List<KlumGdslArchive> archives, String suffix, String model) {
        return archives.stream().map(archive -> {
            var mapping = archive.mappings().stream().filter(rule -> rule.suffix().equals(suffix)
                    && rule.modelType().equals(model)).findFirst();
            return archive.coordinates() + mapping.map(rule -> " / " + rule.id() + " (" + rule.suffix() + ")").orElse("") + " in " + archive.file();
        }).toList().toString();
    }

    private static String unescape(String value) {
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '\'') return null;
            if (character == '\\') {
                if (++index == value.length()) return null;
                character = value.charAt(index);
                if (character != '\\' && character != '\'') return null;
            }
            result.append(character);
        }
        return result.toString();
    }

    private static GradleException legacy(String locator, String origins) {
        return new GradleException("Recognized legacy GDSL copy " + locator + " conflicts with " + origins
                + ". Move the mapping to klumSchema.gdsl, remove the copied resource, rebuild/publish a new Schema or Model version,"
                + " then refresh/reimport IDEA; previous IDE output is stale");
    }

    // Preserve every character inside quoted data. Only whitespace outside literals is ignored.
    private static String compact(String value) {
        StringBuilder result = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (char character : value.toCharArray()) {
            if (quoted || !Character.isWhitespace(character)) result.append(character);
            if (character == '\'' && !escaped) quoted = !quoted;
            escaped = quoted && character == '\\' && !escaped;
        }
        return result.toString();
    }
}
