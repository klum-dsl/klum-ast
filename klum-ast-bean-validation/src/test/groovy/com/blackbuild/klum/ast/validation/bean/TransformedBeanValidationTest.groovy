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

import com.blackbuild.klum.ast.AbstractDSLSpec
import com.blackbuild.klum.ast.runtime.validation.KlumValidationException
import spock.lang.Issue

@Issue("391")
class TransformedBeanValidationTest extends AbstractDSLSpec {

    def "a plain Min constraint does not have duplicate annotation metadata"() {
        given:
        Class<?> plainSchema = loader.parseClass('''
            package fixture.schema

            import jakarta.validation.constraints.Min

            class PlainValidatedStation {
                @Min(10L)
                int capacity
            }
        ''')

        expect:
        plainSchema.getDeclaredField("capacity").annotations*.annotationType().name == ["jakarta.validation.constraints.Min"]
    }

    def "a transformed Min constraint remains reflectively readable before Bean Validation runs"() {
        given:
        createClass('''
            package fixture.schema

            import jakarta.validation.constraints.Min

            @DSL
            class ValidatedStation {
                @Min(10L)
                int capacity
            }
        ''')

        when:
        def annotations = clazz.getDeclaredField("capacity").annotations

        then:
        annotations*.annotationType().name == ["jakarta.validation.constraints.Min"]
    }

    def "a transformed Min constraint remains valid in every Groovy test lane"() {
        given:
        createClass('''
            package fixture.schema

            import jakarta.validation.constraints.Min

            @DSL
            class ValidatedStation {
                @Min(10L)
                int capacity
            }
        ''')

        when:
        def station = clazz.Create.With {
            capacity 12
        }

        then:
        station.capacity == 12
        clazz.getDeclaredField("capacity").annotations*.annotationType().name == ["jakarta.validation.constraints.Min"]
        clazz.declaredClasses.find { it.name.endsWith("\$Builder") }
                .getDeclaredField("capacity").annotations.length == 0

        when:
        clazz.Create.With {
            capacity 9
        }

        then:
        thrown(KlumValidationException)
    }
}
