package com.blackbuild.groovy.configdsl.transform.model

import org.codehaus.groovy.control.MultipleCompilationErrorsException
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
            _use aBar
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
                @Owner Foo owner
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


    def "using exisiting objects in map closure sets owner"() {
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
            _use(aBar)
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

    def "owner may not be overriden"() {
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
        bar.owner = instance

        then:
        def e = thrown(IllegalStateException)
        e.message == "Owner must not be overridden."

    }

    def "Multiple uses of an object are not allowed"() {
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
        def aBar
        instance = clazz.create {
            bars {
               aBar = bar {}
            }
        }

        when:
        clazz.create {
            bars {
                _use aBar
            }
        }

        then:
        def e = thrown(IllegalStateException)
        e.message == "Owner must not be overridden."

    }

}
