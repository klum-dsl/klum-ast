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

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Generates only the bounded v1 envelope, with immutable declarations as task inputs. */
@CacheableTask
public abstract class GenerateKlumGdslMetadata extends DefaultTask {
    @Input
    public abstract MapProperty<String, String> getFileNameSuffixes();

    @Input
    public abstract MapProperty<String, String> getModelTypes();

    @Input
    public abstract Property<String> getSchemaCoordinates();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @TaskAction
    public void generate() throws IOException {
        List<Map<String, String>> declarations = getFileNameSuffixes().get().entrySet().stream()
                .map(entry -> Map.of("id", entry.getKey(), "fileNameSuffix", entry.getValue(),
                        "modelType", getModelTypes().get().getOrDefault(entry.getKey(), ""))).toList();
        List<KlumGdslMetadataFormat.Mapping> mappings = KlumGdslMetadataFormat.validate(
                declarations, getSchemaCoordinates().get());
        Path output = getOutputDirectory().get().getAsFile().toPath();
        if (Files.exists(output)) {
            try (Stream<Path> paths = Files.walk(output)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        Path envelope = output.resolve(KlumGdslMetadataFormat.ENVELOPE);
        Files.createDirectories(envelope);
        Files.writeString(envelope.resolve("mappings.json"), KlumGdslMetadataFormat.catalog(mappings), StandardCharsets.UTF_8);
        for (KlumGdslMetadataFormat.Mapping mapping : mappings)
            Files.writeString(envelope.resolve(mapping.id() + ".gdsl"), KlumGdslMetadataFormat.payload(mapping), StandardCharsets.UTF_8);
    }
}
