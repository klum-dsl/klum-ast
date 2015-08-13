package com.blackbuild.groovy.configdsl.transform.model

import org.codehaus.groovy.control.MultipleCompilationErrorsException
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

        expect:
        create("pk.Bar") {}.class.name == "pk.Bar"

        when:
        instance = create("pk.Bar") {
            name "Klaus"
            value "High"
        }

        then:
        instance.name == "Klaus"
        instance.value == "High"
    }

    def "parent class defines key"() {
        given:
        createClass('''
            package pk

            @DSLConfig(key = "name")
            class Foo {
                String name
                String parentValue
            }

            @DSLConfig
            class Bar extends Foo {
                String value
            }
        ''')

        when:
        instance = create("pk.Bar", "Klaus") {
            parentValue "Low"
            value "High"
        }

        then:
        instance.name == "Klaus"
        instance.value == "High"
        instance.parentValue == "Low"
    }

    def "parent class defines no key, child does leads to exception"() {
        when:
        createClass('''
            package pk

            @DSLConfig
            class Foo {
                String name
                String parentValue
            }

            @DSLConfig(key = "name")
            class Bar extends Foo {
                String value
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "parent class defines no key, child defines different key"() {
        when:
        createClass('''
            package pk

            @DSLConfig(key = "name")
            class Foo {
                String name
                String parentValue
            }

            @DSLConfig(key = "value")
            class Bar extends Foo {
                String value
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }


    def "Polymorphic closure methods"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Owner {
                Foo foo
            }

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
        instance = create("pk.Owner") {
            foo(getClass("pk.Bar")) {
                name = "klaus"
                value = "dieter"
            }
        }

        then:
        instance.foo.class.name == "pk.Bar"
        instance.foo.name == "klaus"
        instance.foo.value == "dieter"
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "Polymorphic list methods"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Owner {
                List<Foo> foos
            }

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
        instance = create("pk.Owner") {

            foos {
                foo(getClass("pk.Bar")) {
                    name = "klaus"
                    value = "dieter"
                }
                foo {
                    name = "heinz"
                }
            }
        }

        then:
        instance.foos[0].class.name == "pk.Bar"
        instance.foos[0].name == "klaus"
        instance.foos[0].value == "dieter"
        instance.foos[1].class.name == "pk.Foo"
        instance.foos[1].name == "heinz"

    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "Polymorphic list methods with keys"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Owner {
                List<Foo> foos
            }

            @DSLConfig(key = "name")
            class Foo {
                String name
            }

            @DSLConfig
            class Bar extends Foo {
                String value
            }
        ''')

        when:
        instance = create("pk.Owner") {

            foos {
                foo(getClass("pk.Bar"), "klaus") {
                    value = "dieter"
                }
                foo("heinz") {
                }
            }
        }

        then:
        instance.foos[0].class.name == "pk.Bar"
        instance.foos[0].name == "klaus"
        instance.foos[0].value == "dieter"
        instance.foos[1].class.name == "pk.Foo"
        instance.foos[1].name == "heinz"

    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "Polymorphic map methods"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Owner {
                Map<String, Foo> foos
            }

            @DSLConfig(key = "name")
            class Foo {
                String name
            }

            @DSLConfig
            class Bar extends Foo {
                String value
            }
        ''')

        when:
        instance = create("pk.Owner") {

            foos {
                foo(getClass("pk.Bar"), "klaus") {
                    value = "dieter"
                }
                foo("heinz") {
                }
            }
        }

        then:
        instance.foos.klaus.class.name == "pk.Bar"
        instance.foos.klaus.name == "klaus"
        instance.foos.klaus.value == "dieter"
        instance.foos.heinz.class.name == "pk.Foo"
        instance.foos.heinz.name == "heinz"
    }

    def "abstract fields must not have a non polymorphic accessor"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Owner {
                Foo foo
            }

            @DSLConfig
            abstract class Foo {
                String name
            }
        ''')

        when:
        clazz.getMethod("foo", Closure)

        then:
        thrown(NoSuchMethodException)

        when:
        clazz.getMethod("foo", Class, Closure)

        then:
        noExceptionThrown()
    }

    def "lists of abstract fields must not have polymorphic accessors"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Owner {
                List<Foo> foos
            }

            @DSLConfig
            abstract class Foo {
                String name
            }
        ''')

        when:
        instance = create("pk.Owner") {

            foos {
                foo {}
            }
        }

        then:
        thrown(MissingMethodException)
    }

    def "maps of abstract fields must not have polymorphic accessors"() {
        given:
        createClass('''
            package pk

            @DSLConfig
            class Owner {
                Map<String, Foo> foos
            }

            @DSLConfig(key = "name")
            abstract class Foo {
                String name
            }
        ''')

        when:
        instance = create("pk.Owner") {

            foos {
                foo("Bla") {}
            }
        }

        then:
        thrown(MissingMethodException)
    }


}