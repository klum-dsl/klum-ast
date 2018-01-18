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
package com.blackbuild.groovy.configdsl.transform

import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

import static com.blackbuild.groovy.configdsl.transform.TestHelper.delegatesToPointsTo

@SuppressWarnings("GroovyAssignabilityCheck")
class RWClassSpec extends AbstractDSLSpec {

    def "RW class is created"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
            }
        ''')

        then:
        noExceptionThrown()
        rwClazz != null
    }

    def "RW class inherits parent RW class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
            }
            
            @DSL
            class Child extends Parent {
            }
            
        ''')

        when:
        Class rwClass = getRwClass('pk.Child')

        then:
        rwClass.superclass.name == 'pk.Parent$_RW'
    }

    def "BUG: RW class should inherit parent RW class regardless of order"() {
        given:
        createClass('''
            package pk

            @DSL
            class Child extends Parent {
            }

            @DSL
            class Parent {
            }
        ''')

        when:
        Class rwClass = getRwClass('pk.Child')

        then:
        rwClass.superclass.name == 'pk.Parent$_RW'
    }

    def "RW class delegates to model"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Model {
                @Field(FieldType.TRANSIENT) int count
                String name = 'bla'
                
                void inc() {
                  count++
                }
            }
        ''')
        def rw = instance.$rw

        when:
        rw.inc()

        then:
        noExceptionThrown()
        instance.count == 1
        rw.name == 'bla'
    }

    def "RW class does have public setters for model, model does not"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Model {
                String name
            }
        ''')

        when:
        def rwSetNameMethod = rwClazz.getMethod("setName", String)

        then:
        rwSetNameMethod.modifiers & Opcodes.ACC_PUBLIC

        when:
        clazz.getMethod("setName", String)

        then:
        thrown(NoSuchMethodException)
    }

    def "RW setter writes through to model"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Model {
                String name
            }
        ''')
        def rw = instance.$rw

        when:
        rw.name = 'bla'

        then:
        instance.name == 'bla'
    }

    def "model.apply delegates to RW"() {
        when:
        createInstance('''
            package pk

            @DSL
            class Model {
            }
''')
        def Model$RW = getRwClass('pk.Model')

        then:
        instance.apply {
            assert Model$RW.isInstance(delegate)
        }
    }

    @Issue("89")
    def "RW instance can be coerced to model"() {
        given:
        createInstance('''
            package pk

            @DSL
            class Model {
            }
''')
        def rw = instance.$rw

        when:
        def coerced = rw.asType(clazz)

        then:
        coerced == instance
    }

    @Issue("99")
    def "config closures for inner objects have access to their owner field with static type checking enabled"() {
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
            }
        ''')

        when:
        def script = createSecondaryClass '''
        @groovy.transform.TypeChecked
        class Configuration extends Script {
      
            def run() {
                pk.Container.create {
                    name "parent"
                    foo {
                        childName "$container.name::child"                    
                    }
                }
            }
        }  
'''
        instance = clazz.createFrom(script)

        then:
        notThrown(MultipleCompilationErrorsException)
        instance.foo.childName == "parent::child"
    }

    def "script allow delegation to RW class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                def configure(@DelegatesToRW Closure body) {
                    apply(body)
                }
            }
        ''')

        when:
        def method = clazz.getMethod("configure", Closure)

        then:
        delegatesToPointsTo(method.parameterAnnotations[0], 'pk.Foo._RW')

    }

    def "script allow delegation different RW class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                Bar bar
            
                @Mutator
                def aBar(@DelegatesToRW(Bar) Closure body) {
                    bar(body)
                }
            }

            @DSL
            class Bar {
            }
        ''')

        when:
        def method = rwClazz.getMethod("aBar", Closure)

        then:
        delegatesToPointsTo(method.parameterAnnotations[0], 'pk.Bar._RW')
    }

    def "delegatesToRW argument must be a model"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                def bar(@DelegatesToRW(String) Closure body) {}
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

}
