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
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Reproduces the #456 documentation contract without credentials, deployment,
 * publication, or alias mutation. Its generated evidence is deliberately
 * limited to identities, paths, hashes, and assertion results.
 */
abstract class VerifyCredentialFreeDocumentationTracerTask extends DefaultTask {

    static final String HISTORICAL_REVISION = '3aa97428c0420fd3d1ca70b4b5e141360d1ca5b6'
    static final String SUCCESSFUL_FOUR_ZERO_REVISION = 'c68d2757301f94ca65964d5fc7c4e76a4e557a8a'
    static final String PRE_RENDERER_REVISION = '963d12dbf28ebeaf9a47e52c56465f8f27b97592'
    static final String TRACER_VERSION = '4.0.0-tracer'

    @InputDirectory abstract DirectoryProperty getObjectDirectory()
    @OutputDirectory abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void verifyTracer() {
        File source = objectDirectory.get().asFile
        File output = outputDirectory.get().asFile
        assertClean(source)
        assertRevision(source, HISTORICAL_REVISION)
        assertRevision(source, SUCCESSFUL_FOUR_ZERO_REVISION)
        assertRevision(source, PRE_RENDERER_REVISION)
        assertTag(source, 'v3.0.1', HISTORICAL_REVISION)
        assertMissingPreRendererInputs(source, PRE_RENDERER_REVISION)
        assertNoForbiddenTaskNames()
        if (output.exists() && output.listFiles()?.length)
            fail("Credential-free tracer output must be empty: $output")
        output.mkdirs()

        File checkout = new File(temporaryDir, 'fixed-source')
        clone(source, checkout)
        Map<String, Object> evidence = new TreeMap<>()
        try {
            checkout(checkout, SUCCESSFUL_FOUR_ZERO_REVISION)
            generateJavadocs(checkout)
            File fourZeroOutput = new File(output, 'fixed-4.0')
            renderFourZero(checkout, fourZeroOutput, SUCCESSFUL_FOUR_ZERO_REVISION)
            assertFourZeroOutput(fourZeroOutput)

            File historicalOutput = new File(output, 'historical-3.0.1')
            renderHistorical(checkout, historicalOutput)
            assertHistoricalOutput(historicalOutput)

            File stubs = new File(output, 'wiki-stubs')
            GenerateGitHubWikiMigrationStubsTask.generate(new File(checkout, 'docs/user'), checkout, stubs)
            assertStubs(stubs)

            String changedRevision = git(checkout, ['rev-parse', "${SUCCESSFUL_FOUR_ZERO_REVISION}^1"]).trim()
            checkout(checkout, changedRevision)
            generateJavadocs(checkout)
            File changedOutput = new File(output, 'changed-input')
            renderFourZero(checkout, changedOutput, changedRevision)
            String fixedManifest = sha256(new File(fourZeroOutput, "$TRACER_VERSION/source-manifest.json").bytes)
            String changedManifest = sha256(new File(changedOutput, "$TRACER_VERSION/source-manifest.json").bytes)
            if (fixedManifest == changedManifest)
                fail('A changed fixed-source revision must change the source manifest')

            evidence = [
                    schemaVersion: 1,
                    purpose      : 'credential-free #456 documentation tracer',
                    historical   : [tag: 'v3.0.1', revision: HISTORICAL_REVISION, manifestSha256: sha256(new File(historicalOutput, '3.0.1/source-manifest.json').bytes)],
                    fixedFourZero: [revision: SUCCESSFUL_FOUR_ZERO_REVISION, renderedVersion: TRACER_VERSION, manifestSha256: fixedManifest],
                    negativeInput: [revision: PRE_RENDERER_REVISION, expectedSourceRoot: 'docs/user', result: 'rejected-before-renderer'],
                    migrationStubs: [inventorySha256: sha256(new File(stubs, 'migration-stub-inventory.json').bytes)],
                    changedInput : [revision: changedRevision, manifestSha256: changedManifest],
                    assertions   : ['six-isolated-api-bases', 'archived-deep-link', 'root-selector', 'labelled-wiki-stub', 'no-publish-deploy-alias-task']
            ]
        } finally {
            if (checkout.exists()) checkout.deleteDir()
        }
        write(new File(output, 'tracer-evidence.json'), JsonOutput.prettyPrint(JsonOutput.toJson(evidence)) + '\n')
    }

