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
package com.blackbuild.groovy.configdsl.transform

import spock.lang.Issue

@Issue("122")
class DefaultImplTest extends AbstractDSLSpec {

    def "interface with defaultImpl"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                @Field(defaultImpl = BarImpl)
                Bar bar
            }
            
            interface Bar {
                String getValue()
            }
            
            @DSL
            class BarImpl implements Bar {
                String value
            } 
        ''')

        when:
        instance.apply {
            bar(value: "Dieter")
        }

        then:
        getClass("BarImpl").isInstance(instance.bar)
        instance.bar.value == "Dieter"
    }

    def "List of interfaces with defaultImpl"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                @Field(defaultImpl = BarImpl)
                List<Bar> bar
            }
            
            interface Bar {
                String getValue()
            }
            
            @DSL
            class BarImpl implements Bar {
                String value
            } 
        ''')

        when:
        instance.apply {
            bar(value: "Dieter")
        }

        then:
        getClass("BarImpl").isInstance(instance.bar.first())
        instance.bar.first().value == "Dieter"
    }

    def "Map of interfaces with defaultImpl"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                @Field(defaultImpl = BarImpl)
                Map<String, Bar> bar
            }
            
            interface Bar {
                String getValue()
            }
            
            @DSL
            class BarImpl implements Bar {
                @Key String id
                String value
            } 
        ''')

        when:
        instance.apply {
            bar("a", value: "Dieter")
        }

        then:
        getClass("BarImpl").isInstance(instance.bar.a)
        instance.bar.a.value == "Dieter"
    }

    def "virtual setter with defaultImpl"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                @Field(FieldType.IGNORED) Bar myBar
                @Field(defaultImpl = BarImpl)
                void bar(Bar value) {
                    myBar = value
                }
            }
            
            interface Bar {
                String getValue()
            }
            
            @DSL
            class BarImpl implements Bar {
                String value
            } 
        ''')

        when:
        instance.apply {
            bar(value: "Dieter")
        }

        then:
        getClass("BarImpl").isInstance(instance.myBar)
        instance.myBar.value == "Dieter"
    }





}