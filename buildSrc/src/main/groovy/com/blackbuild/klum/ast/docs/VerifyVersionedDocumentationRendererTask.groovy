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
import org.gradle.api.tasks.TaskAction

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

/** Hermetic acceptance fixtures for the VD-1 renderer contract. */
abstract class VerifyVersionedDocumentationRendererTask extends DefaultTask {

    @TaskAction
    void verifyRendererContract() {
        File fixture = Files.createTempDirectory(temporaryDir.toPath(), 'documentation-renderer-').toFile()
        File outputs = new File(temporaryDir, 'outputs')
        project.delete(outputs)
        outputs.mkdirs()
        initializeFixture(fixture)
        Map<String, File> moduleJavadocs = initializeModuleJavadocs(fixture)
        String revision = git(fixture, ['rev-parse', 'HEAD']).trim()

        File currentOne = new File(outputs, 'output-one')
        File currentTwo = new File(outputs, 'output-two')
        render(fixture, currentOne, revision, '4.0.0-rc.1', 'public-rc', moduleJavadocs)
        render(fixture, currentTwo, revision, '4.0.0-rc.1', 'public-rc', moduleJavadocs)
        assertEqual(treeDigest(currentOne), treeDigest(currentTwo), 'Repeated exact-revision rendering must be deterministic')
        assertContains(new File(currentOne, '4.0.0-rc.1/Home.md').text, 'Prerelease warning', 'RC chrome')
        assertContains(new File(currentOne, '4.0.0-rc.1/Home.md').text, '/4.0.0-rc.1/status.md', 'RC status link')
        assertTrue(!new File(currentOne, '4.0.0-rc.1/Legacy.md').exists(), '4.x render must not select wiki/')
        assertContains(new File(currentOne, '4.0.0-rc.1/source-manifest.json').text, 'Season 4: The Makeover', 'branding manifest capture')
        assertTrue(new File(currentOne, '4.0.0-rc.1/assets/branding/klumlogo.png').file, 'logo must be local to the exact tree')
        String apiLanding = new File(currentOne, '4.0.0-rc.1/api/index.md').text
        assertContains(apiLanding, 'distinct Javadoc base', 'API landing policy')
        VersionedDocumentationRenderer.MODULE_JAVADOCS.each { String module, String representativeType ->
            File moduleOutput = new File(currentOne, "4.0.0-rc.1/api/$module")
            VerifyVersionedDocumentationRendererTask.assertTrue(new File(moduleOutput, representativeType).file, "representative public type must be reachable for $module")
            VerifyVersionedDocumentationRendererTask.assertContains(new File(moduleOutput, 'index.html').text, module, "isolated API base must retain $module")
            VerifyVersionedDocumentationRendererTask.assertContains(apiLanding, "/4.0.0-rc.1/api/$module/", "API landing must link to $module")
        }
        assertTrue(!new File(currentOne, '4.0.0-rc.1/api/klum-ast-bom').exists(), 'BOM must not have an API output')

        File historical = new File(outputs, 'historical')
        render(fixture, historical, revision, '3.0.1', 'archived', moduleJavadocs)
        assertContains(new File(historical, '3.0.1/Legacy.md').text, 'Archived (legacy)', 'archived chrome')
        assertContains(new File(historical, '3.0.1/Legacy.md').text, '/archive/', 'archive link')
        assertTrue(!new File(historical, '3.0.1/Home.md').exists(), 'historical render must select wiki/')

        expectFailure('dirty input') {
            new File(fixture, 'dirty.txt').text = 'dirty'
            VerifyVersionedDocumentationRendererTask.render(fixture, new File(outputs, 'dirty-output'), revision, '4.0.0-rc.1', 'public-rc', moduleJavadocs)
        }
        new File(fixture, 'dirty.txt').delete()
        expectFailure('unresolved revision') {
            VerifyVersionedDocumentationRendererTask.render(fixture, new File(outputs, 'missing-revision'), '0' * 40, '4.0.0-rc.1', 'public-rc', moduleJavadocs)
        }
        expectFailure('public-rc must be an RC') {
            VerifyVersionedDocumentationRendererTask.render(fixture, new File(outputs, 'not-an-rc'), revision, '4.0.0', 'public-rc', moduleJavadocs)
        }
        expectFailure('development aliases are forbidden') {
            VerifyVersionedDocumentationRendererTask.render(fixture, new File(outputs, 'development'), revision, '4.0.0-rc.1', 'development', moduleJavadocs)
        }

        new File(fixture, 'docs/user/status.md').text = '# collision\n'
        git(fixture, ['add', '.'])
        git(fixture, ['commit', '-m', 'fixture duplicate path'])
        String duplicateRevision = git(fixture, ['rev-parse', 'HEAD']).trim()
        expectFailure('renderer-owned path collision') {
            VerifyVersionedDocumentationRendererTask.render(fixture, new File(outputs, 'duplicate'), duplicateRevision, '4.0.0-rc.1', 'public-rc', moduleJavadocs)
        }

        File missingRoot = Files.createTempDirectory(temporaryDir.toPath(), 'documentation-missing-root-').toFile()
        git(missingRoot, ['init'])
        git(missingRoot, ['config', 'user.email', 'fixtures@example.invalid'])
        git(missingRoot, ['config', 'user.name', 'Documentation fixtures'])
        new File(missingRoot, 'wiki').mkdirs()
        new File(missingRoot, 'wiki/Legacy.md').text = '# legacy\n'
        git(missingRoot, ['add', '.'])
        git(missingRoot, ['commit', '-m', 'fixture missing current root'])
        String missingRootRevision = git(missingRoot, ['rev-parse', 'HEAD']).trim()
        expectFailure('missing current source root') {
            VerifyVersionedDocumentationRendererTask.render(missingRoot, new File(outputs, 'missing-root'), missingRootRevision, '4.0.0-rc.1', 'public-rc', moduleJavadocs)
        }

        File malformedBranding = Files.createTempDirectory(temporaryDir.toPath(), 'documentation-malformed-branding-').toFile()
        initializeFixture(malformedBranding)
        new File(malformedBranding, 'docs/branding/season-4-klumast.json').text = '{}\n'
        git(malformedBranding, ['add', '.'])
        git(malformedBranding, ['commit', '-m', 'fixture malformed branding'])
        String malformedRevision = git(malformedBranding, ['rev-parse', 'HEAD']).trim()
        expectFailure('malformed branding manifest') {
            VerifyVersionedDocumentationRendererTask.render(malformedBranding, new File(outputs, 'malformed-branding'), malformedRevision, '4.0.0-rc.1', 'public-rc', moduleJavadocs)
        }

        Map<String, File> missingModule = new LinkedHashMap<>(moduleJavadocs)
        missingModule.remove('klum-ast-runtime')
        File failedOutput = new File(outputs, 'missing-module-javadoc')
        expectFailure('missing module Javadoc output') {
            VerifyVersionedDocumentationRendererTask.render(fixture, failedOutput, revision, '4.0.0-rc.1', 'public-rc', missingModule)
        }
        assertTrue(!failedOutput.exists(), 'A failed Javadoc input must not produce a partial exact-version render')

        File mirror = new File(moduleJavadocs['klum-ast'], 'com/example/Example_DSL.html')
        mirror.parentFile.mkdirs()
        mirror.text = 'mirror'
        expectFailure('IDE source mirror exclusion') {
            VerifyVersionedDocumentationRendererTask.render(fixture, new File(outputs, 'mirror'), revision, '4.0.0-rc.1', 'public-rc', moduleJavadocs)
        }
        mirror.delete()

        Map<String, File> bom = new LinkedHashMap<>(moduleJavadocs)
        bom['klum-ast-bom'] = new File(temporaryDir, 'bom-javadocs')
        bom['klum-ast-bom'].mkdirs()
        expectFailure('BOM exclusion') {
            VerifyVersionedDocumentationRendererTask.render(fixture, new File(outputs, 'bom'), revision, '4.0.0-rc.1', 'public-rc', bom)
        }

        Map<String, File> merged = new LinkedHashMap<>(moduleJavadocs)
        merged['klum-ast-runtime'] = merged['klum-ast']
        expectFailure('isolated module outputs') {
            VerifyVersionedDocumentationRendererTask.render(fixture, new File(outputs, 'merged'), revision, '4.0.0-rc.1', 'public-rc', merged)
        }

        def renderTask = project.tasks.named('renderVersionedDocumentation').get()
        Set<String> directDependencies = renderTask.taskDependencies.getDependencies(renderTask)*.path as Set
        Set<String> expectedDependencies = VersionedDocumentationRenderer.MODULE_JAVADOCS.keySet().collect { ":$it:javadoc".toString() } as Set
        if (!directDependencies.containsAll(expectedDependencies))
            throw new GradleException("Exact-version rendering must depend on every allowed module Javadoc task; actual direct dependencies: $directDependencies")
        assertTrue(!directDependencies.contains(':klum-ast-bom:javadoc'), 'BOM must not be wired into exact-version API rendering')
        def rootProject = project
        VersionedDocumentationRenderer.MODULE_JAVADOCS.each { String module, String representativeType ->
            def javadocTask = rootProject.project(":$module").tasks.named('javadoc').get()
            VerifyVersionedDocumentationRendererTask.assertTrue(new File(javadocTask.destinationDir, representativeType).file, "standard $module Javadocs must expose a representative public type")
            VerifyVersionedDocumentationRendererTask.assertTrue(javadocTask.source.files.every { !it.name.endsWith('_DSL.java') }, "standard $module Javadocs must exclude IDE source mirrors")
        }

        assertTrue(project.tasks.findByName('gitPublishPush')?.description?.contains('fails closed'),
                'Former mutable wiki publisher must be registered as a fail-closed task')
    }

