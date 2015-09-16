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

    def "template apply does not override default values"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                String name
                String value = "hallo"
            }
        ''')

        when:
        def template = clazz.create {
            name "Welt"
            value "override"
        }

        instance = clazz.create {
            apply template
        }

        then:
        instance.name == "Welt"

        and: "value has a default value, is not overriden"
        instance.value == "hallo"
    }

    def "create template method is created"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                String name
                String value = "hallo"
            }
        ''')

        when:
        clazz.createTemplate {
            name "Welt"
            value "Hallo"
        }

        then:
        clazz.TEMPLATE.name == "Welt"
        clazz.TEMPLATE.value == "Hallo"
    }

    def "create method should apply template"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                String name
                String value
            }
        ''')

        and:
        clazz.createTemplate {
            name "Default"
            value "DefaultValue"
        }

        when:
        instance = clazz.create {
            name "own"
        }

        then:
        instance.name == "own"
        instance.value == "DefaultValue"
    }

}