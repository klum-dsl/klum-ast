package com.blackbuild.klum.ast.gradle

import com.blackbuild.annodocimal.plugin.AnnoDocimalPlugin
import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.testfixtures.ProjectBuilder
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

        when:
        project.getPluginManager().apply(KlumAstSchemaPlugin)

        then:
        project.plugins.hasPlugin(AnnoDocimalPlugin)
        project.plugins.hasPlugin(JavaLibraryPlugin)
        project.plugins.hasPlugin(GroovyPlugin)

        and:
        project.configurations.getByName("compileOnly").dependencies.any { it.name == "klum-ast" && it.group == "com.blackbuild.klum.ast" && it.version == version }
        project.configurations.getByName("implementation").dependencies.any { it.name == "klum-ast-runtime" && it.group == "com.blackbuild.klum.ast" && it.version == version }

        when:
        def java = project.getExtensions().getByType(JavaPluginExtension.class)

        then:
        project.configurations.sourcesElements
        project.configurations.javadocElements
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
