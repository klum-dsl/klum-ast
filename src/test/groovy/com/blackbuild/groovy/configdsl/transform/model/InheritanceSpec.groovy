package com.blackbuild.groovy.configdsl.transform.model

import spock.lang.Ignore


class InheritanceSpec extends AbstractDSLSpec {

    @Ignore
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
        loader.loadClass

        .newInstance()
        instance.bars {
            bar { name "Dieter" }
            bar { name "Klaus"}
        }

        then:
        instance.bars[0].name == "Dieter"
        instance.bars[1].name == "Klaus"
    }


}