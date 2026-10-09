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
package com.blackbuild.klum.ast

import spock.lang.Issue

@Issue("867")
class FieldPhaseTraversalCharacterizationTest extends AbstractDSLSpec {
    def "parent field mutation precedes child callbacks in the same AutoLink phase"() {
        given:
        createClass '''
            package occurrence
            import com.blackbuild.klum.ast.layer3.AutoLink
            @DSL
            class Parent {
                static List<String> events = []
                Child child
                @AutoLink void processField() {
                    events << "parent"
                    child.message = "assigned during parent visit"
                }
            }
            @DSL
            class Child {
                String message
                @AutoLink void observeField() {
                    Parent.events << "child:$message".toString()
                }
            }
        '''
        when:
        def result = clazz.Create.With { child {} }
        then:
        clazz.events == ["parent", "child:assigned during parent visit"]
        result.child.message == "assigned during parent visit"
    }

    def "composition created in a parent callback joins the same phase traversal"() {
        given:
        createClass '''
            package occurrence
            import com.blackbuild.klum.ast.layer3.AutoLink
            @DSL
            class Parent {
                static List<String> events = []
                Child child
                @AutoLink void supplyField() {
                    events << "parent:null=${child == null}".toString()
                    child = Child.Create.AsBuilder().One()
                }
            }
            @DSL
            class Child {
                @AutoLink void observeField() { Parent.events << "child" }
            }
        '''
        when:
        def result = clazz.Create.One()
        then:
        clazz.events == ["parent:null=true", "child"]
        result.child != null
    }
}
