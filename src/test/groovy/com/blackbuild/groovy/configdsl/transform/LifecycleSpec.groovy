package com.blackbuild.groovy.configdsl.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException

class LifecycleSpec extends AbstractDSLSpec {

    def "apply method must not be declared"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                Foo apply(Closure c) {
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "create method must not be declared"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                static Foo create(Closure c) {
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "create _apply method when apply exists"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Foo {
                boolean isCalled
                
                @PostApply
                def postApply() {
                    isCalled = true
                }
            }
        ''')

        when:
        instance.apply {}

        then:
        instance.isCalled == true
    }


}
