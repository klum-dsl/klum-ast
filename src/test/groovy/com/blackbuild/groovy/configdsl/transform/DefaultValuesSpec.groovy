package com.blackbuild.groovy.configdsl.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException

class DefaultValuesSpec extends AbstractDSLSpec {

    def "simple default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
            
                @Default(field = 'another')
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

    def "closure default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                String name
            
                @Default(code ={ name.toLowerCase() })
                String lower
            }
'''
        when:
        instance = create("pk.Foo") {
            name "Hans"
        }

        then:
        instance.lower == "hans"
    }

    def "delegate default values"() {
        given:
        createClass '''
            package pk

            @DSL
            class Container {
                String name
            
                Element element
            }


            @DSL
            class Element {
                @Owner Container owner
            
                @Default(delegate = 'owner')
                String name
            }
'''
        when: "No name is set for the inner element"
        instance = create("pk.Container") {
            name "outer"
            element {

            }
        }

        then: "the name of the outer instance is used"
        instance.element.name == "outer"

        when:
        instance = create("pk.Container") {
            name "outer"
            element {
                name "inner"
            }
        }

        then:
        instance.element.name == "inner"

    }

    def "if delegate is null, delegate default returns null"() {
        given:
        createClass '''
            package pk

            @DSL
            class Container {
                String name
            
                Element element
            }


            @DSL
            class Element {
                @Owner Container owner
            
                @Default(delegate = 'owner')
                String name
            }
'''
        when: "No name is set for both instances"
        instance = create("pk.Container") {
            element {
            }
        }

        then:
        notThrown(NullPointerException)
        instance.element.name == null
    }

    def "It is illegal to use @Delegate without a member"() {
        when:
        createClass '''
            package pk

            @DSL
            class Element {
                @Default
                String name
            }
'''
        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "It is illegal to use more than one member of @Delegate"() {
        when:
        createClass '''
            package pk

            @DSL
            class Element {
                String other
            
                @Default(field = 'other', delegate = 'other')
                String name
            }
'''
        then:
        thrown(MultipleCompilationErrorsException)
    }


    def "default values are coerced to target type"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                @Default(field = 'another')
                int value
                String another
            }
'''
        when:
        instance = create("pk.Foo") {
            another "10"
        }

        then:
        instance.value == 10
    }



}
