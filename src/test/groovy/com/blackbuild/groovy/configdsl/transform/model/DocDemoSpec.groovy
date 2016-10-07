package com.blackbuild.groovy.configdsl.transform.model
/**
 * Tests for the documentation examples
 */
class DocDemoSpec extends AbstractDSLSpec {


    def "first example"() {
        given:
        createClass('''
            package tmp

            @DSL
            class Config {

                Map<String, Project> projects
                boolean debugMode
                List<String> options


            }

            @DSL
            class Project {
                @Key String name
                String url

                MavenConfig mvn
            }

            @DSL
            class MavenConfig {
                List<String> goals
                List<String> profiles
                List<String> cliOptions
            }
        ''')

        when:
        def github = "http://github.com"
        clazz.create {

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
            @DSL
            class Config {
                Map<String, Project> projects
                boolean debugMode
                List<String> options
            }

            @DSL
            abstract class Project {
                @Key String name
                String url
            }

            @DSL
            class MavenProject extends Project{
                List<String> goals
                List<String> profiles
                List<String> cliOptions
            }

            @DSL
            class GradleProject extends Project{
                List<String> tasks
                List<String> options
            }
        ''')

        when:
        def github = "http://github.com"
        clazz.create {

            projects {
                project(getClass("MavenProject"), "demo") {
                    url "$github/x/y"

                    goals "clean", "compile"
                    profile "ci"
                    profile "!developer"

                    cliOptions "-X -pl :abc".split(" ")
                }
                project(getClass("GradleProject"), "demo2") {
                    url "$github/a/b"

                    tasks "build"
                }
            }
        }
        then:
        noExceptionThrown()
    }


}