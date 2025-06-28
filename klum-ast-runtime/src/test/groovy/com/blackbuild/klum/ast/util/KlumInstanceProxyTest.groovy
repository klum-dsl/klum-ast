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
//file:noinspection GrPackage
package com.blackbuild.klum.ast.util


import spock.lang.Subject

class KlumInstanceProxyTest extends AbstractRuntimeTest {

    @Subject KlumInstanceProxy proxy

    void "getKey returns the correct key for inherited classes"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo implements KlumModelObject {
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
        proxy = new KlumInstanceProxy(instance as GroovyObject)

        then:
        proxy.getInstanceProperty("name") == "myName"
        proxy.getInstanceProperty("child") == "myChild"
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


    // TODO: List of Lists, mixed dsl / non dsl elements
}
