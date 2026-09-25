/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2026 Stephan Pauxberger
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
package com.blackbuild.klum.ast

import com.blackbuild.klum.ast.runtime.KlumModelException
import com.blackbuild.klum.ast.runtime.internal.layer3.KlumVisitorException
import groovy.lang.MissingMethodException
import spock.lang.Issue

class NamedMapDiagnosticTest extends AbstractDSLSpec {

    @Issue("794")
    def "unknown root named-map key reports the Model and retains the dispatch failure"() {
        given:
        createClass '''
            @DSL class Deployment {
                String name
            }
        '''

        when:
        clazz.Create.With([nmae: 'catalog'])

        then:
        def error = thrown(KlumModelException)
        error.message.contains("Unknown named-map Builder call 'nmae'")
        error.message.contains('Model Deployment')
        error.message.contains('public Builder method with exactly one argument')
        error.cause instanceof MissingMethodException
        error.cause.method == 'nmae'
        error.cause.type.name in [clazz.name, builderClass.name]
    }

    @Issue("794")
    def "unknown fixed-child named-map key reports the child Model"() {
        given:
        createClass '''
            @DSL class Deployment {
                Service service
            }

            @DSL class Service {
                String image
            }
        '''

        when:
        clazz.Create.With {
            service(nmae: 'catalog')
        }

        then:
        def error = thrown(KlumModelException)
        error.message.contains("Unknown named-map Builder call 'nmae'")
        error.message.contains('Model Service')
        error.cause instanceof MissingMethodException
        error.cause.method == 'nmae'
    }

    @Issue("794")
    def "Template named maps report unknown Builder keys"() {
        given:
        createClass '''
            @DSL class Deployment {
                String name
            }
        '''

        when:
        clazz.Create.Template.With([nmae: 'catalog'])

        then:
        def error = thrown(KlumModelException)
        error.message.contains("Unknown named-map Builder call 'nmae'")
        error.message.contains('Model Deployment')
        error.cause.method == 'nmae'
    }

    @Issue("794")
    def "AutoCreate child named maps report unknown Builder keys"() {
        given:
        createClass '''
            import com.blackbuild.klum.ast.layer3.AutoCreate
            import groovy.transform.TypeChecked
            import groovy.transform.TypeCheckingMode

            @DSL class Deployment {
                Service service

                @AutoCreate
                @TypeChecked(TypeCheckingMode.SKIP)
                void createService() {
                    service(nmae: 'catalog')
                }
            }

            @DSL class Service {
                String name
            }
        '''

        when:
        clazz.Create.With()

        then:
        def error = thrown(KlumVisitorException)
        error.cause.class.name == KlumModelException.name
        error.cause.message.contains("Unknown named-map Builder call 'nmae'")
        error.cause.message.contains('Model Service')
    }

    @Issue("794")
    def "does not translate a missing method thrown from inside a selected configurator"() {
        given:
        createClass '''
            @DSL class Deployment {
                String name

                @Builder.Method
                void configure(String value) {
                    throw new MissingMethodException('configure', this.class, [value] as Object[])
                }
            }
        '''

        when:
        clazz.Create.With([configure: 'catalog'])

        then:
        def error = thrown(MissingMethodException)
        error.method == 'configure'
    }

    @Issue("794")
    def "named maps retain ordinary dispatch and partial application before a later unknown key"() {
        given:
        createClass '''
            @DSL class Deployment extends Parent {
                String name
                String methodValue

                @Builder.Method
                void name(String value) {
                    methodValue = value
                }

                @Builder.Method
                void recordApplied(String value) {
                    System.setProperty('klum.namedMapDiagnostic.applied', value)
                }

                @PostApply
                void recordApply() {
                    System.setProperty('klum.namedMapDiagnostic.lifecycle', 'called')
                }
            }

            @DSL class Parent {
                String inherited
            }
        '''
        clazz = getClass('Deployment')
        builderClass = getBuilderClass(clazz.name)
        System.clearProperty('klum.namedMapDiagnostic.applied')
        System.clearProperty('klum.namedMapDiagnostic.lifecycle')
        def marker = UUID.randomUUID().toString()

        when:
        clazz.Create.With([recordApplied: marker, name: 'method', setName: 'field', inherited: 'parent', typo: 'x'])

        then:
        def error = thrown(KlumModelException)
        System.getProperty('klum.namedMapDiagnostic.applied') == marker
        System.getProperty('klum.namedMapDiagnostic.lifecycle') == null
        error.cause instanceof MissingMethodException
        error.cause.method == 'typo'

        cleanup:
        System.clearProperty('klum.namedMapDiagnostic.applied')
        System.clearProperty('klum.namedMapDiagnostic.lifecycle')
    }

    @Issue("794")
    def "successful MetaClass Builder methods still receive named-map calls"() {
        given:
        createClass '''
            @DSL class Deployment {
                String dynamicValue
            }
        '''
        def expando = new ExpandoMetaClass(builderClass, true, true)
        expando.metaKey = { String value -> delegate.dynamicValue = value }
        expando.initialize()
        GroovySystem.metaClassRegistry.setMetaClass(builderClass, expando)

        when:
        instance = clazz.Create.With([metaKey: 'meta'])

        then:
        instance.dynamicValue == 'meta'

        cleanup:
        GroovySystem.metaClassRegistry.removeMetaClass(builderClass)
    }

    @Issue(["794", "729"])
    def "generated methodMissing bridge keeps its specialized named-map diagnostic"() {
        given:
        createClass '''
            @DSL class Root {
                Child child
            }

            @DSL class Child {
                String value

                static Child fromString(String value) {
                    return materialize(value)
                }

                private static Child materialize(String value) {
                    return Child.Create.With()
                }
            }
        '''

        when:
        clazz.Create.With([child: 'nested'])

        then:
        def error = thrown(KlumModelException)
        error.message.contains('omitted Builder-producing projection child(java.lang.String)')
        !error.message.contains('Unknown named-map Builder call')
    }

    @Issue("794")
    def "Model methodMissing remains available to dynamic Model calls"() {
        given:
        createClass '''
            @DSL class Deployment {
                static int missingCalls = 0

                def methodMissing(String name, args) {
                    missingCalls++
                }
            }
        '''

        when:
        instance = clazz.Create.With().customCall('value')

        then:
        clazz.missingCalls == 1
    }
}
