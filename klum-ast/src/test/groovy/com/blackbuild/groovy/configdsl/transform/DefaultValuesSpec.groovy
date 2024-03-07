/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform


import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Ignore
import spock.lang.Issue

class DefaultValuesSpec extends AbstractDSLSpec {

    def "simple default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
            
                @Default(field = 'another')
                String value
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            another "a"
        }

        then:
        instance.value == "a"

        when:
        instance = create("pk.Foo") {
            value "b"
            another "a"
        }

        then:
        instance.value == "b"
    }

    @Ignore("Obsolete")
    def "undefaulted getter is created"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
            
                @Default(field = 'another')
                String value
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            another "a"
        }

        then:
        instance.value == "a"
        instance.$value == null

    }

    def "default value cascade"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                String base
            
                @Default(field = 'base')
                String value
                
                @Default(field = 'value')
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            base "a"
        }

        then:
        instance.another == "a"
    }

    def "closure default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                String name
            
                @Default(code ={ name.toLowerCase() })
                String lower
            }
'''
        when:
        instance = create("pk.Foo") {
            name "Hans"
        }

        then:
        instance.lower == "hans"
    }

    def "delegate default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Container {
                String name
                Element element
            }

            @DSL
            class Element {
                @Owner Container owner
            
                @Default(delegate = 'owner')
                String name
            }
'''
        when: "No name is set for the inner element"
        instance = create("pk.Container") {
            name "outer"
            element {

            }
        }

        then: "the name of the outer instance is used"
        instance.element.name == "outer"

        when:
        instance = create("pk.Container") {
            name "outer"
            element {
                name "inner"
            }
        }

        then:
        instance.element.name == "inner"
    }

    def "if delegate is null, delegate default returns null"() {
        given:
        createClass '''
            package pk

            @DSL
            class Container {
                String name
            
                Element element
            }


            @DSL
            class Element {
                @Owner Container owner
            
                @Default(delegate = 'owner')
                String name
            }
'''
        when: "No name is set for both instances"
        instance = create("pk.Container") {
            element {
            }
        }

        then:
        notThrown(NullPointerException)
        instance.element.name == null
    }

    def "It is illegal to use @Default without a member"() {
        when:
        createClass '''
            package pk

            @DSL
            class Element {
                @Default
                String name
            }
'''
        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "It is illegal to use more than one member of @Default"() {
        when:
        createClass '''
            package pk

            @DSL
            class Element {
                String other
            
                @Default(field = 'other', delegate = 'other')
                String name
            }
'''
        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "default values are coerced to target type"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                @Default(field = 'another')
                int value
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            another "10"
        }

        then:
        instance.value == 10
    }

    def "copyFrom should ignore default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                @Default(field = 'another')
                String value
                String another
            }
        '''

        when:
        def template = clazz.Create.Template {
            another "template"
        }

        def foo = clazz.Create.With {
            copyFrom template
            another = "model"
        }

        then:
        foo.another == "model"
        foo.value == "model"
    }

    @Issue("318")
    def "default methods should be lifecycle methods"() {
        when:
        createClass '''
            package pk

            @DSL
            class Foo {
                String value
                @Default
                void aDefaultMethod() {
                    value = "default"
                }
            }
        '''

        then:
        notThrown(MultipleCompilationErrorsException)
        hasNoMethod(clazz, "aDefaultMethod")
        hasNoMethod(rwClazz, "aDefaultMethod")

        when:
        def foo = create("pk.Foo")

        then:
        foo.value == "default"
    }

}
