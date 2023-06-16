package com.blackbuild.groovy.configdsl.transform

import spock.lang.Issue

@Issue("122")
class BaseTypeTest extends AbstractDSLSpec {

    def "interface with baseType"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                @Field(baseType = BarImpl)
                Bar bar
            }
            
            interface Bar {
                String getValue()
            }
            
            @DSL
            class BarImpl implements Bar {
                String value
            } 
        ''')

        when:
        instance.apply {
            bar(value: "Dieter")
        }

        then:
        getClass("BarImpl").isInstance(instance.bar)
        instance.bar.value == "Dieter"
    }

    def "List of interfaces with baseType"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                @Field(baseType = BarImpl)
                List<Bar> bar
            }
            
            interface Bar {
                String getValue()
            }
            
            @DSL
            class BarImpl implements Bar {
                String value
            } 
        ''')

        when:
        instance.apply {
            bar(value: "Dieter")
        }

        then:
        getClass("BarImpl").isInstance(instance.bar.first())
        instance.bar.first().value == "Dieter"
    }

    def "Map of interfaces with baseType"() {
        given:
        createInstance('''
            @DSL
            class Foo {
                @Field(baseType = BarImpl)
                Map<String, Bar> bar
            }
            
            interface Bar {
                String getValue()
            }
            
            @DSL
            class BarImpl implements Bar {
                @Key String id
                String value
            } 
        ''')

        when:
        instance.apply {
            bar("a", value: "Dieter")
        }

        then:
        getClass("BarImpl").isInstance(instance.bar.a)
        instance.bar.a.value == "Dieter"
    }



}