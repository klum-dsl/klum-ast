/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2015-2023 Stephan Pauxberger
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
import spock.lang.Ignore

@SuppressWarnings("GroovyAssignabilityCheck")
class MutatorsSpec extends AbstractDSLSpec {

    def "Mutator methods are moved into RW class"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                @Mutator
                def setNameCaseInsensitive(String name) {
                    this.name == name.toLowerCase()
                }
            }
        ''')

        when:
        rwClazz.getDeclaredMethod("setNameCaseInsensitive", String)

        then:
        notThrown(NoSuchMethodException)

        when:
        clazz.getDeclaredMethod("setNameCaseInsensitive", String)

        then:
        thrown(NoSuchMethodException)
    }

    def "Mutator methods can change state"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                @Mutator
                def setNameCaseInsensitive(String name) {
                    this.name = name.toLowerCase()
                }
            }
        ''')

        when:
        instance = clazz.Create.With {
            nameCaseInsensitive = "Bla"
        }

        then:
        instance.name == 'bla'
    }

    def "setting a field is illegal"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def setNameCaseInsensitive(String name) {
                    this.name = name.toLowerCase()
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "setting a static field is allowed"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                static String global
                
                def setNameCaseInsensitive(String value) {
                    global = value
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "setting a field via setter is illegal"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def setNameCaseInsensitive(String name) {
                    setName(name.toLowerCase())
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "pre/postfix increment/decrement on fields is illegal"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                int count
                
                def increase() {
                    count++
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "unary prefixes are allowed"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                int count
                
                def increase() {
                    def x = -count
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "+= is illegal"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                int count
                
                def increase(int value) {
                    count += value
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "setting a field with unqualified access is illegal"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def setNameCaseInsensitive(String value) {
                    name = value.toLowerCase()
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "setting a variable with the same name as a field legal"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def setNameCaseInsensitive(String value) {
                    def name = value.toLowerCase()
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "bug: def assignment is legal"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                def localVarAssignmentIsLegal() {
                    def value = "blub"
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "Calling a mutator method from a non mutator methods is a compile error"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                @Mutator
                def mutate() {
                }
                def nonmutate() {
                  mutate()
                }
            }
        ''')

        then:
        thrown(MultipleCompilationErrorsException)
    }

    def "Calling a non mutator method from a mutator methods is allowed"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                @Mutator
                def mutate() {
                    nonmutate()
                }
                def nonmutate() {
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    def "Calling a protected non mutator method from a mutator methods is allowed"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                @Mutator
                def mutate() {
                    nonmutate()
                }
                protected nonmutate() {
                }
            }
        ''')

        then:
        notThrown(MultipleCompilationErrorsException)
    }

    @Ignore
    def "Calling a protected non mutator method from a subclass mutator method"() {
        given:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                
                transient boolean called
                
                protected nonmutate() {
                    called = true
                }
            }
        ''')

        // two separate calls mean there is no access to Foo's metadata from Bar
        createClass('''
            package pk2

            @DSL
            class Bar extends pk.Foo {
                String name
                
                @Mutator
                def mutate() {
                    nonmutate()
                }
            }
        ''')

        when:
        def bar = create("pk2.Bar") {
            mutate()
        }

       then:
       bar.called == true
    }

    @Ignore
    def "for debug only"() {
        when:
        createClass('''
            package pk

            @DSL
            class Foo {
                String name
                List<String> values
                
                def variousCalls() {
                    name = "bli"
                    this.name = "bli"
                    values[0] = "bläh"
                    this.values[0] = "bläh"
                    values.add("blub")
                    this.values.add("blub")
                    doIt()
                    this.doIt()
                    def x = 5
                    x = 3
                }
                
                def doIt() {}
            }
        ''')

        then:
        true
    }

}
