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
//file:noinspection GrPackage
package com.blackbuild.klum.ast.util

import spock.lang.Issue

class CopyHandlerTest extends AbstractRuntimeTest {

    @Issue("36")
    def "copy from creates copies of nested DSL objects"() {
        given:
        createClass('''
            package pk

import com.blackbuild.groovy.configdsl.transform.DSL

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class Outer {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String name
                
                Inner inner
            }
            
            @DSL
            class Inner {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String value
            } 
         ''')

        def inner = newInstanceOf("pk.Inner")
        def outer = newInstanceOf("pk.Outer")
        inner.value = "bla"
        outer.inner = inner
        outer.name = "bli"

        when:
        def copy = newInstanceOf("pk.Outer")
        CopyHandler.copyToFrom(copy, outer)

        then:
        copy.name == "bli"
        copy.inner.value == "bla"
        !copy.inner.is(inner)
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    @Issue("36")
    def "copy from creates copies of nested DSL object collections and maps"() {
        given:
        createClass('''
            package pk

import com.blackbuild.groovy.configdsl.transform.DSL

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class Outer {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String name
                
                List<Inner> inners = []
                Map<String, Inner> mappedInners = [:]
            }
            
            @DSL
            class Inner {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String value
            } 
         ''')

        def inner = newInstanceOf("pk.Inner")
        def inner2 = newInstanceOf("pk.Inner")
        def minner = newInstanceOf("pk.Inner")
        def minner2 = newInstanceOf("pk.Inner")
        def outer = newInstanceOf("pk.Outer")
        inner.value = "bla"
        inner2.value = "blu"
        minner.value = "mbla"
        minner2.value = "mblu"
        outer.inners.add inner
        outer.inners.add inner2

        outer.mappedInners.putAll(one: minner, two: minner2)
        outer.name = "bli"

        when:
        def copy = newInstanceOf("pk.Outer")
        CopyHandler.copyToFrom(copy, outer)

        then:
        copy.name == "bli"
        !copy.inners.is(outer.inners)
        copy.inners.size() == 2

        and:
        copy.inners[0].value == "bla"
        copy.inners[1].value == "blu"
        !copy.inners[0].is(inner)
        !copy.inners[1].is(inner2)

        and:
        copy.mappedInners.one.value == "mbla"
        copy.mappedInners.two.value == "mblu"
        !copy.mappedInners.one.is(minner)
        !copy.mappedInners.two.is(minner2)
    }

    @Issue("36")
    def "copy from creates copies of Maps of Lists"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.groovy.configdsl.transform.DSL

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class Outer {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String name
                
                Map<String, List<String>> inners = [:]
                List<List<String>> innerLists = []
            }
         ''')

        def outer = newInstanceOf("pk.Outer")
        outer.name = "bli"
        outer.inners.put "a", ["a1", "a2"]
        outer.inners.put "b", ["b1", "b2"]
        outer.innerLists.add(["a1", "a2"])
        outer.innerLists.add(["b1", "b2"])

        when:
        def copy = newInstanceOf("pk.Outer")
        CopyHandler.copyToFrom(copy, outer)

        then:
        copy.name == "bli"
        copy.inners == outer.inners
        !copy.inners.is(outer.inners)
        copy.innerLists == outer.innerLists
        !copy.innerLists.is(outer.innerLists)

        and:
        copy.inners.a == outer.inners.a
        !copy.inners.a.is(outer.inners.a)

        and:
        copy.innerLists[0] == outer.innerLists[0]
        !copy.innerLists[0].is(outer.innerLists[0])
    }

    @Issue("309")
    def "copy with default strategy set replaces lists and map"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.groovy.configdsl.transform.DSL

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class AClass {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                Map<String, String> inners = [:]
                List<String> innerLists = []
            }
         ''')

        def template = newInstanceOf("pk.AClass")
        template.inners.put "a", "aFromTemplate"
        template.inners.put "b", "bFromTemplate"
        template.innerLists.add "aFromTemplate"

        when:
        def receiver = newInstanceOf("pk.AClass")
        receiver.inners.put "a", "aFromReceiver"
        receiver.inners.put "c", "cFromReceiver"
        receiver.innerLists.add "aFromReceiver"
        receiver.innerLists.add "bFromReceiver"
        CopyHandler.copyToFrom(receiver, template)

        then:
        receiver.inners.size() == 2
        !receiver.inners.containsKey("c")
        receiver.inners == [a: "aFromTemplate", b: "bFromTemplate"]

        and:
        receiver.innerLists.size() == 1
        receiver.innerLists == ["aFromTemplate"]
    }

    @Issue("309")
    def "copy with helm strategy set replaces lists and merges maps"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.util.copy.Overwrite
import com.blackbuild.klum.ast.util.copy.OverwriteStrategy

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            @Overwrite(collections = @Overwrite.Collection(OverwriteStrategy.Collection.REPLACE), 
                       maps = @Overwrite.Map(OverwriteStrategy.Map.MERGE_VALUES))
            class AClass {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                Map<String, String> inners = [:]
                List<String> innerLists = []
            }
         ''')

        def template = newInstanceOf("pk.AClass")
        template.inners.put "a", "aFromTemplate"
        template.inners.put "b", "bFromTemplate"
        template.innerLists.add "aFromTemplate"

        when:
        def receiver = newInstanceOf("pk.AClass")
        receiver.inners.put "a", "aFromReceiver"
        receiver.inners.put "c", "cFromReceiver"
        receiver.innerLists.add "aFromReceiver"
        receiver.innerLists.add "bFromReceiver"
        CopyHandler.copyToFrom(receiver, template)

        then:
        receiver.inners.size() == 3
        receiver.inners.containsKey("c")
        receiver.inners == [a: "aFromTemplate", b: "bFromTemplate", c: "cFromReceiver"]

        and:
        receiver.innerLists.size() == 1
        receiver.innerLists == ["aFromTemplate"]
    }


}