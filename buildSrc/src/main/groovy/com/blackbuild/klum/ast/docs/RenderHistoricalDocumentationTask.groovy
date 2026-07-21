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
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.net.HttpURLConnection
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Reconstructs the released documentation trees without treating {@code wiki/}
 * as a current authoring root. Every tag identity and rendered source digest is
 * audited before the task makes its output available.
 */
abstract class RenderHistoricalDocumentationTask extends DefaultTask {

    private static final Map<String, Map<String, String>> RELEASES = [
            '2.0.0': [tag: 'v2.0.0', commit: 'b310027a735eb88a4096117976cec950b06a786e'],
            '2.1.0': [tag: 'v2.1.0', commit: '4e3cb081fecfd2ca5f19ef5c7d9a050ab16dfd25'],
            '2.1.1': [tag: 'v2.1.1', commit: 'f286d169ff10b38528b4d6f33e6fb861ec717153'],
            '2.1.2': [tag: 'v2.1.2', commit: 'af8b46ae67da4970b5a5da4f8838165ecb533bd0'],
            '2.1.3': [tag: 'v2.1.3', commit: 'be42da91ce2bcba7c7fd8cc01a6d02117e01a462'],
            '2.1.4': [tag: 'v2.1.4', commit: 'd49c14c96c32cee6ada9c5204b420fbf23dc5818'],
            '2.1.5': [tag: 'v2.1.5', commit: 'e8c545c7b73ca36e7fea180774d5822f000ab688'],
            '2.2.0': [tag: 'v2.2.0', commit: '4e881c50467c16fdadb1fdcffe72aa72565e2f19'],
            '3.0.1': [tag: 'v3.0.1', commit: '3aa97428c0420fd3d1ca70b4b5e141360d1ca5b6']
    ].asImmutable()
    private static final List<String> MODULES = [
            'klum-ast', 'klum-ast-runtime', 'klum-ast-annotations', 'klum-ast-jackson',
            'klum-ast-bean-validation', 'klum-ast-gradle-plugin'
    ].asImmutable()

    @Internal abstract DirectoryProperty getObjectDirectory()
    @OutputDirectory abstract DirectoryProperty getOutputDirectory()
    @Input abstract Property<String> getJavadocRepository()

    @TaskAction
    void renderHistoricalDocumentation() {
        File input = objectDirectory.get().asFile
        File output = outputDirectory.get().asFile
        if (output.exists() && output.listFiles()?.length)
            fail("Historical documentation output must be empty: $output")
        output.mkdirs()
        File archive = new File(output, 'archive')
        archive.mkdirs()

        File cleanInput = new File(temporaryDir, 'historical-input')
        cloneRepository(input, cleanInput)
        String rendererRevision = git(cleanInput, ['rev-parse', 'HEAD']).trim()
        Map<String, Object> audit = new TreeMap<>()

        RELEASES.each { String version, Map<String, String> release ->
            RenderHistoricalDocumentationTask.assertTagIdentity(cleanInput, release)
            RenderHistoricalDocumentationTask.checkout(cleanInput, release.commit)
            Map<String, String> sourceHashes = RenderHistoricalDocumentationTask.sourceHashes(cleanInput, release.commit)
            File javadocs = new File(temporaryDir, "javadocs/$version")
            Map<String, Object> api = RenderHistoricalDocumentationTask.importJavadocs(version, javadocs, javadocRepository.get())
            Map<String, File> additionalFiles = (api.files as Map<String, File>) + [
                    'api/index.md': RenderHistoricalDocumentationTask.writeText(new File(javadocs, 'api-index.md'), RenderHistoricalDocumentationTask.apiIndex(version, api.modules as Map<String, Object>))
            ]
            File staged = new File(temporaryDir, "staged/$version")
            if (staged.exists()) staged.deleteDir()
            VersionedDocumentationRenderer.render(
                    objectDirectory      : cleanInput,
                    outputDirectory      : staged,
                    revision             : release.commit,
                    rendererRevision     : rendererRevision,
                    version              : version,
                    status               : 'archived',
                    archiveLink          : '../',
                    javadocInputChecksums: api.checksums,
                    additionalFiles      : additionalFiles,
                    writeRootIndices     : false)

            File exact = new File(staged, version)
            Map<String, String> renderedHashes = RenderHistoricalDocumentationTask.renderedSourceHashes(exact, sourceHashes.keySet(), version)
            if (sourceHashes != renderedHashes)
                RenderHistoricalDocumentationTask.fail("Historical prose or examples changed while rendering $version")
            Files.move(exact.toPath(), new File(archive, version).toPath())
            audit[version] = [
                    tag                : release.tag,
                    revision           : release.commit,
                    wikiTree           : RenderHistoricalDocumentationTask.git(cleanInput, ['rev-parse', "${release.commit}:wiki"]).trim(),
                    sourceFileCount    : sourceHashes.size(),
                    sourceContentDigest: RenderHistoricalDocumentationTask.aggregateDigest(sourceHashes),
                    renderedContentDigest: RenderHistoricalDocumentationTask.aggregateDigest(renderedHashes),
                    api                : api.modules
            ]
        }
        writeText(new File(output, 'archive/index.md'), archiveIndex())
        writeText(new File(output, 'index.md'), '# KlumAST historical documentation\n\nThese immutable archived trees preserve released 2.x and 3.0.1 documentation. Browse [the archive](archive/).\n')
        writeText(new File(output, 'historical-content-audit.json'), JsonOutput.prettyPrint(JsonOutput.toJson([
                schemaVersion: 1,
                source       : 'released Git tags and Maven Central Javadoc artifacts',
                releases     : audit
        ])) + '\n')
    }

