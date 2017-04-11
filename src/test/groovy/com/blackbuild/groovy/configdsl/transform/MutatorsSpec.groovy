/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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

@SuppressWarnings("GroovyAssignabilityCheck")
class MutatorsSpec extends AbstractDSLSpec {

    def "Mutator methods are moved into RW class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                @Mutator
                def setNameCaseInsensitive(String name) {
                    this.name == name.toLowerCase()
                }
            }
        ''')

        when:
        rwClazz.getDeclaredMethod("setNameCaseInsensitive", String)

        then:
        notThrown(NoSuchMethodException)

        when:
        clazz.getDeclaredMethod("setNameCaseInsensitive", String)

        then:
        thrown(NoSuchMethodException)
    }

    def "Mutator methods can change state"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                @Mutator
                def setNameCaseInsensitive(String name) {
                    this.name = name.toLowerCase()
                }
            }
        ''')

        when:
        instance = clazz.create {
            nameCaseInsensitive = "Bla"
        }

        then:
        instance.name == 'bla'
    }

    def "Non mutator methods cannot change state"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def setNameCaseInsensitive(String name) {
                    this.name = name.toLowerCase()
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "bug: def assignment is legal"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def localVarAssignmentIsLegal() {
                    def value = "blub"
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

}
