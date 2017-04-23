/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.blackbuild.groovy.configdsl.transform
import org.codehaus.groovy.control.MultipleCompilationErrorsException

class InheritanceSpec extends AbstractDSLSpec {

    def "objects inheriting from DSLObjects are also DSLObjects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }

            @DSL
            class Bar extends Foo {
                String value
            }
        ''')

        expect:
        def clazz = getClass("pk.Bar")
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

            @DSL
            class Foo {
                @Key String name
                String parentValue
            }

            @DSL
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

    def "grandparent class defines key"() {
        given:
        createClass('''
            package pk

            @DSL
            class A {
                @Key String name
            }

            @DSL
            class B extends A {
            }

            @DSL
            class C extends B {
            }

            @DSL
            class D extends C {
            }

            @DSL
            class E extends D {
            }
        ''')

        when:
        instance = create("pk.E", "Klaus") {}

        then:
        instance.name == "Klaus"
    }

    def "error: parent class defines no key, but child defines key"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                String parentValue
            }

            @DSL
            class Bar extends Foo {
                @Key String value
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "error: parent class defines key, child defines different key"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Key String name
                String parentValue
            }

            @DSL
            class Bar extends Foo {
                @Key String value
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "Polymorphic closure methods"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                Foo foo
            }

            @DSL
            class Foo {
                String name
            }

            @DSL
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

        when: 'Named parameters'
        instance = create("pk.Owner") {
            foo(getClass("pk.Bar"), name: "klaus", value: "dieter")
        }

        then:
        instance.foo.class.name == "pk.Bar"
        instance.foo.name == "klaus"
        instance.foo.value == "dieter"
    }

    def "final classes don't create polymorphic closure methods"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                Foo foo
            }

            @DSL
            final class Foo {}
        ''')

        when:
        clazz.getDeclaredMethod("foo", Class, Closure)

        then:
        thrown(NoSuchMethodException)
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "Polymorphic list methods"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                List<Foo> foos
            }

            @DSL
            class Foo {
                String name
            }

            @DSL
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

        when: 'named parameters'
        instance = create("pk.Owner") {

            foos {
                foo(getClass("pk.Bar"), name: "klaus", value: "dieter")
                foo(name: "heinz")
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
    def "list methods with abstract element type must force polymorphic"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                List<Foo> foos
            }

            @DSL
            abstract class Foo {
                String name
            }

            @DSL
            class Bar extends Foo {
                String value
            }
        ''')

        when:
        instance = create("pk.Owner") {

            foos {
                foo {
                    name = "heinz"
                }
            }
        }

        then:
        thrown(MissingMethodException)

    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "no polymorphic list methods for final elements"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                List<Foo> foos
            }

            @DSL
            final class Foo {
                String name
            }
        ''')

        when:
        instance = create("pk.Owner") {

            foos {
                foo(getClass("pk.Foo")) {}
            }
        }

        then:
        thrown(MissingMethodException)
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "Polymorphic list methods with keys"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                List<Foo> foos
            }

            @DSL
            class Foo {
                @Key String name
            }

            @DSL
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

        when: 'with named parameters'
        instance = create("pk.Owner") {

            foos {
                foo(getClass("pk.Bar"), "klaus", value: "dieter")
                foo("heinz")
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

            @DSL
            class Owner {
                Map<String, Foo> foos
            }

            @DSL
            class Foo {
                @Key String name
            }

            @DSL
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

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "no polymorphic map methods for final elements"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                Map<String, Foo> foos
            }

            @DSL
            final class Foo {
                @Key String name
            }
        ''')

        when:
        instance = create("pk.Owner") {

            foos {
                foo(getClass("pk.Foo"), "Klaus") {}
            }
        }

        then:
        thrown(MissingMethodException)
    }

    def "abstract fields must not have a non polymorphic accessor"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                Foo foo
            }

            @DSL
            abstract class Foo {
                String name
            }
        ''')

        when:
        rwClazz.getMethod("foo", Closure)

        then:
        thrown(NoSuchMethodException)

        when:
        rwClazz.getMethod("foo", Class, Closure)

        then:
        noExceptionThrown()
    }

    def "lists of abstract fields must not have non polymorphic accessors"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                List<Foo> foos
            }

            @DSL
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

    def "maps of abstract fields must not have non polymorphic accessors"() {
        given:
        createClass('''
            package pk

            @DSL
            class Owner {
                Map<String, Foo> foos
            }

            @DSL
            abstract class Foo {
                @Key String name
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

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "bug: owner setter on child class throws StackOverflowException"() {
        given:
        createClass('''
            package pk

            @DSL
            class Container {
                Foo inner
            }

            @DSL
            class Foo {
                @Owner Object container
            }

            @DSL
            class Bar extends Foo {
                String value
            }
        ''')

        when:
        create("pk.Container") {
            inner {}
        }

        then:
        notThrown(StackOverflowError)
    }

    def "BUG: equals does not work correctly for inherited objects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
            }

            @DSL
            class Bar extends Foo { }
        ''')

        when:
        def left = getClass("pk.Bar").create(name: "a")
        def right = getClass("pk.Bar").create(name: "b")

        then:
        !left.equals(right)
    }

    def "member names must be unique across hierarchies"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Field(members = 'it')
                List<String> values
            }

            @DSL
            class Bar extends Foo {
                @Field(members = 'it')
                List<String> things
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

}