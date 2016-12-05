package com.blackbuild.groovy.configdsl.transform

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import spock.lang.Specification

/**
 * No actual test, just a place to quickly debug Transformation.
 */
class DebugSpec extends AbstractDSLSpec {

    def "Can be debugged"() {
        when:
        createClass('''
@DSL
class Config {

    String name

    @Field String value
    int age
}

@DSL
class Environment {

    String name
    String url
    Authorization authorization
}
@DSL
class Authorization {

    String roles
}
        ''')

        then:
        noExceptionThrown()
    }
}
