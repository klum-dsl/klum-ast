package com.blackbuild.groovy.configdsl.transform.model

import org.codehaus.groovy.control.MultipleCompilationErrorsException

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

}
