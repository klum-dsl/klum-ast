package com.blackbuild.groovy.configdsl.transform.model

import spock.lang.Ignore


class InheritanceSpec extends AbstractDSLSpec {

    def "objects inheriting from DSLObjects are also DSLObjects"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                String name
            }

            @DSLConfig
            class Bar extends Foo {
                String value
            }
        ''')

        when:
        instance = create("pk.Bar") {
            name "Klaus"
            value "High"

        }

        then:
        instance.name == "Klaus"
        instance.value == "High"
    }


}