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
 * Markdown remains the authoring format; the immutable release payload contains
 * deterministic static HTML, local assets, isolated Javadocs, and one manifest.
 */
class VersionedDocumentationRenderer {

    static final String RENDERER_ID = 'klum-ast-buildsrc-static-html-v1'
    static final Map<String, String> MODULE_REPRESENTATIVE_JAVADOCS = [
            'klum-ast'                : 'com/blackbuild/groovy/configdsl/transform/ast/DSLASTTransformation.html',
            'klum-ast-runtime'        : 'com/blackbuild/klum/ast/KlumModelObject.html',
            'klum-ast-annotations'    : 'com/blackbuild/groovy/configdsl/transform/DSL.html',
            'klum-ast-jackson'        : 'com/blackbuild/klum/ast/jackson/KlumAstModule.html',
            'klum-ast-bean-validation': 'com/blackbuild/klum/ast/validation/bean/JSR380Validator.html',
            'klum-ast-gradle-plugin'  : 'com/blackbuild/klum/ast/gradle/KlumAstSchemaPlugin.html'
    ].asImmutable()
    private static final Set<String> STATUSES = ['current', 'archived', 'public-rc', 'pending', 'tracer'] as Set
    private static final Set<String> RESERVED_PATHS = ['index.html', 'status', 'site-manifest.json', 'assets/site.css'] as Set
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
        String releaseStage = inputs.releaseStage?.toString()
        String finalBrandingApprovalPath = optionalRelativePath(inputs.finalBrandingApprovalPath, 'finalBrandingApprovalPath')
        Map<String, File> moduleJavadocs = version.startsWith('4.') ? requiredModuleJavadocs(inputs.moduleJavadocs, objectDirectory) : [:]
        Map<String, String> javadocInputChecksums = new TreeMap<>(normalizedChecksums(inputs.javadocInputChecksums ?: [:]))
        Map<String, File> additionalFiles = normalizedAdditionalFiles(inputs.additionalFiles ?: [:])
        String apiIndexMarkdown = inputs.apiIndexMarkdown?.toString() ?: ''
        List<String> archivedVersions = ((inputs.archivedVersions ?: []) as List<String>).collect { it.toString() }.sort()

        requireFullSha(revision, 'revision')
        requireFullSha(rendererRevision, 'rendererRevision')
        if (!(version ==~ VERSION_PATTERN))
            fail("Documentation version must be an exact final, RC, or tracer version: $version")
        if (!STATUSES.contains(status))
            fail("Documentation status must be one of $STATUSES; aliases and development trees are not rendered by VD-1: $status")
        if (status == 'public-rc' && !(version ==~ /\d+\.\d+\.\d+-rc\.[1-9]\d*/))
            fail("A public-rc documentation tree requires an RC version: $version")
        if (status == 'tracer' && !(version ==~ /\d+\.\d+\.\d+-tracer/))
            fail("A credential-free tracer tree requires a -tracer version: $version")
        if (status == 'pending' && !(releaseStage in ['candidate', 'final']))
            fail('Pending documentation requires releaseStage=candidate|final.')
        if (status != 'pending' && releaseStage)
            fail('releaseStage is reserved for pending documentation.')
        if (status == 'pending' && releaseStage == 'candidate' && !(version ==~ /\d+\.\d+\.\d+-rc\.[1-9]\d*/))
            fail("Candidate pending documentation requires an RC version: $version")
        if (status == 'pending' && releaseStage == 'final' && !(version ==~ /\d+\.\d+\.\d+/))
            fail("Final pending documentation requires a final version: $version")
        if (!(status in ['public-rc', 'pending']) && version.contains('-rc.'))
            fail("An RC version must be rendered with public-rc status: $version")
        if (status == 'pending' && releaseStage != 'final' && finalBrandingApprovalPath)
            fail('Only final pending documentation may supply a final branding approval.')
        if (status == 'pending' && releaseStage == 'final' && !finalBrandingApprovalPath)
            fail('Final pending documentation requires finalBrandingApprovalPath.')
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
        if (!landingSourcePath && sourceRoot == 'docs/user')
            landingSourcePath = 'Home.md'
        List<String> sourcePaths = git(objectDirectory, ['ls-tree', '-r', '--name-only', revision, '--', sourceRoot])
                .readLines().findAll { it }
        if (sourcePaths.isEmpty())
            fail("Revision $revision does not contain the expected $sourceRoot authoring tree for $version")
        String treeHash = git(objectDirectory, ['rev-parse', "${revision}:${sourceRoot}"]).trim()

        if (outputDirectory.exists() && outputDirectory.listFiles()?.length)
            fail("Output directory must be empty so an exact tree cannot be overwritten: $outputDirectory")
        outputDirectory.mkdirs()
        File exactDirectory = new File(outputDirectory, version)

