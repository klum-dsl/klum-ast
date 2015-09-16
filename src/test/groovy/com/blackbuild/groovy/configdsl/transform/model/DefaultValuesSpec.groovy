package com.blackbuild.groovy.configdsl.transform.model

class DefaultValuesSpec extends AbstractDSLSpec {

    def "apply method using a preexisting object is created"() {
        when:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                String name
            }
        ''')

        then:
        clazz.metaClass.getMetaMethod("apply", getClass("pk.Foo")) != null

        when:
        def template = clazz.create {
            name "Welt"
        }

        instance = clazz.create {
            apply template
        }

        then:
        instance.name == "Welt"

        and:
        !instance.is(template)
    }


}