/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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
import spock.lang.Subject

class KlumInstanceProxyTest extends AbstractRuntimeTest {

    @Subject KlumInstanceProxy proxy

    void "getKey returns the correct key for inherited classes"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
            }
            @DSL
            class Bar extends Foo {
            }
        ''')

        expect:
        DslHelper.getKeyField(getClass("pk.Bar")).get().getName() == "name"

    }

    def "inherited instanceProperties"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }
            @DSL
            class Bar extends Foo {
                String child
            }
        ''')

        when:
        instance = newInstanceOf("pk.Bar")
        instance.name = "myName"
        instance.child = "myChild"
        proxy = new KlumInstanceProxy(instance)

        then:
        proxy.getInstanceProperty("name") == "myName"
        proxy.getInstanceProperty("child") == "myChild"
    }
    def "Default values"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String child
                @Default(field = "child") String withDefaultValue
                @Default(code =  {child.toLowerCase()}) String withDefaultCode
                
            }
        ''')

        instance = newInstanceOf("pk.Foo")
        instance.child = "myChild"
        proxy = new KlumInstanceProxy(instance)

        expect:
        proxy.getInstanceProperty("child") == "myChild"
        proxy.getInstanceProperty("withDefaultValue") == "myChild"
        proxy.getInstanceProperty("withDefaultCode") == "mychild"

        when:
        instance.withDefaultValue = "my"
        instance.withDefaultCode = "child"

        then:
        instance.withDefaultValue == "my"
        instance.withDefaultCode == "child"
    }

    def "invoke via getProperty"() {
        given:
        createClass('''
            package pk
            
            class Foo {
                final KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
            
                String name 
                
                String getName() {
                    $proxy.getInstanceProperty('name')
                }
            }

            @DSL
            class Bar extends Foo {
                String child
                
                String getChild() {
                    $proxy.getInstanceProperty('child')
                }
            }
        ''')

        when:
        instance = newInstanceOf("pk.Bar")
        instance.name = "myName"
        instance.child = "myChild"

        then:
        instance.name == "myName"
        instance.child == "myChild"
    }

    def "Field can be set via various methods"() {
        given:
        createClass('''
            package pk

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class Foo {
                String provider
                
                @Field(key = { provider })
                Object viaProvider

                @Field(key = com.blackbuild.groovy.configdsl.transform.Field.FieldName)
                Object byFieldName
                
                @Field
                Object noKeyMember

                Object noFieldAnnotation
            }
         ''')

        when:
        instance = newInstanceOf("pk.Foo")
        instance.provider = "bar"
        proxy = new KlumInstanceProxy(instance)

        then:
        proxy.resolveKeyForFieldFromAnnotation("viaProvider", proxy.getField("viaProvider")).get() == "bar"
        proxy.resolveKeyForFieldFromAnnotation("byFieldName", proxy.getField("byFieldName")).get() == "byFieldName"
        !proxy.resolveKeyForFieldFromAnnotation("noKeyMember", proxy.getField("noKeyMember")).isPresent()
        !proxy.resolveKeyForFieldFromAnnotation("noFieldAnnotation", proxy.getField("noFieldAnnotation")).isPresent()
    }

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
        proxy = KlumInstanceProxy.getProxyFor(copy)
        proxy.copyFrom(outer)

        then:
        copy.name == "bli"
        copy.inner.value == "bla"
        !copy.inner.is(inner)
    }

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
        proxy = KlumInstanceProxy.getProxyFor(copy)
        proxy.copyFrom(outer)

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
        proxy = KlumInstanceProxy.getProxyFor(copy)
        proxy.copyFrom(outer)

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

    // TODO: List of Lists, mixed dsl / non dsl elements
}
