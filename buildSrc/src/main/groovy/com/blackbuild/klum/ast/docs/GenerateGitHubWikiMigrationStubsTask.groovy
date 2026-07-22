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
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.blackbuild.klum.ast.docs

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Produces the minimal content that may later be placed in the GitHub wiki.
 * It deliberately does not use a publisher or claim to configure HTTP redirects.
 */
abstract class GenerateGitHubWikiMigrationStubsTask extends DefaultTask {

    static final String LEGACY_WIKI_COMMIT = 'c033e9b668ba53cd0a86859bc773fffc99863c09'
    static final String CANONICAL_BASE = 'https://klum-dsl.github.io/klum-ast'

    @InputDirectory abstract DirectoryProperty getCanonicalSourceDirectory()
    @Internal abstract DirectoryProperty getObjectDirectory()
    @OutputDirectory abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void generateStubs() {
        generate(canonicalSourceDirectory.get().asFile, objectDirectory.get().asFile, outputDirectory.get().asFile)
    }

    /**
     * Generates stubs from explicit, checked-out inputs so the credential-free
     * tracer can exercise the same migration contract without a publishing task.
     */
    static void generate(File canonicalSource, File objectDirectory, File output) {
        if (output.exists() && output.listFiles()?.length)
            fail("Wiki migration-stub output must be empty: $output")
        output.mkdirs()

        String resolved = git(objectDirectory, ['rev-parse', '--verify', "${LEGACY_WIKI_COMMIT}^{commit}"]).trim()
        if (resolved != LEGACY_WIKI_COMMIT)
            fail("Legacy mutable-wiki inventory must use $LEGACY_WIKI_COMMIT, not $resolved")
        Set<String> canonicalPages = canonicalSource.listFiles()?.findAll { it.file && it.name.endsWith('.md') }*.name as Set ?: [] as Set
        List<String> legacyPages = git(objectDirectory, ['ls-tree', '-r', '--name-only', LEGACY_WIKI_COMMIT, '--', 'wiki'])
                .readLines().findAll { it.endsWith('.md') && !it.endsWith('/_Sidebar.md') && !it.endsWith('/_Footer.md') }
                .collect { it.substring('wiki/'.length()) }.sort()
        legacyPages << 'Changelog.md'

        Map<String, Object> entries = new TreeMap<>()
        legacyPages.unique().sort().each { String page ->
            String destination = GenerateGitHubWikiMigrationStubsTask.destinationFor(page, canonicalPages)
            File stub = new File(output, page)
            GenerateGitHubWikiMigrationStubsTask.write(stub, GenerateGitHubWikiMigrationStubsTask.stubContent(page, destination))
            entries[page] = [
                    kind       : page == 'Home.md' ? 'landing-stub' : 'deep-link-stub',
                    destination: destination,
                    sha256     : GenerateGitHubWikiMigrationStubsTask.sha256(stub.bytes)
            ]
        }
        Map<String, Object> inventory = [
                schemaVersion : 1,
                legacySource  : [commit: LEGACY_WIKI_COMMIT, root: 'wiki'],
                canonicalBase : CANONICAL_BASE,
                excluded      : ['_Sidebar.md', '_Footer.md', 'img/ (binary asset paths; not GitHub-wiki page slugs)'],
                stubs         : entries
        ]
        write(new File(output, 'migration-stub-inventory.json'), JsonOutput.prettyPrint(JsonOutput.toJson(inventory)) + '\n')
    }

    static String destinationFor(String page, Set<String> canonicalPages) {
        if (page == 'Home.md') return "$CANONICAL_BASE/stable/"
        if (page == 'Changelog.md' || canonicalPages.contains(page)) {
            String pageDirectory = page.substring(0, page.length() - '.md'.length())
            return "$CANONICAL_BASE/stable/$pageDirectory/"
        }
        "$CANONICAL_BASE/archive/"
    }

    static String stubContent(String page, String destination) {
        "<!-- Generated GitHub-wiki migration stub. Do not use as authoring content. -->\n" +
                "# KlumAST documentation migration\n\n" +
                '> **Migration content.** This GitHub wiki page is retained only for legacy landing and deep links. KlumAST does not author current 4.x documentation in the wiki.\n\n' +
                "The canonical destination for **$page** is [$destination]($destination).\n\n" +
                'This stub does not configure or claim an HTTP redirect.\n'
    }

    private static void write(File target, String content) {
        target.parentFile.mkdirs()
        Files.write(target.toPath(), content.getBytes(StandardCharsets.UTF_8))
    }

    private static String git(File directory, List<String> arguments) {
        Process process = new ProcessBuilder((['git'] + arguments).collect { it.toString() }).directory(directory).redirectErrorStream(true).start()
        String output = process.inputStream.getText(StandardCharsets.UTF_8.name())
        if (process.waitFor() != 0)
            fail("Git inventory failed (${arguments.join(' ')}): ${output.trim()}")
        output
    }

    private static String sha256(byte[] content) {
        MessageDigest.getInstance('SHA-256').digest(content).encodeHex().toString()
    }

    private static void fail(String message) {
        throw new GradleException(message)
    }
}
