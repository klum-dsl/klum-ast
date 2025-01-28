/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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
package com.blackbuild.klum.ast.process

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec

class BreadcrumbDSLTest extends AbstractDSLSpec {

    def cleanup() {
        BreadcrumbCollector.INSTANCE.remove()
    }

    def "normal create sets breadcrumb"() {
        given:
        createClass '''import com.blackbuild.groovy.configdsl.transform.DSL
            @DSL class Foo {
                String name
            }
'''
        def path = null

        when:
        create("Foo") {
            path = BreadcrumbCollector.instance.fullPath
        }

        then:
        path == '$/Foo.With'
    }

    def "create with key sets keyed breadcrumb"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            @DSL class Foo {
                @Key String name
            }
'''
        def path = null

        when:
        create("Foo", "bar") {
            path = BreadcrumbCollector.instance.fullPath
        }

        then:
        path == '$/Foo.With(bar)'
    }

    def "nested objects breadcrumbs are correct"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            @DSL class Foo {
                String name
                Bar bar
            }
            @DSL class Bar {
                String name
            }
'''
        def path = null

        when:
        create("Foo") {
            bar {
                name = "bar"
                path = BreadcrumbCollector.instance.fullPath
            }
        }

        then:
        path == '$/Foo.With/bar'
    }

    def "nested collection objects breadcrumbs are correct"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            @DSL class Foo {
                String name
                List<Bar> bar
            }
            @DSL class Bar {
                String name
            }
'''
        def path = null
        def path2 = null

        when:
        create("Foo") {
            bar {
                name = "bar"
                path = BreadcrumbCollector.instance.fullPath
            }
            bar {
                name = "bar2"
                path2 = BreadcrumbCollector.instance.fullPath
            }
        }

        then:
        path == '$/Foo.With/bar'
        path2 == '$/Foo.With/bar[2]'
    }
    def "nested collection of keyed objects uses name instead of index"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            @DSL class Foo {
                String name
                List<Bar> bar
            }
            @DSL class Bar {
                @Key String name
            }
'''
        def path = null
        def path2 = null

        when:
        create("Foo") {
            bar("bar1") {
                path = BreadcrumbCollector.instance.fullPath
            }
            bar("bar2") {
                path2 = BreadcrumbCollector.instance.fullPath
            }
        }

        then:
        path == '$/Foo.With/bar(bar1)'
        path2 == '$/Foo.With/bar(bar2)'
    }

    def "nested map objects breadcrumbs are correct"() {
        given:
        createClass '''
            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Key
            @DSL class Foo {
                String name
                Map<String, Bar> bar
            }
            @DSL class Bar {
                @Key String name
            }
'''
        def path = null
        def path2 = null

        when:
        create("Foo") {
            bar("bar1") {
                path = BreadcrumbCollector.instance.fullPath
            }
            bar("bar2") {
                path2 = BreadcrumbCollector.instance.fullPath
            }
        }

        then:
        path == '$/Foo.With/bar(bar1)'
        path2 == '$/Foo.With/bar(bar2)'
    }
}
