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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Fixed v1 wire format. Inputs are data; the only executable payload is this generator template. */
final class KlumGdslMetadataFormat {
    static final String ENVELOPE = "META-INF/klum-ide/gdsl/v1/";

    private KlumGdslMetadataFormat() {}

    record Mapping(String id, String suffix, String modelType) {}

    static List<Mapping> validate(List<Map<String, String>> declarations, String coordinates) {
        Set<String> ids = new HashSet<>();
        List<Mapping> result = declarations.stream()
                .map(declaration -> validateMapping(declaration, ids, coordinates))
                .sorted(Comparator.comparing(Mapping::id))
                .toList();
        rejectOverlaps(result, coordinates);
        return result;
    }

    private static Mapping validateMapping(Map<String, String> declaration, Set<String> ids, String coordinates) {
        String id = declaration.get("id");
        if (id == null || !id.matches("[a-z][a-z0-9-]*") || !ids.add(id))
            throw invalid(coordinates, id, "mapping ID must be unique and match [a-z][a-z0-9-]*");
        String suffix = normalizeSuffix(declaration.get("fileNameSuffix"), coordinates, id);
        String model = declaration.get("modelType");
        if (!qualifiedName(model) || model.endsWith("_DSL.Builder"))
            throw invalid(coordinates, id, "modelType must be a Model qualified name, not a Builder type");
        return new Mapping(id, suffix, model);
    }

    private static String normalizeSuffix(String suffix, String coordinates, String id) {
        if (suffix == null || !suffix.endsWith(".groovy")
                || suffix.codePoints().anyMatch(KlumGdslMetadataFormat::invalidSuffixCharacter))
            throw invalid(coordinates, id, "fileNameSuffix must be a literal filename suffix ending in .groovy without separators or controls");
        return Normalizer.normalize(suffix, Normalizer.Form.NFC);
    }

    private static boolean invalidSuffixCharacter(int character) {
        return character == '/' || character == '\\' || Character.isISOControl(character)
                || (character >= 0xD800 && character <= 0xDFFF);
    }

    private static void rejectOverlaps(List<Mapping> result, String coordinates) {
        for (int i = 0; i < result.size(); i++) {
            Mapping left = result.get(i);
            for (int j = i + 1; j < result.size(); j++) {
                Mapping right = result.get(j);
                if (left.suffix().endsWith(right.suffix()) || right.suffix().endsWith(left.suffix())) {
                    String witness = left.suffix().length() >= right.suffix().length() ? left.suffix() : right.suffix();
                    throw new GradleException("Overlapping GDSL mappings in " + coordinates + ": " + left.id() + " ("
                            + left.suffix() + ") and " + right.id() + " (" + right.suffix()
                            + "); witness filename: recipe" + witness);
                }
            }
        }
    }

    private static boolean qualifiedName(String value) {
        if (value == null || value.isEmpty()) return false;
        for (String segment : value.split("\\.", -1)) {
            int[] points = segment.codePoints().toArray();
            if (points.length == 0 || !Character.isJavaIdentifierStart(points[0])) return false;
            for (int point : points)
                if (!Character.isJavaIdentifierPart(point) || Character.isIdentifierIgnorable(point)) return false;
            // Java/Groovy keywords cannot identify a Model class or package.
            if (KEYWORDS.contains(segment)) return false;
        }
        return true;
    }

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "package", "private", "protected", "public", "return", "short", "static", "strictfp",
            "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try", "void",
            "volatile", "while", "true", "false", "null", "def", "in", "as", "trait", "_"
    );

    static String payload(Mapping mapping) {
        return "contributor(context(scope: scriptScope())) {\n"
                + "    if (place.containingFile.name.endsWith(" + groovyString(mapping.suffix()) + ")) {\n"
                + "        def model = findClass(" + groovyString(mapping.modelType()) + ")\n"
                + "        if (!model?.modifierList?.findAnnotation('com.blackbuild.klum.ast.DSL')) return\n"
                + "        def builder = findClass(model.qualifiedName + '_DSL.Builder')\n"
                + "        if (builder != null) delegatesTo(builder)\n"
                + "    }\n"
                + "}\n";
    }

    private static String groovyString(String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    static String catalog(List<Mapping> mappings) {
        return mappings.stream().map(KlumGdslMetadataFormat::catalogEntry)
                .collect(Collectors.joining(",", "{\"mappings\":[", "]}\n"));
    }

    private static String catalogEntry(Mapping mapping) {
        return "{\"id\":" + jsonString(mapping.id()) + ",\"fileNameSuffix\":" + jsonString(mapping.suffix())
                + ",\"modelType\":" + jsonString(mapping.modelType()) + ",\"payloadSha256\":"
                + jsonString(sha256(payload(mapping))) + "}";
    }

    private static String jsonString(String value) {
        StringBuilder result = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            if (c == '\\' || c == '"') result.append('\\');
            result.append(c);
        }
        return result.append('"').toString();
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", e);
        }
    }

    private static GradleException invalid(String coordinates, String id, String message) {
        return new GradleException("Invalid GDSL mapping " + coordinates + " / " + id + ": " + message);
    }
}
