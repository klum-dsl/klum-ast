/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2025 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform
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
        clazz.Create.With {

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

        def GradleProject = getClass("GradleProject")
        def MavenProject = getClass("MavenProject")
        clazz.Create.With {

            projects {
                project(MavenProject, "demo") {
                    url "$github/x/y"

                    goals "clean", "compile"
                    profile "ci"
                    profile "!developer"

                    cliOptions "-X -pl :abc".split(" ")
                }
                project(GradleProject, "demo2") {
                    url "$github/a/b"

                    tasks "build"
                }
            }
        }
        then:
        noExceptionThrown()
    }


}