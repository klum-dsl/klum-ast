package com.blackbuild.groovy.configdsl.transform.model

import com.blackbuild.groovy.configdsl.transform.ValidationException
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
        instance = clazz.create {}

        then:
        thrown(ValidationException)
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
            @DSL(validationMode = ValidationMode.MANUAL)
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

}
