package com.blackbuild.groovy.configdsl.transform.model.mockup

import com.blackbuild.groovy.configdsl.transform.Field
import groovy.transform.Canonical

//@DSL
@Canonical
class Config {

    String name

    @Field String value
    int age

    @Field
    Map<String, Environment> environments = [:]


    def name(String value) {
        this.name = value
    }

    def environments(@DelegatesTo(EnvironmentContext) Closure closure) {
        def context = new EnvironmentContext()
        closure.setDelegate(context)
        closure.setResolveStrategy(Closure.DELEGATE_FIRST)
        closure.call()
    }

    // generated
    Config validate() {
        assert name
        //assert age
        assert environments
        return this
    }

    // generiert
    class EnvironmentContext {

        // generated
        def environment(String name, @DelegatesTo(Environment) Closure closure) {
            def value = Environment.create

            closure.delegate = value
            closure.setResolveStrategy(Closure.DELEGATE_FIRST)
            closure.call()

            environments[name] = value.validate()
        }

        @SuppressWarnings("GroovyAssignabilityCheck")
        @Override
        Object invokeMethod(String name, Object args) {
            if (args?.length == 1 && args[0] instanceof Closure) {
                environment(name, args[0] as Closure)
            } else {
                super.invokeMethod(name, args)
            }
        }
    }
}

@Canonical
class Environment {

    String name
    String url
    Authorization authorization

    def validate() {
        this
    }

    def name(String value) {
        this.name = value
    }

    def url(String value) {
        this.url = value
    }

}

@Canonical
class Authorization {

    List<String> roles

    def roles(String... values) {
        this.roles = values
    }

    def role(String value) {
        this.roles << value
    }
}

@Canonical
class User {

    @SuppressWarnings("GrMethodMayBeStatic")
    def config(@DelegatesTo(Config) Closure closure) {
        Config config = new Config()

        closure.delegate = config
        closure.setResolveStrategy(Closure.DELEGATE_FIRST)
        closure.call()

        return config.validate()
    }


    def doIt() {

        config {
            name("Klaus")
            environments {
                environment("DEV") {
                    url "123"
                }
                environment("NEO_Development") {
                    url "123"
                }

                "SI" {
                    url "345"
                }
            }
        }
    }

}

Config c = new User().doIt()
println c
println c.environments
