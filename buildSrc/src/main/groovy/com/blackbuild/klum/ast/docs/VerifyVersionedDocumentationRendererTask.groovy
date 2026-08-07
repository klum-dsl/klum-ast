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
        assertNoAuthoredPresentationHtml(project.rootDir)
        assertNoLegacyKlumVersionPlaceholder(project.rootDir)
        String readme = new File(project.rootDir, 'README.md').text
        assertContains(readme, 'https://klum-dsl.github.io/klum-ast/',
                'README must use the canonical versioned documentation entry point')
        assertTrue(!readme.contains('](docs/user/'),
                'README must not present repository authoring files as current public documentation')
        assertContains(readme, 'https://klum-dsl.github.io/klum-ast/3.0.1/',
                'README must retain intentional exact historical documentation links')
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
        File exactLanding = new File(currentOne, '4.0.0-rc.1/index.html')
        File nestedPage = new File(currentOne, '4.0.0-rc.1/Guide/Nested/index.html')
        File changelog = new File(currentOne, '4.0.0-rc.1/Changelog/index.html')
        assertContains(exactLanding.text, 'is a prerelease, not stable', 'RC chrome')
        assertContains(exactLanding.text, 'href="status/"', 'RC status link must remain inside the exact tree')
        assertContains(nestedPage.text, 'href="../../status/"', 'nested RC status link must remain inside the exact tree')
        assertContains(exactLanding.text, 'href="Guide/Nested/"', 'authored Markdown links must resolve to directory URLs')
        assertContains(exactLanding.text, 'href="Gradle-Onboarding/">Gradle Onboarding</a>',
                'current navigation must use the canonical Gradle Onboarding route and visible label')
        assertContains(exactLanding.text, 'href="#same-heading"', 'same-page Markdown fragments must use rendered heading slugs')
        assertContains(nestedPage.text, 'href="../../"', 'nested pages must link relatively to the exact landing')
        assertContains(nestedPage.text, 'href="../../#same-heading"', 'cross-page Markdown fragments must use rendered heading slugs')
        assertContains(nestedPage.text, "id 'com.blackbuild.klum-ast-schema' version '4.0.0-rc.1'",
                'public RC rendering must substitute the portable KlumAST plugin version token')
        assertContains(nestedPage.text, 'com.blackbuild.klum.ast:klum-ast-runtime:4.0.0-rc.1',
                'public RC rendering must substitute the portable KlumAST dependency version token')
        assertTrue(!nestedPage.text.contains('&lt;klum-version&gt;') && !nestedPage.text.contains('&lt;matching-klum-version&gt;'),
                'public RC rendering must not retain a portable or legacy KlumAST version placeholder')
        assertContains(new File(fixture, 'docs/user/Guide/Nested.md').text, '<klum-version>',
                'source documentation must remain portable after rendering an exact version')
        assertContains(nestedPage.text, "github.com/klum-dsl/klum-ast/blob/$revision/agent-skills/example/SKILL.md",
                'authoring-root escapes must become immutable repository-source links')
        assertContains(changelog.text, 'href="../Builder-First-Migration/"',
                'repository-root changelog links into the authoring tree must become site links')
        assertContains(exactLanding.text, 'id="same-heading-1"', 'duplicate GitHub-compatible heading ids')
        assertContains(exactLanding.text, 'id="überblick"', 'Unicode heading ids')
        assertContains(exactLanding.text, '&lt;unsafe-card&gt;', 'authored raw HTML must be escaped')
        assertContains(exactLanding.text, '<table>', 'GFM tables must render as HTML')
        assertContains(exactLanding.text, '<picture class="season-lockup">', 'season lockup must use a responsive picture')
        assertContains(exactLanding.text, 'media="(max-width: 1000px)" srcset="img/klumast-season-4-documentation-compact.svg"',
                'season lockup must select the compact asset before the desktop content column becomes too narrow')
        assertContains(exactLanding.text, '<img src="img/klumast-season-4-documentation.svg" alt="Season lockup">',
                'season lockup must retain its authored alternate text')
        assertContains(exactLanding.text, '&lt;dependencies&gt;', 'XML code examples must remain escaped code')
        assertTrue(!new File(currentOne, '4.0.0-rc.1/Home').exists(), 'the landing source must not produce a second public page')
        File legacyOnboarding = new File(currentOne, '4.0.0-rc.1/Getting-Started/index.html')
        assertTrue(legacyOnboarding.file, 'legacy onboarding route must remain available in the exact tree')
        assertContains(legacyOnboarding.text, 'href="../Gradle-Onboarding/"', 'legacy onboarding route must point to the canonical route')
        assertContains(new File(currentOne, 'index.html').text, 'href="4.0.0-rc.1/"', 'root landing must use a site-relative exact-version link')
        assertTrue(!new File(currentOne, '4.0.0-rc.1/Legacy').exists(), '4.x render must not select wiki/')
        assertContains(new File(currentOne, '4.0.0-rc.1/site-manifest.json').text, 'Season 4: The Makeover', 'branding manifest capture')
        assertContains(new File(currentOne, '4.0.0-rc.1/site-manifest.json').text, 'commonmark-java-static-html-v1', 'pinned static HTML renderer contract')
        assertContains(new File(currentOne, '4.0.0-rc.1/site-manifest.json').text, 'img/klumlogo.png', 'authored assets must be manifest-covered')
        assertTrue(!containsFileEnding(new File(currentOne, '4.0.0-rc.1'), '.md'), 'authored Markdown must not be deployed')
        assertTrue(new File(currentOne, '4.0.0-rc.1/assets/branding/klumlogo.png').file, 'logo must be local to the exact tree')
        assertTrue(new File(currentOne, '4.0.0-rc.1/img/klumlogo.png').file, 'authored local assets must be copied')
        String apiLanding = new File(currentOne, '4.0.0-rc.1/api/index.html').text
        assertContains(apiLanding, 'distinct Javadoc base', 'API landing policy')
        assertContains(apiLanding, 'href="../"', 'API landing must link back to the exact documentation landing')
        VersionedDocumentationRenderer.MODULE_REPRESENTATIVE_JAVADOCS.each { String module, String representativeType ->
            File moduleOutput = new File(currentOne, "4.0.0-rc.1/api/$module")
            VerifyVersionedDocumentationRendererTask.assertTrue(new File(moduleOutput, representativeType).file, "representative public type must be reachable for $module")
            VerifyVersionedDocumentationRendererTask.assertContains(new File(moduleOutput, 'index.html').text, module, "isolated API base must retain $module")
            VerifyVersionedDocumentationRendererTask.assertContains(apiLanding, "href=\"$module/\"", "API landing must link to $module relative to its own base")
        }
        assertTrue(!new File(currentOne, '4.0.0-rc.1/api/klum-ast-bom').exists(), 'BOM must not have an API output')
        DocumentationSiteServer siteServer = new DocumentationSiteServer(currentOne)
        try {
            siteServer.verify()
        } finally {
            siteServer.close()
        }

        File pending = new File(outputs, 'pending')
        VersionedDocumentationRenderer.render([
                objectDirectory      : fixture,
                outputDirectory      : pending,
                revision             : revision,
                rendererRevision     : revision,
                version              : '4.0.0-rc.1',
                status               : 'pending',
                releaseStage         : 'candidate',
                brandingManifestPath : 'docs/branding/season-4-klumast.json',
                moduleJavadocs       : moduleJavadocs])
        assertContains(new File(pending, '4.0.0-rc.1/index.html').text, 'Pending release evidence', 'pending chrome')
        assertContains(new File(pending, '4.0.0-rc.1/status/index.html').text, 'does not establish a public release', 'pending status boundary')
        expectFailure('final pending documentation requires an approval') {
            VersionedDocumentationRenderer.render([
                    objectDirectory      : fixture,
                    outputDirectory      : new File(outputs, 'unapproved-final'),
                    revision             : revision,
                    rendererRevision     : revision,
                    version              : '4.0.0',
                    status               : 'pending',
                    releaseStage         : 'final',
                    brandingManifestPath : 'docs/branding/season-4-klumast.json',
                    moduleJavadocs       : moduleJavadocs])
        }
        File brandingManifest = new File(fixture, 'docs/branding/season-4-klumast.json')
        new File(fixture, 'docs/branding/final-approval.json').text = JsonOutput.prettyPrint(JsonOutput.toJson([
                schemaVersion          : 1,
                approval               : 'approved-final',
                owner                  : 'Branding owner',
                brandingManifest       : 'docs/branding/season-4-klumast.json',
                brandingManifestSha256 : sha256(brandingManifest.bytes)
        ])) + '\n'
        git(fixture, ['add', '.'])
        git(fixture, ['commit', '-m', 'fixture final branding approval'])
        String finalRevision = git(fixture, ['rev-parse', 'HEAD']).trim()
        VersionedDocumentationRenderer.render([
                objectDirectory            : fixture,
                outputDirectory            : new File(outputs, 'approved-final'),
                revision                   : finalRevision,
                rendererRevision           : finalRevision,
                version                    : '4.0.0',
                status                     : 'pending',
                releaseStage               : 'final',
                brandingManifestPath       : 'docs/branding/season-4-klumast.json',
                finalBrandingApprovalPath  : 'docs/branding/final-approval.json',
                moduleJavadocs             : moduleJavadocs])
        assertContains(new File(outputs, 'approved-final/4.0.0/site-manifest.json').text, 'final-approval.json', 'final approval must be captured')
        brandingManifest.text = brandingManifest.text.replace('Season 4: The Makeover', 'Changed Season')
        git(fixture, ['add', '.'])
        git(fixture, ['commit', '-m', 'fixture changed final branding'])
        String changedBrandingRevision = git(fixture, ['rev-parse', 'HEAD']).trim()
        expectFailure('changed final branding manifest invalidates approval') {
            VersionedDocumentationRenderer.render([
                    objectDirectory            : fixture,
                    outputDirectory            : new File(outputs, 'changed-final-branding'),
                    revision                   : changedBrandingRevision,
                    rendererRevision           : changedBrandingRevision,
                    version                    : '4.0.0',
                    status                     : 'pending',
                    releaseStage               : 'final',
                    brandingManifestPath       : 'docs/branding/season-4-klumast.json',
                    finalBrandingApprovalPath  : 'docs/branding/final-approval.json',
                    moduleJavadocs             : moduleJavadocs])
        }
        git(fixture, ['checkout', '--detach', revision])
        git(fixture, ['update-ref', 'refs/remotes/origin/master', revision])
        PreparePendingDocumentationStageTask.validateIdentity(fixture, 'candidate', '4.0.0-rc.1', revision, '4.0.0-rc.1')
        assertTrue(PreparePendingDocumentationStageTask.validateRenderedManifest(pending, 'candidate', '4.0.0-rc.1', revision).exactPath ==
                "pending/4.0.0-rc.1/$revision/", 'pending handoff must bind stage, version, and SHA to its immutable path')
        expectFailure('missing pending site manifest') {
            PreparePendingDocumentationStageTask.validateRenderedManifest(new File(outputs, 'missing-pending-manifest'), 'candidate', '4.0.0-rc.1', revision)
        }
        expectFailure('malformed pending stage') {
            PreparePendingDocumentationStageTask.validateIdentity(fixture, 'preview', '4.0.0-rc.1', revision, '4.0.0-rc.1')
        }
        expectFailure('malformed pending SHA') {
            PreparePendingDocumentationStageTask.validateIdentity(fixture, 'candidate', '4.0.0-rc.1', 'A' * 40, '4.0.0-rc.1')
        }
        expectFailure('pending stage/version mismatch') {
            PreparePendingDocumentationStageTask.validateIdentity(fixture, 'candidate', '4.0.0', revision, '4.0.0')
        }
        expectFailure('pending off-master revision') {
            VerifyVersionedDocumentationRendererTask.git(fixture, ['update-ref', '-d', 'refs/remotes/origin/master'])
            PreparePendingDocumentationStageTask.validateIdentity(fixture, 'candidate', '4.0.0-rc.1', revision, '4.0.0-rc.1')
        }
        git(fixture, ['update-ref', 'refs/remotes/origin/master', revision])

        File historical = new File(outputs, 'historical')
        render(fixture, historical, revision, '3.0.1', 'archived', moduleJavadocs, '/archive/', '', 'Home.md')
        assertContains(new File(historical, '3.0.1/Legacy/index.html').text, 'Archived (legacy)', 'archived chrome')
        assertContains(new File(historical, '3.0.1/Legacy/index.html').text, '&lt;klum-version&gt;',
                'historical wiki content must retain its portable text without current-documentation substitution')
        assertContains(new File(historical, '3.0.1/Legacy/index.html').text, 'href="../"', 'historical navigation must use extensionless relative links')
        assertTrue(new File(historical, '3.0.1/index.html').file, 'exact version must expose its authoritative landing page')
        assertContains(new File(historical, '3.0.1/site-manifest.json').text, 'not-applicable', 'historical branding exclusion')
        assertContains(new File(historical, 'archive/index.html').text, 'href="../2.2.0/"', 'archive index must use a site-relative exact-version link')
        assertContains(new File(historical, '3.0.1/index.html').text, 'Historical home', 'historical render must select wiki/')
        assertTrue(new File(historical, '3.0.1').directory, 'historical exact versions must live at the root version path')
        assertContains(RenderHistoricalDocumentationTask.apiIndex('3.0.1', [
                'klum-ast': [availability: 'imported', output: 'klum-ast/index.html'],
                'klum-ast-bean-validation': [availability: 'unavailable', reason: 'No released Javadoc JAR exists for this module and version.']
        ]), '[Documentation landing](../)', 'historical API landing back-link')
        assertEqual(GenerateGitHubWikiMigrationStubsTask.destinationFor('Retired.md', [] as Set),
                "${GenerateGitHubWikiMigrationStubsTask.CANONICAL_BASE}/archive/", 'legacy page fallback')
        assertContains(GenerateGitHubWikiMigrationStubsTask.stubContent('Basics.md',
                "${GenerateGitHubWikiMigrationStubsTask.CANONICAL_BASE}/stable/Basics/"),
                'does not configure or claim an HTTP redirect', 'wiki-stub redirect boundary')

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

        new File(fixture, 'docs/user/status/index.html').with { parentFile.mkdirs(); text = 'collision\n' }
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

        File missingResponsiveAsset = Files.createTempDirectory(temporaryDir.toPath(), 'documentation-missing-responsive-asset-').toFile()
        initializeFixture(missingResponsiveAsset)
        new File(missingResponsiveAsset, 'docs/user/img/klumast-season-4-documentation-compact.svg').delete()
        git(missingResponsiveAsset, ['add', '-u'])
        git(missingResponsiveAsset, ['commit', '-m', 'fixture missing responsive asset'])
        String missingResponsiveAssetRevision = git(missingResponsiveAsset, ['rev-parse', 'HEAD']).trim()
        expectFailure('missing compact Season 4 lockup asset') {
            VerifyVersionedDocumentationRendererTask.render(missingResponsiveAsset, new File(outputs, 'missing-responsive-asset'),
                    missingResponsiveAssetRevision, '4.0.0-rc.1', 'public-rc', moduleJavadocs)
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
        Set<String> expectedDependencies = VersionedDocumentationRenderer.MODULE_REPRESENTATIVE_JAVADOCS.keySet().collect { ":$it:javadoc".toString() } as Set
        if (!directDependencies.containsAll(expectedDependencies))
            throw new GradleException("Exact-version rendering must depend on every allowed module Javadoc task; actual direct dependencies: $directDependencies")
        assertTrue(!directDependencies.contains(':klum-ast-bom:javadoc'), 'BOM must not be wired into exact-version API rendering')

        def mavenPublicationTask = project.tasks.named('publishMavenProductToSonatype').get()
        Set<String> mavenPublicationDependencies = mavenPublicationTask.taskDependencies.getDependencies(mavenPublicationTask)*.path as Set
        Set<String> expectedMavenPublicationDependencies = project.subprojects.findAll { subproject ->
            subproject.tasks.findByName('publishToSonatype') != null
        }.collect { subproject ->
            ":${subproject.name}:publishToSonatype".toString()
        } as Set
        assertTrue(!expectedMavenPublicationDependencies.empty,
                'the protected Maven publication aggregate must discover at least one Sonatype publication task')
        assertTrue(mavenPublicationDependencies.containsAll(expectedMavenPublicationDependencies),
                "the protected Maven publication aggregate must depend on every Sonatype publication task; actual direct dependencies: $mavenPublicationDependencies")
        def pluginAssemblyTask = project.project(':klum-ast-gradle-plugin').tasks.named('assemble').get()
        assertTrue(mavenPublicationDependencies.contains(pluginAssemblyTask.path),
                'the protected Maven publication aggregate must assemble Gradle plugins before staging')
        def closeStagingTask = project.tasks.named('closeSonatypeStagingRepository').get()
        assertTrue(closeStagingTask.taskDependencies.getDependencies(closeStagingTask).contains(mavenPublicationTask),
                'closing the protected Sonatype staging repository must require Maven publication first')
        String rootBuild = new File(project.rootDir, 'build.gradle').text
        assertContains(rootBuild, 'mustRunAfter closeSonatypeStagingRepository',
                'Gradle Plugin Portal publication must run after Sonatype release')
        String baseConventions = new File(project.rootDir, 'buildSrc/src/main/groovy/klum-ast.base-conventions.gradle').text
        assertContains(baseConventions, 'gradle.taskGraph.hasTask(":publishCompleteKlumAstProduct")',
                'protected complete-product publication must require Maven signatures')
        assertContains(baseConventions, 'useInMemoryPgpKeys(signingKey, signingPassword)',
                'protected in-memory signing credentials must configure a Gradle signatory')

        def localEntryTask = project.tasks.named('renderLocalDocumentation').get()
        def localPreviewTask = project.tasks.named('previewLocalDocumentation').get()
        def localVerifyTask = project.tasks.named('verifyLocalDocumentationSite').get()
        def localRenderTask = project.tasks.named('renderLocalDocumentationFiles', RenderVersionedDocumentationTask).get()
        Set<String> localRenderDependencies = localRenderTask.taskDependencies.getDependencies(localRenderTask)*.path as Set
        assertTrue(localEntryTask.taskDependencies.getDependencies(localEntryTask)*.name.contains('verifyLocalDocumentationSite'),
                'local documentation entry point must verify the rendered site')
        assertTrue(localVerifyTask.taskDependencies.getDependencies(localVerifyTask)*.name.contains('renderLocalDocumentationFiles'),
                'local documentation verification must require the local render')
        assertTrue(localPreviewTask.taskDependencies.getDependencies(localPreviewTask)*.name.contains('renderLocalDocumentationFiles'),
                'local documentation preview must require the same local render')
        assertTrue(localRenderDependencies.contains(':cleanLocalDocumentationOutput'),
                'local documentation rendering must clear its dedicated output first')
        assertTrue(localRenderDependencies.containsAll(expectedDependencies),
                'local documentation rendering must include every allowed module Javadoc task')
        assertTrue(localRenderTask.documentationVersion.get() == '4.0.0-tracer', 'local documentation version must default to 4.0.0-tracer')
        assertTrue(localRenderTask.status.get() == 'tracer', 'local documentation status must default to tracer')
        assertTrue(localRenderTask.brandingManifestPath.get() == 'docs/branding/season-4-klumast.json',
                'local documentation must default to the Season 4 branding manifest')
        assertTrue(localRenderTask.objectDirectory.get().asFile.canonicalFile == project.rootDir.canonicalFile,
                'local documentation must default to the repository object directory')
        assertTrue(localRenderTask.outputDirectory.get().asFile.canonicalFile ==
                new File(project.buildDir, 'versioned-documentation/local-review').canonicalFile,
                'local documentation must default to its dedicated review output')
        assertTrue(localRenderTask.revision.get() == git(project.rootDir, ['rev-parse', 'HEAD']).trim(),
                'local documentation revision must default to the full current HEAD')
        assertTrue(localRenderTask.rendererRevision.get() == localRenderTask.revision.get(),
                'local renderer revision must default to the rendered revision')
        def rootProject = project
        VersionedDocumentationRenderer.MODULE_REPRESENTATIVE_JAVADOCS.each { String module, String representativeType ->
            def javadocTask = rootProject.project(":$module").tasks.named('javadoc').get()
            VerifyVersionedDocumentationRendererTask.assertTrue(new File(javadocTask.destinationDir, representativeType).file, "standard $module Javadocs must expose a representative public type")
            VerifyVersionedDocumentationRendererTask.assertTrue(javadocTask.source.files.every { !it.name.endsWith('_DSL.java') }, "standard $module Javadocs must exclude IDE source mirrors")
        }

        assertTrue(project.tasks.findByName('gitPublishPush')?.description?.contains('fails closed'),
                'Former mutable wiki publisher must be registered as a fail-closed task')
        def pendingTask = project.tasks.named('preparePendingDocumentationStage').get()
        assertTrue(pendingTask.taskDependencies.getDependencies(pendingTask)*.name.contains('verifyRenderedDocumentationSite'),
                'protected pending preparation must require the HTTP presentation and link crawl')
        String pagesWorkflow = new File(project.rootDir, '.github/workflows/publish-pending-documentation.yml').text
        int workflowCallStart = pagesWorkflow.indexOf('  workflow_call:\n')
        int workflowCallInputs = pagesWorkflow.indexOf('    inputs:\n', workflowCallStart)
        assertTrue(workflowCallStart >= 0 && workflowCallInputs > workflowCallStart,
                'the reusable Pages workflow must declare a workflow_call input contract')
        String pagesWorkflowCallContract = pagesWorkflow.substring(workflowCallStart, workflowCallInputs)
        assertContains(pagesWorkflow, 'pending/$RELEASE_VERSION/$EXPECTED_COMMIT/', 'pending Pages path must be version and SHA scoped')
        assertContains(pagesWorkflow, 'test ! -e "pages/$expected_path"', 'existing immutable pending Pages paths must be rejected')
        assertContains(pagesWorkflow, 'DOCUMENTATION_PAGES_READY', 'pending Pages must fail closed before the rehearsal/configuration gate')
        assertContains(pagesWorkflow, 'test -f pages/.nojekyll', 'protected Pages ledger must be pre-initialized for static HTML')
        assertContains(pagesWorkflow, 'actions/upload-pages-artifact@7b1f4a764d45c48632c6b24a0339c27f5614fb0b', 'read-back-verified gh-pages bytes must become the Pages artifact')
        assertContains(pagesWorkflow, 'actions/deploy-pages@cd2ce8fcbc39b97be8ca5fce6e763baed58fa128', 'official Pages deployment must precede artifact publication')
        assertContains(pagesWorkflow, 'deployment_commit', 'gh-pages commit identity must remain separate from Pages deployment identity')
        assertContains(pagesWorkflow, 'name: documentation-pages-writer', 'only the protected Pages writer environment may receive writer credentials')
        assertContains(pagesWorkflow, 'name: documentation-pages', 'the Pages deployment environment must remain separate from the writer')
        assertTrue(!pagesWorkflowCallContract.contains('PAGES_WRITER_APP_ID') &&
                !pagesWorkflowCallContract.contains('PAGES_WRITER_APP_PRIVATE_KEY'),
                'environment-owned writer secrets must not be required from the reusable workflow caller')
        assertContains(pagesWorkflow, 'actions/create-github-app-token@d72941d797fd3113feb6b93fd0dec494b13a2547', 'gh-pages writes must use the dedicated Pages writer App')
        assertContains(pagesWorkflow, 'PAGES_WRITER_TOKEN', 'the dedicated App token must be used only for gh-pages writes')
        assertTrue(!pagesWorkflow.contains('git push origin HEAD:gh-pages'), 'the workflow token must not write the Pages ledger directly')
        assertContains(pagesWorkflow, '"-Prelease.stage=$RELEASE_STAGE"', 'pending documentation must configure its exact protected release stage without Nebula tagging')
        assertContains(pagesWorkflow, '"-Prelease.version=$RELEASE_VERSION"', 'pending documentation must configure its exact protected release version without Nebula tagging')
        assertTrue(!pagesWorkflow.contains('./gradlew "$RELEASE_STAGE"'), 'pending documentation must not invoke Nebula tag-producing lifecycle tasks')
        String stagedCopy = 'cp -a "build/pending-documentation/$RELEASE_VERSION/." "pages/$EXACT_PATH"'
        String stagedManifest = 'staged_manifest="build/pending-documentation/$RELEASE_VERSION/site-manifest.json"'
        String fetchedManifest = 'git -C pages show "FETCH_HEAD:${EXACT_PATH}site-manifest.json" > "$written_manifest"'
        String manifestComparison = 'cmp -- "$staged_manifest" "$written_manifest"'
        assertContains(pagesWorkflow, stagedCopy, 'the rendered pending snapshot must be copied into the ledger')
        assertContains(pagesWorkflow, fetchedManifest, 'the manifest comparison must read the fetched ledger commit')
        assertTrue(!pagesWorkflow.contains('mv "build/pending-documentation/$RELEASE_VERSION/."'),
                'the staged pending snapshot must remain available for read-back verification')
        assertTrue(pagesWorkflow.indexOf(stagedCopy) < pagesWorkflow.indexOf(stagedManifest),
                'the staged snapshot must be copied before its manifest is retained for read-back')
        assertTrue(pagesWorkflow.indexOf('test "$(git -C pages rev-parse FETCH_HEAD)" = "$DEPLOYMENT_COMMIT"') < pagesWorkflow.indexOf(manifestComparison),
                'the fetched ledger identity must be verified before its manifest is compared')
        assertTrue(pagesWorkflow.indexOf(fetchedManifest) < pagesWorkflow.indexOf(manifestComparison),
                'the manifest comparison must use bytes read from the fetched ledger')
        assertTrue(pagesWorkflow.indexOf(stagedManifest) < pagesWorkflow.indexOf(manifestComparison),
                'the staged manifest must remain available until post-push byte comparison completes')
        assertTrue(!pagesWorkflow.contains('publishCompleteKlumAstProduct'), 'Pages workflow must not publish artifacts')
        assertContains(pagesWorkflow, 'git/ref/tags/v$RELEASE_VERSION',
                'the pending Pages workflow must check release tags through the authoritative remote API')
        assertContains(pagesWorkflow, 'releases/tags/v$RELEASE_VERSION',
                'the pending Pages workflow must check GitHub releases through the authoritative remote API')
        assertTrue(!pagesWorkflow.contains('git show-ref --verify'),
                'the pending Pages workflow must not decide release identity from runner-local Git refs')
        String pagesProbeWorkflow = new File(project.rootDir, '.github/workflows/probe-pages-deployment.yml').text
        assertContains(pagesProbeWorkflow, 'workflow_dispatch:',
                'the Pages probe must require an explicit manual dispatch')
        assertContains(pagesProbeWorkflow, 'test "$GITHUB_REF" = refs/heads/master',
                'the Pages probe must reject dispatches outside master')
        assertContains(pagesProbeWorkflow, 'test "$(git rev-parse origin/master)" = "$EXPECTED_SOURCE_COMMIT"',
                'the Pages probe must require the current trusted master commit')
        assertContains(pagesProbeWorkflow, 'name: documentation-pages',
                'the Pages probe must use the protected Pages deployment environment')
        assertContains(pagesProbeWorkflow, 'pages-deployment-probe-${{ github.run_id }}-${{ github.run_attempt }}',
                'the Pages probe artifact must be unambiguously diagnostic')
        assertContains(pagesProbeWorkflow, 'actions/upload-pages-artifact@7b1f4a764d45c48632c6b24a0339c27f5614fb0b',
                'the Pages probe must upload through the pinned official Pages artifact action')
        assertContains(pagesProbeWorkflow, 'actions/deploy-pages@cd2ce8fcbc39b97be8ca5fce6e763baed58fa128',
                'the Pages probe must deploy through the pinned official Pages action')
        assertContains(pagesProbeWorkflow, 'test "$(git -C pages rev-parse FETCH_HEAD)" = "$EXPECTED_LEDGER_COMMIT"',
                'the Pages probe must prove the remote ledger stays at its snapshot')
        assertContains(pagesProbeWorkflow, 'One probe is diagnostic evidence only',
                'the Pages probe must not overstate one deployment result as a root-cause finding')
        assertTrue(!pagesProbeWorkflow.contains('PAGES_WRITER_'),
                'the Pages probe must not receive gh-pages writer credentials')
        assertTrue(!pagesProbeWorkflow.contains('publishCompleteKlumAstProduct'),
                'the Pages probe must not publish artifacts')
        assertTrue(!pagesProbeWorkflow.contains('pending-rejected/'),
                'the Pages probe must not create rejected release paths')
        assertTrue(!pagesProbeWorkflow.contains('timeout:'),
                'the Pages probe must not override the Pages action polling ceiling')
        String releaseWorkflow = new File(project.rootDir, '.github/workflows/release.yml').text
        int identityPreflightStart = releaseWorkflow.indexOf('  verify-release-identity-available:\n')
        int pendingStageStart = releaseWorkflow.indexOf('  stage-pending-documentation:\n')
        int publishStageStart = releaseWorkflow.indexOf('  publish:\n', pendingStageStart)
        assertTrue(identityPreflightStart >= 0 && pendingStageStart > identityPreflightStart && publishStageStart > pendingStageStart,
                'artifact workflow must preflight remote release identity before pending documentation and publication')
        String identityPreflight = releaseWorkflow.substring(identityPreflightStart, pendingStageStart)
        String pendingDocumentationStage = releaseWorkflow.substring(pendingStageStart, publishStageStart)
        String publicationStage = releaseWorkflow.substring(publishStageStart)
        assertContains(identityPreflight, 'git/ref/tags/v$RELEASE_VERSION',
                'the preflight must check release tags through the authoritative remote API')
        assertContains(identityPreflight, 'releases/tags/v$RELEASE_VERSION',
                'the preflight must check GitHub releases through the authoritative remote API')
        assertTrue(!identityPreflight.contains('git show-ref --verify'),
                'the preflight must not decide release identity from runner-local Git refs')
        assertContains(pendingDocumentationStage, 'uses: ./.github/workflows/publish-pending-documentation.yml',
                'the pending documentation stage must call the reusable Pages workflow')
        assertContains(pendingDocumentationStage, 'needs: [validate-release-input, verify-release-identity-available]',
                'the pending documentation stage must remain unreachable before remote identity preflight succeeds')
        assertContains(pendingDocumentationStage, 'secrets: inherit',
                'the pending documentation stage must forward the reusable Pages workflow secret contract')
        assertContains(releaseWorkflow, 'contents: read', 'the reusable Pages workflow caller must not receive ledger-write authority')
        assertContains(releaseWorkflow, 'pages: write', 'reusable Pages workflow caller must grant Pages deployment authority')
        assertContains(releaseWorkflow, 'id-token: write', 'reusable Pages workflow caller must grant OIDC authority')
        assertContains(releaseWorkflow, 'DOCUMENTATION_MANIFEST_SHA256', 'artifact workflow must recheck the pending manifest handoff')
        assertContains(releaseWorkflow, './gradlew publishCompleteKlumAstProduct', 'artifact publication must use the guarded complete-product task without Nebula tagging')
        assertContains(releaseWorkflow, '-Prelease.version=${{ inputs.version }}', 'artifact publication must configure the exact protected release version')
        assertContains(releaseWorkflow, '-Prelease.stage=${{ inputs.stage }}', 'artifact publication must configure the exact protected release stage')
        assertTrue(!releaseWorkflow.contains('./gradlew ${{ inputs.stage }}'), 'artifact publication must not invoke Nebula tag-producing lifecycle tasks')
        assertContains(publicationStage, 'git/ref/tags/v$RELEASE_VERSION',
                'publication must recheck release tags through the authoritative remote API')
        assertContains(publicationStage, 'releases/tags/v$RELEASE_VERSION',
                'publication must recheck GitHub releases through the authoritative remote API')
        assertTrue(!publicationStage.contains('git show-ref --verify'),
                'publication must not decide release identity from runner-local Git refs')
        assertTrue(releaseWorkflow.indexOf('needs: [validate-release-input, verify-release-identity-available, stage-pending-documentation]') <
                releaseWorkflow.indexOf('publishCompleteKlumAstProduct'), 'artifact publication must remain unreachable before pending documentation success')

        String publicProofWorkflow = new File(project.rootDir, '.github/workflows/verify-public-release.yml').text
        assertContains(publicProofWorkflow, 'stage:', 'public proof must bind the protected release stage')
        assertContains(publicProofWorkflow, 'commit:', 'public proof must bind the full source SHA')
        assertContains(publicProofWorkflow, 'public-release-proof-${{ github.run_id }}.${{ github.run_attempt }}',
                'public proof must retain its immutable run and attempt identity')
        assertContains(publicProofWorkflow, 'outcome: "publicly-resolved"',
                'public proof must name its successful public-resolution outcome')

        String promotionWorkflow = new File(project.rootDir, '.github/workflows/promote-public-documentation.yml').text
        assertContains(promotionWorkflow, 'workflow_dispatch:', 'documentation promotion must require an explicit manual dispatch')
        assertContains(promotionWorkflow, 'group: pending-documentation-gh-pages',
                'promotion must serialize gh-pages mutations with the pending-stage writer')
        assertContains(promotionWorkflow, 'test "$GITHUB_REF" = refs/heads/master',
                'documentation promotion must reject a dispatch outside master')
        assertContains(promotionWorkflow, 'name: documentation-pages-writer',
                'only the protected Pages writer environment may receive documentation writer credentials')
        assertContains(promotionWorkflow, 'name: documentation-pages',
                'public documentation deployment must remain separate from the writer environment')
        assertContains(promotionWorkflow, 'public-release-proof-$PROOF_RUN.$PROOF_ATTEMPT',
                'promotion must consume the exact public-proof run and attempt artifact')
        assertContains(promotionWorkflow, 'pending/$RELEASE_VERSION/$EXPECTED_COMMIT',
                'promotion must require matching immutable pending-stage evidence')
        assertContains(promotionWorkflow, 'documentationStatus=$public_status',
                'promotion must re-render public status chrome rather than expose pending content')
        assertContains(promotionWorkflow, 'aliases=\'["preview"]\'',
                'candidate promotion must advance only the preview alias')
        assertContains(promotionWorkflow, 'aliases="[\\"stable\\",\\"$line\\"]"',
                'final promotion must advance stable and its maintained-line alias')
        assertContains(promotionWorkflow, 'promotions/$RELEASE_VERSION/$EXPECTED_COMMIT/$PROOF_IDENTITY.json',
                'promotion must retain an immutable auditable record')
        assertContains(promotionWorkflow, 'already_promoted=true',
                'an identical completed promotion must be accepted without mutation')
        assertTrue(promotionWorkflow.indexOf('already_promoted == \'false\'') <
                promotionWorkflow.indexOf('Upload the read-back-verified promoted ledger'),
                'an identical promotion must upload the existing ledger for a safe Pages redeployment')
        assertContains(promotionWorkflow, 'test ! -e "pages/$RELEASE_VERSION"',
                'an existing public exact tree without a matching record must fail closed')
        assertTrue(!promotionWorkflow.contains('publishCompleteKlumAstProduct'),
                'documentation promotion must not publish artifacts')
        assertTrue(!promotionWorkflow.contains('SONATYPE_') && !promotionWorkflow.contains('SIGNING_') && !promotionWorkflow.contains('GRADLE_PUBLISH_'),
                'documentation promotion must not receive artifact-publishing credentials')
    }

    private static void initializeFixture(File repository) {
        git(repository, ['init'])
        git(repository, ['config', 'user.email', 'fixtures@example.invalid'])
        git(repository, ['config', 'user.name', 'Documentation fixtures'])
        new File(repository, 'docs/user/img').mkdirs()
        new File(repository, 'docs/user/Guide').mkdirs()
        new File(repository, 'docs/branding').mkdirs()
        new File(repository, 'wiki').mkdirs()
        new File(repository, '.gitignore').text = '*/build/\n'
        new File(repository, 'docs/user/Home.md').text = '''# Current documentation

[Nested guide](Guide/Nested.md), [Gradle onboarding](Gradle-Onboarding.md), [same-page heading](#Same%20heading), and [[Changelog]].

![Local logo](img/klumlogo.png)

![Season lockup](img/klumast-season-4-documentation.svg "season-lockup")

| Name | Value |
| --- | --- |
| Renderer | static |

## Same heading

## Same heading

## Überblick!

<unsafe-card>must not become markup</unsafe-card>

```xml
<dependencies><dependency /></dependencies>
```
'''
        new File(repository, 'docs/user/Guide/Nested.md').text = '''# Nested current documentation

[Home](../Home.md), [home heading](../Home.md#Same%20heading), [[Home#same-heading|Current documentation]], and [source skill](../../../agent-skills/example/SKILL.md).

```groovy
plugins {
    id 'com.blackbuild.klum-ast-schema' version '<klum-version>'
}

dependencies {
    api 'com.blackbuild.klum.ast:klum-ast-runtime:<klum-version>'
}
```
'''
        new File(repository, 'docs/user/Gradle-Onboarding.md').text = '# Gradle onboarding\n\nCurrent setup guidance.\n'
        new File(repository, 'docs/user/Builder-First-Migration.md').text = '# Builder-first migration\n\nFixture migration.\n'
        new File(repository, 'docs/user/_Sidebar.md').text = '* [[Home]]\n* [[Gradle Onboarding]]\n* [[Guide/Nested|Nested]]\n* [[Changelog]]\n'
        new File(repository, 'docs/user/_Footer.md').text = '*KlumAST* — fixture footer\n'
        new File(repository, 'docs/user/_Aliases.json').text = JsonOutput.prettyPrint(JsonOutput.toJson([
                'Getting-Started.md': 'Gradle-Onboarding.md'
        ])) + '\n'
        new File(repository, 'CHANGES.md').text = '# Changelog\n\nFixture changes. See [migration](docs/user/Builder-First-Migration.md).\n'
        byte[] logo = 'fixture-logo'.getBytes(StandardCharsets.UTF_8)
        new File(repository, 'docs/user/img/klumlogo.png').bytes = logo
        new File(repository, 'docs/user/img/klumast-season-4-documentation.svg').text = '<svg xmlns="http://www.w3.org/2000/svg"/>'
        new File(repository, 'docs/user/img/klumast-season-4-documentation-compact.svg').text = '<svg xmlns="http://www.w3.org/2000/svg"/>'
        new File(repository, 'wiki/Home.md').text = '# Historical home\n\nHistorical landing content.\n'
        new File(repository, 'wiki/Legacy.md').text = '# Legacy documentation\n\nHistorical content for <klum-version>.\n'
        new File(repository, 'wiki/_Sidebar.md').text = '* [[Home]]\n* [[Legacy]]\n* [[Changelog]]\n'
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

    private static void assertNoAuthoredPresentationHtml(File repository) {
        new File(repository, 'docs/user').eachFileRecurse { File file ->
            if (file.file && file.name.endsWith('.md') && StaticDocumentationPageRenderer.containsAuthoredHtml(file.getText(StandardCharsets.UTF_8.name())))
                throw new GradleException("Current user documentation contains authored presentation HTML: ${repository.toPath().relativize(file.toPath())}")
        }
        git(repository, ['ls-tree', '-r', '--name-only', 'v3.0.1', '--', 'wiki']).readLines().findAll { it.endsWith('.md') }.each { String path ->
            String content = git(repository, ['show', "v3.0.1:$path"])
            List<String> html = StaticDocumentationPageRenderer.authoredHtmlLiterals(content)
            if (html.any { !(it ==~ /<[A-Z][A-Za-z0-9]*>/) })
                throw new GradleException("v3.0.1 documentation contains authored presentation HTML beyond generic type notation: $path")
        }
    }

    private static void assertNoLegacyKlumVersionPlaceholder(File repository) {
        new File(repository, 'docs/user').eachFileRecurse { File file ->
            if (file.file && file.name.endsWith('.md') && file.text.contains('<matching-klum-version>'))
                throw new GradleException("Current user documentation retains the legacy KlumAST version token: ${repository.toPath().relativize(file.toPath())}")
        }
    }

    private static Map<String, File> initializeModuleJavadocs(File repository) {
        VersionedDocumentationRenderer.MODULE_REPRESENTATIVE_JAVADOCS.collectEntries { String module, String representativeType ->
            File moduleRoot = new File(repository, "$module/build/docs/javadoc")
            new File(moduleRoot, 'index.html').with {
                parentFile.mkdirs()
                text = "<html><a href=\"$representativeType#%3Cinit%3E()\">$module</a>" +
                        '<a href="constant-values.html#com.example">constants</a></html>'
            }
            new File(moduleRoot, representativeType).with {
                parentFile.mkdirs()
                text = "<html><section id=\"&lt;init&gt;()\">$module representative public type</section></html>"
            }
            new File(moduleRoot, 'constant-values.html').text =
                    '<html><section id="com.example.fixture">fixture constants</section></html>'
            [(module): moduleRoot]
        }
    }

    static void render(File repository, File output, String revision, String version, String status, Map<String, File> moduleJavadocs, String archiveLink = '/archive/', String navigationMarkdown = '', String landingSourcePath = '') {
        Map<String, Object> inputs = [
                objectDirectory      : repository,
                outputDirectory      : output,
                revision             : revision,
                rendererRevision     : revision,
                version              : version,
                status               : status,
                archiveLink          : archiveLink,
                navigationMarkdown   : navigationMarkdown,
                landingSourcePath    : landingSourcePath,
                archivedVersions     : ['2.2.0'],
                moduleJavadocs       : moduleJavadocs]
        if (status != 'archived')
            inputs.brandingManifestPath = 'docs/branding/season-4-klumast.json'
        VersionedDocumentationRenderer.render(inputs)
    }

    private static void expectFailure(String description, Closure action) {
        try {
            action.call()
        } catch (IllegalArgumentException ignored) {
            return
        } catch (GradleException ignored) {
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

    private static boolean containsFileEnding(File directory, String suffix) {
        boolean found = false
        directory.eachFileRecurse { File file -> if (file.file && file.name.endsWith(suffix)) found = true }
        found
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
