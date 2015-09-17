package com.blackbuild.groovy.configdsl.transform.model

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import spock.lang.Specification

class DebugSpec extends AbstractDSLSpec {

    def "Can be debugged"() {
        when:
        createClass('''
@DSL
class Config {

    String name

    @Field(optional = true) String value
    int age

    //@Field("env")
    //Map<String, Environment> environments = [:]
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
