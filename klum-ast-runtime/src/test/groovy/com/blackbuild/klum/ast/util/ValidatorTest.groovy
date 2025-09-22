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
package com.blackbuild.klum.ast.util

import com.blackbuild.groovy.configdsl.transform.Validate
import spock.lang.Issue

@SuppressWarnings("GrPackage")
class ValidatorTest extends AbstractRuntimeTest {

    void "empty validation works"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo extends TestObject {
            }
        ''')

        when:
        validate(instance)

        then:
        noExceptionThrown()
    }

    void "simple validation"() {
        given:
        createInstance('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            class Foo extends TestObject {
                @Validate
                String name
            }
        ''')

        when:
        instance.name = 'test'
        validate(instance)

        then:
        noExceptionThrown()

        when:
        instance.name = null
        validate(instance)

        then:
        thrown(KlumValidationException)
    }

    void "simple validation with closure"() {
        given:
        createInstance('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            class Foo extends TestObject {
                // since we don't use AST-Transformation, we need to explicitly use assert
                @Validate({ assert value > 10})
                int value
            }
        ''')

        when:
        validate(instance)

        then:
        thrown(KlumValidationException)

        when:
        createInstance()
        instance.value = 200
        validate(instance)

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
            class Foo extends TestObject {
                @Validate int value
            }

            @DSL
            class Bar extends Foo {
            }
        ''')
        instance = newInstanceOf("pk.Bar")

        when:
        validate(instance)

        then:
        thrown(KlumValidationException)

        when:
        instance = newInstanceOf("pk.Bar")
        instance.value = 200
        validate(instance)

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
            class Foo extends TestObject {
                @Validate Boolean value
            }
        ''')
        instance = newInstanceOf("pk.Foo")

        when:
        validate(instance)

        then:
        thrown(KlumValidationException)

        when:
        instance = newInstanceOf("pk.Foo")
        instance.value = false
        validate(instance)

        then: 'False should satisfy validation'
        noExceptionThrown()

        when:
        instance = newInstanceOf("pk.Foo")
        instance.value = true
        validate(instance)

        then:
        noExceptionThrown()
    }

    @Issue("223")
    def "boolean fields are ignored on class level Validate"() {
        given:
        createClass('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            @Validate
            class Foo extends TestObject {
                boolean value
            }
        ''')
        instance = newInstanceOf("pk.Foo")

        when:
        validate(instance)

        then: 'boolean fields are ignored'
        noExceptionThrown()
    }

    private static void validate(Object instance) {
        def validator = new Validator(instance)
        validator.execute()
        validator.validationIssues.throwOn(Validate.Level.ERROR);
    }
}
