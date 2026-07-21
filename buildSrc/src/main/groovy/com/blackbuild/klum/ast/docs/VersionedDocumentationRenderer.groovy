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
    static final Map<String, String> MODULE_REPRESENTATIVE_JAVADOCS = [
            'klum-ast'                : 'com/blackbuild/groovy/configdsl/transform/ast/DSLASTTransformation.html',
            'klum-ast-runtime'        : 'com/blackbuild/klum/ast/KlumModelObject.html',
            'klum-ast-annotations'    : 'com/blackbuild/groovy/configdsl/transform/DSL.html',
            'klum-ast-jackson'        : 'com/blackbuild/klum/ast/jackson/KlumAstModule.html',
            'klum-ast-bean-validation': 'com/blackbuild/klum/ast/validation/bean/JSR380Validator.html',
            'klum-ast-gradle-plugin'  : 'com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.html'
    ].asImmutable()
    private static final Set<String> STATUSES = ['current', 'archived', 'public-rc', 'tracer'] as Set
    private static final Set<String> RESERVED_PATHS = ['status.md', 'source-manifest.json'] as Set
    private static final String VERSION_PATTERN = /\d+\.\d+\.\d+(?:-rc\.[1-9]\d*|-tracer)?/

    static void render(Map<String, ?> inputs) {
        File objectDirectory = requiredDirectory(inputs, 'objectDirectory')
        File outputDirectory = requiredFile(inputs, 'outputDirectory')
        String revision = requiredString(inputs, 'revision')
        String rendererRevision = requiredString(inputs, 'rendererRevision')
        String version = requiredString(inputs, 'version')
        String status = requiredString(inputs, 'status')
        String archiveLink = inputs.archiveLink?.toString() ?: '/archive/'
        String navigationMarkdown = inputs.navigationMarkdown?.toString() ?: ''
        Set<String> navigationExcludedPaths = ((inputs.navigationExcludedPaths ?: []) as Collection).collect { it.toString() } as Set
        String landingSourcePath = optionalRelativePath(inputs.landingSourcePath, 'landingSourcePath')
        String brandingManifestPath = optionalRelativePath(inputs.brandingManifestPath, 'brandingManifestPath')
        Map<String, File> moduleJavadocs = version.startsWith('4.') ? requiredModuleJavadocs(inputs.moduleJavadocs, objectDirectory) : [:]
        Map<String, String> javadocInputChecksums = new TreeMap<>(normalizedChecksums(inputs.javadocInputChecksums ?: [:]))
        Map<String, File> additionalFiles = normalizedAdditionalFiles(inputs.additionalFiles ?: [:])
        List<String> archivedVersions = ((inputs.archivedVersions ?: []) as List<String>).collect { it.toString() }.sort()

        requireFullSha(revision, 'revision')
        requireFullSha(rendererRevision, 'rendererRevision')
        if (!(version ==~ VERSION_PATTERN))
            fail("Documentation version must be an exact final or RC version: $version")
        if (!STATUSES.contains(status))
            fail("Documentation status must be one of $STATUSES; aliases and development trees are not rendered by VD-1: $status")
        if (status == 'public-rc' && !(version ==~ /\d+\.\d+\.\d+-rc\.[1-9]\d*/))
            fail("A public-rc documentation tree requires an RC version: $version")
        if (status == 'tracer' && !(version ==~ /\d+\.\d+\.\d+-tracer/))
            fail("A credential-free tracer tree requires a -tracer version: $version")
        if (status != 'public-rc' && version.contains('-rc.'))
            fail("An RC version must be rendered with public-rc status: $version")
        if (status != 'tracer' && version.endsWith('-tracer'))
            fail("A -tracer version must be rendered with tracer status: $version")

        if (git(objectDirectory, ['status', '--porcelain']).trim())
            fail('Documentation input worktree is dirty; render a checked-out immutable revision.')
        String resolvedRevision = git(objectDirectory, ['rev-parse', '--verify', "${revision}^{commit}"]).trim()
        if (resolvedRevision != revision)
            fail("Revision must resolve to the supplied full SHA: $revision")
        git(objectDirectory, ['cat-file', '-e', "${revision}^{commit}"])
        if (version.startsWith('4.') && git(objectDirectory, ['rev-parse', 'HEAD']).trim() != revision)
            fail('4.x module Javadocs must be generated from the selected checked-out revision.')

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
            if (outputPath == 'api' || outputPath.startsWith('api/'))
                fail("Source path collides with renderer-owned API output: $outputPath")
            if (!outputPaths.add(outputPath))
                fail("Duplicate output path: $outputPath")
            byte[] content = gitBytes(objectDirectory, ['show', "${revision}:${sourcePath}"])
            if (outputPath.endsWith('.md')) {
                String rendered = chrome(version, status, archiveLink) + new String(content, StandardCharsets.UTF_8)
                if (navigationMarkdown && !navigationExcludedPaths.contains(outputPath))
                    rendered += navigationMarkdown
                content = rendered.getBytes(StandardCharsets.UTF_8)
            }
            write(exactDirectory, outputPath, content)
        }

        if (landingSourcePath) {
            File landingSource = new File(exactDirectory, landingSourcePath)
            if (!landingSource.file)
                fail("Landing source is absent from the rendered exact tree: $landingSourcePath")
            write(exactDirectory, 'index.md', landingSource.bytes)
        }

        Map<String, ?> branding = [mode: 'not-applicable']
        if (status != 'archived') {
            if (!brandingManifestPath)
                fail('brandingManifestPath is required for current and public-rc documentation.')
            branding = readBrandingManifest(objectDirectory, revision, brandingManifestPath)
            String logoPath = requiredRelativePath(branding.logo?.toString(), 'branding logo')
            String logoTarget = "assets/branding/${logoPath.tokenize('/').last()}"
            if (!outputPaths.add(logoTarget))
                fail("Branding logo collides with an authored output path: $logoTarget")
            byte[] logo = gitBytes(objectDirectory, ['show', "${revision}:${logoPath}"])
            String logoDigest = sha256(logo)
            if (logoDigest != branding.sha256)
                fail("Branding manifest digest does not match $logoPath")
            write(exactDirectory, logoTarget, logo)
            branding = [manifest: brandingManifestPath, season: branding.season, altText: branding.altText,
                        approval: branding.approval, sourceAsset: logoPath, outputAsset: logoTarget, sha256: logoDigest]
        }

        javadocInputChecksums.putAll(copyModuleJavadocs(exactDirectory, moduleJavadocs))
        if (!moduleJavadocs.isEmpty())
            write(exactDirectory, 'api/index.md', apiIndex(version).getBytes(StandardCharsets.UTF_8))

        outputPaths.add('status.md')
        write(exactDirectory, 'status.md', statusRecord(version, status).getBytes(StandardCharsets.UTF_8))
        additionalFiles.each { String additionalPath, File additionalFile ->
            requireRelativePath(additionalPath, 'additional file path')
            if (!outputPaths.add(additionalPath))
                fail("Additional file collides with renderer output: $additionalPath")
            write(exactDirectory, additionalPath, additionalFile.bytes)
        }
        if (inputs.writeRootIndices != false) {
            if (status == 'archived')
                archivedVersions << version
            if (!archivedVersions.isEmpty()) {
                write(outputDirectory, 'archive/index.md', archiveIndex(archivedVersions.unique().sort()).getBytes(StandardCharsets.UTF_8))
            }
            write(outputDirectory, 'index.md', rootIndex(version, status).getBytes(StandardCharsets.UTF_8))
        }

        Map<String, String> outputHashes = outputHashes(outputDirectory)
        outputHashes.remove("$version/source-manifest.json")
        Map<String, ?> sourceManifest = [
                schemaVersion         : 1,
                renderer              : [id: RENDERER_ID, revision: rendererRevision],
                source                : [revision: revision, root: sourceRoot, treeHash: treeHash],
                documentation         : [version: version, status: status],
                branding              : branding,
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

    private static Map<String, File> requiredModuleJavadocs(Object values, File objectDirectory) {
        if (!(values instanceof Map))
            fail('Module Javadocs must be a module-to-directory map')
        Map<String, File> normalized = [:]
        (values as Map).each { key, value ->
            if (!(key instanceof String) || !MODULE_REPRESENTATIVE_JAVADOCS.containsKey(key))
                fail("Module Javadocs must use the explicit API allowlist: ${MODULE_REPRESENTATIVE_JAVADOCS.keySet()}")
            File directory = value instanceof File ? value : new File(value.toString())
            File expectedDirectory = new File(objectDirectory, "$key/build/docs/javadoc").canonicalFile
            if (directory.canonicalFile != expectedDirectory)
                fail("Module Javadocs for $key must come from its selected-revision Javadoc task: $expectedDirectory")
            if (!directory.directory)
                fail("Module Javadoc output is missing for $key: $directory")
            if (!new File(directory, 'index.html').file)
                fail("Module Javadoc output is incomplete for $key: $directory")
            if (!new File(directory, MODULE_REPRESENTATIVE_JAVADOCS[key]).file)
                fail("Representative public type is absent from $key Javadocs: ${MODULE_REPRESENTATIVE_JAVADOCS[key]}")
            if (containsMirrorJavadoc(directory))
                fail("IDE source mirrors must not appear in $key Javadocs")
            normalized[key] = directory.canonicalFile
        }
        if (normalized.keySet() != MODULE_REPRESENTATIVE_JAVADOCS.keySet())
            fail("Module Javadocs must contain exactly the explicit API allowlist: ${MODULE_REPRESENTATIVE_JAVADOCS.keySet()}")
        if (normalized.values().toSet().size() != normalized.size())
            fail('Module Javadocs must use distinct isolated module outputs')
        normalized
    }

    private static Map<String, String> copyModuleJavadocs(File exactDirectory, Map<String, File> moduleJavadocs) {
        Map<String, String> checksums = new TreeMap<>()
        moduleJavadocs.each { String module, File source ->
            File target = new File(exactDirectory, "api/$module")
            source.eachFileRecurse { File file ->
                if (file.file) {
                    String path = source.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char)
                    write(target, path, file.bytes)
                }
            }
            checksums[module] = treeDigest(source)
        }
        checksums
    }

    private static boolean containsMirrorJavadoc(File directory) {
        boolean found = false
        directory.eachFileRecurse { File file ->
            if (file.file && file.name.endsWith('_DSL.html'))
                found = true
        }
        found
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

    private static Map<String, File> normalizedAdditionalFiles(Object values) {
        if (!(values instanceof Map))
            fail('Additional files must map safe output paths to existing files')
        Map<String, File> normalized = new TreeMap<>()
        (values as Map).each { key, value ->
            if (!(key instanceof String))
                fail('Additional files must use String output paths')
            File file = requiredFile([file: value], 'file')
            if (!file.file)
                fail("Additional file does not exist: $file")
            normalized[requireRelativePath(key, 'additional file path')] = file
        }
        normalized
    }

    private static String chrome(String version, String status, String archiveLink) {
        String statusLabel = status == 'archived' ? 'Archived (legacy)' : status == 'public-rc' ? 'Public release candidate' : status == 'tracer' ? 'Credential-free tracer' : 'Exact version'
        String notice = status == 'archived'
                ? "> **Archived (legacy).** This exact documentation is retained for compatibility. Browse [the archive](${archiveLink}).\n"
                : status == 'public-rc'
                ? "> **Prerelease warning.** $version is a prerelease, not stable. See its [version-status record](/$version/status.md).\n"
                : status == 'tracer'
                ? '> **Tracer only.** This credential-free render is verification evidence, not a release or prerelease.\n'
                : '> This is an immutable exact-version documentation snapshot.\n'
        "<!-- Generated by $RENDERER_ID. Do not edit this rendered copy. -->\n> **KlumAST $version — $statusLabel**\n$notice\n"
    }

    private static String statusRecord(String version, String status) {
        "# KlumAST $version version status\n\nStatus: **$status**.\n\n" +
                (status == 'public-rc'
                        ? 'This public release candidate is a prerelease and is not stable. Any later final relationship is recorded outside this immutable tree.\n'
                        : status == 'tracer'
                        ? 'This credential-free tracer is local verification evidence and makes no release or prerelease claim.\n'
                        : 'This record belongs to the immutable exact documentation tree.\n')
    }

    private static String archiveIndex(List<String> versions) {
        "# Archived KlumAST documentation\n\n" + versions.collect { "- [$it](/$it/) — Archived (legacy)" }.join('\n') + '\n'
    }

    private static String rootIndex(String version, String status) {
        "# KlumAST documentation snapshot\n\nThis local render contains the immutable [$version](/$version/) documentation tree with status **$status**.\n\n" +
                "Its [isolated module API reference](/$version/api/) belongs to the same exact version.\n"
    }

    private static String apiIndex(String version) {
        "# KlumAST $version API reference\n\n" +
                'Each published module has a distinct Javadoc base. The BOM and IDE-only source mirrors are not API inputs.\n\n' +
                MODULE_REPRESENTATIVE_JAVADOCS.keySet().collect { module -> "- [$module](/$version/api/$module/)" }.join('\n') + '\n'
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

    private static String treeDigest(File root) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        List<File> files = []
        root.eachFileRecurse { File file -> if (file.file) files << file }
        files.sort { File left, File right ->
            root.toPath().relativize(left.toPath()).toString() <=> root.toPath().relativize(right.toPath()).toString()
        }.each { File file ->
            digest.update(root.toPath().relativize(file.toPath()).toString().replace(File.separatorChar, '/' as char).getBytes(StandardCharsets.UTF_8))
            digest.update(file.bytes)
        }
        digest.digest().encodeHex().toString()
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

    private static String optionalRelativePath(Object value, String key) {
        if (value == null || value.toString().trim().empty) return null
        requireRelativePath(value.toString().trim(), key)
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