    private static void initializeFixture(File repository) {
        git(repository, ['init'])
        git(repository, ['config', 'user.email', 'fixtures@example.invalid'])
        git(repository, ['config', 'user.name', 'Documentation fixtures'])
        new File(repository, 'docs/user/img').mkdirs()
        new File(repository, 'docs/branding').mkdirs()
        new File(repository, 'wiki').mkdirs()
        new File(repository, '.gitignore').text = '*/build/\n'
        new File(repository, 'docs/user/Home.md').text = '# Current documentation\n\nCurrent content.\n'
        byte[] logo = 'fixture-logo'.getBytes(StandardCharsets.UTF_8)
        new File(repository, 'docs/user/img/klumlogo.png').bytes = logo
        new File(repository, 'wiki/Legacy.md').text = '# Legacy documentation\n\nHistorical content.\n'
        new File(repository, 'docs/branding/season-4-klumast.json').text = JsonOutput.prettyPrint(JsonOutput.toJson([
                season  : 'Season 4: The Makeover',
                logo    : 'docs/user/img/klumlogo.png',
                altText : 'KlumAST logo for Season 4: The Makeover',
                sha256  : sha256(logo),
                approval: 'candidate'
        ])) + '\n'
        git(repository, ['add', '.'])
        git(repository, ['commit', '-m', 'fixture documentation input'])
    }

