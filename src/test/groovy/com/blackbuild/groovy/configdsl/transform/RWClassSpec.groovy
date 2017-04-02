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

import groovyjarjarasm.asm.Opcodes

@SuppressWarnings("GroovyAssignabilityCheck")
class RWClassSpec extends AbstractDSLSpec {

    Class getRWClass(String name) {
        getClass(name + '$_RW')
    }

    Class getRWClass() {
        getRWClass(clazz.name)
    }

    def "RW class is created"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
            }
        ''')

        when:
        getRWClass()

        then:
        noExceptionThrown()
    }

    def "RW class inherits parent RW class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
            }
            
            @DSL
            class Child extends Parent {
            }
            
        ''')

        when:
        Class rwClass = getRWClass('pk.Child')

        then:
        rwClass.superclass.name == 'pk.Parent$_RW'
    }

    def "RW class delegates to model"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Model {
                transient int count
                String name = 'bla'
                
                void inc() {
                  count++
                }
            }
        ''')
        Class rwClass = getRWClass()
        def rw = rwClass.newInstance(instance)

        when:
        rw.inc()

        then:
        noExceptionThrown()
        instance.count == 1
        rw.name == 'bla'
    }

    def "RW class does have public setters for model, model does not"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Model {
                String name
            }
        ''')
        Class rwClass = getRWClass()

        when:
        def rwSetNameMethod = rwClass.getMethod("setName", String)

        then:
        rwSetNameMethod.modifiers & Opcodes.ACC_PUBLIC

        when:
        clazz.getMethod("setName", String)

        then:
        thrown(NoSuchMethodException)
    }

    def "RW setter writes through to model"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Model {
                String name
            }
        ''')
        Class rwClass = getRWClass()
        def rw = rwClass.newInstance(instance)

        when:
        rw.name = 'bla'

        then:
        instance.name == 'bla'
    }


}
