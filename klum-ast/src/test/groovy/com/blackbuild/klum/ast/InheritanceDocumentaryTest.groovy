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
package com.blackbuild.klum.ast

import spock.lang.Issue
import spock.lang.See
import spock.lang.Tag

@Tag("documentary")
class InheritanceDocumentaryTest extends AbstractDSLSpec {

    @Issue("491")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Inheritance.md#choosing-a-derived-implementation")
    def "configures a derived project through an unkeyed field"() {
        given:
        createClass '''
            package pk

            @DSL
            class Config {
                Project project
            }

            @DSL
            class Project {
                String name
            }

            @DSL
            class MavenProject extends Project {
                List<String> mvnOpts
            }
        '''
        def mavenProject = getClass('pk.MavenProject')

        when:
        def config = clazz.Create.With {
            project(mavenProject) {
                name 'demo'
                mvnOpts 'a', 'b'
            }
        }

        then:
        mavenProject.isInstance(config.project)
        config.project.name == 'demo'
        config.project.mvnOpts == ['a', 'b']
    }

    @Issue("130")
    @See("https://github.com/klum-dsl/klum-ast/blob/master/docs/user/Inheritance.md#keyed-inheritance")
    def "configures a keyed derived project through an inherited key"() {
        given:
        createClass '''
            package pk

            @DSL
            class Config {
                Project project
            }

            @DSL
            class Project {
                @Key String name
            }

            @DSL
            class MavenProject extends Project {
                List<String> mvnOpts
            }
        '''
        def mavenProject = getClass('pk.MavenProject')

        when:
        def config = clazz.Create.With {
            project(mavenProject, 'demo') {
                mvnOpts 'a', 'b'
            }
        }

        then:
        mavenProject.isInstance(config.project)
        config.project.name == 'demo'
        config.project.mvnOpts == ['a', 'b']
    }
}
