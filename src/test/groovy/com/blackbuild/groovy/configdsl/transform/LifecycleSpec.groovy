package com.blackbuild.groovy.configdsl.transform

import groovy.transform.NotYetImplemented
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

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

    def "Lifecycle methods must not be private"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @PostCreate
                private void postCreate() {
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "Lifecycle must not be parameterless"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @PostCreate
                void postCreate(String value) {
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
                def postCreate() {
                    isCalled = true
                }
            }
        ''')

        then:
        clazz.create {
            assert isCalled
        }
    }

    @NotYetImplemented
    @Issue('64')
    def "PostCreate methods are called on child objects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Container {
                Foo foo
                List<Foo> listFoos
                Map<String, Foo> mapFoos
            }
            
            @DSL
            class Foo {
                @Key String name

                boolean isCalled
            
                @PostCreate
                def markAsCalled() {
                    isCalled = true
                }
            }
        ''')

        when:
        instance = clazz.create {
            foo("1") {}
            listFoos {
                listFoo("2")
            }
            mapFoos {
                mapFoo("3")
            }
        }

        then:
        instance.foo.isCalled() == true
        instance.listFoos.first().isCalled() == true
        instance.mapFoos["3"].isCalled() == true

    }

    def "PostCreate methods have access to any owner objects"() {
        given:
        createClass('''
            package pk

            @DSL
            class Container {
                Foo foo
                
                String name
            }
            
            @DSL
            class Foo {
                @Owner Container owner
            
                String childName

                @PostCreate
                def setDefaultValueOfChildName() {
                    childName = "$owner.name::child"
                }
            }
        ''')

        when:
        instance = clazz.create {
            name "parent"
            foo {}
        }

        then:
        instance.foo.childName == "parent::child"
    }

    def "Parent and child lifecycle methods are called"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                List<String> caller
            
                @PostCreate
                def postApplyParent() {
                    caller << "Foo"
                }
            }
            @DSL
            class Bar extends Foo {
                @PostCreate
                def postApplyChild() {
                    caller << "Bar"
                }
            }
'''
        when:
        instance = create("pk.Bar") {}

        then:
        instance.caller as Set == ["Foo", "Bar" ] as Set

    }

    def "child overrides parent lifecycle method"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                List<String> caller
            
                @PostCreate
                def postApply() {
                    caller << "Foo"
                }
            }
            @DSL
            class Bar extends Foo {
                @PostCreate
                def postApply() {
                    caller << "Bar"
                }
            }
'''
        when:
        instance = create("pk.Bar") {}

        then:
        instance.caller == ["Bar" ]

    }


}
