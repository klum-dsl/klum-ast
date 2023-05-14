/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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
