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
package com.blackbuild.groovy.configdsl.transform

import com.blackbuild.klum.ast.util.KlumBuilder
import com.blackbuild.klum.ast.util.KlumModelException
import groovyjarjarasm.asm.Opcodes
import org.codehaus.groovy.control.MultipleCompilationErrorsException
import org.codehaus.groovy.runtime.typehandling.GroovyCastException
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
        rwClass.superclass.name == 'pk.Parent$Builder'
    }

    def "RW class inherits parent RW class in different package"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
            }
        ''')

        createSecondaryClass('''            
            package pk2
            @DSL
            class Child extends pk.Parent {
            }
''')

        when:
        Class rwClass = getRwClass('pk2.Child')

        then:
        rwClass.superclass.name == 'pk.Parent$Builder'

        when:
        getClass("pk2.Child").Create.With()

        then:
        noExceptionThrown()
    }

    def "RW class inherits parent RW class in compilation runs"() {
        given:
        createClass('''
            package pk

            @DSL
            class Parent {
            }
        ''')

        createSecondaryClass('''            
            package pk
            @DSL
            class Child extends Parent {
            }
''')

        when:
        Class rwClass = getRwClass('pk.Child')

        then:
        rwClass.superclass.name == 'pk.Parent$Builder'

        when:
        getClass("pk.Child").Create.With()

        then:
        noExceptionThrown()
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
        rwClass.superclass.name == 'pk.Parent$Builder'

        when:
        getClass('pk.Child').Create.With()

        then:
        noExceptionThrown()
    }

    def "Builder methods mutate construction state before materialization"() {
        given:
        createClass('''
            package pk

            @DSL
            class Model {
                @Field(FieldType.TRANSIENT) int count
                String name = 'bla'
                
                @Mutator
                void inc() {
                  count++
                }
            }
        ''')
        when:
        def builder
        instance = clazz.Create.With {
            builder = delegate
            inc()
        }

        then:
        noExceptionThrown()
        builder instanceof KlumBuilder
        builder.count == 1
        builder.name == 'bla'
        instance.count == 1
        instance.name == 'bla'
        builder.completedModel.is(instance)
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

    def "Builder setter contributes to the completed model snapshot"() {
        given:
        createClass('''
            package pk

            @DSL
            class Model {
                String name
            }
        ''')
        when:
        def builder
        instance = clazz.Create.With {
            builder = delegate
            name = 'bla'
        }

        then:
        builder.name == 'bla'
        instance.name == 'bla'

        when: "the captured Builder is sealed after materialization"
        builder.name = 'changed'

        then:
        thrown(KlumModelException)
        instance.name == 'bla'
    }

    def "factory closures delegate to Builders and models expose no apply"() {
        given:
        createClass('''
            package pk

            @DSL
            class Model {
            }
''')
        def Model$RW = getRwClass('pk.Model')

        when:
        def factoryDelegate
        instance = clazz.Create.With {
            factoryDelegate = delegate
        }

        then:
        Model$RW.isInstance(factoryDelegate)
        instance.metaClass.getMetaMethod("apply", Closure) == null
    }

    @Issue("89")
    def "Builder exposes its completed model without coercion"() {
        given:
        createClass('''
            package pk

            @DSL
            class Model {
            }
''')
        def builder
        instance = clazz.Create.With { builder = delegate }

        when:
        builder.asType(clazz)

        then:
        thrown(GroovyCastException)
        builder.completedModel.is(instance)
    }

    @Issue("225")
    def "subclass Builder materializes a model assignable to its DSL superclass"() {
        given:
        createClass('''
            package pk

            @DSL
            abstract class Model {
            }
            @DSL
            class Impl extends Model {
            }
''')
        def builder
        instance = getClass("pk.Impl").Create.With { builder = delegate }

        when:
        builder.asType(getClass("pk.Model"))

        then:
        thrown(GroovyCastException)
        getClass("pk.Model").isInstance(instance)
        builder.completedModel.is(instance)
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
                
                @PostTree Closure postTree
            }
        ''')

        when:
        def script = createSecondaryClass '''
        @groovy.transform.TypeChecked
        class Configuration extends Script {
      
            def run() {
                pk.Container.Create.With {
                    name "parent"
                    foo {
                        postTree {
                            childName "$container.name::child"
                        }
                    }
                }
            }
        }  
'''
        instance = clazz.Create.From(script)

        then:
        notThrown(MultipleCompilationErrorsException)
        instance.foo.childName == "parent::child"
    }

    def "DelegatesToBuilder points to the generated Builder class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                def configure(@DelegatesToBuilder Closure body) {
                    return body
                }
            }
        ''')

        when:
        def method = clazz.getMethod("configure", Closure)

        then:
        delegatesToPointsTo(method.parameterAnnotations[0], 'pk.Foo.Builder')

    }

    def "legacy DelegatesToRW remains a source alias"() {
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
        delegatesToPointsTo(method.parameterAnnotations[0], 'pk.Bar.Builder')
    }

    def "DelegatesToBuilder argument must be a model"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                def bar(@DelegatesToBuilder(String) Closure body) {}
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "DelegatesToBuilder and its legacy alias cannot be combined"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                def configure(@DelegatesToBuilder @DelegatesToRW Closure body) {}
            }
        ''')

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('Use either @DelegatesToBuilder or @DelegatesToRW, not both')
    }

}
