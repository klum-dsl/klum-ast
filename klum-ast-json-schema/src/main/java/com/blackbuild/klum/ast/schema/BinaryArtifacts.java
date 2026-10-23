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
package com.blackbuild.klum.ast.schema;

import org.objectweb.asm.ClassReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarFile;

/** Resolves only requested identities; artifacts are data, never a classloader search path. */
final class BinaryArtifacts implements AutoCloseable {
    private final List<Path> directories = new ArrayList<>();
    private final List<JarFile> archives = new ArrayList<>();
    private final Map<String, Binary> resolved = new HashMap<>();

    BinaryArtifacts(List<Path> paths) throws IOException {
        try {
            for (Path path : paths) {
                if (Files.isDirectory(path)) directories.add(path.toRealPath());
                else archives.add(new JarFile(path.toFile()));
            }
        } catch (IOException | RuntimeException failure) {
            try { close(); } catch (IOException closeFailure) { failure.addSuppressed(closeFailure); }
            throw failure;
        }
    }

    Binary read(String name) throws IOException {
        if (resolved.containsKey(name)) return resolved.get(name);
        if (name.isEmpty() || name.startsWith(".") || name.endsWith(".") || name.contains("..")
                || name.chars().anyMatch(c -> c == '/' || c == '\\' || c == '[' || c == ';' || c < 32))
            throw new IllegalArgumentException("Invalid binary identity " + name);
        String resource = name.replace('.', '/') + ".class";
        Binary found = null;
        for (Path directory : directories) {
            Path file = directory.resolve(resource);
            if (Files.isRegularFile(file)) found = merge(name, found, new Binary(Files.readAllBytes(file), directory.toString()));
        }
        for (JarFile archive : archives) {
            // Inspect every matching entry: duplicate ZIP members cannot silently choose a winner.
            var entries = archive.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().equals(resource)) {
                    try (var stream = archive.getInputStream(entry)) {
                        found = merge(name, found, new Binary(stream.readAllBytes(), archive.getName()));
                    }
                }
            }
        }
        if (found == null) throw new IllegalArgumentException("Missing binary " + name);
        if (!new ClassReader(found.bytes()).getClassName().replace('/', '.').equals(name))
            throw new IllegalArgumentException("Classfile identity mismatch for " + name + " in " + found.origin());
        resolved.put(name, found);
        return found;
    }

    private static Binary merge(String name, Binary previous, Binary candidate) {
        if (previous != null && !Arrays.equals(previous.bytes(), candidate.bytes()))
            throw new IllegalArgumentException("Conflicting binary " + name + " in " + previous.origin() + " and " + candidate.origin());
        return previous == null ? candidate : previous;
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (JarFile archive : archives) {
            try { archive.close(); }
            catch (IOException exception) { if (failure == null) failure = exception; else failure.addSuppressed(exception); }
        }
        if (failure != null) throw failure;
    }

    record Binary(byte[] bytes, String origin) { }
}