        Map<String, byte[]> authoredMarkdown = new TreeMap<>()
        Map<String, byte[]> authoredAssets = new TreeMap<>()
        sourcePaths.each { String sourcePath ->
            String relativePath = sourcePath.substring(sourceRoot.length() + 1)
            requireRelativePath(relativePath, 'source path')
            byte[] content = gitBytes(objectDirectory, ['show', "${revision}:${sourcePath}"])
            if (relativePath.endsWith('.md')) authoredMarkdown[relativePath] = content
            else authoredAssets[relativePath] = content
        }
        if (!landingSourcePath || !authoredMarkdown.containsKey(landingSourcePath))
            fail("Landing source is absent from the authored tree: $landingSourcePath")
        Map<String, String> supplementaryInputs = new TreeMap<>()
        if (gitObjectExists(objectDirectory, revision, 'CHANGES.md')) {
            byte[] changelog = gitBytes(objectDirectory, ['show', "${revision}:CHANGES.md"])
            authoredMarkdown['Changelog.md'] = changelog
            supplementaryInputs['CHANGES.md'] = sha256(changelog)
        }

        Map<String, String> pageOutputs = new TreeMap<>()
        Map<String, String> wikiPages = new TreeMap<>()
        authoredMarkdown.keySet().findAll { !navigationExcludedPaths.contains(it) && !(it in ['_Sidebar.md', '_Footer.md']) }.each { String sourcePath ->
            String outputPath = StaticDocumentationPageRenderer.pageOutputPath(sourcePath, landingSourcePath)
            if (outputPath != 'index.html' && RESERVED_PATHS.any { outputPath == it || outputPath.startsWith("$it/") })
                fail("Source path collides with renderer-owned output: $sourcePath -> $outputPath")
            if (outputPath == 'api/index.html' || outputPath.startsWith('api/'))
                fail("Source path collides with renderer-owned API output: $sourcePath")
            if (pageOutputs.containsValue(outputPath))
                fail("Duplicate rendered output path: $outputPath")
            pageOutputs[sourcePath] = outputPath
            wikiPages[StaticDocumentationPageRenderer.wikiKey(sourcePath)] = sourcePath
        }

        Set<String> outputPaths = new TreeSet<>(pageOutputs.values())
        authoredAssets.each { String assetPath, byte[] content ->
            if (RESERVED_PATHS.any { assetPath == it || assetPath.startsWith("$it/") } || assetPath == 'api' || assetPath.startsWith('api/'))
                fail("Authored asset collides with renderer-owned output: $assetPath")
            if (!outputPaths.add(assetPath)) fail("Duplicate output path: $assetPath")
            write(exactDirectory, assetPath, content)
        }

        Map<String, ?> branding = [mode: 'not-applicable']
        String logoTarget
        String logoAltText = 'KlumAST'
        if (status != 'archived') {
            if (!brandingManifestPath)
                fail('brandingManifestPath is required for current and public-rc documentation.')
            branding = readBrandingManifest(objectDirectory, revision, brandingManifestPath)
            String logoPath = requiredRelativePath(branding.logo?.toString(), 'branding logo')
            logoTarget = "assets/branding/${logoPath.tokenize('/').last()}"
            if (!outputPaths.add(logoTarget))
                fail("Branding logo collides with an authored output path: $logoTarget")
            byte[] logo = gitBytes(objectDirectory, ['show', "${revision}:${logoPath}"])
            String logoDigest = sha256(logo)
            if (logoDigest != branding.sha256)
                fail("Branding manifest digest does not match $logoPath")
            write(exactDirectory, logoTarget, logo)
            branding = [manifest: brandingManifestPath, season: branding.season, altText: branding.altText,
                        approval: branding.approval, sourceAsset: logoPath, outputAsset: logoTarget, sha256: logoDigest]
            logoAltText = branding.altText
            if (status == 'pending' && releaseStage == 'final')
                branding.finalApproval = readFinalBrandingApproval(objectDirectory, revision, finalBrandingApprovalPath, brandingManifestPath)
        }

