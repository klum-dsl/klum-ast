/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast.validation.bean

import com.blackbuild.groovy.configdsl.transform.Validate
import com.blackbuild.klum.ast.util.AbstractRuntimeTest
import com.blackbuild.klum.ast.util.KlumValidationException
import com.blackbuild.klum.ast.validation.KlumValidationResult
import com.blackbuild.klum.ast.validation.SingleObjectValidationHandler
import spock.lang.Issue

@Issue("258")
class JSR380ValidatorTest extends AbstractRuntimeTest {

    void "empty validation works"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo extends TestObject {
            }
        ''')

        when:
        validateX(instance)

        then:
        noExceptionThrown()
    }

    void "simple validation with JBV 3.0 annotations"() {
        given:
        createInstance('''
            package pk

import jakarta.validation.constraints.Min

            @DSL
            class Foo extends TestObject {
                // since we don't use AST-Transformation, we need to explicitly use assert
                @Min(20L)
                int value
            }
        ''')

        when:
        validateX(instance)

        then:
        thrown(KlumValidationException)

        when:
        createInstance()
        instance.value = 200
        validateX(instance)

        then:
        noExceptionThrown()
    }

    void "simple validation with inheritance"() {
        given:
        createClass('''
            package pk

import jakarta.validation.constraints.Min

            @DSL
            class Foo extends TestObject {
                // since we don't use AST-Transformation, we need to explicitly use assert
                @Min(20L)
                int value
            }
            
            @DSL
            class Bar extends Foo {
                // since we don't use AST-Transformation, we need to explicitly use assert
                @Min(20L)
                int value2
            }
        ''')
        createInstanceOf("pk.Bar")

        when:
        validateX(instance)

        then:
        thrown(KlumValidationException)

        when: "only parent value satisfied"
        createInstanceOf("pk.Bar")
        instance.value = 200
        validateX(instance)

        then:
        thrown(KlumValidationException)

        when: "only child value satisfied"
        createInstanceOf("pk.Bar")
        instance.value2 = 200
        validateX(instance)

        then:
        thrown(KlumValidationException)

        when: "both values satisfied"
        createInstanceOf("pk.Bar")
        instance.value = 200
        instance.value2 = 200
        validateX(instance)

        then:
        noExceptionThrown()
    }

    void "simple validation different levels"() {
        given:
        createInstance('''
            package pk

            import jakarta.validation.constraints.Min

            @DSL
            class Foo extends TestObject {
                @Min(value = 20L, payload = Level.WARNING)
                int value
            }
        ''')

        when:
        def result = validate(instance)

        then:
        result.has(Validate.Level.WARNING)
        result.issues.size() == 1
        result.issues.first().member == "value"
    }

    private static void validateX(Object instance) {
        def validator = new SingleObjectValidationHandler(instance)
        validator.execute().throwOn(Validate.Level.ERROR)
    }

    private static KlumValidationResult validate(Object instance) {
        def validator = new SingleObjectValidationHandler(instance)
        return validator.execute()
    }
}
