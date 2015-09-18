package com.blackbuild.groovy.configdsl.transform.model

class DefaultValuesSpec extends AbstractDSLSpec {

    def "apply method using a preexisting object is created"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }
        ''')

        then:
        clazz.metaClass.getMetaMethod("copyFrom", getClass("pk.Foo")) != null

        when:
        def template = clazz.create {
            name "Welt"
        }

        instance = clazz.create {
            copyFrom template
        }

        then:
        instance.name == "Welt"

        and:
        !instance.is(template)
    }

    def "empty template fields are not copied"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String value
            }
        ''')

        when:
        def template = clazz.create {
            name "Welt"
            value null
        }

        instance = clazz.create {
            name "toOverride"
            value "orig"
            copyFrom template
        }

        then:
        instance.name == "Welt"

        and: "empty values are not copied"
        instance.value == "orig"
    }

    def "create template method is created"() {
        given:
        createClass('''
            package pk

            @DSL
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

            @DSL
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

    def "create method should apply template for keyed objects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String value
                String value2
            }
        ''')

        and:
        clazz.createTemplate {
            value "DefaultValue"
            value2 "DefaultValue2"
        }

        when:
        instance = clazz.create("Hallo") {
            value "own"
        }

        then:
        instance.name == "Hallo"
        instance.value == "own"
        instance.value2 == "DefaultValue2"
    }

    def "Lists and Maps in template object should be cloned"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                List<String> names
            }
        ''')

        and:
        clazz.createTemplate {
            names "a", "b"
        }

        when:
        instance = clazz.create {}

        then:
        !instance.names.is(clazz.TEMPLATE.names)
    }

    def "template for parent class affects child instances"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        getClass("pk.Parent").createTemplate {
            name "default"
        }

        when:
        instance = create("pk.Child") {}

        then:
        instance.name == "default"
    }

    def "template for child class sets parent fields"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        getClass("pk.Child").createTemplate {
            name "default"
        }

        when:
        instance = create("pk.Child") {}

        then:
        instance.name == "default"
    }

    def "template for child class sets child fields"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        println "Creating template"
        getClass("pk.Child").createTemplate {
            name "default"
            value "defaultValue"
        }

        when:
        println "Creating instance"
        instance = create("pk.Child") {}

        then:
        instance.name == "default"
        instance.value == "defaultValue"
    }

    def "child template overrides parent template"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        getClass("pk.Parent").createTemplate {
            name "parent"
        }
        getClass("pk.Child").createTemplate {
            name "child"
        }

        expect:
        getClass("pk.Parent").TEMPLATE.class == getClass("pk.Parent")
        getClass("pk.Child").TEMPLATE.class == getClass("pk.Child")

        when:
        instance = create("pk.Child") {}

        then:
        instance.name == "child"
    }

    def "Default value in sub closures"() {
        given:
        createClass('''
            package pk

            @DSL
            class Config {
                Child child
            }

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        getClass("pk.Child").createTemplate {
            name "child"
        }

        when:
        instance = clazz.create {
            child {}
        }

        then:
        instance.child.name == "child"
    }

    def "Default value in sub closures with parent template"() {
        given:
        createClass('''
            package pk

            @DSL
            class Config {
                Child child
            }

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        getClass("pk.Parent").createTemplate {
            name "parent"
        }

        when:
        instance = clazz.create {
            child {}
        }

        then:
        instance.child.name == "parent"
    }

    def "Default value in list closures"() {
        given:
        createClass('''
            package pk

            @DSL
            class Config {
                @Field(members = "child")
                List<Child> children
            }

            @DSL
            class Parent {
                String name
            }

            @DSL
            class Child extends Parent {
                String value
            }
        ''')

        and:
        getClass("pk.Child").createTemplate {
            name "child"
        }

        when:
        instance = clazz.create {
            children {
                child {}
            }
        }

        then:
        instance.children[0].name == "child"
    }


}