        outputPaths.add('assets/site.css')
        write(exactDirectory, 'assets/site.css', StaticDocumentationPageRenderer.SITE_CSS.getBytes(StandardCharsets.UTF_8))
        String sourceNavigation = navigationMarkdown ?: (authoredMarkdown['_Sidebar.md'] ? new String(authoredMarkdown['_Sidebar.md'], StandardCharsets.UTF_8) : '')
        String sourceFooter = authoredMarkdown['_Footer.md'] ? new String(authoredMarkdown['_Footer.md'], StandardCharsets.UTF_8) : ''
        Map<String, String> presentation = presentation(version, status)
        pageOutputs.each { String sourcePath, String outputPath ->
            String html = StaticDocumentationPageRenderer.render(
                    markdown          : new String(authoredMarkdown[sourcePath], StandardCharsets.UTF_8),
                    sourcePath        : sourcePath,
                    outputPath        : outputPath,
                    pageOutputs       : pageOutputs,
                    wikiPages         : wikiPages,
                    navigationMarkdown: sourceNavigation,
                    footerMarkdown    : sourceFooter,
                    version           : version,
                    status            : status,
                    statusLabel       : presentation.label,
                    notice            : presentation.notice,
                    logoPath          : logoTarget,
                    logoAltText       : logoAltText,
                    repositoryRevision: revision,
                    repositorySourcePath: sourcePath == 'Changelog.md' ? 'CHANGES.md' : "$sourceRoot/$sourcePath",
                    authoringRoot      : sourceRoot)
            write(exactDirectory, outputPath, html.getBytes(StandardCharsets.UTF_8))
        }

        javadocInputChecksums.putAll(copyModuleJavadocs(exactDirectory, moduleJavadocs))
        if (!moduleJavadocs.isEmpty() || apiIndexMarkdown) {
            outputPaths.add('api/index.html')
            writeGeneratedPage(exactDirectory, 'api/index.html', 'api-index.md', apiIndexMarkdown ?: apiIndex(version), version, status,
                    pageOutputs, wikiPages, sourceNavigation, sourceFooter, presentation, logoTarget, logoAltText)
        }

