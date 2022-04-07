/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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
class StaticTypingSpec extends AbstractDSLSpec {

    def "static type checking with illegal method call"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def shouldFail() {
                    name.help()
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "static type checking can be disabled per method"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                @groovy.transform.TypeChecked(groovy.transform.TypeCheckingMode.SKIP)
                def shouldNotFail() {
                    name.help()
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "static type checking can be disabled for the whole model"() {
        when:
        createClass('''
            package pk

            @DSL
            @groovy.transform.TypeChecked(groovy.transform.TypeCheckingMode.SKIP)
            class Foo {
                String name
                
                def shouldNotFail() {
                    name.help()
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "def typed methods are allowed"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def shouldFail() {
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }
}
