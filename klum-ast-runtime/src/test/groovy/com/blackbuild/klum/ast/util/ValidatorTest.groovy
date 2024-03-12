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

import spock.lang.Issue

class ValidatorTest extends AbstractRuntimeTest {

    void "empty validation works"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
            }
        ''')

        when:
        new Validator(instance).execute()

        then:
        noExceptionThrown()
    }

    void "simple validation"() {
        given:
        createInstance('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            class Foo {
                @Validate
                String name
            }
        ''')

        when:
        instance.name = 'test'
        new Validator(instance).execute()

        then:
        noExceptionThrown()

        when:
        instance.name = null
        new Validator(instance).execute()

        then:
        thrown(AssertionError)
    }

    void "simple validation with closure"() {
        given:
        createInstance('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            class Foo {
                // since we don't use AST-Transformation, we need to explicitly use assert
                @Validate({ assert value > 10})
                int value
            }
        ''')

        when:
        new Validator(instance).execute()

        then:
        thrown(AssertionError)

        when:
        instance.value = 200
        new Validator(instance).execute()

        then:
        noExceptionThrown()
    }

    @Issue("https://github.com/klum-dsl/klum-ast/issues/230")
    void "BUG: validator fails on inheritance"() {
        given:
        createClass('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            class Foo {
                @Validate int value
            }

            @DSL
            class Bar extends Foo {
            }
        ''')
        instance = newInstanceOf("pk.Bar")

        when:
        new Validator(instance).execute()

        then:
        thrown(AssertionError)

        when:
        instance.value = 200
        new Validator(instance).execute()

        then:
        noExceptionThrown()
    }

    @Issue("223")
    def "Validation on Boolean checks not null instead of Groovy Truth"() {
        given:
        createClass('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            class Foo {
                @Validate Boolean value
            }
        ''')
        instance = newInstanceOf("pk.Foo")

        when:
        new Validator(instance).execute()

        then:
        thrown(AssertionError)

        when:
        instance.value = false
        new Validator(instance).execute()

        then: 'False should satisfy validation'
        noExceptionThrown()

        when:
        instance.value = true
        new Validator(instance).execute()

        then:
        noExceptionThrown()
    }

    @Issue("223")
    def "boolean fields are ignored on class level Validate"() {
        given:
        createClass('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate
            import com.blackbuild.groovy.configdsl.transform.Validation

            @DSL
            @Validate
            class Foo {
                boolean value
            }
        ''')
        instance = newInstanceOf("pk.Foo")

        when:
        new Validator(instance).execute()

        then: 'boolean fields are ignored'
        noExceptionThrown()
    }


}
