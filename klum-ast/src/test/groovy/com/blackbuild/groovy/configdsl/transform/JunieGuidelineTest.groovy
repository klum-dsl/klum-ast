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

/**
 * A simple test to demonstrate how to write tests for KlumAST.
 */
class JunieGuidelineTest extends AbstractDSLSpec {

    def "simple DSL class can be created and used"() {
        given: "A simple DSL class"
        createClass('''
            package demo

            import com.blackbuild.groovy.configdsl.transform.DSL

            @DSL
            class Person {
                String name
                int age
                List<String> hobbies
            }
        ''')

        when: "We create an instance using the DSL"
        def person = clazz.Create.With {
            name "John Doe"
            age 30
            hobby "Reading"
            hobby "Coding"
        }

        then: "The properties are set correctly"
        person.name == "John Doe"
        person.age == 30
        person.hobbies == ["Reading", "Coding"]
    }
}