    private void renderFourZero(File checkout, File output, String revision) {
        VersionedDocumentationRenderer.render(
                objectDirectory      : checkout,
                outputDirectory      : output,
                revision             : revision,
                rendererRevision     : SUCCESSFUL_FOUR_ZERO_REVISION,
                version              : TRACER_VERSION,
                status               : 'tracer',
                brandingManifestPath : 'docs/branding/season-4-klumast.json',
                moduleJavadocs       : moduleJavadocs(checkout))
    }

    private void renderHistorical(File checkout, File output) {
        String navigation = RenderHistoricalDocumentationTask.sidebarNavigation(checkout, HISTORICAL_REVISION,
                git(checkout, ['ls-tree', '-r', '--name-only', HISTORICAL_REVISION, '--', 'wiki']).readLines() as Set, '3.0.1')
        VersionedDocumentationRenderer.render(
                objectDirectory         : checkout,
                outputDirectory         : output,
                revision                : HISTORICAL_REVISION,
                rendererRevision        : SUCCESSFUL_FOUR_ZERO_REVISION,
                version                 : '3.0.1',
                status                  : 'archived',
                archiveLink             : '/archive/',
                navigationMarkdown      : navigation,
                navigationExcludedPaths : ['_Sidebar.md', '_Footer.md'],
                landingSourcePath       : 'Home.md')
    }

    private static Map<String, File> moduleJavadocs(File checkout) {
        VersionedDocumentationRenderer.MODULE_REPRESENTATIVE_JAVADOCS.collectEntries { String module, String ignored ->
            [(module): new File(checkout, "$module/build/docs/javadoc")]
        }
    }

    private void generateJavadocs(File checkout) {
        List<String> tasks = VersionedDocumentationRenderer.MODULE_REPRESENTATIVE_JAVADOCS.keySet().collect { ":$it:javadoc" }
        ProcessBuilder builder = new ProcessBuilder(['./gradlew', '--no-daemon', '--console=plain'] + tasks).directory(checkout)
        builder.environment().put('GRADLE_USER_HOME', new File(temporaryDir, 'gradle-user-home').absolutePath)
        ['SONATYPE_USERNAME', 'SONATYPE_PASSWORD', 'SIGNING_KEY', 'SIGNING_PASSWORD', 'GRADLE_PUBLISH_KEY', 'GRADLE_PUBLISH_SECRET', 'KLUM_AST_RELEASE_AUTHORIZED'].each { builder.environment().remove(it) }
        builder.redirectErrorStream(true)
        builder.redirectOutput(ProcessBuilder.Redirect.to(new File(temporaryDir, 'nested-javadoc.log')))
        Process process = builder.start()
        if (process.waitFor() != 0)
            fail('Javadoc generation failed for the fixed documentation source revision')
    }

    private void assertNoForbiddenTaskNames() {
        Set<String> forbidden = ['publish', 'deploy', 'alias'] as Set
        Set<String> directDependencies = taskDependencies.getDependencies(this)*.name as Set
        if (directDependencies.any { String name -> forbidden.any { term -> name.toLowerCase().contains(term) } })
            fail("Credential-free tracer has a forbidden task dependency: $directDependencies")
    }

