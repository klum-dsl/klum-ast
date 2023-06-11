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
package com.blackbuild.klum.ast.util.layer3

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec

// is in klum-ast, because the tests are a lot better readable using the actual DSL.
@SuppressWarnings('GrPackage')
class AutoCreateDSLTest extends AbstractDSLSpec {

    def "empty auto create"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate

            @DSL
            class Config {
                @AutoCreate
                Child child
            }

            @DSL
            class Child {
                String name = "defaultName"
            }
        ''')

        when:
        instance = create("tmp.Config") {
            child {
                name "testName"
            }
        }

        then:
        instance.child.name == "testName"

        when:
        instance = create("tmp.Config") {
        }

        then:
        instance.child != null
        instance.child.name == "defaultName"
    }

    def "auto create with values"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate

            @DSL
            class Config {
                @AutoCreate({[name: "acName"]})
                Child child
            }

            @DSL
            class Child {
                String name = "defaultName"
            }
        ''')

        when:
        instance = create("tmp.Config") {
            child {
                name "testName"
            }
        }

        then:
        instance.child.name == "testName"

        when:
        instance = create("tmp.Config") {
        }

        then:
        instance.child != null
        instance.child.name == "acName"
    }

    def "auto create with key"() {
        given:
        createClass('''
            package tmp

            import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate

            @DSL
            class Config {
                @AutoCreate(key= "generated", value = {["name": "acName"]})
                Child child
            }

            @DSL
            class Child {
                @Key String id
                String name = "defaultName"
            }
        ''')

        when:
        instance = create("tmp.Config") {
            child("manual") {
                name "testName"
            }
        }

        then:
        instance.child.name == "testName"

        when:
        instance = create("tmp.Config") {
        }

        then:
        instance.child != null
        instance.child.name == "acName"
    }

    def "auto create with subclass"() {
        given:
        createClass('''
            package tmp

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.util.layer3.annotations.AutoCreate

            @DSL
            class Config {
                @AutoCreate(type = ChildChild, value = {["name": "acName", age: 42]})
                Child child
            }

            @DSL
            class Child {
                String name = "defaultName"
            }
            
            @DSL
            class ChildChild extends Child {
                int age
            }
        ''')

        when:
        instance = create("tmp.Config") {
        }

        then:
        instance.child != null
        instance.child.getClass().name == "tmp.ChildChild"
        instance.child.name == "acName"
        instance.child.age == 42
    }

}
