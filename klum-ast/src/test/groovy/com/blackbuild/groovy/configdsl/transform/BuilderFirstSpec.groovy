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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.util.KlumBuilder

class BuilderFirstSpec extends AbstractDSLSpec {

    def "factory configures a Builder and returns a completed model"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                static int initializerCalls

                String value = initializeValue()

                @Field(FieldType.BUILDER)
                String scratch = "builder-only"

                private static String initializeValue() {
                    initializerCalls++
                    return "initialized"
                }

                @PostTree
                void finishValue() {
                    value = "$value:$scratch"
                }
            }
        '''

        when:
        instance = clazz.Create.With {
            assert delegate instanceof KlumBuilder
            value "configured"
        }

        then:
        rwClazz.superclass == KlumBuilder
        instance.value == "configured:builder-only"
        clazz.initializerCalls == 1
        clazz.declaredFields*.name.contains("scratch") == false
        clazz.methods*.name.contains("apply") == false
    }
}
