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
//file:noinspection GrPackage
package com.blackbuild.klum.ast.jackson

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import com.fasterxml.jackson.databind.ObjectMapper
import org.intellij.lang.annotations.Language

class JsonExportSpec extends AbstractDSLSpec {

    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()

    def "simple serialization"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
                
                static <T> T doIt(Map<Class<?>, Object> args, Closure<T> closure) {
                    return closure.call()
                }
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')

        when:
        instance = clazz.Create.With {
            bar()
        }

        then:
        mapper.writeValueAsString(instance) == '{"bar":{}}'
    }

    def "serialization with map"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
                @Key String name
            }
        ''')
        def Bar = getClass("pk.Bar")

        when:
        instance = clazz.Create.With {
            bars {
                bar("Klaus") {}
                bar(Bar, "Dieter") {}
            }
        }

        then:
        mapper.writeValueAsString(instance) == '{"bars":{"Klaus":{"name":"Klaus"},"Dieter":{"name":"Dieter"}}}'
    }

    def "deserialization with map"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
                @Key String name
            }
        ''')
        def Bar = getClass("pk.Bar")

        instance = clazz.Create.With {
            bars {
                bar("Klaus") {}
                bar(Bar, "Dieter") {}
            }
        }
        @Language("json")
        def json = '''
{"bars":{"Klaus":{"name":"Klaus"},"Dieter":{"name":"Dieter"}}}
'''

        when:
        def deserialized = mapper.readValue(json, getClass("pk.Foo"))

        then:
        deserialized == instance
    }

}