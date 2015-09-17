package com.blackbuild.groovy.configdsl.transform.model

import spock.lang.Specification


/**
 * Small tests to test AbstractDSLSpec itself.
 */
class MetaSpec extends AbstractDSLSpec {

    def "test create without key"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }
        ''')

        when:
        instance = create("pk.Foo") {
            name = "Klaus"
        }

        then:
        noExceptionThrown()
        instance.name == "Klaus"
    }

    def "test create with key"() {
        given:
        createClass('''
            package pk

            @DSL(key = "name")
            class Foo {
                String name
                String value
            }
        ''')

        when:
        instance = create("pk.Foo", "Klaus") {
            value "Bla"
        }

        then:
        noExceptionThrown()
        instance.name == "Klaus"
        instance.value == "Bla"
    }




}