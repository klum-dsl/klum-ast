package com.blackbuild.klum.ast.gradle


import org.gradle.api.Project
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class KlumAstModelPluginTest extends Specification {

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
        project.getPluginManager().apply(KlumAstModelPlugin)

        then:
        project.plugins.hasPlugin(JavaLibraryPlugin)
        project.plugins.hasPlugin(GroovyPlugin)

        and:
        project.configurations.sourcesElements
        project.configurations.javadocElements
    }

    def "schema is translated to dependencies"() {
        given:
        project = ProjectBuilder.builder().build()

        when:
        project.getPluginManager().apply(KlumAstModelPlugin)
        project.klumModel {
            schemas {
                schema "bla:blub:1.0"
                schema "bla:bli:2.0"
            }
        }

        then:
        project.configurations.api.allDependencies.any { it.group == "bla" && it.name == "blub" && it.version == "1.0" }
        project.configurations.api.allDependencies.any { it.group == "bla" && it.name == "bli" && it.version == "2.0" }
    }

    def "model descriptors are created"() {
        given:
        project = ProjectBuilder.builder().build()

        when:
        project.getPluginManager().apply(KlumAstModelPlugin)
        project.klumModel {
            topLevelScript "com.blackbuild.klum.demo.schema.Home", "model.MyHome"
            topLevelScript "com.blackbuild.klum.demo.schema.Dome", "model.MyDome"
        }
        CreateModelProperties task = project.tasks.getByName("createModelDescriptors")

        then:
        task.modelProperties.get() == [
            "com.blackbuild.klum.demo.schema.Home": "model.MyHome",
            "com.blackbuild.klum.demo.schema.Dome": "model.MyDome"
        ]

        when:
        ProcessResources processResources = project.tasks.getByName("processResources")
        def innerSpec = processResources.mainSpec.children.first()


        then:
        innerSpec.destinationDir.toString() == "META-INF/klum-model"
        innerSpec.sourcePaths.first().name == task.name
        innerSpec.sourcePaths.first().type == CreateModelProperties

    }

}
