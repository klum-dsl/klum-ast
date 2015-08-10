package com.blackbuild.groovy.configdsl.transform.model

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import spock.lang.Specification

class DebugSpec extends AbstractDSLSpec {

    def "Can be debugged"() {
        when:
        createClass('''
@DSLConfig
class Config {

    String name

    @DSLField(optional = true) String value
    int age

    //@DSLField("env")
    //Map<String, Environment> environments = [:]
}

@DSLConfig
class Environment {

    String name
    String url
    Authorization authorization
}
@DSLConfig
class Authorization {

    String roles
}
        ''')

        then:
        noExceptionThrown()
    }
}
