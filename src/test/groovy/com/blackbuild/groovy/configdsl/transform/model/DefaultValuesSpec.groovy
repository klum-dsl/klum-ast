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

    def "create template method create template class field"() {
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
        clazz.$TEMPLATE.name == "Welt"
        clazz.$TEMPLATE.value == "Hallo"
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
        !instance.names.is(clazz.$TEMPLATE.names)
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

    def "abstract class creates a artifical implementation"() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Parent {
              def abstract String calcName()
            }
        ''')

        expect:
        getClass('pk.Parent$Template') != null

        when:
        getClass("pk.Parent").createTemplate {
        }

        then:
        notThrown(InstantiationException)
    }

    def "abstract keyed class creates a artifical implementation"() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Parent {
              @Key String name
            }
        ''')

        expect:
        getClass('pk.Parent$Template') != null

        when:
        getClass("pk.Parent").createTemplate {
        }

        then:
        notThrown(InstantiationException)
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
        getClass("pk.Parent").$TEMPLATE.class == getClass("pk.Parent")
        getClass("pk.Child").$TEMPLATE.class == getClass("pk.Child")

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

    @SuppressWarnings("GroovyAssignabilityCheck")
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

    def "order of precedence for templates"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                String name = "default"
            }

            @DSL
            class Child extends Parent {
            }
        ''')

        expect:
        create("pk.Child") {}.name == "default";

        when:
        getClass("pk.Parent").createTemplate {
            name "parent"
        }

        then:
        create("pk.Child") {}.name == "parent";

        when:
        getClass("pk.Child").createTemplate {
            name "child"
        }

        then:
        create("pk.Child") {}.name == "child";

        and:
        create("pk.Child") { name "explicit" }.name == "explicit"
    }

    def "templates add to parent templates collections"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                List<String> names = ["default"]
            }

            @DSL
            class Child extends Parent {
            }
        ''')

        expect:
        create("pk.Child") {}.names == ["default"];

        when:
        getClass("pk.Parent").createTemplate {
            names "parent"
        }

        then:
        create("pk.Child") {}.names == ["default", "parent"];

        when:
        getClass("pk.Child").createTemplate {
            names "child"
        }

        then:
        create("pk.Child") {}.names == ["default", "parent", "child"];

        and:
        create("pk.Child") { name "explicit"}.names == ["default", "parent", "child", "explicit"];
    }

    def "explicitly overrid parent templates collections"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
                List<String> names = ["default"]
            }

            @DSL
            class Child extends Parent {
            }
        ''')
        getClass("pk.Parent").createTemplate {
            names "parent"
        }

        when:
        getClass("pk.Child").createTemplate {
            names = ["child"]
        }

        then:
        create("pk.Child") { name "explicit"}.names == ["child", "explicit"];
    }

    def "BUG: apply overrides overridden values again"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String value
            }
        ''')
        clazz.createTemplate {
            value "default"
        }

        when:
        instance = create("pk.Foo") {
            value "non-default"
        }

        then:
        instance.value == "non-default"

        when:
        instance.apply {}

        then:
        instance.value == "non-default"

    }
}