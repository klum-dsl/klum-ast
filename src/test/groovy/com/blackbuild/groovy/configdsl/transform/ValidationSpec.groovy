package com.blackbuild.groovy.configdsl.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

class ValidationSpec extends AbstractDSLSpec {

    def "validation with Groovy Truth"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated
            }
        ''')

        when:
        clazz.create {}

        then:
        thrown(IllegalStateException)
    }

    @Issue("25")
    def "validation of nested elements does not work"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated

                @Validate
                Inner inner
            }

            @DSL
            class Inner {
                @Validate
                String value
            }
        ''')

        when: 'missing inner instance'
        clazz.create {
            validated "correct"
        }

        then:
        thrown(IllegalStateException)

        when: 'inner instance does not validate'
        clazz.create {
            validated "correct"
            inner {}
        }

        then:
        thrown(IllegalStateException)

        when:
        clazz.create {
            validated "correct"
            inner {
                value "valid"
            }
        }

        then:
        notThrown(IllegalStateException)
    }

    @Issue("25")
    def "validation of nested list elements"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated

                @Validate
                List<Inner> inners
            }

            @DSL
            class Inner {
                @Validate
                String value
            }
        ''')

        when: 'inners is empty'
        clazz.create {
            validated "correct"
        }

        then:
        thrown(IllegalStateException)

        when:
        clazz.create {
            validated "correct"
            inners {
                inner {}
            }
        }

        then:
        thrown(IllegalStateException)

        when:
        clazz.create {
            validated "correct"
            inners {
                inner {
                    value "valid"
                }
            }
        }

        then:
        notThrown(IllegalStateException)
    }

    @Issue("25")
    def "validation of nested map elements"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated

                @Validate
                Map<String, Inner> inners
            }

            @DSL
            class Inner {
                @Key
                String name
                @Validate
                String value
            }
        ''')

        when: 'inners is empty'
        clazz.create {
            validated "correct"
        }

        then:
        thrown(IllegalStateException)

        when:
        clazz.create {
            validated "correct"
            inners {
                inner("bla") {}
            }
        }

        then:
        thrown(IllegalStateException)

        when:
        clazz.create {
            validated "correct"
            inners {
                inner("bla") {
                    value "valid"
                }
            }
        }

        then:
        notThrown(IllegalStateException)
    }



    def "validation with default message"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String name
            }
        ''')

        when:
        clazz.create {}

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith("'name' must be set!")
    }

    def "validation with message"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate(message = "We need a name")
                String name
            }
        ''')

        when:
        clazz.create {}

        then:
        def e = thrown(IllegalStateException)
        e.message.startsWith("We need a name")
    }

    def "validation with explicit Groovy Truth"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate(Validate.GroovyTruth)
                String validated
            }
        ''')

        when:
        clazz.create {}

        then:
        thrown(IllegalStateException)
    }

    def "validation with Ignore"() {
        given:
        createClass('''
            @DSL @Validation(option = Validation.Option.VALIDATE_UNMARKED)
            class Foo {
                @Validate(Validate.Ignore)
                String validated
            }
        ''')

        when:
        clazz.create {}

        then:
        notThrown(IllegalStateException)
    }

    def "validation with Closure"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate({ it.length > 3 })
                String validated
            }
        ''')

        when:
        clazz.create {}

        then:
        thrown(IllegalStateException)

        when:
        clazz.create { validated "bla"}

        then:
        thrown(IllegalStateException)

        when:
        clazz.create { validated "valid"}

        then:
        thrown(IllegalStateException)
    }

    def "validation with named Closure"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate({ string -> string.length > 3 })
                String validated
            }
        ''')

        when:
        clazz.create {}

        then:
        thrown(IllegalStateException)

        when:
        clazz.create { validated "bla"}

        then:
        thrown(IllegalStateException)

        when:
        clazz.create { validated "valid"}

        then:
        thrown(IllegalStateException)
    }

    def "validation only allows GroovyTruth, Ignore or literal closure"() {
        when:
        createClass('''
            @DSL
            class Foo {
                @Validate(String.class)
                String validated
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "defer validation via method"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated
            }
        ''')

        when:
        instance = clazz.create {
            manualValidation(true)
        }

        then:
        notThrown(IllegalStateException)

        when:
        instance.validate()

        then:
        thrown(IllegalStateException)

        when:
        instance.validated "bla"
        instance.validate()

        then:
        notThrown(IllegalStateException)
    }

    def "defer validation via annotation"() {
        given:
        createClass('''
            @DSL
            @Validation(mode = Validation.Mode.MANUAL)
            class Foo {
                @Validate
                String validated
            }
        ''')

        when:
        instance = clazz.create {}

        then:
        notThrown(IllegalStateException)

        when:
        instance.validate()

        then:
        thrown(IllegalStateException)

        when:
        instance.validated "bla"
        instance.validate()

        then:
        notThrown(IllegalStateException)
    }

    def "validation is not performed on templates"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate String validated
                String nonValidated
            }
        ''')

        when:
        instance = clazz.createTemplate {
            nonValidated "bla"
        }

        then:
        notThrown(IllegalStateException)
    }

    def "non annotated fields are not validated"() {
        given:
        createClass('''
            @DSL
            class Foo {
                @Validate
                String validated

                String notvalidated
            }
        ''')

        when:
        clazz.create {
            validated "bla"
        }

        then:
        notThrown(IllegalStateException)
    }

    def "Option.VALIDATE_UNMARKED validates all unmarked fields"() {
        given:
        createClass('''
            @DSL
            @Validation(option = Validation.Option.VALIDATE_UNMARKED)
            class Foo {
                String validated
            }
        ''')

        when:
        instance = clazz.create {}

        then:
        thrown(IllegalStateException)

        when:
        instance = clazz.create {
            validated "bla"
        }

        then:
        notThrown(IllegalStateException)
    }

    def "validation is inherited"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Validate
                String validated
            }

            @DSL
            class Bar extends Foo {
                String child
            }
        ''')

        when:
        instance = create("pk.Bar") {}

        then:
        thrown(IllegalStateException)
    }

    def "explicit validation method"() {
        given:
        createClass('''
            @DSL
            class Foo {
                String value1
                String value2

                def doValidate() {
                    assert value1.length() < value2.length()
                }
            }
        ''')

        when:
        clazz.create {
            value1 "bla"
            value2 "b"
        }

        then:
        thrown(IllegalStateException)

        when:
        clazz.create {
            value1 "b"
            value2 "bla"
        }

        then:
        notThrown(IllegalStateException)
    }

}
