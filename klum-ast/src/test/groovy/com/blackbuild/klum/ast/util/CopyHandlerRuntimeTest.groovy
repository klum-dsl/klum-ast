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

import com.blackbuild.groovy.configdsl.transform.AbstractDSLSpec
import spock.lang.Issue

@SuppressWarnings('GrPackage')
class CopyHandlerRuntimeTest extends AbstractDSLSpec {

    @Issue("359")
    def "copy from map creates copies of nested DSL objects"() {
        given:
        createClass('''
            package pk

import com.blackbuild.groovy.configdsl.transform.DSL

            @DSL
            class Outer {
                String name
                Inner inner
            }
            
            @DSL
            class Inner {
                String value
                String another
            } 
         ''')

        when:
        def target = Outer.Create.One()
        CopyHandler.copyToFrom(target, [name: "bli", inner: [value: "bla", another: "blub"]])

        then:
        target.name == "bli"
        target.inner.value == "bla"
        getClass("pk.Inner").isInstance(target.inner)
    }


}