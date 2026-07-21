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
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
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
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Validates the documentation-owned release identity and emits the deliberately
 * small handoff which #488 must recheck before it can publish artifacts.
 */
abstract class PreparePendingDocumentationStageTask extends DefaultTask {

    @Input abstract Property<String> getStage()
    @Input abstract Property<String> getExpectedVersion()
    @Input abstract Property<String> getExpectedRevision()
    @Input abstract Property<String> getNebulaVersion()
    @InputDirectory abstract DirectoryProperty getObjectDirectory()
    @InputDirectory abstract DirectoryProperty getRenderedDocumentationDirectory()
    @OutputFile abstract RegularFileProperty getHandoffFile()

    @TaskAction
    void prepare() {
        String releaseStage = stage.get()
        String version = expectedVersion.get()
        String revision = expectedRevision.get()
        File repository = objectDirectory.get().asFile
        validateIdentity(repository, releaseStage, version, revision, nebulaVersion.get())

        File manifest = new File(renderedDocumentationDirectory.get().asFile, "$version/source-manifest.json")
        if (!manifest.file)
            fail("Pending documentation render did not produce its source manifest for $version")
        Map<String, ?> parsed = new JsonSlurper().parseText(manifest.getText(StandardCharsets.UTF_8.name())) as Map<String, ?>
        if (parsed.documentation != [version: version, status: 'pending'])
            fail('Pending documentation manifest does not describe the requested pending snapshot')
        if ((parsed.source as Map)?.revision != revision)
            fail('Pending documentation manifest does not describe the requested source revision')
        if (releaseStage == 'final' && !((parsed.branding as Map)?.finalApproval instanceof Map))
            fail('Final pending documentation manifest does not retain the exact branding-owner approval')

        Map<String, Object> handoff = [
                schemaVersion  : 1,
                stage          : releaseStage,
                version        : version,
                sha            : revision,
                exactPath      : "pending/$version/$revision/",
                manifestSha256 : sha256(manifest.bytes)
        ]
        File output = handoffFile.get().asFile
        output.parentFile.mkdirs()
        Files.write(output.toPath(), (JsonOutput.prettyPrint(JsonOutput.toJson(handoff)) + '\n').getBytes(StandardCharsets.UTF_8))
    }

    static void validateIdentity(File repository, String stage, String version, String revision, String nebulaVersion) {
        if (!(stage in ['candidate', 'final']))
            fail("Pending documentation stage must be candidate or final: $stage")
        if (!(revision ==~ /[0-9a-f]{40}/))
            fail("Pending documentation requires a full lowercase commit SHA: $revision")
        if (stage == 'candidate' && !(version ==~ /\d+\.\d+\.\d+-rc\.[1-9]\d*/))
            fail("Candidate pending documentation requires X.Y.Z-rc.N: $version")
        if (stage == 'final' && !(version ==~ /\d+\.\d+\.\d+/))
            fail("Final pending documentation requires X.Y.Z: $version")
        if (nebulaVersion != version)
            fail("Nebula version $nebulaVersion does not match pending documentation version $version")
        if (git(repository, ['status', '--porcelain']).trim())
            fail('Pending documentation requires a clean checked-out source revision')
        if (git(repository, ['rev-parse', '--verify', "${revision}^{commit}"]).trim() != revision)
            fail("Pending documentation revision must resolve to its supplied full SHA: $revision")
        if (git(repository, ['rev-parse', 'HEAD']).trim() != revision)
            fail('Pending documentation must render the checked-out requested revision')
        git(repository, ['merge-base', '--is-ancestor', revision, 'origin/master'])
    }

    private static String git(File directory, List<String> arguments) {
        Process process = new ProcessBuilder((['git'] + arguments).collect { it.toString() }).directory(directory).redirectErrorStream(true).start()
        String output = process.inputStream.getText(StandardCharsets.UTF_8.name())
        if (process.waitFor() != 0)
            fail("Git validation failed (${arguments.join(' ')}): ${output.trim()}")
        output
    }

    private static String sha256(byte[] content) {
        MessageDigest.getInstance('SHA-256').digest(content).encodeHex().toString()
    }

    private static void fail(String message) {
        throw new GradleException(message)
    }
}