    private static void assertFourZeroOutput(File output) {
        File exact = new File(output, TRACER_VERSION)
        assertContains(new File(exact, 'Home.md'), 'Credential-free tracer', 'tracer status chrome')
        assertContains(new File(output, 'index.md'), "/$TRACER_VERSION/", 'root selector')
        VersionedDocumentationRenderer.MODULE_REPRESENTATIVE_JAVADOCS.each { String module, String type ->
            if (!new File(exact, "api/$module/$type").file)
                fail("Tracer omitted isolated API base for $module")
        }
        if (new File(exact, 'api/klum-ast-bom').exists()) fail('Tracer must not render a BOM API base')
    }

    private static void assertHistoricalOutput(File output) {
        File historicalHome = new File(output, '3.0.1/Home.md')
        assertContains(historicalHome, 'Archived (legacy)', 'archived deep-link chrome')
        assertContains(historicalHome, 'Historical', 'historical tagged source')
    }

    private static void assertStubs(File stubs) {
        assertContains(new File(stubs, 'Home.md'), 'Migration content', 'wiki migration stub')
        assertContains(new File(stubs, 'Home.md'), 'does not configure or claim an HTTP redirect', 'wiki stub boundary')
        if (!new File(stubs, 'migration-stub-inventory.json').file) fail('Tracer omitted the wiki-stub inventory')
    }

    private static void assertMissingPreRendererInputs(File source, String revision) {
        ['docs/user', 'buildSrc/src/main/groovy/com/blackbuild/klum/ast/docs/VersionedDocumentationRenderer.groovy'].each { String path ->
            Process process = new ProcessBuilder('git', 'cat-file', '-e', "${revision}:${path}").directory(source).start()
            if (process.waitFor() == 0) fail("Pre-renderer fixture unexpectedly contains $path: $revision")
        }
    }

    private static void assertClean(File source) {
        if (git(source, ['status', '--porcelain']).trim()) fail('Credential-free tracer requires a clean checkout')
    }

    private static void assertRevision(File source, String revision) {
        if (git(source, ['rev-parse', '--verify', "${revision}^{commit}"]).trim() != revision)
            fail("Required tracer revision is unavailable: $revision")
    }

    private static void assertTag(File source, String tag, String revision) {
        if (git(source, ['rev-parse', '--verify', "${tag}^{commit}"]).trim() != revision)
            fail("$tag does not resolve to its required tracer revision")
    }

    private static void clone(File source, File checkout) {
        run(source, ['git', 'clone', '--no-checkout', source.absolutePath, checkout.absolutePath], 'Unable to create a clean tracer checkout')
    }

    private static void checkout(File repository, String revision) {
        run(repository, ['git', 'checkout', '--detach', revision], 'Unable to select the fixed tracer revision')
    }

    private static void run(File directory, List<String> command, String failure) {
        ProcessBuilder builder = new ProcessBuilder(command).directory(directory).redirectErrorStream(true)
        builder.redirectOutput(ProcessBuilder.Redirect.to(new File(directory, '.tracer-git.log')))
        Process process = builder.start()
        if (process.waitFor() != 0) fail(failure)
        new File(directory, '.tracer-git.log').delete()
    }

    private static void assertContains(File file, String expected, String description) {
        if (!file.file || !file.getText(StandardCharsets.UTF_8.name()).contains(expected))
            fail("Tracer assertion failed ($description)")
    }

    private static String sha256(byte[] content) {
        MessageDigest.getInstance('SHA-256').digest(content).encodeHex().toString()
    }

    private static String git(File directory, List<String> arguments) {
        ProcessBuilder builder = new ProcessBuilder((['git'] + arguments).collect { it.toString() }).directory(directory).redirectErrorStream(true)
        Process process = builder.start()
        String output = process.inputStream.getText(StandardCharsets.UTF_8.name())
        if (process.waitFor() != 0) fail('Unable to inspect a tracer source identity')
        output
    }

    private static void write(File file, String content) {
        file.parentFile.mkdirs()
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8))
    }

    private static void fail(String message) {
        throw new GradleException(message)
    }
}
