package com.blackbuild.groovy.configdsl.transform.model

import com.blackbuild.groovy.configdsl.transform.ValidationException

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
        instance.$validate()


        then:
        thrown(ValidationException)
    }


}
