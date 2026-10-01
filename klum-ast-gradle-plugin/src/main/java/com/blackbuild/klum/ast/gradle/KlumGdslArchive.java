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

import groovy.json.JsonSlurper;
import groovy.json.JsonException;
import org.gradle.api.GradleException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.HexFormat;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

/** Validates the complete envelope before the root writer can change any output. Never executes archive content. */
record KlumGdslArchive(File file, String coordinates, List<KlumGdslMetadataFormat.Mapping> mappings) {
    static KlumGdslArchive read(File file, String expectedOrigin, List<String> normalOrigins) {
        try (ZipFile zip = new ZipFile(file)) {
            Set<String> names = validateZipNames(zip, file);
            String origin = validateManifest(zip, file, expectedOrigin, normalOrigins);
            List<KlumGdslMetadataFormat.Mapping> mappings = readCatalog(zip, file, origin);
            validatePayloads(zip, file, origin, names, mappings);
            return new KlumGdslArchive(file, origin, mappings);
        } catch (IOException | IllegalArgumentException | JsonException exception) {
            throw new GradleException("Invalid GDSL metadata " + file + "; previous IDE output is stale", exception);
        }
    }

    private static Set<String> validateZipNames(ZipFile zip, File file) {
        Set<String> names = new HashSet<>();
        var entries = zip.entries();
        while (entries.hasMoreElements()) {
            String name = entries.nextElement().getName();
            if (!names.add(name) || name.startsWith("/") || name.contains("\\") || name.contains("../"))
                throw invalid(file, "duplicate or unsafe ZIP entry " + name);
        }
        return names;
    }

    private static String validateManifest(ZipFile zip, File file, String expectedOrigin, List<String> normalOrigins) throws IOException {
        var entry = zip.getEntry("META-INF/MANIFEST.MF");
        if (entry == null) throw invalid(file, "missing metadata manifest");
        Manifest manifest;
        try (var stream = zip.getInputStream(entry)) { manifest = new Manifest(stream); }
        String origin = manifest.getMainAttributes().getValue("Klum-Schema-Coordinates");
        if (!expectedOrigin.equals(origin))
            throw invalid(file, "Schema origin mismatch: selected " + expectedOrigin + ", manifest " + origin);
        if (!normalOrigins.contains(origin))
            throw invalid(file, "metadata " + origin + " does not match the selected normal Schema: " + normalOrigins);
        if (!"1".equals(manifest.getMainAttributes().getValue("Klum-Gdsl-Format")))
            throw invalid(file, "unsupported GDSL format; upgrade the KlumAST plugin and Schema together");
        return origin;
    }

    private static List<KlumGdslMetadataFormat.Mapping> readCatalog(ZipFile zip, File file, String origin) throws IOException {
        String catalog = text(zip, KlumGdslMetadataFormat.ENVELOPE + "mappings.json");
        Object parsed = new JsonSlurper().parseText(catalog);
        if (!(parsed instanceof Map<?, ?> object) || !(object.get("mappings") instanceof List<?> declarations))
            throw invalid(file, "missing mappings catalog");
        List<Map<String, String>> data = new ArrayList<>();
        for (Object value : declarations) {
            if (!(value instanceof Map<?, ?> mapping)) throw invalid(file, "invalid mapping entry");
            Map<String, String> declaration = new LinkedHashMap<>();
            for (String key : List.of("id", "fileNameSuffix", "modelType")) {
                if (!(mapping.get(key) instanceof String field)) throw invalid(file, "missing string field " + key);
                declaration.put(key, field);
            }
            data.add(declaration);
        }
        List<KlumGdslMetadataFormat.Mapping> mappings;
        try {
            mappings = KlumGdslMetadataFormat.validate(data, origin);
        } catch (GradleException exception) {
            throw invalid(file, exception.getMessage());
        }
        if (!catalog.equals(KlumGdslMetadataFormat.catalog(mappings)))
            throw invalid(file, "noncanonical catalog or payload hash/template mismatch");
        return mappings;
    }

    private static void validatePayloads(ZipFile zip, File file, String origin, Set<String> names,
                                         List<KlumGdslMetadataFormat.Mapping> mappings) throws IOException {
        Set<String> allowed = new HashSet<>(List.of("META-INF/MANIFEST.MF", KlumGdslMetadataFormat.ENVELOPE + "mappings.json"));
        for (var mapping : mappings) {
            String name = KlumGdslMetadataFormat.ENVELOPE + mapping.id() + ".gdsl";
            allowed.add(name);
            if (!text(zip, name).equals(KlumGdslMetadataFormat.payload(mapping)))
                throw invalid(file, "payload hash/template mismatch for " + origin + " / " + mapping.id());
        }
        for (String name : names)
            if (!zip.getEntry(name).isDirectory() && !allowed.contains(name))
                throw invalid(file, "unexpected ZIP entry " + name);
    }

    String destination() {
        String[] gav = coordinates.split(":", -1);
        return "schema-owned/" + hex(gav[0]) + "/" + hex(gav[1]) + "/";
    }

    private static String hex(String value) {
        return HexFormat.of().formatHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String text(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        if (entry == null) throw new GradleException("Missing GDSL entry " + name + " in " + zip.getName() + "; previous IDE output is stale");
        try (var stream = zip.getInputStream(entry)) { return new String(stream.readAllBytes(), StandardCharsets.UTF_8); }
    }

    private static GradleException invalid(File file, String reason) {
        return new GradleException("Invalid GDSL metadata " + file + ": " + reason + "; previous IDE output is stale");
    }
}
