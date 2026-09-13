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

import com.blackbuild.annodocimal.plugin.AnnoDocimalGroovyPlugin
import com.blackbuild.annodocimal.plugin.SourceProjectionTask
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.tasks.GenerateMavenPom
import org.gradle.api.tasks.SourceSet
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import spock.lang.Issue
import spock.lang.Specification

import java.util.regex.Pattern

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
        project.tasks.withType(SourceProjectionTask).configureEach { mirrorTaskRealized = true }

        when:
        project.getPluginManager().apply(KlumAstSchemaPlugin)

        then:
        project.plugins.hasPlugin(AnnoDocimalGroovyPlugin)
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
        def mirrors = project.tasks.named("createKlumDslSourceMirrors", SourceProjectionTask)
        !mirrorTaskRealized

        when:
        def mirrorTask = mirrors.get()

        then:
        mirrorTaskRealized
        def mirrorDirectory = mirrorTask.outputDirectory.get().asFile
        def gdslDirectory = KlumDslGdslMaterializationPlugin.outputDirectory(project).get().asFile
        def main = java.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
        def idea = project.extensions.getByType(IdeaModel)
        mirrorTask.group == 'klum'
        !main.java.sourceDirectories.files.contains(mirrorDirectory)
        !main.groovy.sourceDirectories.files.contains(mirrorDirectory)
        idea.module.sourceDirs.contains(mirrorDirectory)
        idea.module.generatedSourceDirs.contains(mirrorDirectory)
        !main.java.sourceDirectories.files.contains(gdslDirectory)
        !main.groovy.sourceDirectories.files.contains(gdslDirectory)
        idea.module.resourceDirs.contains(gdslDirectory)
        idea.module.generatedSourceDirs.contains(gdslDirectory)
        mirrorTask.taskDependencies.getDependencies(mirrorTask).any {
            it.name == KlumDslGdslMaterializationPlugin.TASK_NAME
        }
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

    @Issue('552')
    def "schema publication metadata exposes the KlumAST runtime"() {
        given:
        project.group = 'com.example.platform'
        project.version = '1.4.2'
        project.pluginManager.apply(KlumAstSchemaPlugin)
        project.pluginManager.apply('maven-publish')
        MavenPublication publication = project.extensions
                .getByType(PublishingExtension)
                .publications
                .getByName('mavenJava') as MavenPublication
        File pomFile = File.createTempFile('klum-schema-', '.pom')
        GenerateMavenPom generatePom = project.tasks.create('generateFixturePom', GenerateMavenPom)
        generatePom.pom = publication.pom
        generatePom.destination = pomFile

        when:
        generatePom.doGenerate()
        String pom = pomFile.text

        then:
        pom =~ /(?s)<dependency>\s*<groupId>com\.blackbuild\.klum\.ast<\/groupId>\s*<artifactId>klum-ast-bom<\/artifactId>\s*<version>${Pattern.quote(version)}<\/version>\s*<type>pom<\/type>\s*<scope>import<\/scope>\s*<\/dependency>/
        pom =~ /(?s)<dependency>\s*<groupId>com\.blackbuild\.klum\.ast<\/groupId>\s*<artifactId>klum-ast-runtime<\/artifactId>\s*<scope>compile<\/scope>\s*<\/dependency>/

        cleanup:
        pomFile.delete()
    }

}
