package com.blackbuild.klum.ast.util

import spock.lang.Issue

class ValidatorTest extends AbstractRuntimeTest {

    void "empty validation works"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
            }
        ''')

        when:
        new Validator(instance).execute()

        then:
        noExceptionThrown()
    }

    void "simple validation"() {
        given:
        createInstance('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            class Foo {
                @Validate
                String name
            }
        ''')

        when:
        instance.name = 'test'
        new Validator(instance).execute()

        then:
        noExceptionThrown()

        when:
        instance.name = null
        new Validator(instance).execute()

        then:
        thrown(AssertionError)
    }

    void "simple validation with closure"() {
        given:
        createInstance('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            class Foo {
                // since we don't use AST-Transformation, we need to explicitly use assert
                @Validate({ assert value > 10})
                int value
            }
        ''')

        when:
        new Validator(instance).execute()

        then:
        thrown(AssertionError)

        when:
        instance.value = 200
        new Validator(instance).execute()

        then:
        noExceptionThrown()
    }

    @Issue("https://github.com/klum-dsl/klum-ast/issues/230")
    void "BUG: validator fails on inheritance"() {
        given:
        createClass('''
            package pk
            import com.blackbuild.groovy.configdsl.transform.Validate

            @DSL
            class Foo {
                @Validate int value
            }

            @DSL
            class Bar extends Foo {
            }
        ''')
        instance = newInstanceOf("pk.Bar")

        when:
        new Validator(instance).execute()

        then:
        thrown(AssertionError)

        when:
        instance.value = 200
        new Validator(instance).execute()

        then:
        noExceptionThrown()
    }


}