    private static Map<String, Object> importJavadocs(String version, File root, String repository) {
        Map<String, File> files = new TreeMap<>()
        Map<String, String> checksums = new TreeMap<>()
        Map<String, Object> modules = new TreeMap<>()
        MODULES.each { String module ->
            String artifact = "$module-$version-javadoc.jar"
            String url = "${repository.replaceAll('/+$', '')}/com/blackbuild/klum/ast/$module/$version/$artifact"
            File jar = new File(root, artifact)
            int status = download(url, jar)
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                modules[module] = [availability: 'unavailable', reason: 'No released Javadoc JAR exists for this module and version.']
                return
            }
            if (status != HttpURLConnection.HTTP_OK)
                fail("Unable to obtain released Javadocs for $module $version (HTTP $status): $url")
            String digest = sha256(jar.bytes)
            checksums[module] = digest
            File moduleRoot = new File(root, module)
            unzip(jar, moduleRoot)
            moduleRoot.eachFileRecurse { File file ->
                if (file.file)
                    files["api/$module/${moduleRoot.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char)}"] = file
            }
            modules[module] = [availability: 'imported', source: url, sha256: digest, output: "$module/"]
        }
        [files: files, checksums: checksums, modules: modules]
    }

    private static int download(String source, File target) {
        target.parentFile.mkdirs()
        Files.deleteIfExists(target.toPath())
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection()
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        int status = connection.responseCode
        if (status == HttpURLConnection.HTTP_OK)
            connection.inputStream.withCloseable { input -> Files.copy(input, target.toPath()) }
        connection.disconnect()
        status
    }

    private static void unzip(File archive, File targetRoot) {
        if (targetRoot.exists()) targetRoot.deleteDir()
        ZipFile zip = new ZipFile(archive)
        try {
            zip.entries().each { entry ->
                if (entry.directory) return
                File target = new File(targetRoot, entry.name)
                if (!target.canonicalPath.startsWith(targetRoot.canonicalPath + File.separator))
                    fail("Javadoc archive contains an unsafe path: ${entry.name}")
                target.parentFile.mkdirs()
                zip.getInputStream(entry).withCloseable { input -> Files.copy(input, target.toPath()) }
            }
        } finally {
            zip.close()
        }
    }

    private static Map<String, String> sourceHashes(File input, String revision) {
        Map<String, String> hashes = new TreeMap<>()
        git(input, ['ls-tree', '-r', '--name-only', revision, '--', 'wiki']).readLines().findAll { it }.each { String sourcePath ->
            String outputPath = sourcePath.substring('wiki/'.length())
            hashes[outputPath] = sha256(gitBytes(input, ['show', "$revision:$sourcePath"]))
        }
        hashes
    }

    private static Map<String, String> renderedSourceHashes(File exact, Set<String> paths, String version) {
        Map<String, String> hashes = new TreeMap<>()
        String archivedChrome = "<!-- Generated by ${VersionedDocumentationRenderer.RENDERER_ID}. Do not edit this rendered copy. -->\n" +
                "> **KlumAST $version — Archived (legacy)**\n" +
                '> **Archived (legacy).** This exact documentation is retained for compatibility. Browse [the archive](../).\n\n'
        paths.each { String path ->
            File rendered = new File(exact, path)
            if (!rendered.file) fail("Historical render omitted source path: $path")
            byte[] bytes = rendered.bytes
            if (path.endsWith('.md')) {
                String renderedText = new String(bytes, StandardCharsets.UTF_8)
                if (!renderedText.startsWith(archivedChrome))
                    fail("Historical render is missing mechanical chrome: $path")
                bytes = renderedText.substring(archivedChrome.length()).getBytes(StandardCharsets.UTF_8)
            }
            hashes[path] = sha256(bytes)
        }
        hashes
    }

    static String apiIndex(String version, Map<String, Object> modules) {
        String lines = modules.collect { String module, Map<String, Object> detail ->
            detail.availability == 'imported'
                    ? "- [$module](${detail.output}) — imported from its released Javadoc JAR."
                    : "- **$module — unavailable.** ${detail.reason}"
        }.join('\n')
        "# KlumAST $version API reference\n\n" +
                'This archived API reference uses only Javadocs released for this exact version. Missing artifacts remain unavailable; no 4.x API tree is substituted.\n\n' +
                "$lines\n"
    }

    private static String archiveIndex() {
        "# Archived KlumAST documentation\n\n" + RELEASES.keySet().collect { "- [$it]($it/) — Archived (legacy)" }.join('\n') + '\n'
    }

    private static String aggregateDigest(Map<String, String> hashes) {
        sha256(hashes.collect { path, hash -> "$path=$hash\n" }.join('').getBytes(StandardCharsets.UTF_8))
    }

    private static File writeText(File file, String text) {
        file.parentFile.mkdirs()
        Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8))
        file
    }

    private static void cloneRepository(File source, File target) {
        if (target.exists()) target.deleteDir()
        run(source.parentFile, ['git', 'clone', '--no-checkout', '--local', source.absolutePath, target.absolutePath])
    }

    private static void assertTagIdentity(File directory, Map<String, String> release) {
        String resolved = git(directory, ['rev-parse', '--verify', "${release.tag}^{commit}"]).trim()
        if (resolved != release.commit)
            fail("Historical tag ${release.tag} must resolve to its released commit ${release.commit}, not $resolved")
    }

    private static void checkout(File directory, String revision) {
        run(directory, ['git', 'checkout', '--detach', '--quiet', revision])
    }

    private static String git(File directory, List<String> arguments) {
        new String(gitBytes(directory, arguments), StandardCharsets.UTF_8)
    }

    private static byte[] gitBytes(File directory, List<String> arguments) {
        List<String> command = (['git'] + arguments).collect { it.toString() }
        Process process = new ProcessBuilder(command).directory(directory).redirectErrorStream(true).start()
        byte[] output = process.inputStream.bytes
        if (process.waitFor() != 0)
            fail("Command failed (${command.join(' ')}): ${new String(output, StandardCharsets.UTF_8).trim()}")
        output
    }

    private static String run(File directory, List<String> command) {
        Process process = new ProcessBuilder(command.collect { it.toString() }).directory(directory).redirectErrorStream(true).start()
        String output = process.inputStream.getText(StandardCharsets.UTF_8.name())
        if (process.waitFor() != 0)
            fail("Command failed (${command.join(' ')}): ${output.trim()}")
        output
    }

    private static String sha256(byte[] content) {
        MessageDigest.getInstance('SHA-256').digest(content).encodeHex().toString()
    }

    private static void fail(String message) {
        throw new GradleException(message)
    }
}
