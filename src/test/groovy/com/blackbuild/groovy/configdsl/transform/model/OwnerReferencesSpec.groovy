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
    }

}
