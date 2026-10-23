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

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;

/** Optional command-line tool. Schema artifacts are supplied as data, never loaded as Java classes. */
public final class JsonSchemaGenerator {
    private JsonSchemaGenerator() { }

    /**
     * Writes one self-contained Draft 2020-12 document.
     * @param args concrete Model binary name, output file, then explicit compiled directories/JARs
     * @throws IOException when artifacts or output cannot be read/written
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 3) throw new IllegalArgumentException(
                "Usage: klum-ast-json-schema <Model-binary-name> <output.json> <compiled-directory-or-jar>...");
        String root = args[0];
        Path output = Path.of(args[1]).toAbsolutePath();
        var paths = Arrays.stream(args).skip(2).map(Path::of).toList();
        try (BinaryArtifacts artifacts = new BinaryArtifacts(paths)) {
            SchemaProjection projection = new SchemaProjection(artifacts);
            String document = projection.generate(root);
            Files.createDirectories(output.getParent());
            Path temporary = Files.createTempFile(output.getParent(), ".klum-schema-", ".tmp");
            try {
                Files.writeString(temporary, document);
                try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temporary); }
            projection.diagnostics().stream().sorted().forEach(System.err::println);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IOException("Cannot generate JSON Schema for " + root + " from " + paths
                    + "; recompile and package the Schema with its current WIRE-PLAN/B carrier and public generated contracts: "
                    + failure.getMessage(), failure);
        }
    }
}
