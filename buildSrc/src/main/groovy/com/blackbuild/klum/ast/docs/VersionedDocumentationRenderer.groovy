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
package com.blackbuild.klum.ast.docs

import groovy.json.JsonOutput
import groovy.json.JsonSlurper

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Renders one immutable documentation tree from an explicitly named Git commit.
 *
 * The renderer deliberately handles Markdown as content. Its only presentation
 * responsibility is the stable, mechanical status chrome prepended to every page.
 */
class VersionedDocumentationRenderer {

    static final String RENDERER_ID = 'klum-ast-buildsrc-vd-1'
    private static final Set<String> STATUSES = ['current', 'archived', 'public-rc'] as Set
    private static final Set<String> RESERVED_PATHS = ['status.md', 'source-manifest.json'] as Set
    private static final String VERSION_PATTERN = /\d+\.\d+\.\d+(?:-rc\.[1-9]\d*)?/

    static void render(Map<String, ?> inputs) {
        File objectDirectory = requiredDirectory(inputs, 'objectDirectory')
        File outputDirectory = requiredFile(inputs, 'outputDirectory')
        String revision = requiredString(inputs, 'revision')
        String rendererRevision = requiredString(inputs, 'rendererRevision')
        String version = requiredString(inputs, 'version')
        String status = requiredString(inputs, 'status')
        String brandingManifestPath = requiredRelativePath(requiredString(inputs, 'brandingManifestPath'), 'brandingManifestPath')
        Map<String, String> javadocInputChecksums = normalizedChecksums(inputs.javadocInputChecksums ?: [:])
        List<String> archivedVersions = ((inputs.archivedVersions ?: []) as List<String>).collect { it.toString() }.sort()

        requireFullSha(revision, 'revision')
        requireFullSha(rendererRevision, 'rendererRevision')
        if (!(version ==~ VERSION_PATTERN))
            fail("Documentation version must be an exact final or RC version: $version")
        if (!STATUSES.contains(status))
            fail("Documentation status must be one of $STATUSES; aliases and development trees are not rendered by VD-1: $status")
        if (status == 'public-rc' && !(version ==~ /\d+\.\d+\.\d+-rc\.[1-9]\d*/))
            fail("A public-rc documentation tree requires an RC version: $version")
        if (status != 'public-rc' && version.contains('-rc.'))
            fail("An RC version must be rendered with public-rc status: $version")

        if (git(objectDirectory, ['status', '--porcelain']).trim())
            fail('Documentation input worktree is dirty; render a checked-out immutable revision.')
        String resolvedRevision = git(objectDirectory, ['rev-parse', '--verify', "${revision}^{commit}"]).trim()
        if (resolvedRevision != revision)
            fail("Revision must resolve to the supplied full SHA: $revision")
        git(objectDirectory, ['cat-file', '-e', "${revision}^{commit}"])

        String sourceRoot = version.startsWith('4.') ? 'docs/user' : 'wiki'
        List<String> sourcePaths = git(objectDirectory, ['ls-tree', '-r', '--name-only', revision, '--', sourceRoot])
                .readLines().findAll { it }
        if (sourcePaths.isEmpty())
            fail("Revision $revision does not contain the expected $sourceRoot authoring tree for $version")
        String treeHash = git(objectDirectory, ['rev-parse', "${revision}:${sourceRoot}"]).trim()

        if (outputDirectory.exists() && outputDirectory.listFiles()?.length)
            fail("Output directory must be empty so an exact tree cannot be overwritten: $outputDirectory")
        outputDirectory.mkdirs()
        File exactDirectory = new File(outputDirectory, version)

        Set<String> outputPaths = new TreeSet<>()
        sourcePaths.each { String sourcePath ->
            String outputPath = sourcePath.substring(sourceRoot.length() + 1)
            requireRelativePath(outputPath, 'source path')
            if (RESERVED_PATHS.contains(outputPath))
                fail("Source path collides with renderer-owned output: $outputPath")
            if (!outputPaths.add(outputPath))
                fail("Duplicate output path: $outputPath")
            byte[] content = gitBytes(objectDirectory, ['show', "${revision}:${sourcePath}"])
            if (outputPath.endsWith('.md'))
                content = (chrome(version, status) + new String(content, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)
            write(exactDirectory, outputPath, content)
        }

        Map<String, ?> branding = readBrandingManifest(objectDirectory, revision, brandingManifestPath)
        String logoPath = requiredRelativePath(branding.logo?.toString(), 'branding logo')
        String logoTarget = "assets/branding/${logoPath.tokenize('/').last()}"
        if (!outputPaths.add(logoTarget))
            fail("Branding logo collides with an authored output path: $logoTarget")
        byte[] logo = gitBytes(objectDirectory, ['show', "${revision}:${logoPath}"])
        String logoDigest = sha256(logo)
        if (logoDigest != branding.sha256)
            fail("Branding manifest digest does not match $logoPath")
        write(exactDirectory, logoTarget, logo)

        outputPaths.add('status.md')
        write(exactDirectory, 'status.md', statusRecord(version, status).getBytes(StandardCharsets.UTF_8))
        if (status == 'archived')
            archivedVersions << version
        if (!archivedVersions.isEmpty()) {
            write(outputDirectory, 'archive/index.md', archiveIndex(archivedVersions.unique().sort()).getBytes(StandardCharsets.UTF_8))
        }
        write(outputDirectory, 'index.md', rootIndex(version, status).getBytes(StandardCharsets.UTF_8))

        Map<String, String> outputHashes = outputHashes(outputDirectory)
        outputHashes.remove("$version/source-manifest.json")
        Map<String, ?> sourceManifest = [
                schemaVersion         : 1,
                renderer              : [id: RENDERER_ID, revision: rendererRevision],
                source                : [revision: revision, root: sourceRoot, treeHash: treeHash],
                documentation         : [version: version, status: status],
                branding              : [manifest: brandingManifestPath, season: branding.season, altText: branding.altText,
                                         approval: branding.approval, sourceAsset: logoPath, outputAsset: logoTarget, sha256: logoDigest],
                javadocInputChecksums : new TreeMap<>(javadocInputChecksums),
                generatedFiles        : new TreeSet<>(outputHashes.keySet()),
                outputHashes          : new TreeMap<>(outputHashes)
        ]
        write(exactDirectory, 'source-manifest.json', canonicalJson(sourceManifest).getBytes(StandardCharsets.UTF_8))
    }

    private static Map<String, ?> readBrandingManifest(File objectDirectory, String revision, String path) {
        Object parsed
        try {
            parsed = new JsonSlurper().parseText(new String(gitBytes(objectDirectory, ['show', "${revision}:${path}"]), StandardCharsets.UTF_8))
        } catch (Exception exception) {
            fail("Branding manifest is malformed or absent at $path: ${exception.message}")
        }
        if (!(parsed instanceof Map))
            fail("Branding manifest must be an object: $path")
        Map<String, ?> branding = parsed as Map<String, ?>
        ['season', 'logo', 'altText', 'sha256', 'approval'].each { String field ->
            if (!(branding[field] instanceof String) || branding[field].trim().empty)
                fail("Branding manifest $path requires a non-empty $field")
        }
        if (!(branding.sha256 ==~ /[0-9a-f]{64}/))
            fail("Branding manifest $path has an invalid sha256")
        branding
    }

    private static Map<String, String> normalizedChecksums(Object values) {
        if (!(values instanceof Map))
            fail('Javadoc input checksums must be a module-to-sha256 map')
        Map<String, String> normalized = [:]
        (values as Map).each { key, value ->
            if (!(key instanceof String) || !key || !(value instanceof String) || !(value ==~ /[0-9a-f]{64}/))
                fail('Javadoc input checksums must use non-empty module names and SHA-256 values')
            normalized[key] = value
        }
        normalized
    }

    private static String chrome(String version, String status) {
        String statusLabel = status == 'archived' ? 'Archived (legacy)' : status == 'public-rc' ? 'Public release candidate' : 'Exact version'
        String notice = status == 'archived'
                ? '> **Archived (legacy).** This exact documentation is retained for compatibility. Browse [/archive/](/archive/).\n'
                : status == 'public-rc'
                ? "> **Prerelease warning.** $version is a prerelease, not stable. See its [version-status record](/$version/status.md).\n"
                : '> This is an immutable exact-version documentation snapshot.\n'
        "<!-- Generated by $RENDERER_ID. Do not edit this rendered copy. -->\n> **KlumAST $version — $statusLabel**\n$notice\n"
    }

    private static String statusRecord(String version, String status) {
        "# KlumAST $version version status\n\nStatus: **$status**.\n\n" +
                (status == 'public-rc'
                        ? 'This public release candidate is a prerelease and is not stable. Any later final relationship is recorded outside this immutable tree.\n'
                        : 'This record belongs to the immutable exact documentation tree.\n')
    }

    private static String archiveIndex(List<String> versions) {
        "# Archived KlumAST documentation\n\n" + versions.collect { "- [$it](/$it/) — Archived (legacy)" }.join('\n') + '\n'
    }

    private static String rootIndex(String version, String status) {
        "# KlumAST documentation snapshot\n\nThis local render contains the immutable [$version](/$version/) documentation tree with status **$status**.\n"
    }

    private static Map<String, String> outputHashes(File root) {
        Map<String, String> hashes = new TreeMap<>()
        if (!root.exists()) return hashes
        root.eachFileRecurse { File file ->
            if (file.file)
                hashes[root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char)] = sha256(file.bytes)
        }
        hashes
    }

