package com.blackbuild.groovy.configdsl.transform

class DefaultValuesSpec extends AbstractDSLSpec {

    def "simple default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
            
                @Default('another')
                String value
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            another "a"
        }

        then:
        instance.value == "a"

        when:
        instance = create("pk.Foo") {
            value "b"
            another "a"
        }

        then:
        instance.value == "b"
    }

    def "default value cascade"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                String base
            
                @Default('base')
                String value
                
                @Default('value')
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            base "a"
        }

        then:
        instance.another == "a"
    }


}
