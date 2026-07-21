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

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

abstract class RenderVersionedDocumentationTask extends DefaultTask {

    @Input abstract Property<String> getRevision()
    @Input abstract Property<String> getRendererRevision()
    @Input abstract Property<String> getDocumentationVersion()
    @Input abstract Property<String> getStatus()
    @Input abstract Property<String> getBrandingManifestPath()
    @Input abstract ListProperty<String> getArchivedVersions()
    @InputDirectory abstract DirectoryProperty getObjectDirectory()
    @InputFiles
    @PathSensitive(PathSensitivity.RELATIVE)
    abstract ConfigurableFileCollection getModuleJavadocs()
    @Internal abstract MapProperty<String, File> getModuleJavadocDirectories()
    @OutputDirectory abstract DirectoryProperty getOutputDirectory()

    @TaskAction
    void render() {
        VersionedDocumentationRenderer.render(
                objectDirectory      : objectDirectory.get().asFile,
                outputDirectory      : outputDirectory.get().asFile,
                revision             : revision.get(),
                rendererRevision     : rendererRevision.get(),
                version              : documentationVersion.get(),
                status               : status.get(),
                brandingManifestPath : brandingManifestPath.get(),
                archivedVersions     : archivedVersions.get(),
                moduleJavadocs       : moduleJavadocDirectories.get())
    }
}
