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

            @DSLConfig
            class Foo {
                Bar bar
            }

            @DSLConfig(owner="owner")
            class Bar {
                Foo owner
            }
        ''')

        then:
        getClass("pk.Bar").metaClass.getMetaMethod("owner", clazz) == null
    }

    def "error: two different owners in hierarchy"() {
        when:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                Bar bar
            }

            @DSLConfig(owner="owner")
            class Bar {
                Foo owner
            }

            @DSLConfig(owner="owner2")
            class ChildBar extends Bar {
                Foo owner2
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "error: owner points to not existing field"() {
        when:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                Bar bar
            }

            @DSLConfig(owner="own")
            class Bar {
                Foo owner
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

            @DSLConfig
            class Foo {
                Bar bar
            }

            @DSLConfig(owner="owner")
            class Bar {
                String owner
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "owner reference for single dsl object"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                Bar bar
            }

            @DSLConfig(owner="owner")
            class Bar {
                Foo owner
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

    def "reusing of objects in list closure sets owner"() {
        given:
        createInstance('''
            package pk

            @DSLConfig
            class Foo {
                List<Bar> bars
            }

            @DSLConfig(owner = "owner")
            class Bar {
                Foo owner
            }
        ''')
        def aBar = create("pk.Bar") {}

        when:
        instance.bars {
            reuse(aBar)
        }

        then:
        instance.bars[0].owner.is(instance)
    }

    @Ignore("yet")
    def "reusing of objects in map closure does not set owner"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                Bar bar
            }

            @DSLConfig(key = "name", owner = "owner")
            class Bar {
                String name
                Foo owner
            }

            @DSLConfig
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
                reuse aBar
            }
        }

        then:
        aBar.owner.is(instance)
    }


    def "reusing of objects in map closure sets owner"() {
        given:
        createInstance('''
            package pk

            @DSLConfig
            class Foo {
                Map<String, Bar> bars
            }

            @DSLConfig(key = "name", owner = "owner")
            class Bar {
                String name
                Foo owner
            }
        ''')
        def aBar = create("pk.Bar", "Klaus") {}

        when:
        instance.bars {
            reuse(aBar)
        }

        then:
        instance.bars.Klaus.owner.is(instance)
    }

    def "owner reference for dsl object list"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                List<Bar> bars
            }

            @DSLConfig(owner="owner")
            class Bar {
                Foo owner
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

            @DSLConfig
            class Foo {
                Map<String, Bar> bars
            }

            @DSLConfig(key="name", owner="owner")
            class Bar {
                Foo owner
                String name
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

}
