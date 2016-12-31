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

    def "PostApply methods are called"() {
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

    def "PostCreate methods are called"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                boolean isCalled
                
                @PostCreate
                def postApply() {
                    isCalled = true
                }
            }
        ''')

        then:
        clazz.create {
            assert isCalled
        }
    }

    def "it is illegal to call a super method in lifecycle method"() {
        when:
        createClass '''
            package pk

            @DSL
            class Foo {
                @PostCreate
                def postApply() {
                }
            }
            @DSL
            class Bar extends Foo {
                @PostCreate
                def postApply() {
                    super.postApply()
                }
            }
'''
        then:
        thrown MultipleCompilationErrorsException

    }

    def "it is allowed to call a different super method in lifecycle method"() {
        when:
        createClass '''
            package pk

            @DSL
            class Foo {
                def other() {
                }
            }
            @DSL
            class Bar extends Foo {
                @PostCreate
                def postApply() {
                    super.other()
                }
            }
'''
        then:
        notThrown MultipleCompilationErrorsException

    }


}
