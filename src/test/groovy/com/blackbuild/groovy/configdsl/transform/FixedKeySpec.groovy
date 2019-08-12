package com.blackbuild.groovy.configdsl.transform

import spock.lang.Issue

@Issue("https://github.com/klum-dsl/klum-ast/issues/186")
class FixedKeySpec extends AbstractDSLSpec {

    def "Field can be set statically"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Field(key = {"static"})
                Bar singleBar
            }

            @DSL
            class Bar {
                @Key String id
                String value
            }
        ''')

        expect:
        rwClassHasMethod("singleBar")
        rwClassHasMethod("singleBar", Closure)
        rwClassHasMethod("singleBar", Map)
        rwClassHasMethod("singleBar", Map, Closure)
        rwClassHasMethod("singleBar", Class)
        rwClassHasMethod("singleBar", Class, Closure)
        rwClassHasMethod("singleBar", Map, Class)
        rwClassHasMethod("singleBar", Map, Class, Closure)

        and:
        rwClassHasNoMethod("singleBar", String)
        rwClassHasNoMethod("singleBar", String, Closure)
        rwClassHasNoMethod("singleBar", Map, String)
        rwClassHasNoMethod("singleBar", Map, String, Closure)
        rwClassHasNoMethod("singleBar", Class, String)
        rwClassHasNoMethod("singleBar", Class, String, Closure)
        rwClassHasNoMethod("singleBar", Map, Class, String)
        rwClassHasNoMethod("singleBar", Map, Class, String, Closure)

        when:
        instance = create("pk.Foo") {
            singleBar([:])
        }

        then:
        instance.singleBar.id == "static"

        when:
        instance = create("pk.Foo") {
            singleBar()
        }

        then:
        instance.singleBar.id == "static"
    }

}