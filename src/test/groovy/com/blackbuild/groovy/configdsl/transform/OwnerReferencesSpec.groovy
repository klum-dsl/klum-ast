package com.blackbuild.groovy.configdsl.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
import spock.lang.Ignore

import java.lang.reflect.Method

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
        getClass("pk.Bar").metaClass.getMetaMethod("owner", clazz) == null
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
                @Owner Foo owner
            }

            @DSL(owner="owner2")
            class ChildBar extends Bar {
                @Owner Foo owner2
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "error: owner points to non existing field"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            }

            @DSL(owner="own")
            class Bar {
                @Owner Foo owner
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
                @Owner Foo owner
            }
        ''')

        when:
        instance = clazz.create {
            bar {}
        }

        then:
        instance.bar.owner.is(instance)

        when:
        instance = clazz.create {
            bar(getClass("pk.Bar")) {}
        }

        then:
        instance.bar.owner.is(instance)
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
                @Owner Foo owner
            }
        ''')
        def aBar = create("pk.Bar") {}

        when:
        instance.bars {
            bar aBar
        }

        then:
        instance.bars[0].owner.is(instance)
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
                @Owner Foo owner
            }
        ''')

        when:
        def aBar
        instance.bars {
            aBar = bar {}
        }

        def otherInstance = create("pk.Foo") {
            bars {
                bar(aBar)
            }
        }

        then: "bar's owner should still be the first object"
        otherInstance.bars[0].owner.is(instance)
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
                @Owner Object owner
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
        aBar.owner.is(instance)
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
                @Owner Foo owner
            }
        ''')
        def aBar = create("pk.Bar", "Klaus") {}

        when:
        instance.bars {
            bar(aBar)
        }

        then:
        instance.bars.Klaus.owner.is(instance)
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
                @Owner Foo owner
            }
        ''')

        when:
        instance = clazz.create {
            bars {
                bar {}
                bar(getClass("pk.Bar")) {}
            }
        }

        then:
        instance.bars[0].owner.is(instance)
        instance.bars[1].owner.is(instance)
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
                @Owner Foo owner
                @Key String name
            }
        ''')

        when:
        instance = clazz.create {
            bars {
                bar("Klaus") {}
                bar(getClass("pk.Bar"), "Dieter") {}
            }
        }

        then:
        instance.bars.Klaus.owner.is(instance)
        instance.bars.Dieter.owner.is(instance)
    }

    def "owner will not be overridden"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                List<Bar> bars
            }

            @DSL
            class Bar {
                @Owner Foo owner
            }
        ''')
        def bar = create("pk.Bar") {}
        bar.owner = instance

        when:
        bar.owner = clazz.create {}

        then:
        bar.owner == instance
    }

    def "bug: Reusing an object in a different structure throws ClassCatException"() {
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
                @Owner Foo owner
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
        secondFoo.bars[0].owner == aFoo
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