        outputPaths.add('status/index.html')
        writeGeneratedPage(exactDirectory, 'status/index.html', 'status.md', statusRecord(version, status), version, status,
                pageOutputs, wikiPages, sourceNavigation, sourceFooter, presentation, logoTarget, logoAltText)
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
                write(outputDirectory, 'archive/index.html', selectorPage('Archived KlumAST documentation',
                        archiveIndex(archivedVersions.unique().sort()), "../$version/assets/site.css").getBytes(StandardCharsets.UTF_8))
            }
            write(outputDirectory, 'index.html', selectorPage('KlumAST documentation snapshot', rootIndex(version, status),
                    "$version/assets/site.css").getBytes(StandardCharsets.UTF_8))
        }

        Map<String, String> outputHashes = outputHashes(exactDirectory)
        outputHashes.remove('site-manifest.json')
        Map<String, ?> sourceManifest = [
                schemaVersion         : 2,
                renderer              : [id: RENDERER_ID, revision: rendererRevision,
                                         contract: StaticDocumentationPageRenderer.CONTRACT_ID,
                                         commonmarkVersion: StaticDocumentationPageRenderer.COMMONMARK_VERSION,
                                         extensions: ['gfm-tables'], rawHtml: 'escaped', unsafeUrls: 'sanitized'],
                source                : [revision: revision, root: sourceRoot, treeHash: treeHash,
                                         supplementaryInputs: supplementaryInputs],
                documentation         : [version: version, status: status],
                branding              : branding,
                javadocInputChecksums : new TreeMap<>(javadocInputChecksums),
                generatedFiles        : new TreeSet<>(outputHashes.keySet()),
                outputHashes          : new TreeMap<>(outputHashes)
        ]
        write(exactDirectory, 'site-manifest.json', canonicalJson(sourceManifest).getBytes(StandardCharsets.UTF_8))
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

    private static Map<String, String> readFinalBrandingApproval(File objectDirectory, String revision, String path, String brandingManifestPath) {
        byte[] approvalBytes = gitBytes(objectDirectory, ['show', "${revision}:${path}"])
        Object parsed
        try {
            parsed = new JsonSlurper().parseText(new String(approvalBytes, StandardCharsets.UTF_8))
        } catch (Exception exception) {
            fail("Final branding approval is malformed or absent at $path: ${exception.message}")
        }
        if (!(parsed instanceof Map))
            fail("Final branding approval must be an object: $path")
        Map<String, ?> approval = parsed as Map<String, ?>
        if (approval.schemaVersion != 1 || approval.approval != 'approved-final')
            fail("Final branding approval $path must declare schemaVersion 1 and approval approved-final")
        if (!(approval.owner instanceof String) || approval.owner.trim().empty)
            fail("Final branding approval $path requires a non-empty owner")
        if (approval.brandingManifest != brandingManifestPath)
            fail("Final branding approval $path must name $brandingManifestPath")
        String manifestDigest = sha256(gitBytes(objectDirectory, ['show', "${revision}:${brandingManifestPath}"]))
        if (approval.brandingManifestSha256 != manifestDigest)
            fail("Final branding approval $path does not match the exact branding manifest")
        [path: path, sha256: sha256(approvalBytes), owner: approval.owner, brandingManifestSha256: manifestDigest]
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
            if (key.toLowerCase(Locale.ROOT).endsWith('.md'))
                fail('Authored Markdown cannot be added to a deployed static-site payload')
            File file = requiredFile([file: value], 'file')
            if (!file.file)
                fail("Additional file does not exist: $file")
            normalized[requireRelativePath(key, 'additional file path')] = file
        }
        normalized
    }

    private static Map<String, String> presentation(String version, String status) {
        String statusLabel = status == 'archived' ? 'Archived (legacy)' : status == 'public-rc' ? 'Public release candidate' : status == 'tracer' ? 'Credential-free tracer' : status == 'pending' ? 'Pending release evidence' : 'Exact version'
        String notice = status == 'archived'
                ? 'Archived (legacy). This exact documentation is retained for compatibility.'
                : status == 'public-rc'
                ? "$version is a prerelease, not stable."
                : status == 'tracer'
                ? 'Tracer only. This credential-free render is verification evidence, not a release or prerelease.'
                : status == 'pending'
                ? 'Pending release evidence. This unlisted snapshot is a protected publication gate, not a public release or alias.'
                : 'This is an immutable exact-version documentation snapshot.'
        [label: statusLabel, notice: notice]
    }

    private static void writeGeneratedPage(File exactDirectory, String outputPath, String sourcePath, String markdown,
                                           String version, String status, Map<String, String> pageOutputs,
                                           Map<String, String> wikiPages, String navigationMarkdown, String footerMarkdown,
                                           Map<String, String> presentation, String logoPath, String logoAltText) {
        String html = StaticDocumentationPageRenderer.render(
                markdown          : markdown,
                sourcePath        : sourcePath,
                outputPath        : outputPath,
                pageOutputs       : pageOutputs,
                wikiPages         : wikiPages,
                navigationMarkdown: navigationMarkdown,
                footerMarkdown    : footerMarkdown,
                version           : version,
                status            : status,
                statusLabel       : presentation.label,
                notice            : presentation.notice,
                logoPath          : logoPath,
                logoAltText       : logoAltText)
        write(exactDirectory, outputPath, html.getBytes(StandardCharsets.UTF_8))
    }

    private static String statusRecord(String version, String status) {
        "# KlumAST $version version status\n\nStatus: **$status**.\n\n" +
                (status == 'public-rc'
                        ? 'This public release candidate is a prerelease and is not stable. Any later final relationship is recorded outside this immutable tree.\n'
                        : status == 'tracer'
                        ? 'This credential-free tracer is local verification evidence and makes no release or prerelease claim.\n'
                        : status == 'pending'
                        ? 'This unlisted pending snapshot is release-gate evidence. It does not establish a public release or advance an alias.\n'
                        : 'This record belongs to the immutable exact documentation tree.\n')
    }

    private static String archiveIndex(List<String> versions) {
        '<ul>' + versions.collect { "<li><a href=\"../$it/\">$it</a> — Archived (legacy)</li>" }.join('') + '</ul>'
    }

    private static String rootIndex(String version, String status) {
        "<p>This local render contains the immutable <a href=\"$version/\">$version</a> documentation tree with status <strong>$status</strong>.</p>" +
                "<p>Its <a href=\"$version/api/\">isolated module API reference</a> belongs to the same exact version.</p>"
    }

    private static String apiIndex(String version) {
        "# KlumAST $version API reference\n\n" +
                'Each published module has a distinct Javadoc base. The BOM and IDE-only source mirrors are not API inputs.\n\n' +
                MODULE_REPRESENTATIVE_JAVADOCS.keySet().collect { module -> "- [$module](api/$module/)" }.join('\n') +
                '\n\n[Documentation landing](../)\n'
    }

    private static String selectorPage(String title, String body, String cssPath) {
        """<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>$title</title><link rel="stylesheet" href="$cssPath"></head>
<body><main class="content" style="width:min(70rem,calc(100% - 2rem));margin:2rem auto"><h1>$title</h1>$body</main></body></html>
"""
    }

    private static String relativeExactLink(String sourcePath, String targetPath) {
        ('../' * sourcePath.count('/')) + targetPath
    }

    private static String relativeSiteLink(String sourcePath, String targetPath) {
        ('../' * (sourcePath.count('/') + 1)) + targetPath
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

    private static boolean gitObjectExists(File directory, String revision, String path) {
        Process process = new ProcessBuilder('git', 'cat-file', '-e', "${revision}:${path}").directory(directory).start()
        process.waitFor() == 0
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
