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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@Issue("https://github.com/klum-dsl/klum-ast/issues/186")
class FixedKeySpec extends AbstractDSLSpec {

    def "Field can be set statically"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Field(key = {"static"})
                Bar singleBar
            }

            @DSL
            class Bar {
                @Key String id
                String value
            }
        ''')

        expect:
        rwClassHasMethod("singleBar")
        rwClassHasMethod("singleBar", Closure)
        rwClassHasMethod("singleBar", Map)
        rwClassHasMethod("singleBar", Map, Closure)
        rwClassHasMethod("singleBar", Class)
        rwClassHasMethod("singleBar", Class, Closure)
        rwClassHasMethod("singleBar", Map, Class)
        rwClassHasMethod("singleBar", Map, Class, Closure)

        and:
        rwClassHasNoMethod("singleBar", String)
        rwClassHasNoMethod("singleBar", String, Closure)
        rwClassHasNoMethod("singleBar", Map, String)
        rwClassHasNoMethod("singleBar", Map, String, Closure)
        rwClassHasNoMethod("singleBar", Class, String)
        rwClassHasNoMethod("singleBar", Class, String, Closure)
        rwClassHasNoMethod("singleBar", Map, Class, String)
        rwClassHasNoMethod("singleBar", Map, Class, String, Closure)

        when:
        instance = create("pk.Foo") {
            singleBar([:])
        }

        then:
        instance.singleBar.id == "static"

        when:
        instance = create("pk.Foo") {
            singleBar()
        }

        then:
        instance.singleBar.id == "static"
    }

    def "Field can be set by fieldname"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Field(key = Field.FieldName)
                Bar singleBar
            }

            @DSL
            class Bar {
                @Key String id
                String value
            }
        ''')

        when:
        instance = create("pk.Foo") {
            singleBar()
        }

        then:
        instance.singleBar.id == "singleBar"
    }

    def "it is illegal to set Field.key for a collection"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Field(key = { 'bla' })
                List<Bar> singleBar
            }

            @DSL
            class Bar {
                @Key String id
                String value
            }
        ''')

        then:
        def exception = thrown(MultipleCompilationErrorsException)
        exception.errorCollector.errors
    }

    def "it is illegal to set Field.key for a non keyed field"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Field(key = { 'bla' })
                Bar singleBar
            }

            @DSL
            class Bar {
                String value
            }
        ''')

        then:
        def exception = thrown(MultipleCompilationErrorsException)
        exception.errorCollector.errors
    }


}