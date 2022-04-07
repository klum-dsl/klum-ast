/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2022 Stephan Pauxberger
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
package com.blackbuild.groovy.configdsl.transform

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import static groovyjarjarasm.asm.Opcodes.ACC_PROTECTED

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

    def "Lifecycle must be parameterless"() {
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

                int called
            
                @PostCreate
                def markAsCalled() {
                    called++
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
        instance.foo.called == 1
        instance.listFoos.first().called == 1
        instance.mapFoos["3"].called == 1
    }

    def "PostApply methods are called on child objects"() {
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

                int called
            
                @PostApply
                def markAsCalled() {
                    called++
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
        instance.foo.called == 1
        instance.listFoos.first().called == 1
        instance.mapFoos["3"].called == 1
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
                @Owner Container container
            
                String childName

                @PostCreate
                def setDefaultValueOfChildName() {
                    childName = "$container.name::child"
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

    def "Parent's lifecycle methods are called before child's"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                List<String> caller
            
                @PostCreate
                def postCreateParent() {
                    caller << "Foo"
                }
            }
            @DSL
            class Bar extends Foo {
                @PostCreate
                def postCreateChild() {
                    this.caller << "Bar"
                }
            }
'''
        when:
        instance = create("pk.Bar") {}

        then:
        instance.apply { caller == ["Foo", "Bar"] }

    }

    @Issue("138")
    def "Parent's lifecycle methods are called before child's even when Child is compiled first"() {
        given:
        createClass '''
            package pk

            @DSL
            class Bar extends Foo {
                @PostCreate
                def postCreateChild() {
                    this.caller << "Bar"
                }
            }

            @DSL
            class Foo {
                List<String> caller
            
                @PostCreate
                def postCreateParent() {
                    caller << "Foo"
                }
            }
'''
        when:
        instance = create("pk.Bar") {}

        then:
        instance.apply { caller == ["Foo", "Bar"] }

    }

    def "child overrides parent lifecycle method"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                List<String> caller
            
                @PostCreate
                def postCreate() {
                    caller << "Foo"
                }
            }
            @DSL
            class Bar extends Foo {
                @PostCreate
                def postCreate() {
                    caller << "Bar"
                }
            }
'''
        when:
        instance = create("pk.Bar") {}

        then:
        instance.apply { caller == ["Bar"] }

    }

    @Issue("138")
    def "child overrides parent lifecycle method with reverse compilation order"() {
        given:
        createClass '''
            package pk

            @DSL
            class Bar extends Foo {
                @PostCreate
                def postCreate() {
                    caller << "Bar"
                }
            }

            @DSL
            class Foo {
                List<String> caller
            
                @PostCreate
                def postCreate() {
                    caller << "Foo"
                }
            }
'''
        when:
        instance = create("pk.Bar") {}

        then:
        instance.apply { caller == ["Bar"] }

    }

    def "Overridden lifecycle methods are called from their original place in the hierarchy"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                List<String> caller
            
                @PostCreate
                def postCreateParent() {
                    caller << "FromParent"
                }

                @PostCreate
                def theOtherParent() {
                    caller << "otherParent"
                }
            }
            @DSL
            class Bar extends Foo {
                @PostCreate @Override
                def postCreateParent() {
                    caller << "FromOverridden"
                }

                @PostCreate
                def postCreateChild() {
                    caller << "child"
                }
            }
'''
        when:
        instance = create("pk.Bar") {}

        then:
        instance.caller == ["FromOverridden", "otherParent", "child"]

    }

    def "lifecycle methods are moved to RW class and made protected"() {
        given:
        createClass '''
            package pk

            @DSL
            class Foo {
                @PostCreate
                def postCreate() {
                }
                @PostApply
                def postApply() {
                }
            }
'''
        when:
        def postCreate = rwClazz.getDeclaredMethod("postCreate")
        def postApply = rwClazz.getDeclaredMethod("postApply")

        then:
        noExceptionThrown()
        postCreate.modifiers & ACC_PROTECTED
        postApply.modifiers & ACC_PROTECTED

        when:
        clazz.getMethod("postCreate")

        then:
        thrown(NoSuchMethodException)

        when:
        clazz.getMethod("postApply")

        then:
        thrown(NoSuchMethodException)


    }

    def "PostApply methods can call mutator methods of child"() {
        given:
        createClass('''
            package pk

            @DSL
            class Container {
                Foo foo

                @PostApply
                def markAsCalled() {
                    foo?.name "bla"
                }
            }
            
            @DSL
            class Foo {
                String name
            
            }
        ''')

        when:
        instance = clazz.create {
            foo()
        }

        then:
        instance.foo.name == "bla"
    }

    def "PostApply methods can set fields of inner object"() {
        given:
        createClass('''
            package pk

            @DSL
            class Container {
                Foo foo

                @PostApply
                def markAsCalled() {
                    foo?.name = "bla"
                }
            }
            
            @DSL
            class Foo {
                String name
            
            }
        ''')

        when:
        instance = clazz.create {
            foo()
        }

        then:
        instance.foo.name == "bla"

        when:
        instance = clazz.create {
        }

        then:
        notThrown(NullPointerException)
    }

    def "PostApply methods can set fields of child collections"() {
        given:
        createClass('''
            package pk

            @DSL
            class Container {
                List<Foo> foos

                @PostApply
                def markAsCalled() {
                    foos.each { it.name = "bla" }
                }
            }
            
            @DSL
            class Foo {
                String name
            
            }
        ''')

        when:
        instance = clazz.create {
            foos {
                foo()
                foo()
            }
        }

        then:
        instance.foos.every { it.name == "bla" }

        when:
        instance = clazz.create {
        }

        then:
        notThrown(NullPointerException)
    }
}
