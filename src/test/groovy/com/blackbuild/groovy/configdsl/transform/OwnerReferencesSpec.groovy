/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import spock.lang.Ignore

@SuppressWarnings("GroovyAssignabilityCheck")
class OwnerReferencesSpec extends AbstractDSLSpec {

    def "if owners is specified, no owner accessor is created"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                @Owner Foo owner
            }
        ''')

        then:
        rwClazz.metaClass.getMetaMethod("owner", clazz) == null
    }

    def "error: two different owners in hierarchy"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }

            @DSL
            class ChildBar extends Bar {
                @Owner Foo foo2
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    @Ignore("Currently, we allow non dsl-owners (for example Object)")
    def "error: owner field is no dsl object"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                @Owner String owner
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "owner reference for single dsl object"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')
        def Bar = getClass("pk.Bar")

        when:
        instance = clazz.create {
            bar {}
        }

        then:
        instance.bar.foo.is(instance)

        when:
        instance = clazz.create {
            bar(Bar) {}
        }

        then:
        instance.bar.foo.is(instance)
    }

    def 'owner is accessible via get$owner()'() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')

        when:
        instance = clazz.create {
            bar {}
        }

        then:
        instance.bar.$owner.is(instance)
    }

    def "using of existing objects in list closure sets owner"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')
        def aBar = create("pk.Bar") {}

        when:
        instance.apply {
            bars {
                bar aBar
            }
        }

        then:
        instance.bars[0].foo.is(instance)
    }

    def "reusing of objects in list closure does not set owner"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')

        when:
        def aBar
        instance.apply {
            bars {
                aBar = bar {}
            }
        }

        def otherInstance = create("pk.Foo") {
            bars {
                bar(aBar)
            }
        }

        then: "bar's owner should still be the first object"
        otherInstance.bars[0].foo.is(instance)
    }

    def "reusing of existing objects in map closure does not set owner"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL
            class Bar {
                @Key String name
                @Owner Object outer
            }

            @DSL
            class Fum {
                Map<String, Bar> bars
            }
        ''')

        when:
        def aBar
        instance = create("pk.Foo") {
            aBar = bar("Klaus") {}
        }

        create("pk.Fum") {
            bars {
                bar aBar
            }
        }

        then:
        aBar.outer.is(instance)
    }


    def "using existing objects in map closure sets owner"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Key String name
                @Owner Foo foo
            }
        ''')
        def aBar = create("pk.Bar", "Klaus") {}

        when:
        instance.apply {
            bars {
                bar(aBar)
            }
        }

        then:
        instance.bars.Klaus.foo.is(instance)
    }

    def "owner reference for dsl object list"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')
        def Bar = getClass("pk.Bar")

        when:
        instance = clazz.create {
            bars {
                bar {}
                bar(Bar) {}
            }
        }

        then:
        instance.bars[0].foo.is(instance)
        instance.bars[1].foo.is(instance)
    }

    def "owner reference for dsl object map"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
                @Key String name
            }
        ''')
        def Bar = getClass("pk.Bar")

        when:
        instance = clazz.create {
            bars {
                bar("Klaus") {}
                bar(Bar, "Dieter") {}
            }
        }

        then:
        instance.bars.Klaus.foo.is(instance)
        instance.bars.Dieter.foo.is(instance)
    }

    def "owner will not be overridden"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')

        def aBar = create("pk.Bar") {}

        when:
        instance = clazz.create {
            bar(aBar)
        }

        then:
        aBar.foo.is(instance)


        when:
        def anotherInstance = clazz.create {
            bar(aBar)
        }

        then: "bar.owner is not replaced"
        aBar.foo.is(instance)
    }

    def "owner is not overridden in Maps"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                Map<String, Bar> bars
            }

            @DSL
            class Bar {
                @Key String name
                @Owner Foo foo
            }
        ''')
        def aBar = create("pk.Bar", "Klaus") {}
        def otherFoo = create("pk.Foo") {
            bar(aBar)
        }

        when:
        instance.apply {
            bars {
                bar(aBar)
            }
        }

        then:
        instance.bars.Klaus.foo.is(otherFoo)
    }

    def "owner is not overridden in Lists"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')
        def aBar = create("pk.Bar") {}
        def otherFoo = create("pk.Foo") {
            bar(aBar)
        }

        when:
        instance.apply {
            bars {
                bar(aBar)
            }
        }

        then:
        instance.bars[0].foo.is(otherFoo)
    }


    def "bug: Reusing an object in a different structure throws ClassCastException"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class OtherOwner {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }
        ''')

        when:
        def aFoo = create("pk.Foo") {
            bars {
                bar {}
            }
        }

        def secondFoo = create("pk.OtherOwner") {
            bars {
                bar aFoo.bars[0]
            }
        }

        then:
        notThrown(GroovyCastException)
        secondFoo.bars[0].foo == aFoo
    }

    def "owner must be set before calling the closure"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
                List<Bar> listBars
                Map<String, Keyed> keyeds
            }

            @DSL
            class Bar {
                @Owner Foo foo
            }

            @DSL
            class Keyed {
                @Owner Foo foo
                @Key String name
            }
        ''')

        when:
        instance = clazz.create {
            bar {
                assert foo != null
            }
            listBars {
                bar {
                    assert foo != null
                }
            }
            keyeds {
                keyed("bla") {
                    assert foo != null
                }
            }

        }

        then:
        noExceptionThrown()
    }
}
