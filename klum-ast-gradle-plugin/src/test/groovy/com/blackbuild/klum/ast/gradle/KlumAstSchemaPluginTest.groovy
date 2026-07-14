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
package com.blackbuild.klum.ast.gradle

import com.blackbuild.annodocimal.plugin.AnnoDocimalPlugin
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import spock.lang.Specification

class KlumAstSchemaPluginTest extends Specification {

    Project project
    String version

    def setup() {
        project = ProjectBuilder.builder().build()
        version = PluginHelper.determineOwnVersion()
    }

    def "basic plugin configuration"() {
        given:
        project = ProjectBuilder.builder().build()
        boolean mirrorTaskRealized = false
        project.tasks.withType(CreateKlumDslSourceMirrors).configureEach { mirrorTaskRealized = true }

        when:
        project.getPluginManager().apply(KlumAstSchemaPlugin)

        then:
        project.plugins.hasPlugin(AnnoDocimalPlugin)
        project.plugins.hasPlugin(IdeaPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)
        project.plugins.hasPlugin(GroovyPlugin)

        and:
        project.configurations.getByName("compileOnly").dependencies.any { it.name == "klum-ast" && it.group == "com.blackbuild.klum.ast" && it.version == null }
        project.configurations.getByName("api").dependencies.any { it.name == "klum-ast-runtime" && it.group == "com.blackbuild.klum.ast" && it.version == null }

        when:
        def java = project.getExtensions().getByType(JavaPluginExtension.class)

        then:
        project.configurations.sourcesElements
        project.configurations.javadocElements

        and:
        def mirrors = project.tasks.named("createKlumDslSourceMirrors", CreateKlumDslSourceMirrors)
        !mirrorTaskRealized

        when:
        def mirrorTask = mirrors.get()

        then:
        mirrorTaskRealized
        def mirrorDirectory = mirrorTask.outputDirectory.get().asFile
        def main = java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        def idea = project.extensions.getByType(IdeaModel)
        mirrorTask.group == 'klum'
        !main.java.sourceDirectories.files.contains(mirrorDirectory)
        !main.groovy.sourceDirectories.files.contains(mirrorDirectory)
        idea.module.sourceDirs.contains(mirrorDirectory)
        idea.module.generatedSourceDirs.contains(mirrorDirectory)
    }

    def "publications are created if maven publish is applied"() {
        given:
        project = ProjectBuilder.builder().build()

        when:
        project.getPluginManager().apply(KlumAstSchemaPlugin)
        project.getPluginManager().apply("maven-publish")

        then:
        project.publishing.publications.size() == 1
    }

}
