package com.blackbuild.klum.ast.util.layer3

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec

// is in klum-ast, because the tests are a lot better readable using the actual DSL.
class StructureUtilDSLTest extends AbstractDSLSpec {

    def "Root path is correctly returned"() {
        createClass('''
            package tmp

import com.blackbuild.groovy.configdsl.transform.Owner

            @DSL
            class Config {

                Map<String, Project> projects
                boolean debugMode
                List<String> options


            }

            @DSL
            class Project {
                @Key String name
                @Owner Config config
                String url

                MavenConfig mvn
            }

            @DSL
            class MavenConfig {
                @Owner Project project
            
                List<String> goals
                List<String> profiles
                List<String> cliOptions
            }
        ''')

        when:
        def github = "http://github.com"
        instance = create("tmp.Config") {

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
                project("demo-2") {
                    url "$github/a/b"

                    mvn {
                        goals "compile"
                        profile "ci"
                    }
                }
            }
        }

        then:
        StructureUtil.getFullPath(instance.projects.demo.mvn, "<root>") == "<root>.projects.demo.mvn"
        StructureUtil.getFullPath(instance.projects['demo-2'].mvn, "<root>") == "<root>.projects.'demo-2'.mvn"
    }
}
