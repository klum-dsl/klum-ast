package com.blackbuild.groovy.configdsl.transform.model

import com.blackbuild.groovy.configdsl.transform.ValidationException
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Ignore

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
        thrown(ValidationException)
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
        def e = thrown(ValidationException)
        e.message == "We need a name"
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
        thrown(ValidationException)
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
        notThrown(ValidationException)
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
        thrown(ValidationException)

        when:
        clazz.create { validated "bla"}

        then:
        thrown(ValidationException)

        when:
        clazz.create { validated "valid"}

        then:
        thrown(ValidationException)
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
        thrown(ValidationException)

        when:
        clazz.create { validated "bla"}

        then:
        thrown(ValidationException)

        when:
        clazz.create { validated "valid"}

        then:
        thrown(ValidationException)
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
        notThrown(ValidationException)

        when:
        instance.validate()

        then:
        thrown(ValidationException)

        when:
        instance.validated "bla"
        instance.validate()

        then:
        notThrown(ValidationException)
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
        notThrown(ValidationException)

        when:
        instance.validate()

        then:
        thrown(ValidationException)

        when:
        instance.validated "bla"
        instance.validate()

        then:
        notThrown(ValidationException)
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
        notThrown(ValidationException)
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
        notThrown(ValidationException)
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
        thrown(ValidationException)

        when:
        instance = clazz.create {
            validated "bla"
        }

        then:
        notThrown(ValidationException)
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
        thrown(ValidationException)
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
        thrown(ValidationException)

        when:
        clazz.create {
            value1 "b"
            value2 "bla"
        }

        then:
        notThrown(ValidationException)
    }

}
