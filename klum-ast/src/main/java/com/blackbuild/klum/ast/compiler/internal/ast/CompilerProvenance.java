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
package com.blackbuild.klum.ast.compiler.internal.ast;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Reads only build-owned metadata colocated with the executing compiler, never a classpath lookup. */
final class CompilerProvenance {
    private static final String RESOURCE = "META-INF/klum-ast/compiler.properties";
    private static final String VERSION = "compilerVersion";

    private CompilerProvenance() {}

    static String version() {
        try {
            Path artifact = Path.of(CompilerProvenance.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isDirectory(artifact)) {
                try (InputStream input = Files.newInputStream(artifact.resolve(RESOURCE))) {
                    return readVersion(input);
                }
            }
            try (JarFile jar = new JarFile(artifact.toFile())) {
                JarEntry entry = jar.getJarEntry(RESOURCE);
                if (entry == null) throw new IOException("Missing " + RESOURCE);
                try (InputStream input = jar.getInputStream(entry)) {
                    return readVersion(input);
                }
            }
        } catch (IOException | URISyntaxException | RuntimeException e) {
            throw new IllegalStateException("Cannot establish KlumAST compiler provenance from the emitting compiler artifact: "
                    + e.getMessage(), e);
        }
    }

    private static String readVersion(InputStream input) throws IOException {
        Properties properties = new Properties() {
            @Override
            public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate compiler provenance property: " + key);
                return super.put(key, value);
            }
        };
        properties.load(input);
        String version = properties.getProperty(VERSION);
        if (properties.size() != 1 || version == null || version.isBlank())
            throw new IOException("Expected one non-blank " + VERSION + " in " + RESOURCE);
        return version;
    }
}
