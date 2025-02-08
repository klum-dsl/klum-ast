/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2024 Stephan Pauxberger
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
package com.blackbuild.klum.ast.util

class DslHelperTest extends AbstractRuntimeTest {

    void "getField returns correct Field"() {
        given:
        createClass('''
            class Dummy {
                String name
            }
            
            class Yummy extends Dummy {
                String value
            }
        ''')

        when:
        def value = DslHelper.getField(getClass("Yummy"), "value")
        def name = DslHelper.getField(getClass("Yummy"), "name")

        then:
        noExceptionThrown()
        value.isPresent()
        name.isPresent()
    }

    void "getMethod returns correct Method"() {
        given:
        createClass('''
            class Dummy {
                void name(String bla) {}
            }
            
            class Yummy extends Dummy {
                void value(String bla) {}
            }
        ''')

        when:
        def value = DslHelper.getMethod(getClass("Yummy"), "value", String)
        def name = DslHelper.getMethod(getClass("Yummy"), "name", String)

        then:
        noExceptionThrown()
        value.isPresent()
        name.isPresent()

    }

    def 'getElementType with nested generics'() {
        given:
        createClass('''
            class Dummy {
                Map<String, List<String>> authorizationRoles
            }

        ''')

        when:
        def dummy = getClass("Dummy")
        def value = DslHelper.getElementTypeOfField(dummy, "authorizationRoles")

        then:
        noExceptionThrown()
    }

    def "getMatchingMethod with subclass Parameter"() {
        given:
        createClass('''
            import com.blackbuild.groovy.configdsl.transform.DSL
            @DSL
            class Dummy {
                @com.blackbuild.groovy.configdsl.transform.Field
                void doIt(Parent parent) {}
            }
            
            abstract class Parent {}
            class Child extends Parent {}
        ''')

        when:
        def method = DslHelper.getVirtualSetter(clazz, "doIt", getClass("Child"))

        then:
        method.isPresent()
    }

}
