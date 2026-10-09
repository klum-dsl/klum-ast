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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Checks the complete project-wide catalog before the sole root writer runs. */
final class KlumGdslProjectValidation {
    private KlumGdslProjectValidation() {}

    static void validate(List<KlumGdslArchive> archives, List<String> normalOrigins) {
        Map<String, KlumGdslArchive> origins = new LinkedHashMap<>();
        List<Rule> rules = new ArrayList<>();
        for (var archive : archives) {
            String[] gav = archive.coordinates().split(":", -1);
            String module = gav[0] + ":" + gav[1] + ":";
            var versions = new TreeSet<String>();
            normalOrigins.stream().filter(origin -> origin.startsWith(module)).forEach(versions::add);
            if (versions.size() > 1) throw invalid("Conflicting project-wide Schema versions: " + versions);
            var previous = origins.putIfAbsent(archive.coordinates(), archive);
            if (previous != null) {
                String reason = previous.mappings().equals(archive.mappings()) ? "Duplicate GDSL payload identity" : "Different GDSL payloads for one Schema GAV";
                throw invalid(reason + " " + archive.coordinates() + ": " + previous.file() + " and " + archive.file());
            }
            for (var mapping : archive.mappings()) {
                Rule current = new Rule(archive, mapping);
                for (var earlier : rules) {
                    String left = earlier.mapping().suffix();
                    String right = mapping.suffix();
                    if (left.endsWith(right) || right.endsWith(left)) {
                        String witness = left.length() >= right.length() ? left : right;
                        throw invalid("Overlapping project-wide GDSL mappings: " + earlier + " and " + current
                                + "; witness filename: recipe" + witness);
                    }
                }
                rules.add(current);
            }
        }
    }

    private record Rule(KlumGdslArchive archive, KlumGdslMetadataFormat.Mapping mapping) {
        @Override public String toString() {
            return archive.coordinates() + " / " + mapping.id() + " (" + mapping.suffix() + ") in " + archive.file();
        }
    }

    private static GradleException invalid(String reason) {
        return new GradleException(reason + "; previous IDE output is stale");
    }
}
