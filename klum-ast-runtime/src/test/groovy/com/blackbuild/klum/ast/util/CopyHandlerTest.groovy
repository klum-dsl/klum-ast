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

import spock.lang.Issue

class CopyHandlerTest extends AbstractRuntimeTest {

    @Issue("36")
    def "copy from creates copies of nested DSL objects"() {
        given:
        createClass('''
            package pk

import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.KlumModelObject

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class Outer implements KlumModelObject {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String name
                
                Inner inner
            }
            
            @DSL
            class Inner implements KlumModelObject {
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
            class Outer implements KlumModelObject {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String name
                
                List<Inner> inners = []
                Map<String, Inner> mappedInners = [:]
            }
            
            @DSL
            class Inner implements KlumModelObject {
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
            class Outer implements KlumModelObject {
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

    @Issue("36")
    def "copy from Map creates copies of Maps of Lists"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.groovy.configdsl.transform.DSL

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class Outer implements KlumModelObject {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String name
                
                Map<String, List<String>> inners = [:]
                List<List<String>> innerLists = []
            }
         ''')

        def outer = [name: "bli", inners: [a: ["a1", "a2"], b: ["b1", "b2"]], innerLists: [["a1", "a2"], ["b1", "b2"]]]

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

    def "copy from Map uses converters"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.groovy.configdsl.transform.DSL

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class Outer implements KlumModelObject {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String name
                
                Inner inner
                Dummy dummy
            }
            
            enum Dummy {
                ABC, BCD
            }
            
            class Inner {
                String firstName
                String lastName
                
                static Inner fromString(String name) {
                    def strings = name.tokenize(" ")
                    return new Inner(firstName: strings[0], lastName: strings[1])
                }
            }
            
         ''')

        def outer = [name: "bli", inner: "Hans Wurst", dummy: "BCD"]

        when:
        def copy = newInstanceOf("pk.Outer")
        CopyHandler.copyToFrom(copy, outer)

        then:
        copy.name == "bli"
        copy.inner.firstName == "Hans"
        copy.inner.lastName == "Wurst"
        copy.dummy == getClass("pk.Dummy").valueOf("BCD")
    }

    @Issue("309")
    def "copy with default strategy set replaces lists and map"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.groovy.configdsl.transform.DSL

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class AClass implements KlumModelObject {
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
            class AClass implements KlumModelObject {
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

    @Issue("348")
    def "copy with Overwrite.Missing Ignore ignores missing fields"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.groovy.configdsl.transform.DSL
import com.blackbuild.klum.ast.util.copy.Overwrite
import com.blackbuild.klum.ast.util.copy.OverwriteStrategy

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            @Overwrite(missing = @Overwrite.Missing(OverwriteStrategy.Missing.IGNORE))
            class AClass implements KlumModelObject {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                Map<String, String> inners = [:]
                List<String> innerLists = []
            }

            @DSL
            class BClass implements KlumModelObject {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                Map<String, String> inners = [:]
                List<String> otherLists = []
            }
         ''')

        def template = newInstanceOf("pk.BClass")
        template.inners.put "a", "aFromTemplate"
        template.inners.put "b", "bFromTemplate"
        template.otherLists.add "aFromTemplate"

        when:
        def receiver = newInstanceOf("pk.AClass")
        receiver.inners.put "a", "aFromReceiver"
        receiver.inners.put "c", "cFromReceiver"
        receiver.innerLists.add "aFromReceiver"
        receiver.innerLists.add "bFromReceiver"
        CopyHandler.copyToFrom(receiver, template)

        then:
        notThrown(KlumModelException)
    }

    @Issue("374")
    def "copy handler ignores TRANSIENT and IGNORED field"() {
        given:
        createClass('''
            package pk

            import com.blackbuild.groovy.configdsl.transform.DSL
            import com.blackbuild.groovy.configdsl.transform.Field
            import com.blackbuild.groovy.configdsl.transform.FieldType

            @SuppressWarnings('UnnecessaryQualifiedReference')
            @DSL
            class AClass implements KlumModelObject {
                KlumInstanceProxy $proxy = new KlumInstanceProxy(this)
                String normalField
                @Field(FieldType.TRANSIENT)
                String transientField
                @Field(FieldType.IGNORED)
                String ignoredField
            }
         ''')

        def template = newInstanceOf("pk.AClass")
        template.normalField = "normalFieldFromTemplate"
        template.transientField = "transientFieldFromTemplate"
        template.ignoredField = "ignoredFieldFromTemplate"

        when:
        def receiver = newInstanceOf("pk.AClass")
        CopyHandler.copyToFrom(receiver, template)

        then:
        receiver.normalField == "normalFieldFromTemplate"
        receiver.transientField == null
        receiver.ignoredField == null
    }


}