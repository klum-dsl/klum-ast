/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2017 Stephan Pauxberger
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
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
            }
        }
    }

}

Config c = new User().doIt()
println c
println c.environments
