package com.blackbuild.groovy.configdsl.transform.model

import spock.lang.Specification


/**
 * Tests for the documentation examples
 */
class DocDemoSpec extends AbstractDSLSpec {


    def "first example"() {
        given:
        createClass('''
            package tmp

            @DSLConfig
            class Config {

                Map<String, Project> projects
                boolean debugMode
                List<String> options


            }

            @DSLConfig(key = "name")
            class Project {
                String name
                String url

                MavenConfig mvn
            }

            @DSLConfig
            class MavenConfig {
                List<String> goals
                List<String> profiles
                List<String> cliOptions
            }
        ''')

        when:
        def github = "http://github.com"
        def config = clazz.create {

            debugMode true

            options "demo", "fast"
            option "another"

            projects {
                project("demo") {
                    url "$github/x/y"

                    mvn {
                        goals "clean", "compile"
                        profile "ci"
                        profile "!developer"

                        cliOptions "-X -pl :abc".split(" ")
                    }
                }
                project("demo2") {
                    url "$github/a/b"

                    mvn {
                        goals "compile"
                        profile "ci"
                    }
                }
            }
        }
        then:
        noExceptionThrown()
    }

    def "second example with sublasses"() {
        given:
        createClass('''
            @DSLConfig
            class Config {
                @DSLField(alternatives=[MavenProject, GradleProject])
                Map<String, Project> projects
                boolean debugMode
                List<String> options
            }

            @DSLConfig(key = "name")
            abstract class Project {
                String name
                String url
            }

            @DSLConfig
            class MavenProject extends Project{
                List<String> goals
                List<String> profiles
                List<String> cliOptions
            }

            @DSLConfig
            class GradleProject extends Project{
                List<String> tasks
                List<String> options
            }
        ''')

        when:
        def github = "http://github.com"
        def config = clazz.create {

            projects {
                mavenProject("demo") {
                    url "$github/x/y"

                    goals "clean", "compile"
                    profile "ci"
                    profile "!developer"

                    cliOptions "-X -pl :abc".split(" ")
                }
                gradleProject("demo2") {
                    url "$github/a/b"

                    tasks "build"
                }
            }
        }
        then:
        noExceptionThrown()
    }


}