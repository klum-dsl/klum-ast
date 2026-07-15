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
//file:noinspection GrPackage
package com.blackbuild.klum.ast.jackson

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import com.fasterxml.jackson.databind.JsonMappingException
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

    def "serialization rejects a marked Template instead of dropping its recipe actions"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }
        ''')
        def template = clazz.Template.Create {
            name "template"
            applyLater {
                name "replayed"
            }
        }

        when:
        mapper.writeValueAsString(template)

        then:
        JsonMappingException error = thrown()
        error.message.startsWith("Cannot serialize marked Template pk.Foo as JSON because JSON preserves values " +
                "but not Template recipe actions. Rehydrate it through Template.With or another Template/copy API " +
                "and serialize the completed model instead.")
    }

    def "serialization rejects a marked Template nested in an ordinary value"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Object value
            }
        ''')
        def template = clazz.Template.Create()
        instance = clazz.Create.With {
            value template
        }

        when:
        mapper.writeValueAsString(instance)

        then:
        JsonMappingException error = thrown()
        error.message.contains("Cannot serialize marked Template pk.Foo as JSON")
        error.pathReference.contains('pk.Foo["value"]')
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

    def "deserialization restores Builders before lifecycle materialization and validation"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.klum.ast.util.KlumBuilder

            @DSL
            class Root {
                static List<String> events = []

                String name = "initializer"
                Child child

                @PostCreate
                void afterCreate() {
                    assert this instanceof KlumBuilder
                    events << "postCreate:$name".toString()
                    name += "-post"
                }

                @PostApply
                void afterApply() {
                    assert this instanceof KlumBuilder
                    events << "postApply:$name".toString()
                    name += "-apply"
                }

                @PostTree
                void afterTree() {
                    events << "postTree:$name:${child instanceof KlumBuilder}".toString()
                }

                @Validate
                void validateModel() {
                    assert !(this instanceof KlumBuilder)
                    events << "validate:$name:${getClass().simpleName}".toString()
                }
            }

            @DSL
            class Child {
                String value
                @Owner Root parent
            }
        ''')
        def Root = clazz

        when:
        def deserialized = mapper.readValue('{"name":"json","child":{"value":"nested"}}', Root)

        then:
        deserialized.name == "json-apply"
        deserialized.child.value == "nested"
        deserialized.child.parent.is(deserialized)
        Root.events == [
                "postCreate:initializer",
                "postApply:json",
                "postTree:json-apply:true",
                "validate:json-apply:Root"
        ]
        !deserialized.metaClass.respondsTo(deserialized, "apply", Closure)
    }

}
