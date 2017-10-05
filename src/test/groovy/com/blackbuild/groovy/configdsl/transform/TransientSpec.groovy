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
class TransientSpec extends AbstractDSLSpec {

    def "Setters for Transient fields are created"() {
        when:
        createInstance('''
            package pk

            @DSL
            class Foo {
                @Transient
                String metadata
            }
        ''')

        then:
        // in groovy, private fields can be accessed anyway, so we can only check for visibility
        clazz.getDeclaredField("metadata").modifiers | Opcodes.ACC_PRIVATE
        clazz.getMethod("setMetadata", String).modifiers | Opcodes.ACC_PUBLIC
    }

    def "Changing a transient field from a non mutator method is allowed"() {
        when:
        createClass '''
            package pk

            @DSL
            class Foo {
                @Transient
                boolean read
                
                void count() {
                    read = true
                }
            }'''
        then:
        noExceptionThrown()
    }

    def "Accessor methods are not generated for @Transient fields"() {
        when:
        createClass '''
            package pk

            @DSL
            class Foo {
                @Transient
                boolean read
            }'''

        then:
        !clazz.metaClass.methods.find { it.name == "read" }

    }

}
