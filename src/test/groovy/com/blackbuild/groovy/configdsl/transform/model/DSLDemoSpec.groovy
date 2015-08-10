package com.blackbuild.groovy.configdsl.transform.model

class DSLDemoSpec extends AbstractDSLSpec {

    def "Real Life demo"() {
        given:
        createClass('''
            package com.blackbuild.groovy.configdsl.transform.test

            @DSLConfig
            class Config {
                String name

                Map<String, Environment> envs
            }

            @DSLConfig(key = "name")
            class Environment {
                String name
                String value
            }
        ''')

        when:
        instance = clazz.create {
            name "Demo"

            envs {
                env("Dev") {
                    value "active"
                }
            }
        }

        then:
        instance.envs.Dev.value == "active"
    }



}