    private static Map<String, File> initializeModuleJavadocs(File repository) {
        VersionedDocumentationRenderer.MODULE_JAVADOCS.collectEntries { String module, String representativeType ->
            File moduleRoot = new File(repository, "$module/build/docs/javadoc")
            new File(moduleRoot, 'index.html').with {
                parentFile.mkdirs()
                text = "<html>$module</html>"
            }
            new File(moduleRoot, representativeType).with {
                parentFile.mkdirs()
                text = "<html>$module representative public type</html>"
            }
            [(module): moduleRoot]
        }
    }

    static void render(File repository, File output, String revision, String version, String status, Map<String, File> moduleJavadocs) {
        VersionedDocumentationRenderer.render(
                objectDirectory      : repository,
                outputDirectory      : output,
                revision             : revision,
                rendererRevision     : revision,
                version              : version,
                status               : status,
                brandingManifestPath : 'docs/branding/season-4-klumast.json',
                archivedVersions     : ['2.2.0'],
                moduleJavadocs       : moduleJavadocs)
    }

    private static void expectFailure(String description, Closure action) {
        try {
            action.call()
        } catch (IllegalArgumentException ignored) {
            return
        }
        throw new GradleException("Expected renderer rejection: $description")
    }

    private static void assertContains(String actual, String expected, String description) {
        if (!actual.contains(expected)) throw new GradleException("Fixture failed ($description): expected $expected")
    }

    private static void assertEqual(String left, String right, String description) {
        if (left != right) throw new GradleException("Fixture failed: $description")
    }

    private static void assertTrue(boolean condition, String description) {
        if (!condition) throw new GradleException("Fixture failed: $description")
    }

    private static String treeDigest(File directory) {
        MessageDigest digest = MessageDigest.getInstance('SHA-256')
        List<File> files = []
        directory.eachFileRecurse { File file -> if (file.file) files << file }
        files.sort { left, right ->
            directory.toPath().relativize(left.toPath()).toString() <=> directory.toPath().relativize(right.toPath()).toString()
        }.each { File file ->
            digest.update(directory.toPath().relativize(file.toPath()).toString().getBytes(StandardCharsets.UTF_8))
            digest.update(file.bytes)
        }
        digest.digest().encodeHex().toString()
    }

    private static String sha256(byte[] content) {
        MessageDigest.getInstance('SHA-256').digest(content).encodeHex().toString()
    }

    private static String git(File directory, List<String> arguments) {
        List<String> command = (['git'] + arguments).collect { it.toString() }
        Process process = new ProcessBuilder(command).directory(directory).redirectErrorStream(true).start()
        String output = process.inputStream.getText(StandardCharsets.UTF_8.name())
        if (process.waitFor() != 0) throw new GradleException("Fixture Git command failed (${command.join(' ')}): $output")
        output
    }
}
