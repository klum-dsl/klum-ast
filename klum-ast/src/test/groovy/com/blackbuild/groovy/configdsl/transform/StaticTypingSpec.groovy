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

import org.codehaus.groovy.control.MultipleCompilationErrorsException
import spock.lang.Issue

@SuppressWarnings("GroovyAssignabilityCheck")
class StaticTypingSpec extends AbstractDSLSpec {

    def "static type checking with illegal method call"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def shouldFail() {
                    name.help()
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "static type checking can be disabled per method"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                @groovy.transform.TypeChecked(groovy.transform.TypeCheckingMode.SKIP)
                def shouldNotFail() {
                    name.help()
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "static type checking can be disabled for the whole model"() {
        when:
        createClass('''
            package pk

            @DSL
            @groovy.transform.TypeChecked(groovy.transform.TypeCheckingMode.SKIP)
            class Foo {
                String name
                
                def shouldNotFail() {
                    name.help()
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    @Issue('654')
    def "static type checking rejects completed-model instanceof checks on Builder relationship values"() {
        when:
        createClass('''
            package pk

            @DSL
            class Child { }

            @DSL
            class Parent {
                Child child

                @Mutator
                void classifyChild() {
                    assert child instanceof Child
                }
            }
        ''')

        then:
        def failure = thrown(MultipleCompilationErrorsException)
        failure.message.contains('relationship value is a Builder before materialization')
        failure.message.contains('Child_DSL$Builder')
        failure.message.contains('Builder-First-Migration.md')
    }

    @Issue('654')
    def "static type checking rejects completed-model instanceof checks in Builder lifecycle and annotation closures"() {
        when:
        createClass('''
            package pk

            @DSL
            class Child { }

            @DSL
            class LifecycleParent {
                Child child

                @PostTree
                void classifyChild() {
                    assert child instanceof Child
                }
            }
        ''')

        then:
        def lifecycleFailure = thrown(MultipleCompilationErrorsException)
        lifecycleFailure.message.contains('relationship value is a Builder before materialization')

        when:
        createClass('''
            package pk

            @DSL
            class Child { }

            @DSL
            class AnnotationParent {
                Child child

                @Default(code = { child instanceof Child ? 'builder' : 'model' })
                String lifecycleState
            }
        ''')

        then:
        def annotationFailure = thrown(MultipleCompilationErrorsException)
        annotationFailure.message.contains('relationship value is a Builder before materialization')
    }

    @Issue('654')
    def "static type checking permits completed-model, ordinary, and Object instanceof checks"() {
        when:
        createClass('''
            package pk

            @DSL
            class Child { }

            @DSL
            class Parent {
                Child child
                String name

                @Mutator
                void classifySafely(Object unknown) {
                    assert unknown instanceof Child
                    assert name instanceof String
                }

                @Validate
                void validateCompletedChild() {
                    assert child instanceof Child
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "def typed methods are allowed"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def shouldFail() {
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    @Issue('656')
    def "static type checking identifies nested root factories in Builder-phase methods"() {
        when:
        createClass('''
            package pk

            @DSL
            class Child { }

            @DSL
            class Parent {
                Child child

                @Mutator
                void configureChild() {
                    child = Child.Create.With()
                }
            }
        ''')

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('Child.Create.With starts a completed-model root factory')
        error.message.contains('Child.Create.AsBuilder().With')
        error.message.contains('attach the returned Builder to an owned relationship')
    }

    @Issue('656')
    def "static type checking identifies nested root factories in Builder lifecycle and annotation closures"() {
        when:
        createClass('''
            package pk

            @DSL
            class Child { }

            @DSL
            class LifecycleParent {
                Child child

                @PostTree
                void configureChild() {
                    child = Child.Create.One()
                }
            }
        ''')

        then:
        MultipleCompilationErrorsException lifecycleError = thrown()
        lifecycleError.message.contains('Child.Create.One starts a completed-model root factory')

        when:
        createClass('''
            package pk

            @DSL
            class Child { }

            @DSL
            class AnnotationParent {
                @Default(code = { Child.Create.With() })
                Child child
            }
        ''')

        then:
        MultipleCompilationErrorsException annotationError = thrown()
        annotationError.message.contains('Child.Create.With starts a completed-model root factory')
    }

    @Issue('656')
    def "static type checking identifies nested From root factories in Builder-phase code"() {
        when:
        createClass('''
            package pk

            @DSL
            class Child { }

            @DSL
            class Parent {
                Child child

                @Mutator
                void configureChild() {
                    child = Child.Create.From('')
                }
            }
        ''')

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('Child.Create.From starts a completed-model root factory')
        error.message.contains('Child.Create.AsBuilder().From')
    }

    @Issue('656')
    def "static type checking permits Builder composition, validation root factories, static factories, and non-DSL Create"() {
        when:
        createSecondaryClass('''
            package pk

            class External {
                static final Creator Create = new Creator()

                static class Creator {
                    String One() { 'external' }
                }
            }
        ''')

        and:
        createClass('''
            package pk

            @DSL
            class Child {
                String name
            }

            @DSL
            class Parent {
                Child child

                @Mutator
                void configureChild() {
                    child = Child.Create.AsBuilder().With(name: 'child')
                    assert External.Create.One() == 'external'
                }

                @Validate
                void validateCompletedChild() {
                    Child completedChild = Child.Create.One()
                    assert completedChild instanceof Child
                }

                static Child createStandaloneChild() {
                    Child.Create.With()
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    @Issue('644')
    def "static type checking accepts same-session Builder copies in Builder lifecycle code"() {
        when:
        createClass('''
            package pk

            @DSL
            class ProductSource {
                String name
            }
        ''')

        and:
        createClass('''
            package pk

            import com.blackbuild.klum.ast.layer3.AutoCreate

            @DSL
            class ProductCatalog {
                @Default
                void mergeDefaultSource() {
                    ProductSource_DSL.Builder<ProductSource> source = ProductSource.Create.AsBuilder().With(name: 'default source')
                    ProductSource.Create.AsBuilder().With {
                        copyFrom source
                        name 'default recipient'
                    }
                }

                @AutoCreate
                void mergeAutoCreatedSource() {
                    ProductSource_DSL.Builder<ProductSource> source = ProductSource.Create.AsBuilder().With(name: 'auto-created source')
                    ProductSource.Create.AsBuilder().With {
                        copyFrom source
                        name 'auto-created recipient'
                    }
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)

        when:
        clazz.Create.One()

        then:
        noExceptionThrown()
    }

    @Issue('644')
    def "static type checking rejects a Builder from another model as a copy source"() {
        when:
        createClass('''
            package pk

            @DSL
            class ProductSource { }
        ''')

        and:
        createClass('''
            package pk

            @DSL
            class OtherSource { }
        ''')

        and:
        createClass('''
            package pk

            import com.blackbuild.klum.ast.layer3.AutoCreate

            @DSL
            class ProductCatalog {
                @AutoCreate
                void attemptsCrossModelCopy() {
                    ProductSource_DSL.Builder<ProductSource> target = ProductSource.Create.AsBuilder().One()
                    OtherSource_DSL.Builder<OtherSource> source = OtherSource.Create.AsBuilder().One()
                    target.copyFrom(source)
                }
            }
        ''')

        then:
        MultipleCompilationErrorsException error = thrown()
        error.message.contains('copyFrom')
    }
}