    private static String canonicalJson(Map<String, ?> value) {
        JsonOutput.prettyPrint(JsonOutput.toJson(value)) + '\n'
    }

    private static void write(File root, String relativePath, byte[] content) {
        File target = new File(root, relativePath)
        target.parentFile.mkdirs()
        Files.write(target.toPath(), content)
    }

    private static String git(File directory, List<String> arguments) {
        new String(gitBytes(directory, arguments), StandardCharsets.UTF_8)
    }

    private static byte[] gitBytes(File directory, List<String> arguments) {
        List<String> command = (['git'] + arguments).collect { it.toString() }
        Process process = new ProcessBuilder(command).directory(directory).redirectErrorStream(true).start()
        byte[] output = process.inputStream.bytes
        if (process.waitFor() != 0)
            fail("Git input acquisition failed (${command.join(' ')}): ${new String(output, StandardCharsets.UTF_8).trim()}")
        output
    }

    private static String sha256(byte[] content) {
        MessageDigest.getInstance('SHA-256').digest(content).encodeHex().toString()
    }

    private static File requiredDirectory(Map<String, ?> inputs, String key) {
        File value = requiredFile(inputs, key)
        if (!value.directory) fail("$key must be a directory: $value")
        value
    }

    private static File requiredFile(Map<String, ?> inputs, String key) {
        Object value = inputs[key]
        if (value == null) fail("$key is required")
        value instanceof File ? value : new File(value.toString())
    }

    private static String requiredString(Map<String, ?> inputs, String key) {
        Object value = inputs[key]
        if (!(value instanceof String) || value.trim().empty) fail("$key is required")
        value.trim()
    }

    private static String requiredRelativePath(Object value, String key) {
        if (!(value instanceof String) || value.trim().empty) fail("$key is required")
        requireRelativePath(value.trim(), key)
    }

    private static String requireRelativePath(String path, String key) {
        if (path.startsWith('/') || path.contains('\\') || path.tokenize('/').contains('..'))
            fail("$key must be a safe repository-relative path: $path")
        path
    }

    private static void requireFullSha(String value, String key) {
        if (!(value ==~ /[0-9a-f]{40}/)) fail("$key must be a full lowercase Git SHA: $value")
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message)
    }
}
